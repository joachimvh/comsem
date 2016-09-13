package test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

import org.opencv.core.Rect;

import test.serialization.ComicBean;
import test.serialization.PageBean;
import test.serialization.PanelBean;
import test.serialization.PanelTextBean;

public class DatabaseManager 
{
    private static String connectionString;
    
    // TODO: always close connections on catch
    public static void init(String path)
    {
        try
        {
            /*try {
                Files.deleteIfExists(FileSystems.getDefault().getPath(path)); // TODO: probably shouldn't keep this in production code
            } catch (IOException ex) { ex.printStackTrace(); }*/
            connectionString = "jdbc:sqlite:" + path.replace("\\", "/");
            Class.forName("org.sqlite.JDBC"); // needed to load sqlite driver
            Connection c = openConnection();
            
            Statement stmnt = c.createStatement();
            
            ResultSet rs = stmnt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");
            int count = 0;
            while (rs.next())
            {
                //String name = rs.getString("name");
                ++count;
            }
            if (count <= 0)
            {
                stmnt.executeUpdate("CREATE TABLE IMAGES (id INTEGER PRIMARY KEY, img BLOB);");
                stmnt.executeUpdate("CREATE TABLE PAGES (id INTEGER PRIMARY KEY, img REFERENCES IMAGES(id) ON DELETE CASCADE, comic REFERENCES COMICS(id) ON DELETE CASCADE, position INTEGER);");
                stmnt.executeUpdate("CREATE TABLE PANELS (id INTEGER PRIMARY KEY, page REFERENCES PAGES(id) ON DELETE CASCADE, x INTEGER, y INTEGER, width INTEGER, height INTEGER, position INTEGER);");
                stmnt.executeUpdate("CREATE TABLE PANEL_METADATA (panel REFERENCES PANELS(id) ON DELETE CASCADE, meta TEXT);");
                stmnt.executeUpdate("CREATE TABLE PANEL_TEXT (panel REFERENCES PANELS(id) ON DELETE CASCADE, lang REFERENCES LANGUAGES(id) ON DELETE SET NULL, predicate REFERENCES TEXT_PREDICATES(id) ON DELETE CASCADE, text TEXT);");
                //stmnt.executeUpdate("CREATE TABLE PANEL_ACTIONS (panel REFERENCES PANELS(id) ON DELETE CASCADE, action TEXT);");
                stmnt.executeUpdate("CREATE TABLE COMICS (id INTEGER PRIMARY KEY);");
                stmnt.executeUpdate("CREATE TABLE METADATA (id INTEGER PRIMARY KEY, meta TEXT UNIQUE);");
                stmnt.executeUpdate("CREATE TABLE LANGUAGES (id INTEGER PRIMARY KEY, lang TEXT UNIQUE);");
                stmnt.executeUpdate("CREATE TABLE TEXT_PREDICATES (id INTEGER PRIMARY KEY, predicate TEXT UNIQUE);");
                stmnt.executeUpdate("CREATE TABLE COMIC_METADATA (comic REFERENCES COMICS(id) ON DELETE CASCADE, key TEXT, meta TEXT);");
                
                //stmnt.executeUpdate(sql);
            }
            
            stmnt.close();
            c.close();
        }
        catch (ClassNotFoundException | SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static List<ComicBean> listComics()
    {
        List<ComicBean> result = new ArrayList<>();
        
        try (Connection c = openConnection())
        {
            CachedRowSet comics = executeQuery(c, "SELECT id FROM COMICS ORDER BY id;");
            while (comics.next())
            {
                ComicBean comic = new ComicBean();
                comic.id = comics.getInt("id");

                CachedRowSet metadata = executeQuery(c, "SELECT key, meta FROM COMIC_METADATA WHERE comic=?;", comic.id);
                while (metadata.next())
                {
                    String key = metadata.getString("key");
                    String val = metadata.getString("meta");
                    if (!comic.metadata.containsKey(key))
                        comic.metadata.put(key, new ArrayList<String>());
                    List<String> vals = comic.metadata.get(key);
                    vals.add(val);
                }
                result.add(comic);
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        
        return result;
    }
    
    public static ComicBean getComic(int comic)
    {
        ComicBean result = new ComicBean();
        result.id = comic;
        
        try (Connection c = openConnection())
        {
            CachedRowSet pages = executeQuery(c, "SELECT id, position, img FROM PAGES WHERE comic=? ORDER BY position;", comic);
            while (pages.next())
            {
                PageBean page = new PageBean();
                page.img = pages.getInt("img");
                int pageID = pages.getInt("id");
                page.id = pageID;
                CachedRowSet panels = executeQuery(c, "SELECT id, position, x, y, width, height FROM PANELS WHERE page=? ORDER BY position;", pageID);
                while (panels.next())
                {
                    PanelBean panel = new PanelBean();
                    panel.id = panels.getInt("id");
                    panel.x = panels.getInt("x");
                    panel.y = panels.getInt("y");
                    panel.width = panels.getInt("width");
                    panel.height = panels.getInt("height");
                    
                    CachedRowSet metadata = executeQuery(c, "SELECT meta FROM PANEL_METADATA WHERE panel=?;", panel.id);
                    while (metadata.next())
                        panel.metadata.add(metadata.getString("meta"));
                    CachedRowSet texts = executeQuery(c, "SELECT l.lang as lang, tp.predicate as predicate, pt.text as text FROM PANEL_TEXT pt LEFT OUTER JOIN LANGUAGES l ON pt.lang = l.id JOIN TEXT_PREDICATES tp ON pt.predicate = tp.id WHERE panel=?;", panel.id);
                    while (texts.next())
                        panel.texts.add(new PanelTextBean(texts.getString("lang"), texts.getString("predicate"), texts.getString("text")));
                    //CachedRowSet actions = executeQuery(c, "SELECT action FROM PANEL_ACTIONS WHERE panel=?;", panel.id);
                    //while (actions.next())
                        //panel.actions.add(actions.getString("action"));
                    
                    page.panels.add(panel);
                }
                result.pages.add(page);
            }
            
            CachedRowSet metadata = executeQuery(c, "SELECT key, meta FROM COMIC_METADATA WHERE comic=?;", comic);
            while (metadata.next())
            {
                String key = metadata.getString("key");
                String val = metadata.getString("meta");
                if (!result.metadata.containsKey(key))
                    result.metadata.put(key, new ArrayList<String>());
                List<String> vals = result.metadata.get(key);
                vals.add(val);
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        
        return result;
    }
    
    public static int createFullComic (Map<String, List<String>> metadata, Map<Integer, byte[]> images, Map<Integer, List<PanelBean>> panels)
    {
        // will crash so hard if no keys were generated
        try (Connection c = openConnection())
        {
            c.setAutoCommit(false);
            
            ResultSet keys = execute(c, "INSERT INTO COMICS DEFAULT VALUES;");
            keys.next();
            int comic = keys.getInt(1);
            
            for (String key : metadata.keySet())
                for (String val : metadata.get(key))
                    execute(c, "INSERT INTO COMIC_METADATA(comic, key, meta) VALUES(?, ?, ?);", comic, key, val);
            
            TreeMap<Integer, Integer> imgIDs = new TreeMap<>();
            for (int key : images.keySet())
            {
                byte[] data = images.get(key);
                keys = execute(c, "INSERT INTO IMAGES(img) VALUES(?);", data);
                keys.next();
                imgIDs.put(key, keys.getInt(1));
            }
            
            Map<Integer, Integer> pageIDs = new HashMap<>();
            for (int key : imgIDs.keySet())
            {
                keys = execute(c, "INSERT INTO PAGES(comic, img, position) SELECT ?, ?, coalesce(MAX(position)+1, 0) FROM PAGES WHERE comic=?;", comic, imgIDs.get(key), comic);
                keys.next();
                pageIDs.put(key, keys.getInt(1));
            }
            
            for (int key : panels.keySet())
            {
                for (PanelBean panel : panels.get(key))
                {
                    keys = execute(c, "INSERT INTO PANELS(position, x, y, width, height, page) SELECT coalesce(MAX(position)+1, 0), ?, ?, ?, ?, ? FROM PANELS WHERE page=?", 
                            panel.x, panel.y, panel.width, panel.height, pageIDs.get(key), pageIDs.get(key));
                    keys.next();
                    int panelID = keys.getInt(1);
                    for (String meta : panel.metadata)
                        execute(c, "INSERT INTO PANEL_METADATA(panel, meta) SELECT id, ? FROM PANELS WHERE id=?;", meta, panelID);
                    for (PanelTextBean panelText : panel.texts) {
                        int langID = -1;
                        if (panelText.lang != null)
                        {
                            CachedRowSet langs = executeQuery(c, "SELECT id FROM LANGUAGES WHERE lang=?;", panelText.lang);
                            if (langs.next())
                                langID = langs.getInt("id");
                        }
                        int predicateID = -1;
                        if (panelText.predicate != null)
                        {
                            CachedRowSet predicates = executeQuery(c, "SELECT id FROM TEXT_PREDICATES WHERE predicate=?;", panelText.predicate);
                            if (predicates.next())
                                predicateID = predicates.getInt("id");
                        }
                        if (predicateID < 0)
                            continue;
                        
                        if (langID < 0)
                            execute(c, "INSERT INTO PANEL_TEXT(panel, predicate, text) SELECT id, ?, ? FROM PANELS WHERE id=?;", predicateID, panelText.text, panelID);
                        else
                            execute(c, "INSERT INTO PANEL_TEXT(panel, lang, predicate, text) SELECT id, ?, ?, ? FROM PANELS WHERE id=?;", langID, predicateID, panelText.text, panelID);
                    }
                }
            }
            
            c.commit();
            
            return comic;
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        
        return -1;
    }
    
    public static void deleteComic(int comic)
    {
        // delete images first
        try (Connection c = openConnection())
        {
            execute(c, "DELETE FROM IMAGES WHERE id in (SELECT IMAGES.id FROM IMAGES JOIN PAGES ON IMAGES.id = PAGES.img WHERE comic=?);", comic);
            execute(c, "DELETE FROM COMICS WHERE id=?;", comic);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static int createComic()
    {
        try (Connection c = openConnection())
        {
            ResultSet keys = execute(c, "INSERT INTO COMICS DEFAULT VALUES;");
            
            int key = -1;
            if (keys.next())
                key = keys.getInt(1);
            
            return key;
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
            return -1;
        }
    }
    
    public static void addComicMetadata(int comic, String key, String val)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT 1 FROM COMICS WHERE id=? LIMIT 1;", comic);
            if (rowset.next())
                execute(c, "INSERT INTO COMIC_METADATA(comic, key, meta) VALUES(?, ?, ?);", comic, key, val);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void deleteComicMetadata(int comic, String key, String val)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT 1 FROM COMICS WHERE id=? LIMIT 1;", comic);
            if (rowset.next())
                execute(c, "DELETE FROM COMIC_METADATA WHERE comic=? AND key=? AND meta=?;", comic, key, val);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    /*public static void setComicMetadata(int comic, String key, String val)
    {
        key = key.toLowerCase();
        try
        {
            CachedRowSet rowset = executeQuery("SELECT 1 FROM COMICS WHERE id=? LIMIT 1;", comic);
            if (rowset.next())
            {
                rowset = executeQuery("SELECT 1 FROM COMIC_METADATA WHERE comic=? AND key=? LIMIT 1;", comic, key);
                if (rowset.next())
                {
                    if (val.isEmpty())
                    {
                        execute("DELETE FROM COMIC_METADATA WHERE comic=? AND key=?;", comic, key);
                    }
                    else
                    {
                        execute("UPDATE COMIC_METADATA SET meta=? WHERE comic=? AND key=?;", val, comic, key);
                    }
                }
                else
                {
                    execute("INSERT INTO COMIC_METADATA(comic, key, meta) VALUES(?, ?, ?);", comic, key, val);
                }
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }*/
    
    public static void deletePage(int page)
    {
        execute("DELETE FROM PAGES WHERE id=?;", page);
    }
    
    public static int createPage(int parent)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT 1 FROM COMICS WHERE id=? LIMIT 1;", parent);
            
            if (rowset.next())
            {
                CachedRowSet keys = execute(c, "INSERT INTO PAGES(comic, position) SELECT ?, coalesce(MAX(position)+1, 0) FROM PAGES WHERE comic=?;", parent, parent);
        
                int key = -1;
                if (keys.next())
                    key = keys.getInt(1);
                
                return key;
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return -1;
    }
    
    public static void changePageOrder(int page, int newPos)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT position, comic FROM PAGES WHERE id=? LIMIT 1;", page);
            if (rowset.next())
            {
                int oldPos = rowset.getInt("position");
                int comic = rowset.getInt("comic");
                
                c.setAutoCommit(false);
                
                execute(c, "UPDATE PAGES SET position=? WHERE id=?;", newPos, page);                
                
                if (oldPos < newPos)
                    execute(c, "UPDATE PAGES SET position=position-1 WHERE comic=? AND id!=? AND position>=? AND position <=?;", comic, page, oldPos, newPos);
                else
                    execute(c, "UPDATE PAGES SET position=position+1 WHERE comic=? AND id!=? AND position>=? AND position <=?;", comic, page, newPos, oldPos);
                
                c.commit();
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void setPageOrder(int comic, List<Integer> pageOrder)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT 1 FROM COMICS WHERE id=? LIMIT 1;", comic);
            if (rowset.next())
            {
                c.setAutoCommit(false);
                for (int i = 0; i < pageOrder.size(); ++i)
                {
                    int id = pageOrder.get(i);
                    rowset = executeQuery(c, "SELECT 1 FROM PAGES WHERE id=? LIMIT 1;", id);
                    if (!rowset.next())
                        return;
                    execute(c, "UPDATE PAGES SET position=? WHERE id=? AND comic=?;", i, id, comic);
                }
                c.commit();
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void deletePanel(int panel)
    {
        execute("DELETE FROM PANELS WHERE id=?;", panel);
    }
    
    public static int addPanel(int page, Rect rect)
    {
        int panelID = -1;
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT id FROM PAGES WHERE id=? LIMIT 1;", page);
            if (rowset.next())
            {
                CachedRowSet keys = execute(c, "INSERT INTO PANELS(position, x, y, width, height, page) SELECT coalesce(MAX(position)+1, 0), ?, ?, ?, ?, ? FROM PANELS WHERE page=?", rect.x, rect.y, rect.width, rect.height, page, page);
                if (keys.next())
                    panelID = keys.getInt(1);
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return panelID;
    }
    
    public static void updatePanelRect(int panel, Rect rect)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT 1 FROM PANELS WHERE id=? LIMIT 1;", panel);
            if (rowset.next())
                execute(c, "UPDATE PANELS SET x=?, y=?, width=?, height=? WHERE id=?;", rect.x, rect.y, rect.width, rect.height, panel);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }    
    
    public static void changePanelOrder(int panel, int newPos)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT position, page FROM PANELS WHERE id=? LIMIT 1;", panel);
            if (rowset.next())
            {
                int oldPos = rowset.getInt("position");
                int page = rowset.getInt("page");
                
                c.setAutoCommit(false);
                
                execute(c, "UPDATE PANELS SET position=? WHERE id=?;", newPos, panel);                
                
                if (oldPos < newPos)
                    execute(c, "UPDATE PANELS SET position=position-1 WHERE page=? AND id!=? AND position>=? AND position <=?;", page, panel, oldPos, newPos);
                else
                    execute(c, "UPDATE PANELS SET position=position+1 WHERE comic=? AND id!=? AND position>=? AND position <=?;", page, panel, newPos, oldPos);
                
                c.commit();
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void addPanelMetadata(int panel, String metadata)
    {
        if (metadata.isEmpty())
            return;
        execute("INSERT INTO PANEL_METADATA(panel, meta) SELECT id, ? FROM PANELS WHERE id=?;", metadata, panel);
    }
    
    public static void deletePanelMetadata(int panel, String metadata)
    {
        execute("DELETE FROM PANEL_METADATA WHERE meta=? AND panel=?;", metadata, panel);
    }
    
    /*public static void addPanelTranslation(int panel, String lang, String translation)
    {
        if (translation.isEmpty())
            return;
        execute("INSERT INTO PANEL_TEXT(panel, lang, text) SELECT id, ?, ? FROM PANELS WHERE id=?;",  lang, translation, panel);
    }
    
    public static void deletePanelTranslation(int panel, String lang)
    {
        execute("DELETE FROM PANEL_TEXT WHERE lang=? AND panel=?;", lang, panel);
    }*/
    
    public static void addPanelText(int panel, String lang, String predicate, String text)
    {
        if (text.isEmpty())
            return;
        if (lang != null && !lang.isEmpty())
            execute("INSERT INTO PANEL_TEXT(panel, lang, predicate, text) SELECT p.id, l.id, tp.id, ? FROM PANELS p, LANGUAGES l, TEXT_PREDICATES tp WHERE p.id=? AND l.lang=? AND tp.predicate=?;", text, panel, lang, predicate);
        else
            execute("INSERT INTO PANEL_TEXT(panel, predicate, text) SELECT p.id, tp.id, ? FROM PANELS p, TEXT_PREDICATES tp WHERE p.id=? AND tp.predicate=?;", text, panel, predicate);
    }
    
    public static void deletePanelText(int panel, String lang, String predicate, String text)
    {
        if (text.isEmpty())
            return;
        // find correct id's before deleting
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT id FROM TEXT_PREDICATES WHERE predicate=? LIMIT 1;", predicate);
            rowset.next();
            int predicateID = rowset.getInt("id");
            int langID = -1;
            if (lang != null)
            {
                rowset = executeQuery(c, "SELECT id FROM LANGUAGES WHERE lang=? LIMIT 1;", lang);
                if (rowset.next())
                    langID = rowset.getInt("id");
            }
            
            if (langID >= 0)
                execute("DELETE FROM PANEL_TEXT WHERE panel=? AND lang=? AND predicate=? AND text=?;", panel, langID, predicateID, text);
            else
                execute("DELETE FROM PANEL_TEXT WHERE panel=? AND lang IS NULL AND predicate=? AND text=?;", panel, predicateID, text);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    /*public static void addPanelAction(int panel, String action)
    {
        if (action.isEmpty())
            return;
        execute("INSERT INTO PANEL_ACTIONS(panel, action) SELECT id, ? FROM PANELS WHERE id=?;", action, panel);
    }
    
    public static void deletePanelAction(int panel, String action)
    {
        execute("DELETE FROM PANEL_ACTIONS WHERE action=? AND panel=?;", action, panel);
    }*/
    
    public static void addMetadata(String metadata)
    {
        if (metadata.isEmpty())
            return;
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT 1 FROM METADATA WHERE meta=? LIMIT 1;", metadata);
            if (!rs.next())
                execute(c, "INSERT INTO METADATA(meta) VALUES(?);", metadata);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void deleteMetadata(String metadata)
    {
        execute("DELETE FROM METADATA WHERE meta=?;", metadata);
    }
    
    public static List<String> getMetadata()
    {
        List<String> result = new ArrayList<>();
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT meta FROM METADATA;");
            while (rs.next())
                result.add(rs.getString("meta"));
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return result;
    }
    
    public static void addLanguage(String lang)
    {
        if (lang.isEmpty())
            return;
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT 1 FROM LANGUAGES WHERE lang=? LIMIT 1;", lang);
            if (!rs.next())
                execute(c, "INSERT INTO LANGUAGES(lang) VALUES(?);", lang);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void deleteLanguage(String lang)
    {
        execute("DELETE FROM LANGUAGES WHERE lang=?;", lang);
    }
    
    public static List<String> getLanguages()
    {
        List<String> result = new ArrayList<>();
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT lang FROM LANGUAGES;");
            while (rs.next())
                result.add(rs.getString("lang"));
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return result;
    }
    
    public static void addPredicate(String predicate)
    {
        if (predicate.isEmpty())
            return;
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT 1 FROM TEXT_PREDICATES WHERE predicate=? LIMIT 1;", predicate);
            if (!rs.next())
                execute(c, "INSERT INTO TEXT_PREDICATES(predicate) VALUES(?);", predicate);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static void deletePredicate(String predicate)
    {
        execute("DELETE FROM TEXT_PREDICATES WHERE predicate=?;", predicate);
    }
    
    public static List<String> getPredicates()
    {
        List<String> result = new ArrayList<>();
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT predicate FROM TEXT_PREDICATES;");
            while (rs.next())
                result.add(rs.getString("predicate"));
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return result;
    }
    
    public static void setPageImage(int page, int img)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet rowset = executeQuery(c, "SELECT 1 FROM PAGES WHERE id=? LIMIT 1;", page);
            CachedRowSet imgset = executeQuery(c, "SELECT 1 FROM IMAGES WHERE id=? LIMIT 1;", img);
            if (rowset.next() && imgset.next())
                execute(c, "UPDATE PAGES SET img=? WHERE id=?;", img, page);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }
    
    public static int storeImage(byte[] data)
    {
        int key = -1;
        try (Connection c = openConnection())
        {
            CachedRowSet keys = execute(c, "INSERT INTO IMAGES(img) VALUES(?);", data);
            
            if (keys.next())
                key = keys.getInt(1);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return key;
    }
    
    public static byte[] getImage(int id)
    {        
        try (Connection c = openConnection())
        {
            CachedRowSet rs = executeQuery(c, "SELECT img FROM IMAGES WHERE id=?;", id);
            if (rs.next())
                return (byte[])rs.getObject("img");
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
        return null;
    }
    
    private static Connection openConnection() throws SQLException
    {
        Connection c = DriverManager.getConnection(connectionString);
        Statement stmnt = c.createStatement();
        stmnt.execute("PRAGMA foreign_keys = ON;");
        stmnt.close();
        
        return c;
    }
    
    private static CachedRowSet executeQuery(Connection c, String statement, Object... params) throws SQLException
    {
        PreparedStatement prepStmnt = c.prepareStatement(statement);
        for (int i = 0; i < params.length; ++i)
            prepStmnt.setObject(i+1, params[i]);
        ResultSet rs = prepStmnt.executeQuery();
        CachedRowSet result = RowSetProvider.newFactory().createCachedRowSet();
        result.populate(rs);
        
        rs.close();
        prepStmnt.close();
        
        return result;
    }
    
    private static CachedRowSet execute(Connection c, String statement, Object... params) throws SQLException
    {
        PreparedStatement prepStmnt = c.prepareStatement(statement);
        for (int i = 0; i < params.length; ++i)
            prepStmnt.setObject(i+1, params[i]);
        prepStmnt.execute();

        CachedRowSet result = RowSetProvider.newFactory().createCachedRowSet();
        result.populate(prepStmnt.getGeneratedKeys());
        
        prepStmnt.close();
        
        return result;
    }
    
    private static CachedRowSet execute(String statement, Object... params)
    {
        try (Connection c = openConnection())
        {
            CachedRowSet result = execute(c, statement, params);
            
            return result;
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }
}
