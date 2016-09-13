package test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileSystemProvider;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.jamesmurty.utils.XMLBuilder;
import com.sun.jersey.core.header.ContentDisposition;
import com.twelvemonkeys.io.enc.Base64Encoder;

import test.enrichment.DBPediaSpotlight;
import test.enrichment.IReadPlus;
import test.epub3.Epub3Package;
import test.epub3.Epub3PanelPage;
import test.epub3.Epub3SinglePanelComic;
import test.epub3.Epub3Parser;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class Main {

    static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }
    
    public static String geoFromName (String name) throws Exception {
        String url = "http://api.geonames.org/search?q=" + URLEncoder.encode(name, "UTF-8") + "&maxRows=1&username=Joachimvh&style=short&type=json";
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = null;
        while (root == null) {
            try {
                root = mapper.readTree(getDataFromAddress(url)).get("geonames");
            } catch (ConnectException ex) {
                System.out.println("sleeping");
                Thread.sleep(5000);
            }
        }
        if (root.size() == 0)
            return null;
        root = root.get(0);
        return root.get("lat").asText() + "," + root.get("lng").asText();
    }
    
    public static String getDataFromAddress (String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        byte[] result = datafromConnection(connection, null);
        return new String(result);
    }
    
    public static byte[] datafromConnection (HttpURLConnection connection, String request) throws IOException {
        // fiddler stuff
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "8888");
        System.setProperty("https.proxyPort", "8888");
        
        connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.connect();
        
        if (connection.getDoOutput() && request != null) {
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(request);
            wr.close();
        }

        String response = "";
        
        InputStream stream = null;
        int responseCode = connection.getResponseCode();
        stream = connection.getInputStream();
        if (stream == null)
            stream = connection.getErrorStream();
        ArrayList<Byte> bytes = new ArrayList<>();
        if (stream != null) {
            int read = stream.read();
            while (read >= 0) {
                response += (char)read;
                bytes.add((byte)read);
                read = stream.read();
            }
            //reader.close();
        }
        connection.disconnect();
        response = new String(response.getBytes(), "UTF-8");
        if (responseCode >= 300) {
            throw new IOException("Server returned HTTP response code: " + responseCode + " for URL " + connection.getURL() + "(" + response + ")");
        }
        byte[] array = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); ++i)
            array[i] = bytes.get(i);
        return array;
    }
    
    private static void increaseMapValue (HashMap<String, Integer> map, String key) {
        if (!map.containsKey(key))
            map.put(key, 0);
        map.put(key, map.get(key)+1);
    }
    
    private static List<String> extractWikiLinks (String input) {
        ArrayList<String> result = new ArrayList<>();
        int start = input.indexOf("[[");
        while (start >= 0) {
            int end = input.indexOf("]]");
            result.add(input.substring(start+2, end));
            input = input.substring(end+2);
            start = input.indexOf("[[");
        }
        
        return result;
    }
    
    
    public static void main (String[] args) throws Exception 
    {        
        //Epub3Parser.parse(new FileInputStream("C:/Users/jvherweg/Desktop/comic.epub"));
        
        DBPediaSpotlight sl = new DBPediaSpotlight();
        IReadPlus irp = new IReadPlus();
        
        File file = new File(".data/wpg/tekst/flapteksten.txt");
        BufferedReader reader = new BufferedReader(new FileReader(file));
        BufferedWriter writer = new BufferedWriter(new FileWriter(".data/wpg/tekst/results_irp.txt"));
        String line = reader.readLine();
        int count = 0;
        while (line != null)
        {
            String txt = new String(line.getBytes(), Charset.forName("UTF-8"));
            
            try
            {
                HashSet<String> result = new HashSet<>(irp.enrich(txt, "nl"));
                //HashSet<String> result = new HashSet<>(sl.enrich(txt));
                
                String output = StringUtils.join(result,';');
                writer.write(output + "\n");
                System.out.println(output);
            }
            catch (IOException ex)
            {
                writer.write("\n");
            }
            writer.flush();
            
            System.out.println(++count);
            
            line = reader.readLine();
        }
        reader.close();
        writer.close();
        
        
        /*HttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost("http://enrichr.test.iminds.be:8080/EnrWs/v1/srcdocs");
        MultipartEntityBuilder multipart = MultipartEntityBuilder.create();
        //multipart.addBinaryBody("srcdoc", new File(".data/wpg/tekst/test.xml"));
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?> <content>De nieuwsgierigheid van Wiske brengt onze vrienden in een grot waar ze drie kwelduivels ontmoeten die al 1500 jaar opgesloten zitten. Hoewel Jerom denkt dat hij de drie duiveltjes overwonnen heeft, zijn ze niet van hen af! Ze slaan Barabas buiten westen, die valt op de teletijdmachine en de kwelduivels worden teruggeflitst naar hun eigen tijd! Jerom gaat hen achterna om de Nerviërs te behoeden voor de drie pestkoppen. Dat blijkt niet zo eenvoudig. Er zit voor Jerom niets anders op dan zich in te burgeren in het leven van de Nerviërs en hij wordt verliefd op de fee Libelildis</content>";
        multipart.addTextBody("srcdoc", input, ContentType.create("text/plain", MIME.UTF8_CHARSET));
        multipart.addTextBody("lang", "nl");
        post.setEntity(multipart.build());
        post.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
        HttpResponse response = client.execute(post);
        System.out.println(response);
        
        String src = response.getFirstHeader("Location").getValue();
        String ctl = "http://enrichr.test.iminds.be:8080/EnrWs/v1/ctldoc/irp-demo-2014-03-26-00001-ctl";
        post.releaseConnection();

        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair("srcdocuri", src));
        pairs.add(new BasicNameValuePair("ctldocuri", ctl));
        post = new HttpPost("http://enrichr.test.iminds.be:8080/EnrWs/v1/enrdocgen");
        post.setEntity(new UrlEncodedFormEntity(pairs));
        post.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
        response = client.execute(post);
        System.out.println(response);
        String queryLocation = response.getFirstHeader("Content-Location").getValue();
        post.releaseConnection();
        
        HttpGet query = new HttpGet(queryLocation);
        query.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
        boolean finished = false;
        String enr = null;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        XPath xpath = XPathFactory.newInstance().newXPath();
        
        while (!finished)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex) { }
            
            response = client.execute(query);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 400)
            {
                finished = true;
                System.out.println(response);
            }
            else if (status < 300)
            {
                Document document = builder.parse(response.getEntity().getContent());
                XPathExpression expr = xpath.compile("//enrdocgenjobstate/state");
                NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                String state = nl.item(0).getTextContent();
                if (state.equals("failed") || state.equals("done"))
                {
                    finished = true;
                    System.out.println(response);
                    if (state.equals("done"))
                    {
                        enr = response.getFirstHeader("Location").getValue();
                    }
                }  
            }
            query.releaseConnection();
        }

        HttpDelete delete = new HttpDelete(src);
        delete.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
        response = client.execute(delete);
        System.out.println(response);
        IOUtils.copy(response.getEntity().getContent(), System.out);
        delete.releaseConnection();
        
        if (enr != null)
        {
            HttpGet get = new HttpGet(enr);
            get.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
            response = client.execute(get);
            System.out.println(response);
            if (response.getStatusLine().getStatusCode() == 200)
            {
                Document document = builder.parse(response.getEntity().getContent());
                XPathExpression expr = xpath.compile("//TEI/text/body/div/p/PEnrich/PNerd/ne");
                
                NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                for (int i = 0; i < nl.getLength(); ++i)
                {
                    System.out.println(nl.item(i).getTextContent());
                }
            }
            get.releaseConnection();

            delete = new HttpDelete(enr);
            delete.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
            response = client.execute(delete);
            System.out.println(response);
            IOUtils.copy(response.getEntity().getContent(), System.out);
            delete.releaseConnection();
        }*/
        
        //HttpPost post = new HttpPost("http://enrichr.test.iminds.be:8080/EnrWsDemo/DocumentEnricher");
        /*List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair("usr", DatatypeConverter.printBase64Binary("irp.demo.demo".getBytes())));
        pairs.add(new BasicNameValuePair("pw", DatatypeConverter.printBase64Binary("demo8500".getBytes())));
        pairs.add(new BasicNameValuePair("lang", "nl"));
        pairs.add(new BasicNameValuePair("text", "Wiske en Tante Sidonia vissen een oude kruik op uit de Schelde die hen en professor Barabas op het mysterieuze eiland Amoras brengt waar ze Suske ontmoeten. Als Sidonia en Barabas gevangen genomen worden, proberen Suske en Wiske hen te bevrijden. Ze krijgen hulp van Sus Antigoon en slagen erin om de valse machthebbers van het eiland te verslaan. Suske kan zijn Wiske niet meer missen en verlaat Amoras om mee in Vlaanderen te gaan wonen."));
        post.setEntity(new UrlEncodedFormEntity(pairs));
        
        HttpResponse response = client.execute(post);
        System.out.println(response);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response.getEntity().getContent(), writer);
        String output = writer.toString();
        System.out.println(output);
        if (response.getStatusLine().getStatusCode() == 200 && !output.substring(2, 14).equals("errorMessage"))
        {
            // sample: {"enrdocid":"irp-demo-nl-2014-05-26-00006-enr"}
            output = output.substring("{\"enrdocid\":\"".length(), output.length()-2);
            System.out.println(output);
            
            HttpGet get = new HttpGet("http://enrichr.test.iminds.be:8080/EnrWs/v1/enrdoc/"+output);
            get.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
            response = client.execute(get);
            System.out.println(response);
            if (response.getStatusLine().getStatusCode() == 200)
            {
                Document document = builder.parse(response.getEntity().getContent());
                XPathExpression expr = xpath.compile("//TEI/text/body/div/p/PEnrich/PNerd/ne");
                
                NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                for (int i = 0; i < nl.getLength(); ++i)
                {
                    System.out.println(nl.item(i).getTextContent());
                }
            }
            
            HttpDelete delete = new HttpDelete("http://enrichr.test.iminds.be:8080/EnrWs/v1/enrdoc/"+output);
            delete.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
            response = client.execute(delete);
            System.out.println(response);
            IOUtils.copy(response.getEntity().getContent(), System.out);
        }*/
        
        //PanelExtractor.extract();
        //TextExtractor.extract();
        //SceneExtractor.extract();
        //FaceDetector.doStuff();
        //AdvancedPanelExtractor.doStuff();
        //SceneExtractor.extract();
        
        /*File root = new File(".data/wpg/strips/SUSKE EN WISKE");
        for (File comic : root.listFiles())
        {
            System.out.println(comic.getName());
            PanelExtractor extractor = new PanelExtractor();
            Epub3SinglePanelComic epub = new Epub3SinglePanelComic(comic.getName(), comic.getName(), "nl");
            
            File[] xmls = comic.listFiles(new FilenameFilter() 
            {
                public boolean accept(File dir, String name) 
                {
                    return name.substring(name.length()-3).equalsIgnoreCase("xml");
                }
            });
            
            if (xmls == null)
                continue;
            
            if (xmls.length > 0)
            {
                XMLBuilder xml = XMLBuilder.parse(new InputSource(new FileInputStream(xmls[0])));
                String title = xml.xpathFind("//titel").getElement().getTextContent();
                String isbn = xml.xpathFind("//isbn").getElement().getTextContent();
                epub = new Epub3SinglePanelComic(title, isbn, "nl");
            }

            Files.createDirectories(FileSystems.getDefault().getPath(".data", "wpg", "strips", "SUSKE EN WISKE", "EPUB", comic.getName()));
            int panelID = 0;
            for (File panel : comic.listFiles())
            {
                if (panel.getName().substring(panel.getName().length()-3).equals("jpg"))
                {
                    ++panelID;
                    Mat m = MatUtils.fileToMat(panel.getPath());
                    extractor.generateData(m);
                    epub.addImage(MatUtils.matToImg(m), extractor.rects);
                    for (int i = 0; i < extractor.rects.size(); ++i)
                    {
                        Mat sub = m.submat(extractor.rects.get(i));
                        MatUtils.matToFile(sub, FileSystems.getDefault().getPath(".data", "wpg", "strips", "SUSKE EN WISKE", "EPUB", comic.getName(), "img" + panelID + "_" + (i+1) + ".jpg"));
                    }
                }
            }
            ByteArrayOutputStream out = epub.generateEPUB3();
            Files.write(FileSystems.getDefault().getPath(".data", "wpg", "strips", "SUSKE EN WISKE", "EPUB", comic.getName() + ".epub"), out.toByteArray(), StandardOpenOption.CREATE);
        }*/
        
        //Epub3PanelPage page = new Epub3PanelPage(null, new Rect(5, 10, 15, 20));
        //System.out.println(page.toXML());
        
        //System.out.println(new Epub3Package("TITLE", "IDENTIFIER", "nl").toXML());
        
        /*Mat thresh = new Mat(); 
        Mat m = MatUtils.fileToMat(".data/wpg/text_vbig.png");
        Imgproc.cvtColor(m, thresh, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(thresh, thresh, 100, 255, Imgproc.THRESH_BINARY);

        Files.write(FileSystems.getDefault().getPath("matrix.txt"), "".getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        //for (int x = 0; x < m.width(); ++x)
        {
            for (int y = 0; y < thresh.height(); ++y)
            {
                Files.write(FileSystems.getDefault().getPath("matrix.txt"), thresh.row(y).dump().getBytes(), StandardOpenOption.APPEND);
                Files.write(FileSystems.getDefault().getPath("matrix.txt"), "\n".getBytes(), StandardOpenOption.APPEND);
            }
        }*/
        
        //FaceDetector.generateTrainingData();
        //FaceDetector.doStuff();
        
        /*PanelExtractor extractor = new PanelExtractor();
        Mat m = MatUtils.fileToMat(".data/wpg/strips/SUSKE EN WISKE/SW255/S&W255_149-150.jpg");
        extractor.generateData(m);
        MatUtils.drawContours(m, extractor.contours);
        MatUtils.drawRects(m, extractor.rects);
        MatUtils.displayMat(m);*/
        
        /*Epub3SinglePanelComic comic = new Epub3SinglePanelComic("TITLE", "ID", "nl");
        PanelExtractor extractor = new PanelExtractor();
        Mat m = MatUtils.fileToMat(".data/wpg/amorasB.jpg");
        extractor.generateData(m);
        comic.addImage(MatUtils.matToImg(m), extractor.rects);
        m = MatUtils.fileToMat(".data/wpg/amorasB2.jpg");
        extractor.generateData(m);
        comic.addImage(MatUtils.matToImg(m), extractor.rects);
        ByteArrayOutputStream out = comic.generateEPUB3();
        FileOutputStream fileStream = new FileOutputStream(".data/epub/test.epub");
        out.writeTo(fileStream);
        out.close();
        fileStream.close();*/
        
        /*Mat m = MatUtils.fileToMat(".data/wpg/SW32-DRR-009-012-lay-03.color.panel.tif");
        MatOfInt channels = new MatOfInt(0);
        Mat out1 = new Mat();
        Mat out2 = new Mat();
        Mat out3 = new Mat();
        MatOfInt histSize = new MatOfInt(16);
        MatOfFloat ranges = new MatOfFloat(0.0f, 255.0f);
        int hw = 1024;
        int hh = 512;
        
        List<Mat> ars = new ArrayList<>(Arrays.asList(new Mat(), new Mat(), new Mat()));
        Core.split(m, ars);
        Imgproc.calcHist(ars.subList(0, 1), channels, new Mat(), out1, histSize, ranges);
        Imgproc.calcHist(ars.subList(1, 2), channels, new Mat(), out2, histSize, ranges);
        Imgproc.calcHist(ars.subList(2, 3), channels, new Mat(), out3, histSize, ranges);
        
        Mat histImage = new Mat(hh, hw, CvType.CV_8UC3, new Scalar(255, 255, 255));
        Core.normalize(out1, out1, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());
        Core.normalize(out2, out2, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());
        Core.normalize(out3, out3, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());
        for( int i = 1; i < histSize.toArray()[0]; i++)
        {
            Core.line(histImage, 
                    new Point((hw/(histSize.toArray()[0]-1))*(i-1), hh - out1.get(i-1, 0)[0]) ,
                    new Point((hw/(histSize.toArray()[0]-1))*(i), hh - out1.get(i, 0)[0]),
                    new Scalar(255, 0, 0), 
                    2, 8, 0);
            Core.line(histImage, 
                    new Point((hw/(histSize.toArray()[0]-1))*(i-1), hh - out2.get(i-1, 0)[0]) ,
                    new Point((hw/(histSize.toArray()[0]-1))*(i), hh - out2.get(i, 0)[0]),
                    new Scalar(0, 255, 0), 
                    2, 8, 0);
            Core.line(histImage, 
                    new Point((hw/(histSize.toArray()[0]-1))*(i-1), hh - out3.get(i-1, 0)[0]) ,
                    new Point((hw/(histSize.toArray()[0]-1))*(i), hh - out3.get(i, 0)[0]),
                    new Scalar(0, 0, 255), 
                    2, 8, 0);
        }
        MatUtils.displayMat(histImage);*/
        
        /*File dir = new File(".data/wpg/strips_data/SUSKE EN WISKE_324/324SW_BW map/Links");
        List<File> files = new ArrayList<>();
        for (File child : dir.listFiles())
        {
            //if (child.getName().substring(0, 5).equals("SW32-"))
            if (child.getName().contains("SW32-DRR-045-048"))
            {
                files.add(child);
            }
        }
        Collections.sort(files);
        PanelExtractor pe = new PanelExtractor();
        ExactTextExtractor ete = new ExactTextExtractor();
        ExactTextMatcher etm = new ExactTextMatcher();
        for (int i = 0; i < files.size()-1; i+=2)
        {
            System.out.println(files.get(i).getName());
            
            Mat m = MatUtils.mergeInkColor(MatUtils.fileToMat(files.get(i+1).getPath()), MatUtils.fileToMat(files.get(i).getPath()));
            
            pe.generateData(m);
            
            for (Rect r : pe.rects)
            {
                Mat submat = m.submat(r);
                ete.generateData(submat);
                
                // TODO: automatic font size detection would fix the fact that panels get matched
                for (int parent : ete.charData.keySet())
                {
                    for (ExactTextMatch child : ete.charData.get(parent))
                    {
                        System.out.print(etm.matToString(child));
                        MatUtils.drawRect(submat, child.r);
                    }
                    System.out.println();
                    System.out.println();
                }
                //MatUtils.displayMat(submat);
                //MatUtils.drawRect(m, r);
            }
           // MatUtils.displayMat(m);
        }*/
        
        /*String input = "I 5AID WE EHOULD ETART BACK. THE WILDLINGE ARE DEAD.".toLowerCase();
        String compare = "\"We should start back,\" Gared urged as the woods began to grow dark around them. \"The wildlings are dead.\"".toLowerCase();
        
        List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath(".data", "got", "text", "0_Prologue.txt"), Charset.forName("UTF-8"));
        String text = StringUtils.join(lines, ' ').toLowerCase();*/
        
        /*Mat m = MatUtils.fileToMat(".data/wpg/text_vbig5.png");
        ExactTextExtractor ete = new ExactTextExtractor();
        ete.generateData(m);
        ExactTextMatcher etm = new ExactTextMatcher();
        // TODO: need some way to indicate position of text blocks (need to account for multiple blocks in one box)
        for (int parent : ete.charData.keySet())
        {
            for (ExactTextMatch child : ete.charData.get(parent))
            {
                MatUtils.drawRect(m, child.r);
                System.out.print(etm.matToString(child));
            }
            System.out.println();
            System.out.println();
        }
        MatUtils.displayMat(m);*/

        /*Mat m2 = MatUtils.fileToMat(".data/wpg/SW32-DRR-009-012-lay-03.cmy2.tif");
        Imgproc.resize(m2, m2, m.size());
        Core.bitwise_and(m, m2, m2);
        MatUtils.matToFile(m2, ".data/wpg/SW32-DRR-009-012-lay-03.color.tif");*/
        
        //TextMatcher.combineImagesText();
        //TextMatcher.smithWaterman(input, text);
        
        //TextMatcher.match(input, text);
        //TextMatcher.extract();
        
        /*int dist = StringUtils.getLevenshteinDistance(input, compare);
        System.out.println(dist);
        
        System.out.println(OptimalStringAlignment.editDistance(input, compare, 100));*/
       
        /*input = "kk aba k ccc kk";
        compare = "c abba c cac dd";
        NeedlemanWunsch nw = new NeedlemanWunsch();
        nw.init(input.toCharArray(), compare.toCharArray());
        nw.process();
        nw.backtrack();
       
        nw.printScoreAndAlignments();
        System.out.println(1.0*nw.mScore/compare.length());
        
        for (int i = 0; i < nw.mSeqA.length; ++i)
        {
            for (int j = 0; j < nw.mSeqB.length; ++j)
            {
                System.out.print(String.format("%3d ", nw.mD[i][j]));
            }
            System.out.println("");
        }*/
        
        /*for (int i = 32; i < 127; ++i) {
            for (int j = 32; j < 127; ++j) {
                System.out.print((char)i + "" + (char)j );
            }
        }*/
        
        /*CSVReader reader = new CSVReader(Files.newBufferedReader(FileSystems.getDefault().getPath("suskewiske.csv"), Charset.forName("UTF-8")), ',');
        String[] csv;
        HashMap<String, Integer> locations = new HashMap<>();
        HashMap<String, Integer> characters = new HashMap<>();
        ArrayList<Album> albums = new ArrayList<>();
        while ((csv = reader.readNext()) != null) {
            Album a = Album.parse(csv);
            albums.add(a);
            for (String location : a.locations)
                increaseMapValue(locations, location);
            for (String character : a.characters)
                increaseMapValue(characters, character);
        }
        reader.close();*/
        
        /*HashSet<String> allCharacters = new HashSet<>();
        for (Album a : albums) {
            ArrayList<String> newCharacters = new ArrayList<>();
            for (String character : a.characters) {
                character = character.replaceAll("\\(.*\\)", "").trim();
                if (character.contains(" en ")) {
                    for (String subCharacter : character.split(" en "))
                        newCharacters.add(subCharacter.trim());
                } else {
                    newCharacters.add(character);
                }
            }
            while (newCharacters.remove(""));
            a.characters = newCharacters;
            allCharacters.addAll(newCharacters);
        }
        HashMap<String, Integer> relations = new HashMap<>();
        HashSet<String> finalAllCharacters = new HashSet<>();
        for (int i = 0; i < albums.size(); ++i) {
            Album a = albums.get(i);
            for (int j = 0; j < a.characters.size(); ++j) {
                String character = a.characters.get(j);
                String bestMatch = character;
                for (String possibleMatch : allCharacters) {
                    if (Arrays.asList(character.split("\\W")).contains(possibleMatch)) {
                        if (possibleMatch.length() < bestMatch.length() && Character.isUpperCase(possibleMatch.charAt(0))) {
                            bestMatch = possibleMatch;
                        }
                    }
                }
                a.characters.set(j, bestMatch);
            }
            finalAllCharacters.addAll(a.characters);
        }
        for (String character : finalAllCharacters) {
            if (!Arrays.asList("Wiske", "[[Suske]]", "[[Lambik]]", "Sidonia", "[[Jerom]]", "[[professor Barabas]]", "[[Schanulleke]]").contains(character)) {
                for (Album b : albums) {
                    if (b.characters.contains(character)) {
                        for (String characterB : b.characters) {
                            if (!character.equals(characterB)) {
                                if (!Arrays.asList("Wiske", "[[Suske]]", "[[Lambik]]", "Sidonia", "[[Jerom]]", "[[professor Barabas]]", "[[Schanulleke]]").contains(characterB)) {
                                    increaseMapValue(relations, character + "_" + characterB);
                                }
                            }
                        }
                    }
                }
            }
        }
        ArrayList<String> sortList = new ArrayList<>();
        for (String couple : relations.keySet()) {
            if (relations.get(couple) > 1)
                sortList.add(relations.get(couple) + " " + couple);
        }

        Collections.sort(sortList, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return Integer.valueOf(o1.substring(0, o1.indexOf(" "))).compareTo(Integer.valueOf(o2.substring(0, o2.indexOf(" "))));
            }
        });
        for (String sort : sortList)
            System.out.println(sort);*/

        // list comicvine: http://www.comicvine.com/api/volume/4050-41217/?api_key=a500b8b3cc0d13de7d77034e84ed97a6681e23e0&format=json
        // album: http://www.comicvine.com/api/issue/4000-278499/?api_key=a500b8b3cc0d13de7d77034e84ed97a6681e23e0&format=json
        // small_url: http://static.comicvine.com/uploads/scale_small/11/110802/3037590-001_01.jpg
        /*String lookup = "lowie";
        for (Album a : albums) {
            ArrayList<String> matches = new ArrayList<>();
            for (String character : a.characters) {
                if (character.toLowerCase().contains(lookup.toLowerCase())) {
                    matches.add(character);
                }
            }
            if (!matches.isEmpty()) {
                System.out.println(a.title);
                System.out.println("    " + matches);
            }
        }*/
        
        /*Path path = FileSystems.getDefault().getPath("geo.csv");
        Files.write(path, "".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        for (Album a : albums) {
            for (String location : a.locations) {
                for (String wiki : extractWikiLinks(location)) {
                    if (wiki.contains("|"))
                        wiki = wiki.split("\\|")[1].trim();
                    if (Character.isUpperCase(wiki.charAt(0))) {
                        String geo = geoFromName(wiki);
                        if (geo != null) {
                            String val = String.format("%s,%s,\"%s\"", a.title.replace("\"", ""), wiki, geo);
                            Files.write(path, (val + "\n").getBytes(Charset.forName("UTF-8")), StandardOpenOption.APPEND);
                        }
                    }
                }
            }
            System.out.println(a.id);
        }*/
        
        /*ArrayList<String> sort = new ArrayList<>();
        Path path = FileSystems.getDefault().getPath("locations.txt");
        Files.write(path, "".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        for (String key : locations.keySet())
            sort.add(locations.get(key) + " " + key);
        Collections.sort(sort, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return Integer.valueOf(o1.substring(0, o1.indexOf(" "))).compareTo(Integer.valueOf(o2.substring(0, o2.indexOf(" "))));
            }
        });
        for (String val : sort)
            Files.write(path, (val + "\n").getBytes(), StandardOpenOption.APPEND);
        
        sort = new ArrayList<>();
        path = FileSystems.getDefault().getPath("characters.txt");
        Files.write(path, "".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        for (String key : characters.keySet())
            sort.add(characters.get(key) + " " + key);
        Collections.sort(sort, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return Integer.valueOf(o1.substring(0, o1.indexOf(" "))).compareTo(Integer.valueOf(o2.substring(0, o2.indexOf(" "))));
            }
        });
        for (String val : sort)
            Files.write(path, (val + "\n").getBytes(), StandardOpenOption.APPEND);*/
        
        /*URL url = new URL("http://nl.wikipedia.org/w/api.php?format=json&action=query&titles=Lijst_van_verhalen_van_Suske_en_Wiske&prop=revisions&rvprop=content");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(url);
        root = root.get("query").get("pages").iterator().next().get("revisions").get(0).get("*");
        String content = root.asText();
        CSVWriter writer = new CSVWriter(Files.newBufferedWriter(FileSystems.getDefault().getPath("suskewiske.csv"), Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), ',');
        for (String line : content.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && line.charAt(0) == '|' && line.contains("||")) {
                Album a = Album.parse(line);
                a.parseWikiArticle();
                //Files.write(FileSystems.getDefault().getPath("suskewiske.csv"), (a.toCSV()+"\n").getBytes(), StandardOpenOption.APPEND);
                writer.writeNext(a.toCSV());
                writer.flush();
                System.out.println(a.id);
            }
        }
        writer.close();*/
    }
}
