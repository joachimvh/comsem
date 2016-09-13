package test.epub3;

import org.opencv.core.Rect;

import com.jamesmurty.utils.XMLBuilder;

public class Epub3PanelPage extends Epub3Page
{
    private XMLBuilder divRoot;
    
    public Epub3PanelPage (String imgPath, Rect rect, Rect original)
    {
        this.getRoot().namespace("schema", "http://schema.org/");
        
        //this.getHead().element("link").a("rel", "stylesheet").a("type", "text/css").a("href", "css/style.css");
        this.getHead().element("meta").a("name", "viewport").a("content", String.format("width=%d, height=%d", rect.width, rect.height));
        this.getHead().element("style").text(
                ".crop-wrapper{ display:block;  overflow:hidden; position:relative; } " +
                // !importants are workarounds for readium trying to fit the img to a single page
                String.format(".crop-wrapper img{position:absolute; max-width: %dpx !important; max-height: %dpx !important;}", original.width, original.height)); 
        
        this.divRoot = this.getBody().element("div");
        this.divRoot.attr("typeof", "schema:MediaObject");
        
        XMLBuilder divImg = this.divRoot.element("div");
        divImg.attribute("property", "schema:image");
        
        XMLBuilder div = divImg.element("div");
        div.attribute("id", "img");
        div.attribute("class", "crop-wrapper");
        div.attribute("style", String.format("width:%dpx; height:%dpx;", rect.width, rect.height));
        
        XMLBuilder img = div.element("img");
        img.attribute("src", imgPath);
        img.attribute("style", String.format("left:-%dpx; top:-%dpx;", rect.x, rect.y));
    }
    
    public void addMetadata (String meta)
    {
        XMLBuilder metaElement = this.divRoot.element("div");
        metaElement.attribute("property", "schema:about");
        metaElement.attribute("resource", meta);
    }
    
    public void addText (String lang, String predicate, String text)
    {
        XMLBuilder metaElement = this.divRoot.element("div");
        if (lang != null)
        {
            metaElement.attribute("xml:lang", lang);
            metaElement.attribute("lang", lang);
        }
        metaElement.attribute("class", "panel_text");
        metaElement.attribute("property", predicate);
        metaElement.attribute("hidden", "hidden");
        if (predicate.equals("dcterms:subject"))
            metaElement.attribute("resource", text);
        else
            metaElement.text(text);
    }
    
    /*public void addTranslation (String lang, String translation)
    {
        XMLBuilder metaElement = this.divRoot.element("div");
        metaElement.attribute("property", "schema:text");
        metaElement.attribute("xml:lang", lang);
        metaElement.attribute("lang", lang);
        metaElement.attribute("hidden", "hidden");
        metaElement.text(translation);
    }
    
    public void addAction (String action)
    {
        XMLBuilder actionElement = this.divRoot.element("div");
        actionElement.attribute("property", "schema:potentialAction");
        actionElement.attribute("resource", action);
        actionElement.attribute("hidden", "hidden");
    }*/
}
