package com.chesshelper.app.data

import java.util.UUID

data class TopMove(
    val id: String = UUID.randomUUID().toString(),
    val rank: Int,                 // 1 = best
    val uci: String,               // "e2e4"
    val san: String,               // "e4" (may fallback to UCI)
    val evaluationCp: Int?,        // centipawns from side-to-move? we store as white POV externally
    val mateIn: Int?,              // mate distance
    val depth: Int
) {
    val evalString: String
        get() {
            mateIn?.let { if (it != 0) return "M${kotlin.math.abs(it)}" }
            evaluationCp?.let { return String.format("%+.2f", it / 100.0) }
            return "—"
        }

    // Side-to-move win chance (0..100). Mirrors StockfishEngine.swift winChance sigmoid.
    val winChance: Double
        get() {
            mateIn?.let { return if (it > 0) 99.0 else 1.0 }
            evaluationCp?.let { cp ->
                val x = cp / 100.0
                return 50 + 50 * kotlin.math.tanh(x / 4.0)
            }
            return 50.0
        }
}

data class EngineAnalysis(
    val topMoves: List<TopMove>,
    val bestMove: TopMove?,
    val depth: Int
)
