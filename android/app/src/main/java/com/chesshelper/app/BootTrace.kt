package com.chesshelper.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only trace of app startup. Each run starts with a fresh file; the
 * previous run's trace is returned so it can be displayed if that run never
 * reached the UI_READY marker. CrashHandler appends the stack trace here too.
 */
object BootTrace {
    private const val FILE_NAME = "boot-trace.log"
    private val stampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Trace of the previous process run, captured before the file was cleared. */
    @Volatile
    var previousRun: String = ""
        private set

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun startNewRun(context: Context): String {
        val previous = try {
            if (file(context).exists()) file(context).readText() else ""
        } catch (_: Exception) {
            ""
        }
        previousRun = previous
        try {
            file(context).writeText("")
        } catch (_: Exception) {
        }
        mark(context, "RUN_START")
        return previous
    }

    fun mark(context: Context, stage: String) {
        try {
            val line = "${stampFormat.format(Date())} $stage\n"
            file(context).appendText(line)
        } catch (_: Exception) {
            // Tracing must never cause a crash.
        }
    }

    fun read(context: Context): String = try {
        file(context).readText()
    } catch (_: Exception) {
        ""
    }
}
