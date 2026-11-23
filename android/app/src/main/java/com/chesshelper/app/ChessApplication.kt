package com.chesshelper.app

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper

class ChessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Catch any crash before the UI starts so it can be reported next launch.
        CrashHandler.install(this)

        val previousRun = BootTrace.startNewRun(this)
        if (previousRun.isNotBlank() && !previousRun.contains("UI_READY")) {
            // The last launch never finished: show a shareable report instead of
            // relying on the small dialog in MainActivity (which may never load).
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val intent = Intent(this, CrashReportActivity::class.java).apply {
                        putExtra("log", previousRun.take(30000))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }, 300)
        }
    }
}
