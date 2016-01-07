package nz.com.paulo;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A program that takes as input a set of markdown files and extract sections marked to be used as slides.
 * Those extracted sections are then wrapped with an appropriate html wrapper and injected into the template
 * file and written to an output directory: one set of slides per input file. These html files can be used
 * with the reveal.js framework to give a slideshow.
 */
public class Main {

    static class Slide {
        final List<String> lines = new ArrayList<>();
    }

    static class Presentation {

        private final String lessonName;
        Slide currentSlide;
        final List<Slide> slides = new ArrayList<>();

        public Presentation(String lessonName) {
            this.lessonName = lessonName.substring(0, lessonName.lastIndexOf('.'));
        }

        void createSlide() {
            currentSlide = new Slide();
        }

        void saveSlide() {
            slides.add(currentSlide);
            currentSlide = null;
        }

        void filterLine(String line) {
            if (isEndOfSlide(line)) {
                saveSlide();
            } else if (isStartOfSlide(line)) {
                createSlide();
            } else if (creatingSlide()) {
                currentSlide.lines.add(line);
            }
        }

        private boolean creatingSlide() {
            return currentSlide != null;
        }

        private boolean isStartOfSlide(String line) {
            return line.trim().equalsIgnoreCase(Settings.SLIDE_START_LINE);
        }

        private boolean isEndOfSlide(String line) {
            return line.trim().equalsIgnoreCase(Settings.SLIDE_END_LINE);
        }

        public void writeToFile() throws IOException, URISyntaxException {
            URL url = Main.class.getClassLoader().getResource(Settings.getSettings().getTemplate());
            List<String> template = Files.readAllLines(Paths.get(url.toURI()));
            List<String> lines = new ArrayList<>();
            slides.forEach((s) -> {
                lines.add("<section data-markdown><script type=\"text/template\">");
                lines.addAll(s.lines);
                lines.add("</script></section>");
            });
            int insertionPoint = template.indexOf(Settings.SLIDES_INSERTION_LINE) + 1;
            template.addAll(insertionPoint, lines);
            String target = Settings.getSettings().getRevealDirectory() + "/" + lessonName + ".html";
            System.out.println("Writing to: " + target);
            Files.write(Paths.get(target), template);
        }
    }

    static final private BiPredicate<Path, BasicFileAttributes> isLessonFile = (path, attrs) -> {
        PathMatcher lessonMatcher = FileSystems.getDefault().getPathMatcher(
                "glob:" + Settings.getSettings().getLessonsFileRegex());
        boolean isLesson = lessonMatcher.matches(path.getFileName());
        return attrs.isRegularFile() && isLesson;
    };

    private static void extractSlides(Path p) {
        try {
            System.out.println("Working on: " + p);
            Presentation presentation = new Presentation(p.getFileName().toString());
            Files.lines(p).forEach(presentation::filterLine);
            presentation.writeToFile();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("SlideExtractor").
                description("Extracts reveal.js slides from markdown.");
        parser.addArgument("-p", "--properties")
                .required(true)
                .help("the path of the properties file to read the settings from");
        try {
            Namespace res = parser.parseArgs(args);
            Settings.build(res.get("properties"));
            Files.find(Settings.getSettings().getLessonsDir(), 3, isLessonFile).forEach(Main::extractSlides);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
