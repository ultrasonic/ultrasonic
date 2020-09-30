package org.moire.ultrasonic.log

import android.content.Context
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber
import java.io.File
import java.io.FileWriter

/**
 * A Timber Tree which can be used to log to a file
 * Subclass of the DebugTree so it inherits the Tag handling
 */
class FileLoggerTree(val context: Context) : Timber.DebugTree() {
    private val filename = "ultrasonic.log"

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        var file: File? = null
        var writer: FileWriter? = null
        try {
            file = File(FileUtil.getUltrasonicDirectory(context), filename)
            writer = FileWriter(file, true)
            val exceptionString = t?.toString() ?: "";
            writer.write("${logLevelToString(priority)} $tag $message $exceptionString\n")
            writer.flush()
        } catch (x: Throwable) {
            super.e(x, "Failed to write log to %s", file)
        } finally {
            if (writer != null) Util.close(writer)
        }
    }

    private fun logLevelToString(logLevel: Int) : String {
        return when(logLevel) {
            2 -> "V"
            3 -> "D"
            4 -> "I"
            5 -> "W"
            6 -> "E"
            7 -> "A"
            else -> "U"
        }
    }
}