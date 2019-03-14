package nz.com.paulo

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * The application settings
 * Created by Martin Paulo on 12/10/2015.
 */
class Settings private constructor(base_Directory: String, propertiesFile: String) {

    private val defaultProps = Properties()
    private val baseDirectory: String

    val revealDirectory: String
        get() = baseDirectory + defaultProps.getProperty("revealDirectory")

    private val lessonsDirectory: String
        get() = baseDirectory + defaultProps.getProperty("lessonsDirectory")

    val lessonsDir: Path
        get() = Paths.get(lessonsDirectory)

    val lessonsFileRegex: String
        get() = defaultProps.getProperty("lessonsFileRegex")

    val template: String
        get() = baseDirectory + defaultProps.getProperty("template")

    init {
        var baseDirectory = base_Directory
        if (!baseDirectory.endsWith(File.separator)) {
            baseDirectory += File.separator
        }
        this.baseDirectory = baseDirectory
        try {
            FileInputStream(baseDirectory + propertiesFile).use { `in` -> defaultProps.load(`in`) }
        } catch (e: IOException) {
            throw RuntimeException("Could not read the resource file named $propertiesFile", e)
        }

    }

    companion object {


        internal const val SLIDE_START_LINE = "-- *Slide* --"
        internal const val SLIDE_END_LINE = "-- *Slide End* --"

        internal const val SLIDES_INSERTION_LINE = "<!-- Slides go here -->"

        private lateinit var instance: Settings


        internal fun build(baseDirectory: String, propertiesFile: String) {
            instance = Settings(baseDirectory, propertiesFile)
        }

        internal val settings: Settings
            get() {
                return instance
            }
    }
}
