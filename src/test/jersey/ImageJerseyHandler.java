package test.jersey;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import test.DatabaseManager;
import test.MatUtils;
import test.PanelExtractor;

@Path("/img/")
public class ImageJerseyHandler 
{
    @Path("{img}")
    @GET
    @Produces("image/*")
    public byte[] getImage(@PathParam("img") int img)
    {
        return DatabaseManager.getImage(img);
    }
    
    @Path("{img}/rects")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Rect> getRects(@PathParam("img") int img)
    {
        byte[] imgData = getImage(img);
        try
        {
            Mat m = MatUtils.streamToMat(new ByteArrayInputStream(imgData));
            PanelExtractor extractor = new PanelExtractor();
            extractor.generateData(m);
            return extractor.rects;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        return new ArrayList<>();
    }
}
