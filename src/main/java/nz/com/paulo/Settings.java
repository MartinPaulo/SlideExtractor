package nz.com.paulo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * The application settings
 * Created by Martin Paulo on 12/10/2015.
 */
public final class Settings {


    static final String SLIDE_START_LINE = "-- *Slide* --";
    static final String SLIDE_END_LINE = "-- *Slide End* --";

    static final String SLIDES_INSERTION_LINE = "<!-- Slides go here -->";

    private static Settings instance;

    private final Properties defaultProps = new Properties();

    private Settings(String propertiesFile) {
        try (InputStream in = new FileInputStream(propertiesFile)) {
            defaultProps.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the resource file named " + propertiesFile, e);
        }
    }

    static void build(String propertiesFile) {
        if (instance != null) {
            throw new RuntimeException("You are trying to build the settings twice!");
        }
        instance = new Settings(propertiesFile);
    }

    static Settings getSettings() {
        if (instance == null) {
            throw new RuntimeException("You have forgotten to build the settings!");
        }
        return instance;
    }

    public String getRevealDirectory() {
        return defaultProps.getProperty("revealDirectory");
    }

    public String getLessonsDirectory() {
        return defaultProps.getProperty("lessonsDirectory");
    }

    public Path getLessonsDir() {
        return Paths.get(getLessonsDirectory());
    }

    public String getLessonsFileRegex() {
        return defaultProps.getProperty("lessonsFileRegex");
    }

    public String getTemplate() {
        return defaultProps.getProperty("template");
    }
}
