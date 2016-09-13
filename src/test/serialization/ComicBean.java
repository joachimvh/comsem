package test.serialization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ComicBean 
{
    public int id;
    public Map<String, List<String>> metadata = new HashMap<String, List<String>>();
    public List<PageBean> pages = new ArrayList<>();
    
    public ComicBean () {}
}
