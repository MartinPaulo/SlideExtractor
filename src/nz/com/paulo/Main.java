package nz.com.paulo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.RuntimeException;import java.lang.String;import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;import java.nio.file.Files;import java.nio.file.Path;import java.nio.file.PathMatcher;import java.nio.file.Paths;import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BiPredicate;

/**
 * A program that takes as input a set of markdown files and extract sections marked to be used as slides.
 * Those extracted sections are then wrapped witha appropriate html and injected into the template.html file
 * and written to an output directory: one set of slides per input file. These html files can be used with the reveal.js
 * framework to give a slideshow.
 */
public class Main {

    static Properties defaultProps = new Properties();

    static {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("SlideBuilder.properties")) {
            defaultProps.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static final String REVEAL_DIRECTORY = defaultProps.getProperty("revealDirectory");
    static final String LESSONS_DIRECTORY = defaultProps.getProperty("lessonsDirectory");
    static final String LESSONS_FILE_REGEX = defaultProps.getProperty("lessonsFileRegex");

    static final String SLIDE_START_LINE = "<!-- slide -->";
    static final String SLIDE_END_LINE = "<!-- slide end -->";

    static final String SLIDES_INSERTION_LINE = "<!-- Slides go here -->";

    static final Path lessonsDir = Paths.get(LESSONS_DIRECTORY);

    static class Slide {
        List<String> lines = new ArrayList<>();
    }

    static class Presentation {

        private final String lessonName;
        Slide currentSlide;
        List<Slide> slides = new ArrayList<>();

        public Presentation(String lessonName) {
            this.lessonName = lessonName.substring(0, lessonName.lastIndexOf('.'));
        }

        void slideStart() {
            currentSlide = new Slide();
        }

        void slideEnd() {
            slides.add(currentSlide);
            currentSlide = null;
        }

        void filterLine(String line) {
            if (line.trim().equalsIgnoreCase(SLIDE_END_LINE)) {
                slideEnd();
            } else if (line.trim().equalsIgnoreCase(SLIDE_START_LINE)) {
                slideStart();
            } else if (currentSlide != null) {
                currentSlide.lines.add(line);
            }
        }

        public void write() throws IOException, URISyntaxException {
            URL url = Main.class.getClassLoader().getResource("template.html");
            List<String> template = Files.readAllLines(Paths.get(url.toURI()));
            List<String> lines = new ArrayList<>();
            lines.add("<style type=\"text/css\">.reveal ol {list-style-type: upper-alpha;}</style>");
            slides.forEach((s) -> {
                lines.add("<section data-markdown><script type=\"text/template\">");
                lines.addAll(s.lines);
                lines.add("</script></section>");
            });
            int insertionPoint = template.indexOf(SLIDES_INSERTION_LINE);
            template.addAll(insertionPoint, lines);
            Files.write(Paths.get(REVEAL_DIRECTORY + "/" + lessonName + ".html"), template);
        }
    }

    static final BiPredicate<Path, BasicFileAttributes> isLessonFile = (path, attrs) -> {
        PathMatcher lessonMatcher = FileSystems.getDefault().getPathMatcher("glob:" + LESSONS_FILE_REGEX);
        boolean isLesson = lessonMatcher.matches(path.getFileName());
        return attrs.isRegularFile() && isLesson;
    };

    private static void extractSlides(Path p) {
        try {
            Presentation presentation = new Presentation(p.getFileName().toString());
            Files.lines(p).forEach(presentation::filterLine);
            presentation.write();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        Files.find(lessonsDir, 3, isLessonFile).forEach(Main::extractSlides);
    }
}
