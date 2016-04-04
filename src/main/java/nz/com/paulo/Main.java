package nz.com.paulo;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * A program that takes as input a set of markdown files and extract sections marked to be used as slides.
 * Those extracted sections are then wrapped with an appropriate html wrapper and injected into the template
 * file and written to an output directory: one set of slides per input file. These html files can be used
 * with the reveal.js framework to give a slideshow.
 */
public class Main {

    private static final String DEFAULT_PROPERTIES_FILE_NAME = "SlideExtractor.properties";
    private static Map<String, String> pagesWritten = new LinkedHashMap<>();

    static class Slide {
        final List<String> lines = new ArrayList<>();
    }

    static class Presentation {

        /* If slide with more than this number of lines is found a warning is emitted. */
        private static final int MAX_SLIDE_LENGTH = 22;
        private final String lessonName;
        Slide currentSlide;
        final List<Slide> slides = new ArrayList<>();

        public Presentation(String lessonName) {
            this.lessonName = lessonName.substring(0, lessonName.lastIndexOf('.'));
        }

        void createSlide() {
            currentSlide = new Slide();
        }

        void saveSlide(int lineNo) {
            if (currentSlide.lines.size() > MAX_SLIDE_LENGTH) {
                System.out.println("Warning. Long slide with "+ currentSlide.lines.size()+ " lines found: line " + lineNo);
            }
            slides.add(currentSlide);
            currentSlide = null;
        }

        void filterLine(String line, int lineNo) {
            if (isEndOfSlide(line)) {
                if (!creatingSlide()) {
                    System.out.println("Error! Slide end found without slide start: line " + lineNo);
                } else {
                    saveSlide(lineNo);
                }
            } else if (isStartOfSlide(line)) {
                if (creatingSlide()) {
                    System.out.println("Error! New slide start found whilst still creating slide: line " + lineNo);
                    saveSlide(lineNo);
                }
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
            Path templatePath = Paths.get(Settings.getSettings().getTemplate());
            List<String> template = Files.readAllLines(templatePath);
            List<String> lines = new ArrayList<>();
            slides.forEach((s) -> {
                lines.add("<section data-markdown><script type=\"text/template\">");
                lines.addAll(s.lines);
                lines.add("</script></section>");
            });
            int insertionPoint = template.indexOf(Settings.SLIDES_INSERTION_LINE) + 1;
            template.addAll(insertionPoint, lines);
            String targetFileName = lessonName + ".html";
            String target = Settings.getSettings().getRevealDirectory() + "/" + targetFileName;
            System.out.println("Writing to: " + target);
            Files.write(Paths.get(target), template);
            pagesWritten.put(lessonName, targetFileName);
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
            Files.lines(p).forEach(new Consumer<String>() {
                int lineNo = 0;

                @Override
                public void accept(String line) {
                    lineNo++;
                    presentation.filterLine(line, lineNo);
                }
            });
            presentation.writeToFile();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeIndexPage() throws IOException {
        Path templatePath = Paths.get(Settings.getSettings().getTemplate());
        List<String> template = Files.readAllLines(templatePath);
        int insertionPoint = template.indexOf(Settings.SLIDES_INSERTION_LINE) + 1;
        List<String> lines = new ArrayList<>();
        lines.add("<ul>");
        pagesWritten.forEach((k, v) -> lines.add("<li><a href=\"" + v + "#/\">" + k + "</a></li>"));
        lines.add("</ul>");
        template.addAll(insertionPoint, lines);
        String targetFileName = "index.html";
        String target = Settings.getSettings().getRevealDirectory() + "/" + targetFileName;
        System.out.println("Writing to: " + target);
        Files.write(Paths.get(target), template);
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("SlideExtractor")
                .description("Extracts reveal.js slides from markdown.")
                // following reads the manifest in the jar: if not in the jar, returns a version of null
                .version("${prog} " + Main.class.getPackage().getImplementationVersion());
        parser.addArgument("-d", "--workingdir")
                .required(false)
                .setDefault(".")
                .help("the working directory (defaults to the current directory)");
        parser.addArgument("-p", "--properties")
                .required(false)
                .setDefault(DEFAULT_PROPERTIES_FILE_NAME)
                .help("the properties file to read the settings from (defaults to " + DEFAULT_PROPERTIES_FILE_NAME + ")");
        parser.addArgument("-v", "--version")
                .required(false)
                .help("print the version number of the application and exit")
                .action(Arguments.version());
        try {
            Namespace res = parser.parseArgs(args);
            Settings.build(res.get("workingdir"), res.get("properties"));
            Files.find(Settings.getSettings().getLessonsDir(), 3, isLessonFile).forEach(Main::extractSlides);
            writeIndexPage();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
