package com.chesshelper.app

import android.app.Application

class ChessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Intentional no-op here: Stockfish + Stealth are initialized from MainActivity
        // once we know the arm64 stockfish asset has been copied to the app's files dir.
    }
}
