package test.epub3;

import com.jamesmurty.utils.XMLBuilder;

public class Epub3SimpleNavPage extends Epub3Page
{

    private XMLBuilder ol;
    
    public Epub3SimpleNavPage ()
    {
        this.ol = this.getBody().e("nav").a("epub:type", "toc").e("ol");
    }
    
    public void addEntry (Epub3SimpleNavPageEntry entry)
    {
        this.ol.importXMLBuilder(entry.getRoot());
    }
}
