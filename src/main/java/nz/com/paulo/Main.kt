package nz.com.paulo

import com.github.ajalt.mordant.TermColors
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.impl.Arguments
import net.sourceforge.argparse4j.inf.ArgumentParserException
import java.io.File
import java.io.IOException
import java.lang.System.exit
import java.net.URISyntaxException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Consumer

/**
 * A program that takes as input a set of markdown files and extract sections marked to be used as slides.
 * Those extracted sections are then wrapped with an appropriate html wrapper and injected into the template
 * file and written to an output directory: one set of slides per input file. These html files can be used
 * with the reveal.js framework to give a slideshow.
 */
object Main {

    private const val DEFAULT_PROPERTIES_FILE_NAME = "SlideExtractor.properties"
    private val pagesWritten = LinkedHashMap<String, String>()

    private val isLessonFile = BiPredicate { path: Path, attrs: BasicFileAttributes ->
        val lessonMatcher = FileSystems.getDefault().getPathMatcher(
                "glob:" + Settings.settings.lessonsFileRegex)
        val isLesson = lessonMatcher.matches(path.fileName)
        attrs.isRegularFile && isLesson
    }

    private fun printError(message: String) {
        with(TermColors()) {
            println("${red(bold("Error!"))} ${reset(message)}")
        }
    }

    internal class Slide {
        val lines: MutableList<String> = ArrayList()
    }

    internal class Presentation(lessonName: String) {
        private val lessonName: String = lessonName.substring(0, lessonName.lastIndexOf('.'))
        private var currentSlide: Slide? = null
        private val slides: MutableList<Slide> = ArrayList()

        private fun createSlide() {
            currentSlide = Slide()
        }

        private fun saveSlide(lineNo: Int) {
            if (currentSlide!!.lines.size > MAX_SLIDE_LENGTH) {
                println("Warning. Long slide with " + currentSlide!!.lines.size + " lines found: line " + lineNo)
            }
            slides.add(currentSlide!!)
            currentSlide = null
        }

        fun filterLine(line: String, lineNo: Int) {
            if (isEndOfSlide(line)) {
                if (!creatingSlide()) {
                    printError("Slide end found without slide start: line $lineNo")
                } else {
                    saveSlide(lineNo)
                }
            } else if (isStartOfSlide(line)) {
                if (creatingSlide()) {
                    printError("New slide start found whilst still creating slide: line $lineNo")
                    saveSlide(lineNo)
                }
                createSlide()
            } else if (creatingSlide()) {
                currentSlide!!.lines.add(line)
            }
        }

        private fun creatingSlide(): Boolean {
            return currentSlide != null
        }

        private fun isStartOfSlide(line: String): Boolean {
            return line.trim { it <= ' ' }.equals(Settings.SLIDE_START_LINE, ignoreCase = true)
        }

        private fun isEndOfSlide(line: String): Boolean {
            return line.trim { it <= ' ' }.equals(Settings.SLIDE_END_LINE, ignoreCase = true)
        }

        @Throws(IOException::class, URISyntaxException::class)
        fun writeToFile() {
            if (creatingSlide()) {
                printError("Slides being saved without last slide being properly closed!")
            }
            val templatePath = Paths.get(Settings.settings.template)
            val template = Files.readAllLines(templatePath)
            val lines = ArrayList<String>()
            slides.forEach { s ->
                lines.add("<section data-markdown><script type=\"text/template\">")
                lines.addAll(s.lines)
                lines.add("</script></section>")
            }
            val insertionPoint = template.indexOf(Settings.SLIDES_INSERTION_LINE) + 1
            template.addAll(insertionPoint, lines)
            val targetFileName = "$lessonName.html"
            val target = Settings.settings.revealDirectory + "/" + targetFileName
            println("Writing to: $target")
            Files.write(Paths.get(target), template)
            pagesWritten[lessonName] = targetFileName
        }

        companion object {

            /* If slide with more than this number of lines is found a warning is emitted. */
            private const val MAX_SLIDE_LENGTH = 22
        }
    }

    private fun extractSlides(p: Path) {
        try {
            println("Working on: $p")
            val presentation = Presentation(p.fileName.toString())
            Files.lines(p).forEach(object : Consumer<String> {
                var lineNo = 0

                override fun accept(line: String) {
                    lineNo++
                    presentation.filterLine(line, lineNo)
                }
            })
            presentation.writeToFile()
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }

    }

    @Throws(IOException::class)
    private fun writeIndexPage() {
        val templatePath = Paths.get(Settings.settings.template)
        val template = Files.readAllLines(templatePath)
        val insertionPoint = template.indexOf(Settings.SLIDES_INSERTION_LINE) + 1
        val lines = ArrayList<String>()
        lines.add("<ul>")
        pagesWritten.forEach { (k, v) -> lines.add("<li><a href=\"$v#/\">$k</a></li>") }
        lines.add("</ul>")
        template.addAll(insertionPoint, lines)
        val targetFileName = "index.html"
        val target = Settings.settings.revealDirectory + "/" + targetFileName
        println("Writing to: $target")
        Files.write(Paths.get(target), template)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgumentParsers.newArgumentParser("SlideExtractor")
                .description("Extracts reveal.js slides from markdown.")
                // following reads the manifest in the jar: if not in the jar, returns a version of null
                .version("\${prog} " + Main::class.java.getPackage().implementationVersion)
        parser.addArgument("-d", "--workingdir")
                .required(false)
                .setDefault(".")
                .help("the working directory (defaults to the current directory)")
        parser.addArgument("-p", "--properties")
                .required(false)
                .setDefault(DEFAULT_PROPERTIES_FILE_NAME)
                .help("the properties file to read the settings from (defaults to $DEFAULT_PROPERTIES_FILE_NAME)")
        parser.addArgument("-v", "--version")
                .required(false)
                .help("print the version number of the application and exit")
                .action(Arguments.version())
        try {
            val res = parser.parseArgs(args)
            println("Working directory is: ${res.get<String>("workingdir")}")
            Settings.build(res.get("workingdir"), res.get("properties"))
            println("SlideExtractor version: ${Settings.settings.version}")
            println("Checking for presence of reveal.js...")
            val file = File("${Settings.settings.revealDirectory}/reveal.js/README.md")
            if (!file.exists()) {
                printError("Could not find reveal.js. Have you forgotten to check it out?")
                println("If so, run ${System.getProperty("line.separator")}" +
                        "   git submodule init${System.getProperty("line.separator")}" +
                        "   git submodule update${System.getProperty("line.separator")}" +
                        "to fetch it...")
                exit(1)
            } else {
                println("Required library reveal.js is present.")
            }
            Files.find(Settings.settings.lessonsDir, 3, isLessonFile).forEach { extractSlides(it) }
            writeIndexPage()
        } catch (e: ArgumentParserException) {
            parser.handleError(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

}
