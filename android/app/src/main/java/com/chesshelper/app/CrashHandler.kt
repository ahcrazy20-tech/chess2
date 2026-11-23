package com.chesshelper.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves uncaught exceptions to `filesDir/crash.log` so a crash is never silent.
 * MainActivity shows the saved trace in a dialog on the next launch (and the
 * user can copy/report it), then clears the file.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private var appContext: Context? = null
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun crashFile(): File? =
        appContext?.let { File(it.filesDir, "crash.log") }

    fun readAndClear(): String? {
        val f = crashFile() ?: return null
        if (!f.exists()) return null
        val text = try {
            f.readText()
        } catch (e: Exception) {
            null
        }
        try { f.delete() } catch (e: Exception) { }
        return text
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val text = "=== crash $stamp thread=${t.name} ===\n$sw\n"
            crashFile()?.appendText(text)
        } catch (ignored: Throwable) {
            // Never mask the original crash.
        }
        previous?.uncaughtException(t, e)
    }
}
