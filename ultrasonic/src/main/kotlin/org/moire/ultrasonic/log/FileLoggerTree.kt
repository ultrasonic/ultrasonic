package org.moire.ultrasonic.log

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * A Timber Tree which can be used to log to a file
 * Subclass of the DebugTree so it inherits the Tag handling
 */
@Suppress("MagicNumber")
class FileLoggerTree : Timber.DebugTree() {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Writes a log entry to file
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        var writer: FileWriter? = null
        callNum++
        try {
            getNextLogFile()
            writer = FileWriter(file, true)
            val exceptionString = t?.toString() ?: ""
            val time: String = dateFormat.format(Date())
            synchronized(file!!) {
                writer.write(
                    "$time: ${logLevelToString(priority)} $tag $message $exceptionString\n"
                )
                writer.flush()
            }
        } catch (x: Throwable) {
            // Using base class DebugTree here, we don't want to try to log this into file
            super.log(6, TAG, String.format("Failed to write log to %s", file), x)
        } finally {
            if (writer != null) Util.close(writer)
        }
    }

    /**
     * Sets the file to log into
     * This function also rotates the log files periodically, when they reach the predefined size
     */
    private fun getNextLogFile() {
        if (file == null) {
            synchronized(this) {
                if (file != null) return
                getNumberedFile(false)
                // Using base class DebugTree here, we don't want to try to log this into file
                super.log(4, TAG, String.format("Logging into file %s", file?.name), null)
                return
            }
        }
        if (callNum % 100 == 0) {
            // Gain some performance by only executing this rarely
            if (file!!.length() > MAX_LOGFILE_LENGTH) {
                synchronized(this) {
                    if (file!!.length() <= MAX_LOGFILE_LENGTH) return
                    getNumberedFile(true)
                    // Using base class DebugTree here, we don't want to try to log this into file
                    super.log(
                        4,
                        TAG,
                        String.format("Log file rotated, logging into file %s", file?.name),
                        null
                    )
                }
            }
        }
    }

    /**
     * Checks the number of log files
     * @param next: if false, sets the current log file with the greatest number
     * if true, sets a new file for logging with the next number
     */
    private fun getNumberedFile(next: Boolean) {
        var fileNum = 1
        val fileList = getLogFileList()

        if (!fileList.isNullOrEmpty()) {
            fileList.sortByDescending { t -> t.name }
            val lastFile = fileList[0]
            val number = fileNumberRegex.find(lastFile.name)?.groups?.get(1)?.value
            if (number != null) {
                fileNum = number.toInt()
            }
        }

        if (next) fileNum++
        file = File(
            FileUtil.getUltrasonicDirectory(),
            FILENAME.replace("*", fileNum.toString())
        )
    }

    private fun logLevelToString(logLevel: Int): String {
        return when (logLevel) {
            2 -> "V"
            3 -> "D"
            4 -> "I"
            5 -> "W"
            6 -> "E"
            7 -> "A"
            else -> "U"
        }
    }

    companion object {
        val TAG = FileLoggerTree::class.simpleName
        @Volatile private var file: File? = null
        const val FILENAME = "ultrasonic.*.log"
        private val fileNameRegex = Regex(
            FILENAME.replace(".", "\\.").replace("*", "\\d*")
        )
        private val fileNumberRegex = Regex(
            FILENAME.replace(".", "\\.").replace("*", "(\\d*)")
        )
        const val MAX_LOGFILE_LENGTH = 10000000
        var callNum = 0

        fun plantToTimberForest() {
            if (!Timber.forest().any { t -> t is FileLoggerTree }) {
                Timber.plant(FileLoggerTree())
            }
        }

        fun uprootFromTimberForest() {
            val fileLoggerTree = Timber.forest().singleOrNull { t -> t is FileLoggerTree }
                ?: return
            Timber.uproot(fileLoggerTree)
            file = null
        }

        fun getLogFileNumber(): Int {
            val fileList = getLogFileList()
            if (!fileList.isNullOrEmpty()) return fileList.size
            return 0
        }

        fun getLogFileSizes(): Long {
            var sizeSum: Long = 0
            val fileList = getLogFileList()
            if (fileList.isNullOrEmpty()) return sizeSum
            for (file in fileList) {
                sizeSum += file.length()
            }
            return sizeSum
        }

        fun deleteLogFiles() {
            val fileList = getLogFileList()
            if (fileList.isNullOrEmpty()) return
            for (file in fileList) {
                file.delete()
            }
        }

        private fun getLogFileList(): Array<File> {
            val directory = FileUtil.getUltrasonicDirectory()
            return directory.listFiles { t -> t.name.matches(fileNameRegex) }
        }
    }
}
