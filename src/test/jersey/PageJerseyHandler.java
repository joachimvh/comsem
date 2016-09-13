package test.jersey;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import test.DatabaseManager;
import test.serialization.PanelBean;

@Path("/page/")
public class PageJerseyHandler 
{
    @Path("{page}")
    @DELETE
    public void deletePage(@PathParam("page") int page)
    {
        DatabaseManager.deletePage(page);
    }
    
    @Path("{page}/position")
    @POST
    public void changePageOrder(@PathParam("page") int page, String newPos)
    {
        DatabaseManager.changePageOrder(page, Integer.parseInt(newPos));
    }
    
    @Path("{page}/panel")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public String createPanel(@PathParam("page") int page, PanelBean rect)
    {
        return DatabaseManager.addPanel(page, rect.toRect())+"";
    }
    
    @Path("{page}/img")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String storeImage(@PathParam("page") int page, byte[] img)
    {
        int id = DatabaseManager.storeImage(img);
        DatabaseManager.setPageImage(page, id);
        return id+"";
    }
}
