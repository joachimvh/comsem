package test.jersey;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import test.DatabaseManager;
import test.MatUtils;
import test.PanelExtractor;
import test.enrichment.DBPediaSpotlight;
import test.enrichment.IReadPlus;
import test.epub3.Epub3SinglePanelComic;
import test.serialization.ComicBean;
import test.serialization.PageBean;
import test.serialization.PanelBean;

import com.sun.jersey.core.header.ContentDisposition;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.FormDataParam;

@Path("/")
public class JerseyHandler 
{
    
    @GET
    public String works()
    {
        //PanelExtractor.extract();
        return "It works!";
    }
    
    @Path("enrich")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response enrich(String text)
    {
        try
        {
            IReadPlus enricher = new IReadPlus();
            //DBPediaSpotlight enricher = new DBPediaSpotlight();
            List<String> enrichment = enricher.enrich(text, "nl");
            return Response.ok().entity(enrichment).build();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return Response.status(500).entity(ex.getMessage()).build();
        }
    }
    
    /*@Path("upload")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("image/jpeg")
    public ByteArrayInputStream upload(@FormDataParam("file") InputStream uploadedInputStream,
                         @FormDataParam("file") FormDataContentDisposition fileDetail) 
    {
        PanelExtractor extractor = new PanelExtractor();
        try
        {
            Mat m = MatUtils.streamToMat(uploadedInputStream);
            extractor.generateData(m);
            
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ImageIO.write(MatUtils.matToImg(m), "jpeg", outStream);
            
            return new ByteArrayInputStream(outStream.toByteArray());
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        return null;
    }*/
    
    @Path("upload")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public List<Rect> upload(@FormDataParam("img") InputStream uploadedInputStream,
                             @FormDataParam("img") FormDataContentDisposition fileDetail) 
    {
        PanelExtractor extractor = new PanelExtractor();
        try
        {
            Mat m = MatUtils.streamToMat(uploadedInputStream);
            extractor.generateData(m);
            return extractor.rects;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        return new ArrayList<>();
    }

    // if output streaming is needed: http://stackoverflow.com/questions/3496209/input-and-output-binary-streams-using-jersey
    /*@Path("zip")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/epub+zip")
    public Response zip (
            @FormDataParam("title") String title,
            @FormDataParam("id") String id,
            @FormDataParam("title") String lang,
            @FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail)
    {
        ZipInputStream zip = new ZipInputStream(uploadedInputStream);
        try
        {
            PanelExtractor extractor = new PanelExtractor();
            ZipEntry zipEntry = zip.getNextEntry();
            Epub3SinglePanelComic comic = new Epub3SinglePanelComic(title, id, lang);
            while (zipEntry != null)
            {
                Mat m = MatUtils.streamToMat(zip);
                extractor.generateData(m);
                comic.addImage(MatUtils.matToImg(m), extractor.rects);
                
                zipEntry = zip.getNextEntry();
            }
            return Response
                    .ok(new ByteArrayInputStream(comic.generateEPUB3().toByteArray()) , "application/epub+zip")
                    .header("Content-Disposition", ContentDisposition.type("attachment").fileName("comic.epub").creationDate(new Date()).build())
                    .build();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return Response.serverError().build();
        }
    }*/

    /*@Path("EPUB/{id:[a-zA-Z0-9:._/-]+}")
    @GET
    @Produces("application/epub+zip")
    public Response epubGET (@PathParam("id") String id)
    {
        return storage.remove(id).response;
    }*/
    
    
    /*@Path("EPUB")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response epub (FormDataMultiPart multiPart)
    {
        List<List<Rect>> rects = new ArrayList<>();
        List<FormDataBodyPart> rectInput = multiPart.getFields("rect");
        for (FormDataBodyPart rect : rectInput)
        {
            List<Rect> imgRects = new ArrayList<>();
            rects.add(imgRects);
            ObjectMapper mapper = new ObjectMapper();
            try
            {
                JsonNode tree = mapper.readTree(rect.getValue());
                for (int i = 0; i < tree.size(); ++i)
                    imgRects.add(mapper.readValue(tree.get(i), RectBean.class).toRect());
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        List<FormDataBodyPart> fields = multiPart.getFields("img");
        Epub3SinglePanelComic comic = new Epub3SinglePanelComic("title", "id", "nl");
        for (int i = 0; i < fields.size(); ++i)
        {
            FormDataBodyPart imgData = fields.get(i);
            InputStream is = imgData.getValueAs(InputStream.class);
            try
            {
                BufferedImage img = MatUtils.streamToImg(is);
                comic.addImage(img, rects.get(i));
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
        TemporaryStorage data = new TemporaryStorage(response);
        storage.put(data.id, data);
        return Response.ok(data.id).build();
    }*/
    
    /*private class TemporaryStorage
    {
        public long creationDate;
        public Response response;
        public String id;
        public TemporaryStorage(Response response)
        {
            this.response = response;
            this.creationDate = System.currentTimeMillis();
            this.id = UUID.randomUUID().toString();
        }
    }*/
}
