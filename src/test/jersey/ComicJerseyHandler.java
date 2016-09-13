package test.jersey;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.ParserConfigurationException;

import com.jamesmurty.utils.XMLBuilder;
import com.sun.jersey.core.header.ContentDisposition;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import test.DatabaseManager;
import test.MatUtils;
import test.epub3.Epub3SinglePanelComic;
import test.epub3.Epub3Parser;
import test.serialization.ComicBean;
import test.serialization.PageBean;

@Path("/comic/")
public class ComicJerseyHandler 
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ComicBean> getComics()
    {
        return DatabaseManager.listComics();
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String createID()
    {
        return DatabaseManager.createComic()+"";
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public void importEPUB3(Object EPUB3){}
    
    @Path("{comic}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ComicBean comicJson(@PathParam("comic") int comicID)
    {
        return DatabaseManager.getComic(comicID);
    }
    
    @Path("{comic}")
    @DELETE
    public void deleteComic(@PathParam("comic") int comicID)
    {
        DatabaseManager.deleteComic(comicID);
    }
    
    @Path("{comic}/page")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String createPage(@PathParam("comic") int comic) 
    {
        return DatabaseManager.createPage(comic)+"";
    }
    
    @Path("{comic}/meta/{key}")
    @POST
    public void addMetadata(@PathParam("comic") int comic, @PathParam("key") String key, String val)
    {
        DatabaseManager.addComicMetadata(comic, key, val);
    }
    
    @Path("{comic}/meta/{key}/delete")
    @POST
    public void deleteMetadata(@PathParam("comic") int comic, @PathParam("key") String key, String val)
    {
        DatabaseManager.deleteComicMetadata(comic, key, val);
    }
    
    @Path("{comic}/page/order")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void setPageOrder(@PathParam("comic") int comic, List<Integer> pageOrder)
    {
        DatabaseManager.setPageOrder(comic, pageOrder);
    }

    @Path("epub")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    public Response importEPUB3(@Context UriInfo uriInfo,
                              @FormDataParam("upload_epub") InputStream uploadedInputStream,
                              @FormDataParam("upload_epub") FormDataContentDisposition fileDetail)
    {
        try
        {
            int id = Epub3Parser.parse(uploadedInputStream);
            return Response.seeOther(uriInfo.getAbsolutePathBuilder().path("../../../dnd.html").queryParam("comic", id).build().normalize()).build();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return Response.status(500).entity(ex).build();
        }
    }
    
    @Path("{comic}/epub")
    @GET
    @Produces("application/epub+zip")
    public Response epubFromDatabase(@PathParam("comic") int comicID)
    {
        ComicBean comicBean = DatabaseManager.getComic(comicID);
        List<String> title = comicBean.metadata.get("dc:title");
        List<String> isbn = comicBean.metadata.get("dc:identifier");
        List<String> language = comicBean.metadata.get("dc:language");
        List<String> author = comicBean.metadata.get("dc:creator");
        List<String> description = comicBean.metadata.get("dc:description");
        
        Epub3SinglePanelComic comic = new Epub3SinglePanelComic(title == null ? null : title.get(0), "urn:isbn:"+ isbn == null ? null : isbn.get(0), language == null ? null : language.get(0));
        try
        {
            if (author != null && !author.isEmpty())
                comic.addMeta(XMLBuilder.create("dc:creator").a("id", "creator").text(author.get(0)));
            if (description != null && !description.isEmpty())
                comic.addMeta(XMLBuilder.create("dc:description").text(description.get(0)));
            
            List<String> presets = Arrays.asList("dc:title", "dc:identifier", "dc:language", "dc:creator", "dc:description", "dcterms:modified");
            for (String key : comicBean.metadata.keySet())
            {
                if (!presets.contains(key))
                {
                    for (String val : comicBean.metadata.get(key))
                        comic.addMeta(XMLBuilder.create("meta").a("property", key).text(val));
                }
            }
        }
        catch (ParserConfigurationException ex)
        {
            ex.printStackTrace();
        }
        for (PageBean pageBean : comicBean.pages)
        {
            try
            {
                BufferedImage img = MatUtils.streamToImg(new ByteArrayInputStream(DatabaseManager.getImage(pageBean.img)));
                comic.addImage(img, pageBean.panels);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        Response response = Response
            .ok(new ByteArrayInputStream(comic.generateEPUB3().toByteArray()) , "application/epub+zip")
            .header("Content-Disposition", ContentDisposition.type("attachment").fileName("comic.epub").creationDate(new Date()).build())
            .build();
        
        return response;
    }
}
