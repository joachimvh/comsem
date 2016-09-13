package test.enrichment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.jamesmurty.utils.XMLBuilder;

public class IReadPlus
{

    private final String ROOT = "https://enrichr.test.iminds.be/EnrWs/v1/";
    private final String usr = "irp.demo.demo";
    private final String pwd = "demo8500";
    
    private Header authorization;
    private String ctl = ROOT + "ctldoc/irp-demo-2014-03-26-00001-ctl";
    
    private HttpClient client;
    private DocumentBuilder builder;
    private XPath xpath;
    
    public IReadPlus ()
    {
        String authVal = "Basic " + DatatypeConverter.printBase64Binary((this.usr+":"+this.pwd).getBytes());
        this.authorization = new BasicHeader("Authorization", authVal);
        
        this.client = HttpClients.createDefault();
        
        try
        {
            this.builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            this.xpath = XPathFactory.newInstance().newXPath();
        }
        catch (ParserConfigurationException ex) 
        {
            ex.printStackTrace();
        }
    }
    
    public List<String> enrich(String input, String lang) throws IOException
    {
        String src = this.createSource(input, lang); // remove non-ACII characters
        String enrQuery = this.startEnrichJob(src, this.ctl);
        String enr = this.waitForEnrichment(enrQuery);
        List<String> result = this.getEnrichments(enr);
        this.delete(src);
        this.delete(enr);
        
        return result;
    }
    
    private String createSource(String input, String lang) throws IOException
    {
        try
        {
            XMLBuilder xmlBuilder = XMLBuilder.create("content").text(input);
            
            HttpPost post = new HttpPost(ROOT + "srcdocs");
            MultipartEntityBuilder multipart = MultipartEntityBuilder.create();
            multipart.addTextBody("srcdoc", xmlBuilder.asString(), ContentType.create("text/plain", MIME.UTF8_CHARSET));
            multipart.addTextBody("lang", lang);
            post.setEntity(multipart.build());
            post.addHeader(this.authorization);
            
            HttpResponse response = this.client.execute(post);
            if (response.getStatusLine().getStatusCode() >= 300)
                throw new IOException(response.toString());            
            String src = response.getFirstHeader("Location").getValue();
            post.releaseConnection();
            
            return src;
        }
        catch (ParserConfigurationException | TransformerException ex)
        {
            throw new IOException(ex);
        }
    }
    
    private String startEnrichJob(String src, String ctl) throws IOException
    {
        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair("srcdocuri", src));
        pairs.add(new BasicNameValuePair("ctldocuri", ctl));
        HttpPost post = new HttpPost(ROOT + "enrdocgen");
        post.setEntity(new UrlEncodedFormEntity(pairs));
        post.addHeader(authorization);
        
        HttpResponse response = this.client.execute(post);
        if (response.getStatusLine().getStatusCode() >= 300)
            throw new IOException(response.toString());
        String queryLocation = response.getFirstHeader("Content-Location").getValue();
        post.releaseConnection();
        
        return queryLocation;
    }
    
    private String waitForEnrichment(String enrQuery) throws IOException
    {
        HttpGet query = new HttpGet(enrQuery);
        query.addHeader(authorization);
        String enr = null;
        
        while (enr == null)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex) { }
            
            HttpResponse response = this.client.execute(query);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 300)
            {
                throw new IOException(response.toString());
            }
            else
            {
                try
                {
                    Document document = this.builder.parse(response.getEntity().getContent());
                    XPathExpression expr = this.xpath.compile("//enrdocgenjobstate/state");
                    NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                    String state = nl.item(0).getTextContent();
                    if (state.equals("failed") || state.equals("done"))
                    {
                        if (state.equals("done"))
                        {
                            enr = response.getFirstHeader("Location").getValue();
                        }
                        else
                        {
                            throw new IOException(response.toString());
                        }
                    }
                }
                catch (SAXException | XPathExpressionException ex)
                {
                    throw new IOException(ex);
                }
            }
            query.releaseConnection();
        }
        
        return enr;
    }
    
    private void delete(String url) throws IOException
    {
        HttpDelete delete = new HttpDelete(url);
        delete.addHeader(authorization);
        client.execute(delete);
        delete.releaseConnection();
    }
    
    private List<String> getEnrichments(String enr) throws IOException
    {
        List<String> result = new ArrayList<>();
        HttpGet get = new HttpGet(enr);
        get.addHeader("Authorization", "Basic " + DatatypeConverter.printBase64Binary("irp.demo.demo:demo8500".getBytes()));
        HttpResponse response = client.execute(get);
        if (response.getStatusLine().getStatusCode() < 300)
        {
            try
            {
                Document document = builder.parse(response.getEntity().getContent());
                XPathExpression expr = xpath.compile("//TEI/text/body/div/p/PEnrich/PNerd/ne");
                
                NodeList nl = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                for (int i = 0; i < nl.getLength(); ++i)
                {
                    result.add(nl.item(i).getTextContent());
                }
            }
            catch (SAXException | XPathExpressionException ex)
            {
                throw new IOException(ex);
            }
        }
        else
        {
            throw new IOException(response.toString());
        }
        get.releaseConnection();
        
        return result;
    }
}
