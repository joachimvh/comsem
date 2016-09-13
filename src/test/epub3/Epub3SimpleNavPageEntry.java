package test.epub3;

import com.jamesmurty.utils.XMLBuilder;

public class Epub3SimpleNavPageEntry extends Epub3File
{
    
    private XMLBuilder ol;
    
    public Epub3SimpleNavPageEntry (String href, String text)
    {
        super("li");
        
        this.getRoot().element("a").attribute("href", href).text(text);
        this.ol = null;
    }
    
    public void addChild (Epub3SimpleNavPageEntry entry)
    {
        if (this.ol == null)
        {
            this.ol = this.getRoot().element("ol");
        }
        this.ol.importXMLBuilder(entry.getRoot());
    }
}
