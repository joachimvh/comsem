package test.jersey;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import test.DatabaseManager;

@Path("/meta/")
public class MetadataJerseyHandler 
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getMetadata()
    {
        return DatabaseManager.getMetadata();
    }
    
    @POST
    public void addMetadataEntry(String metadata)
    {
        DatabaseManager.addMetadata(metadata);
    }
    
    @Path("delete")
    @POST // TODO: not restful
    public void deleteMetadataEntry(String metadata)
    {
        DatabaseManager.deleteMetadata(metadata);
    }
}