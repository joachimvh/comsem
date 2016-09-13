package test.jersey;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

import test.DatabaseManager;
import test.serialization.PanelBean;
import test.serialization.PanelTextBean;

@Path("/panel/")
public class PanelJerseyHandler 
{
    @Path("{panel}/rect")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void setRect(@PathParam("panel") int panel, PanelBean rect)
    {
        DatabaseManager.updatePanelRect(panel, rect.toRect());
    }
    
    @Path("{panel}")
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    public void deletePanel(@PathParam("panel") int panel)
    {
        DatabaseManager.deletePanel(panel);
    }
    
    @Path("{panel}/position")
    @POST
    public void changePanelOrder(@PathParam("panel") int panel, String newPos)
    {
        DatabaseManager.changePanelOrder(panel, Integer.parseInt(newPos));
    }

    @Path("{panel}/meta")
    @POST
    public void addPanelMetadata(@PathParam("panel") int panel, String metadata)
    {
        DatabaseManager.addPanelMetadata(panel, metadata);
    }

    @Path("{panel}/meta/delete")
    @POST
    public void deletePanelMetadata(@PathParam("panel") int panel, String metadata)
    {
        DatabaseManager.deletePanelMetadata(panel, metadata);
    }

    /*@Path("{panel}/translation/{lang}")
    @POST
    public void addPanelTranslation(@PathParam("panel") int panel, @PathParam("lang") String lang, String translation)
    {
        DatabaseManager.addPanelTranslation(panel, lang, translation);
    }

    @Path("{panel}/translation/{lang}")
    @DELETE
    public void deletePanelTranslation(@PathParam("panel") int panel, @PathParam("lang") String lang)
    {
        DatabaseManager.deletePanelTranslation(panel, lang);
    }*/

    @Path("{panel}/text")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void addPanelText(@PathParam("panel") int panel, PanelTextBean panelText)
    {
        DatabaseManager.addPanelText(panel, panelText.lang, panelText.predicate, panelText.text);
    }

    @Path("{panel}/text/delete")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void deletePanelText(@PathParam("panel") int panel, PanelTextBean panelText)
    {
        DatabaseManager.deletePanelText(panel, panelText.lang, panelText.predicate, panelText.text);
    }

    /*@Path("{panel}/action")
    @POST
    public void addPanelAction(@PathParam("panel") int panel, String action)
    {
        DatabaseManager.addPanelAction(panel, action);
    }

    @Path("{panel}/action/delete")
    @POST
    public void deletePanelAction(@PathParam("panel") int panel, String action)
    {
        DatabaseManager.deletePanelAction(panel, action);
    }*/
}
