package nz.com.paulo;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BiPredicate;

/**
 * A program that takes as input a set of markdown files and extract sections marked to be used as slides.
 * Those extracted sections are then wrapped with an appropriate html wrapper and injected into the template
 * file and written to an output directory: one set of slides per input file. These html files can be used
 * with the reveal.js framework to give a slideshow.
 */
public class Main {

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
            if (line.trim().equalsIgnoreCase(Settings.SLIDE_END_LINE)) {
                slideEnd();
            } else if (line.trim().equalsIgnoreCase(Settings.SLIDE_START_LINE)) {
                slideStart();
            } else if (currentSlide != null) {
                currentSlide.lines.add(line);
            }
        }

        public void write() throws IOException, URISyntaxException {
            URL url = Main.class.getClassLoader().getResource(Settings.instance.getTemplate());
            List<String> template = Files.readAllLines(Paths.get(url.toURI()));
            List<String> lines = new ArrayList<>();
            slides.forEach((s) -> {
                lines.add("<section data-markdown><script type=\"text/template\">");
                lines.addAll(s.lines);
                lines.add("</script></section>");
            });
            int insertionPoint = template.indexOf(Settings.SLIDES_INSERTION_LINE) + 1;
            template.addAll(insertionPoint, lines);
            String target = Settings.instance.getRevealDirectory() + "/" + lessonName + ".html";
            System.out.println("Writing to: " + target);
            Files.write(Paths.get(target), template);
        }
    }

    static final BiPredicate<Path, BasicFileAttributes> isLessonFile = (path, attrs) -> {
        PathMatcher lessonMatcher = FileSystems.getDefault().getPathMatcher(
                "glob:" + Settings.instance.getLessonsFileRegex());
        boolean isLesson = lessonMatcher.matches(path.getFileName());
        return attrs.isRegularFile() && isLesson;
    };

    private static void extractSlides(Path p) {
        try {
            System.out.println("Working on: " + p);
            Presentation presentation = new Presentation(p.getFileName().toString());
            Files.lines(p).forEach(presentation::filterLine);
            presentation.write();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("SlideExtractor").
                description("Extracts reveal.js slides from markdown.");
        parser.addArgument("-p", "--properties")
                .required(true)
                .help("the properties file to read the settings from");

        try {
            Namespace res = parser.parseArgs(args);
            Settings.build(res.get("properties"));
            Files.find(Settings.instance.getLessonsDir(), 3, isLessonFile).forEach(Main::extractSlides);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        }
    }
}
