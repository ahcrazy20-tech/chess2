package com.chesshelper.app

/**
 * One candidate move returned by Stockfish. Mirrors TopMove in StockfishEngine.swift.
 */
data class TopMove(
    val rank: Int,              // 1 = best, 2 = 2nd best, 3 = 3rd best
    val uci: String,            // "e2e4"
    val san: String = "",       // "e4" (falls back to UCI if we can't convert)
    val evaluationCp: Int? = null, // centipawns, white POV
    val mateIn: Int? = null,    // mate distance (positive = white winning)
    val depth: Int = 0
) {
    val evalString: String
        get() = when {
            mateIn != null && mateIn != 0 -> "M${mateIn.absoluteValue}"
            evaluationCp != null -> String.format("%.2f", evaluationCp / 100.0)
            else -> "—"
        }

    // Sigmoid win chance (side-to-move POV), matching the iOS view model.
    val winChance: Double
        get() {
            mateIn?.let { m ->
                return if (m != 0) if (m > 0) 99.0 else 1.0 else 50.0
            }
            val cp = evaluationCp ?: return 50.0
            val x = cp / 100.0
            return 50.0 + 50.0 * Math.tanh(x / 4.0)
        }
}
