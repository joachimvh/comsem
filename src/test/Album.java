package test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

public class Album {

    public int id;
    public Date date;
    public String title;
    public String wikiTitle;
    
    public HashMap<String, Integer> series;
    public ArrayList<String> locations;
    public ArrayList<String> characters;
    
    public String comicvineAPI;
    public String comicvineThumb;
    
    public static HashMap<Integer, String> seriesPositionMapping;
    static {
        seriesPositionMapping = new HashMap<>();
        seriesPositionMapping.put(3, "VO");
        seriesPositionMapping.put(4, "BR");
        seriesPositionMapping.put(5, "HO");
        seriesPositionMapping.put(6, "VT");
        seriesPositionMapping.put(7, "HT");
        seriesPositionMapping.put(8, "GT");
        seriesPositionMapping.put(9, "VK");
        seriesPositionMapping.put(10, "SK");
        seriesPositionMapping.put(11, "RK");
        seriesPositionMapping.put(12, "PR");
    }
    
    public static Album parse (String input) {
        String[] parts = input.split("\\|\\|");
        
        for (int i = 0; i < parts.length; ++i) {
            String line = parts[i];
            Pattern p = Pattern.compile("style=[^\\|]*\\|");
            Matcher m = p.matcher(line);
            if (m.find())
                line = line.substring(m.end()+1);
            if (line.charAt(0) == '|')
                line = line.substring(1);
            parts[i] = line.trim();
        }
        
        Album result = new Album();
        result.id = Integer.parseInt(parts[0]);
        String[] formats = new String[]{"dd-MM-yyyy", "MM-yyyy", "yyyy"};
        for (String format : formats) {
            try {
                DateFormat df = new SimpleDateFormat(format);
                result.date = df.parse(parts[1]);
                break;
            } catch (ParseException ex) { }
        }
        parts[2] = parts[2].substring(2, parts[2].length()-2); // remove [[ ]]
        String[] titles = parts[2].split("\\|");
        if (titles.length == 2)
            result.title = titles[1];
        else
            result.title = titles[0];
        result.wikiTitle = titles[0].replace(' ', '_');
        
        for (int i = 3; i < parts.length; ++i) {
            try {
                int serie = Integer.parseInt(parts[i]);
                result.series.put(seriesPositionMapping.get(i), serie);
            } catch (NumberFormatException ex) {}
        }
        
        return result;
    }
    
    public static Album parse (String[] csv) {
        if (csv.length != 7)
            return null;
        Album result = new Album();
        result.id = Integer.parseInt(csv[0]);
        try {
            result.date = new SimpleDateFormat("dd-MM-yyy").parse(csv[1]);
        } catch (ParseException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        result.title = csv[2];
        result.wikiTitle = csv[3];
        if (!csv[4].isEmpty())
            for (String series : csv[4].split(";")) {
                String[] couple = series.split(":");
                result.series.put(couple[0], Integer.parseInt(couple[1]));
            }
        
        if (!csv[5].isEmpty())
            for (String location : csv[5].split(";")) {
                result.locations.add(location);
            }
        if (!csv[6].isEmpty())
            for (String character : csv[6].split(";"))
                result.characters.add(character);
        
        return result;
    }
    
    public Album() {
        series = new HashMap<>();
        locations = new ArrayList<>();
        characters = new ArrayList<>();
    }
    
    private List<String> splitLine(String line) {
        ArrayList<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        line = line.replaceAll("<ref.*?>", "(");
        line = line.replaceAll("</ref>", ")");
        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if ((c == ',' || i == line.length()-1) && depth == 0) {
                result.add(line.substring(start, i).replace(';', ':').trim());
                start = i + 1;
            } else if (c == '(' || c == '[') {
                ++depth;
            } else if (c == ')' || c == ']') {
                --depth;
            }
        }
        return result;
    }
    
    public void parseWikiArticle () {
        try {
            URL url = new URL("http://nl.wikipedia.org/w/api.php?format=json&action=query&titles=" + wikiTitle + "&prop=revisions&rvprop=content");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(url);
            try {
                root = root.get("query").get("pages").iterator().next().get("revisions").get(0).get("*");
            } catch (NullPointerException ex) { // no wiki page
                return;
            }
            String content = root.asText();
            Pattern spacePattern = Pattern.compile("== (.*?) ==");
            Matcher spaceMatcher = spacePattern.matcher(content);
            content = spaceMatcher.replaceAll("==$1==");
            String[] chapters = content.split("\n==");
            for (String chapter : chapters) {
                chapter = chapter.trim().replace("*\n", ",").replace(":\n", ":");
                if (chapter.startsWith("Locaties==\n")) {
                    chapter = chapter.substring("Locaties==\n".length());
                    Pattern p = Pattern.compile("((locaties.*?)|(speelt zich .*? af.*?(in|op|rond)( de volgende locaties)?)):? ?\\*?");
                    Matcher m = p.matcher(chapter);
                    if (m.find())
                        chapter = chapter.substring(m.end()).trim();
                    
                    String[] lines = chapter.split("\n");
                    // System.out.println(title + " ||| " + lines[0].substring(0, Math.min(100, lines[0].length())));
                    // lines[0] = lines[0].replaceAll("\\(.*?\\)|<ref>.*?</ref>", "");
                    //for (String loc : lines[0].split(","))
                    //    locations.add(loc.trim());
                    locations.addAll(splitLine(lines[0]));
                }
                if (chapter.startsWith("Personages==") || chapter.startsWith("Personages en uitvindingen==")) {
                    chapter = chapter.substring(chapter.indexOf("==\n")+3);
                    Pattern p = Pattern.compile("(personages|Hoofdrolspelers).*?: ?\\*?");
                    Matcher m = p.matcher(chapter);
                    if (m.find())
                        chapter = chapter.substring(m.end()).trim();
                    
                    String[] lines = chapter.split("\n");
                    for (String character : splitLine(lines[0])) {
                        // special case
                        if (character.contains("[[Wiske]] met [[Schanulleke]]"))
                            for (String ss : character.split("met"))
                                characters.add(ss.trim());
                        else
                            characters.add(character.trim());
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    
    public void parseComicvine (String api) {
        this.comicvineAPI = api;
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readTree(new URL(api));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    
    public String toString() {
        return String.format("%d. %s (%s)\n    %s", id, title, new SimpleDateFormat("dd-MM-yyyy").format(date), series);
    }
    
    public String[] toCSV() {
        String nrString = "";
        for (String key : series.keySet())
            nrString += ";" + key + ":" + series.get(key);
        if (!nrString.isEmpty())
            nrString = nrString.substring(1);
        
        String locString = "";
        for (String loc : locations)
            locString += ";" + loc;
        if (!locString.isEmpty())
            locString = locString.substring(1);

        String characterString = "";
        for (String character : characters)
            characterString += ";" + character;
        if (!characterString.isEmpty())
            characterString = characterString.substring(1);
        
        return new String[]{ 
                id+"", 
                new SimpleDateFormat("dd-MM-yyyy").format(date), 
                title,
                wikiTitle,
                nrString,
                locString,
                characterString};
    }
}
