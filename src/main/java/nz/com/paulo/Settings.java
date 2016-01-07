package nz.com.paulo;

import java.io.File;
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
    private final String baseDirectory;

    private Settings(String baseDirectory,String propertiesFile) {
        if (!baseDirectory.endsWith(File.separator)) {
            baseDirectory = baseDirectory + File.separator;
        }
        this.baseDirectory = baseDirectory;
        try (InputStream in = new FileInputStream(baseDirectory + propertiesFile)) {
            defaultProps.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the resource file named " + propertiesFile, e);
        }
    }


    static void build(String baseDirectory, String propertiesFile) {
        if (instance != null) {
            throw new RuntimeException("You are trying to build the settings twice!");
        }
        instance = new Settings(baseDirectory, propertiesFile);
    }

    static Settings getSettings() {
        if (instance == null) {
            throw new RuntimeException("You have forgotten to build the settings!");
        }
        return instance;
    }

    public String getRevealDirectory() {
        return baseDirectory + defaultProps.getProperty("revealDirectory");
    }

    public String getLessonsDirectory() {
        return baseDirectory + defaultProps.getProperty("lessonsDirectory");
    }

    public Path getLessonsDir() {
        return Paths.get(getLessonsDirectory());
    }

    public String getLessonsFileRegex() {
        return defaultProps.getProperty("lessonsFileRegex");
    }

    public String getTemplate() {
        return baseDirectory + defaultProps.getProperty("template");
    }
}
