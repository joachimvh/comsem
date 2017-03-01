package test.jersey;

import java.sql.*;
import java.util.Enumeration;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.lang3.SystemUtils;
import org.opencv.core.Core;

import test.DatabaseManager;

public class ServletContextClass implements ServletContextListener 
{

    @Override
    public void contextInitialized(ServletContextEvent event) 
    {
        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        if (SystemUtils.IS_OS_WINDOWS)
            System.load(event.getServletContext().getRealPath("/WEB-INF/lib/opencv_java247.dll"));
        else
            System.load(event.getServletContext().getRealPath("/WEB-INF/lib/libopencv_java249.so"));
        DatabaseManager.init(event.getServletContext().getRealPath("/WEB-INF/db.sqlite"));
        
        // TODO: test code
        DatabaseManager.addMetadata("http://nl.dbpedia.org/page/Suske");
        DatabaseManager.addMetadata("http://nl.dbpedia.org/page/Wiske");
        DatabaseManager.addMetadata("http://nl.dbpedia.org/page/Lambik");
        DatabaseManager.addMetadata("http://nl.dbpedia.org/page/Tante_Sidonia");
        
        DatabaseManager.addLanguage("nl");
        DatabaseManager.addLanguage("de");
        DatabaseManager.addLanguage("fr");
        DatabaseManager.addLanguage("en");

        DatabaseManager.addPredicate("dcterms:subject");
        DatabaseManager.addPredicate("schema:text");
        DatabaseManager.addPredicate("schema:url");
        DatabaseManager.addPredicate("schema:audio");
    }
    
    @Override
    public final void contextDestroyed(ServletContextEvent sce) {
        // ... First close any background tasks which may be using the DB ...
        // ... Then close any DB connection pools ...

        // Now deregister JDBC drivers in this context's ClassLoader:
        // Get the webapp's ClassLoader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        // Loop through all drivers
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == cl) {
                // This driver was registered by the webapp's ClassLoader, so deregister it:
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ex) {
                }
            } else {
                // driver was not registered by the webapp's ClassLoader and may be in use elsewhere
            }
        }
    }

}
