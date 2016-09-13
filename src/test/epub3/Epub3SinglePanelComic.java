package test.epub3;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.FilenameUtils;
import org.opencv.core.Rect;

import test.serialization.PanelBean;
import test.serialization.PanelTextBean;

import com.jamesmurty.utils.XMLBuilder;

public class Epub3SinglePanelComic
{
    
    private Epub3Package pckg;
    private Epub3SimpleNavPage nav;
    private Map<String, BufferedImage> images;
    private Map<String, Epub3PanelPage> panels;
    private int imgCounter = 0;
    
    public Epub3SinglePanelComic (String title, String identifier, String language)
    {
        this.pckg = new Epub3Package(title, identifier, language);
        /*try
        {
            this.pckg.addMeta(XMLBuilder.create("meta").a("property", "rendition:layout").text("pre-paginated"));
        }
        catch (ParserConfigurationException ex)
        {
            ex.printStackTrace();
        }*/
        this.nav = new Epub3SimpleNavPage();
        this.pckg.addManifest("toc", "toc.xhtml", "application/xhtml+xml", "nav");
        
        this.images = new HashMap<>();
        this.panels = new HashMap<>();
    }
    
    public void addMeta (XMLBuilder meta)
    {
        this.pckg.addMeta(meta);
    }

    public void addImage (BufferedImage img, List<PanelBean> panels)
    {
        ++imgCounter;
        String imgName = "img" + imgCounter;
        String imgPath = "img/" + imgName + ".jpg";
        Rect original = new Rect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < panels.size(); ++i)
        {
            String name = "panel"+imgCounter+"_"+(i+1);
            Epub3PanelPage panel = new Epub3PanelPage(imgPath, panels.get(i).toRect(), original);
            for (String meta : panels.get(i).metadata)
                panel.addMetadata(meta);
            for (PanelTextBean textBean : panels.get(i).texts)
                panel.addText(textBean.lang, textBean.predicate, textBean.text);
            this.panels.put(name+".xhtml", panel);
            this.pckg.addManifest(name, name+".xhtml", "application/xhtml+xml");
            this.pckg.addSpine(name);
        }
        
        this.images.put(imgPath, img);
        this.pckg.addManifest(imgName, imgPath, "image/jpeg");
        this.nav.addEntry(new Epub3SimpleNavPageEntry("panel"+imgCounter+"_"+1+".xhtml", "Page " + imgCounter));
    }
    
    public ByteArrayOutputStream generateEPUB3 ()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out, Charset.forName("UTF-8"));
        //ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out);
        OutputStreamWriter writer = new OutputStreamWriter(zip, Charset.forName("UTF-8"));
        
        try
        {
            //zip.setEncoding("UTF-8");
            zip.setMethod(ZipOutputStream.STORED);
            // need to start with mimetype file
            ZipEntry entry = new ZipEntry("mimetype");
            //ZipArchiveEntry entry = new ZipArchiveEntry("mimetype");
            String s = "application/epub+zip";
            entry.setCompressedSize(s.length());
            entry.setSize(s.length());
            entry.setCrc(0x2cab616f);
            zip.putNextEntry(entry);
            //zip.putArchiveEntry(entry);
            writer.append(s);
            writer.flush();
            //zip.write("application/epub+zip".getBytes("UTF-8"));
            zip.closeEntry();
            //zip.closeArchiveEntry();
            
            zip.setMethod(ZipOutputStream.DEFLATED); // only manifest needs to be uncompressed

            // META-INF data
            zip.putNextEntry(new ZipEntry("META-INF/container.xml"));
            XMLBuilder container = XMLBuilder
                    .create("container").ns("urn:oasis:names:tc:opendocument:xmlns:container").a("version", "1.0")
                    .e("rootfiles")
                    .e("rootfile").a("full-path", "EPUB/package.opf").a("media-type", "application/oebps-package+xml");

            Properties outputProperties = new Properties();
            outputProperties.put(OutputKeys.METHOD, "xml");
            outputProperties.put(OutputKeys.INDENT, "yes");
            outputProperties.put("{http://xml.apache.org/xslt}indent-amount", "4");
            writer.append(container.asString(outputProperties));
            writer.flush();
            zip.closeEntry();
            
            // EPUB data
            // opf package
            zip.putNextEntry(new ZipEntry("EPUB/package.opf"));
            writer.append(this.pckg.toXML());
            writer.flush();
            zip.closeEntry();
            
            // toc file
            zip.putNextEntry(new ZipEntry("EPUB/toc.xhtml"));
            writer.append(this.nav.toXML());
            writer.flush();
            zip.closeEntry();
            
            // panel metadata
            for (String panelPath : this.panels.keySet())
            {
                zip.putNextEntry(new ZipEntry("EPUB/" + panelPath));
                writer.append(this.panels.get(panelPath).toXML());
                writer.flush();
                zip.closeEntry();
            }
            
            // image data
            for (String imgPath: images.keySet())
            {
                BufferedImage img = images.get(imgPath);
                zip.putNextEntry(new ZipEntry("EPUB/"+imgPath));
                ImageIO.write(img, "jpg", zip);
                zip.closeEntry();
            }
            
            //writer.close();
            zip.close();
        }
        catch (IOException | ParserConfigurationException | TransformerException ex)
        {
            ex.printStackTrace();
        }
        
        return out;
    }
}
