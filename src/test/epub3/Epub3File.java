package test.epub3;

import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import com.jamesmurty.utils.XMLBuilder;

// http://code.google.com/p/java-xmlbuilder/wiki/ExampleUsage
public abstract class Epub3File 
{
    private XMLBuilder root;
    
    protected Epub3File (String rootName)
    {
        try
        {
            this.root = XMLBuilder.create(rootName);
        }
        catch (ParserConfigurationException ex)
        {
            ex.printStackTrace();
        }
    }
    
    protected XMLBuilder getRoot ()
    {
        return this.root;
    }
    
    public String toXML ()
    {
        Properties outputProperties = new Properties();
        outputProperties.put(OutputKeys.METHOD, "xml");
        outputProperties.put(OutputKeys.INDENT, "yes");
        outputProperties.put("{http://xml.apache.org/xslt}indent-amount", "4");

        try
        {
            return this.root.asString(outputProperties);
        }
        catch (TransformerException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }
}
