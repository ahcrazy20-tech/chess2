package com.chesshelper.app

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Chooses and holds the active engine for the whole app:
 * the bundled Stockfish 19 binary when present, otherwise the pure-Kotlin
 * [FallbackEngine]. Also provides a blocking analyze for background workers
 * (Blunder Review, Weakness Detector).
 */
object EngineService {

    lateinit var active: ChessEngine
        private set

    var usingStockfish: Boolean = false
        private set

    fun init(context: Context) {
        if (::active.isInitialized) return
        try {
            StockfishEngine.init(context)
            usingStockfish = StockfishEngine.isAvailable()
        } catch (e: Exception) {
            usingStockfish = false
        }
        active = if (usingStockfish) StockfishEngine else FallbackEngine
    }

    /** Switch permanently to the built-in engine (after a Stockfish failure). */
    fun degradeToFallback() {
        try { StockfishEngine.destroy() } catch (_: Exception) {}
        usingStockfish = false
        active = FallbackEngine
    }

    /**
     * Blocking analysis for background threads. Never touches the UI thread.
     * Returns null on failure/timeout.
     */
    fun analyzeBlocking(
        fen: String,
        multiPV: Int = 1,
        depth: Int = 12,
        movetimeMs: Int = 400,
        timeoutSec: Long = 30
    ): EngineAnalysis? {
        val latch = CountDownLatch(1)
        var result: EngineAnalysis? = null
        var error: Throwable? = null
        try {
            active.analyze(
                fen, multiPV, depth, movetimeMs,
                onResult = { result = it; latch.countDown() },
                onError = { error = it; latch.countDown() }
            )
            if (!latch.await(timeoutSec, TimeUnit.SECONDS)) return null
        } catch (e: Exception) {
            return null
        }
        if (error != null) {
            // One retry on the built-in engine if Stockfish blew up.
            if (usingStockfish) {
                degradeToFallback()
                return analyzeBlocking(fen, multiPV, depth, movetimeMs, timeoutSec)
            }
            return null
        }
        return result
    }
}
