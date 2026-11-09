package com.chesshelper.app

import android.app.Application
import com.chesshelper.app.data.StockfishEngine

class ChessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm Stockfish on app start (matches iOS: await StockfishEngine.shared.start())
        // Offload to background so launch stays snappy on S26 Ultra's Snapdragon 8 Elite.
        StockfishEngine.initialize(this)
    }
}
