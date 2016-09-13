package test.jersey;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import test.DatabaseManager;

@Path("/languages/")
public class LanguagesJerseyHandler 
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getLanguages()
    {
        return DatabaseManager.getLanguages();
    }
    
    @POST
    public void addLanguage(String language)
    {
        DatabaseManager.addLanguage(language);
    }
    
    @Path("delete")
    @POST // TODO: not restful
    public void deleteLanguage(String language)
    {
        DatabaseManager.deleteLanguage(language);
    }
}
