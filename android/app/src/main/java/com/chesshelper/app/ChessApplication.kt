package com.chesshelper.app

import android.app.Application

class ChessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Catch any crash before the UI starts so it can be reported next launch.
        CrashHandler.install(this)
    }
}
