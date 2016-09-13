package test.enrichment;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class DBPediaSpotlight
{

    private final String ROOT = "http://spotlight.sztaki.hu:2232/rest/";
    
    private HttpClient client;
    
    public DBPediaSpotlight()
    {
        this.client = HttpClients.createDefault();
    }
    
    public List<String> enrich(String input) throws IOException
    {
        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair("text", input));
        HttpPost post = new HttpPost(ROOT + "annotate");
        post.setEntity(new UrlEncodedFormEntity(pairs));
        RequestConfig params = RequestConfig.custom().setConnectTimeout(30000).setSocketTimeout(30000).build();
        post.setConfig(params);
        HttpResponse response = client.execute(post);
        
        if (response.getStatusLine().getStatusCode() >= 300)
            throw new IOException(response.toString());

        List<String> result = new ArrayList<>();
        InputStream in = response.getEntity().getContent();
        // received html is a mess (invalid xml), let's do regexes in html!
        StringWriter writer = new StringWriter();
        IOUtils.copy(in, writer);
        String html = new String(writer.toString().getBytes(), Charset.forName("UTF-8"));
        html.substring(html.indexOf("<body>"));
        Pattern pattern = Pattern.compile("<a href=\"(.*?)\" ");
        Matcher matcher = pattern.matcher(html);
        while(matcher.find())
        {
            String url = matcher.group(1);
            String[] splits = url.split("/");
            result.add(splits[splits.length-1]);
        }
        
        post.releaseConnection();
        
        return result;
    }
}
