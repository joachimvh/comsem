package test.serialization;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class PanelTextBean
{
    public String lang;
    public String predicate;
    public String text;
    
    public PanelTextBean () {}
    
    public PanelTextBean (String lang, String predicate, String text) 
    {
        this.lang = lang;
        this.predicate = predicate;
        this.text = text;
    }
}
