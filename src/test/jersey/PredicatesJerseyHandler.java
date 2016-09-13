package test.jersey;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import test.DatabaseManager;

@Path("/predicates/")
public class PredicatesJerseyHandler 
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getPredicates()
    {
        return DatabaseManager.getPredicates();
    }
    
    @POST
    public void addPredicate(String predicate)
    {
        DatabaseManager.addPredicate(predicate);
    }
    
    @Path("delete")
    @POST // TODO: not restful
    public void deletePredicate(String predicate)
    {
        DatabaseManager.deletePredicate(predicate);
    }
}
