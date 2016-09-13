package test.epub3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import com.jamesmurty.utils.XMLBuilder;

public class Epub3Page extends Epub3File
{    
    // head node
    private XMLBuilder head;
    
    // body node
    private XMLBuilder body;
    
    public Epub3Page ()
    {
        super("html");
        XMLBuilder root = this.getRoot();
        root.namespace("http://www.w3.org/1999/xhtml");
        root.namespace("epub", "http://www.idpf.org/2007/ops");
        
        this.head = root.element("head");
        this.body = root.element("body");
    }
    
    protected XMLBuilder getHead ()
    {
        return this.head;
    }
    
    protected XMLBuilder getBody ()
    {
        return this.body;
    }
}
