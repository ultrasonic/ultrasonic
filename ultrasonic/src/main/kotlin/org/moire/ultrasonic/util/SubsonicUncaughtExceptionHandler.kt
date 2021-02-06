package org.moire.ultrasonic.util

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.PrintWriter

private const val filename = "ultrasonic-stacktrace.txt"

/**
 * Logs the stack trace of uncaught exceptions to a file on the SD card.
 */
class SubsonicUncaughtExceptionHandler (
    private val context: Context
    ) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        var file: File? = null
        var printWriter: PrintWriter? = null

        try {
            file = File(FileUtil.getUltrasonicDirectory(context), filename)
            printWriter = PrintWriter(file)
            val logMessage = String.format(
                "Android API level: %s\nUltrasonic version name: %s\nUltrasonic version code: %s\n\n",
                Build.VERSION.SDK_INT, Util.getVersionName(context), Util.getVersionCode(context))
            printWriter.println(logMessage)
            throwable.printStackTrace(printWriter)
            Timber.e(throwable, "Uncaught Exception! %s", logMessage)
            Timber.i("Stack trace written to %s", file)
        } catch (x: Throwable) {
            Timber.e(x, "Failed to write stack trace to %s", file)
        } finally {
            Util.close(printWriter)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}