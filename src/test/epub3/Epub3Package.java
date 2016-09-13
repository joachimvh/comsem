package test.epub3;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

import com.jamesmurty.utils.XMLBuilder;

public class Epub3Package extends Epub3File
{
    
    private XMLBuilder metadata;
    private XMLBuilder manifest;
    private XMLBuilder spine;
    
    private List<String> prefixes;
    
    public Epub3Package (String title, String identifier, String language)
    {
        super("package");
        XMLBuilder root = this.getRoot();
        
        root.namespace("http://www.idpf.org/2007/opf");
        root.namespace("dc", "http://purl.org/dc/elements/1.1/");
        root.namespace("dcterms", "http://purl.org/dc/terms/");
        
        prefixes = new ArrayList<>();
        addPrefix("rendition: http://www.idpf.org/vocab/rendition/#");
        addPrefix("schema: http://schema.org/");
        
        root.attribute("unique-identifier", "pub-id");
        root.attribute("version", "3.0");
        
        this.metadata = root.element("metadata");
        
        this.metadata.e("dc:title").text(title);
        this.metadata.e("dc:language").text(language);
        this.metadata.e("dc:identifier").attribute("id", "pub-id").text(identifier);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date now = new Date();
        this.metadata.e("meta").a("property", "dcterms:modified").text(dateFormat.format(now));
        
        this.manifest = root.element("manifest");
        
        this.spine = root.element("spine");
    }
    
    public void addPrefix(String prefix)
    {
        prefixes.add(prefix);
        Element e = getRoot().getElement();
        e.removeAttribute("prefix");
        getRoot().attribute("prefix", StringUtils.join(prefixes, " "));
    }
    
    public void addMeta (XMLBuilder metaElement)
    {
        this.metadata.importXMLBuilder(metaElement);
    }

    public void addManifest (String id, String href, String mediaType)
    {
        this.addManifest(id, href, mediaType, null);
    }
    
    public void addManifest (String id, String href, String mediaType, String properties)
    {
        XMLBuilder item = this.manifest.element("item");
        item.attribute("id", id);
        item.attribute("href", href);
        item.attribute("media-type", mediaType);
        if (properties != null)
        {
            item.attribute("properties", properties);
        }
    }

    public void addSpine (String id)
    {
        this.addSpine(id, null);
    }
    
    public void addSpine (String id, String properties)
    {
        XMLBuilder itemref = this.spine.element("itemref");
        itemref.attribute("idref", id);
        if (properties != null)
        {
            itemref.attribute("properties", properties);
        }
    }
}
