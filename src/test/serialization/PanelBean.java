package test.serialization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.opencv.core.Rect;

@XmlRootElement
public class PanelBean 
{

    public int id;
    public int x;
    public int y;
    public int width;
    public int height;
    public List<String> metadata = new ArrayList<>();
    //public List<String> actions = new ArrayList<>();
    //public Map<String, String> translations = new HashMap<String, String>();
    public List<PanelTextBean> texts = new ArrayList<>();
    
    public PanelBean () {}
    
    public PanelBean (Rect r)
    {
        this.x = r.x;
        this.y = r.y;
        this.width = r.width;
        this.height = r.height;
    }
    
    public Rect toRect ()
    {
        return new Rect(this.x, this.y, this.width, this.height);
    }
}
