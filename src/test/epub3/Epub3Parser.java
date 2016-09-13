package test.epub3;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.opencv.core.Rect;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.jamesmurty.utils.XMLBuilder;

import test.DatabaseManager;
import test.MatUtils;
import test.jersey.ComicJerseyHandler;
import test.serialization.ComicBean;
import test.serialization.PageBean;
import test.serialization.PanelBean;
import test.serialization.PanelTextBean;

public class Epub3Parser 
{

    public static int parse (InputStream in) throws IOException
    {
        ZipInputStream zip = new ZipInputStream(in, Charset.forName("UTF-8"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(zip, Charset.forName("UTF-8")));
        

        DocumentBuilder builder;
        XPath xpath;
        
        try
        {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            xpath = XPathFactory.newInstance().newXPath();
        }
        catch (ParserConfigurationException ex)
        {
            throw new IOException(ex);
        }
        
        ZipEntry entry;
        HashMap<String, List<String>> metadata = new HashMap<>();
        HashMap<Integer, List<PanelBean>> panels = new HashMap<>();
        HashMap<Integer, byte[]> images = new HashMap<>();
        while ((entry = zip.getNextEntry()) != null)
        {
            String name = entry.getName();
            
            //System.out.println(name);
            if (name.equals("EPUB/package.opf"))
            {
                StringBuffer xml = new StringBuffer();
                String line;
                while ((line = reader.readLine()) != null)
                    xml.append(line);
                try
                {
                    Document document = builder.parse(new InputSource(new StringReader(xml.toString()))); // Document automatically closes stream, so can't give it zip stream
                    XPathExpression expr = xpath.compile("//package/metadata");
                    NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                    nl = nl.item(0).getChildNodes();
                    for (int i = 0; i < nl.getLength(); ++i)
                    {
                        Node item = nl.item(i);
                        if (item.getNodeName().equals("#text"))
                            continue;
                        String meta = item.getNodeName();
                        if (meta.equals("meta") && item.getAttributes().getNamedItem("property") != null)
                            meta = item.getAttributes().getNamedItem("property").getTextContent();
                        if (!metadata.containsKey(meta))
                            metadata.put(meta, new ArrayList<String>());
                        metadata.get(meta).add(item.getTextContent());
                    }
                }
                catch (SAXException | XPathExpressionException ex)
                {
                    throw new IOException(ex);
                }
            }
            else if (name.startsWith("EPUB/panel") && name.endsWith(".xhtml"))
            {
                String sizeStyle = "";
                String shiftStyle = "";
                List<String> resources = new ArrayList<>();
                List<PanelTextBean> texts = new ArrayList<>();
                StringBuffer xml = new StringBuffer();
                String line;
                while ((line = reader.readLine()) != null)
                    xml.append(line);
                try
                {
                    Document document = builder.parse(new InputSource(new StringReader(xml.toString()))); // Document automatically closes stream, so can't give it zip stream
                    XPathExpression expr = xpath.compile("//div[@class='crop-wrapper']");
                    NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                    sizeStyle = nl.item(0).getAttributes().getNamedItem("style").getTextContent();
                    
                    expr = xpath.compile("img");
                    nl = (NodeList)expr.evaluate(nl.item(0), XPathConstants.NODESET);
                    shiftStyle = nl.item(0).getAttributes().getNamedItem("style").getTextContent();
                    
                    // metadata
                    expr = xpath.compile("//div[@property='schema:about']");
                    nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                    for (int i = 0; i < nl.getLength(); ++i)
                        resources.add(nl.item(i).getAttributes().getNamedItem("resource").getTextContent());
                    
                    // texts
                    expr = xpath.compile("//div[@class='panel_text']");
                    nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                    for (int i = 0; i < nl.getLength(); ++i)
                    {
                        String lang = null;
                        Node langNode = nl.item(i).getAttributes().getNamedItem("xml:lang");
                        if (langNode != null)
                            lang = langNode.getTextContent();
                        String predicate = nl.item(i).getAttributes().getNamedItem("property").getTextContent();
                        String text;
                        Node resourceNode = nl.item(i).getAttributes().getNamedItem("resource");
                        if (resourceNode == null)
                            text = nl.item(i).getTextContent();
                        else
                            text = resourceNode.getTextContent();
                        texts.add(new PanelTextBean(lang, predicate, text));
                    }
                }
                catch (SAXException | XPathExpressionException ex)
                {
                    throw new IOException(ex);
                }
                
                // will totally crash if there are no matches
                Matcher matcher = Pattern.compile("width\\D*(\\d*)").matcher(sizeStyle);
                matcher.find();
                int width = Integer.parseInt(matcher.group(1));
                matcher = Pattern.compile("height\\D*(\\d*)").matcher(sizeStyle);
                matcher.find();
                int height = Integer.parseInt(matcher.group(1));
                matcher = Pattern.compile("left\\D*(\\d*)").matcher(shiftStyle);
                matcher.find();
                int left = Integer.parseInt(matcher.group(1)); // we don't want the negation symbol anyway
                matcher = Pattern.compile("top\\D*(\\d*)").matcher(shiftStyle);
                matcher.find();
                int top = Integer.parseInt(matcher.group(1));
                
                PanelBean panel = new PanelBean(new Rect(left, top, width, height));
                panel.metadata = resources;
                panel.texts = texts;
                
                matcher = Pattern.compile("panel(\\d*)_(\\d*).xhtml").matcher(name);
                int id = -1;
                int pos = -1;
                if (matcher.find())
                {
                    id = Integer.parseInt(matcher.group(1));
                    pos = Integer.parseInt(matcher.group(2)) - 1; // counting starts at 1
                }
                if (!panels.containsKey(id))
                    panels.put(id, new ArrayList<PanelBean>());
                List<PanelBean> panelList = panels.get(id);
                while (panelList.size() <= pos)
                    panelList.add(null);
                panelList.set(pos, panel);
            }
            else if (name.endsWith(".jpg"))
            {
                Pattern pattern = Pattern.compile("img(\\d*).jpg");
                Matcher matcher = pattern.matcher(name);
                int id = -1;
                if (matcher.find())
                    id = Integer.parseInt(matcher.group(1));
                images.put(id, IOUtils.toByteArray(zip));
            }
        }
        
        return DatabaseManager.createFullComic(metadata, images, panels);
    }
}
