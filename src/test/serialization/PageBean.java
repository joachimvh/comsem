package test.serialization;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class PageBean 
{
    public int id;
    public int img;
    public List<PanelBean> panels = new ArrayList<>();
    
    public PageBean () {}
}
