package com.chesshelper.app

/**
 * One candidate move returned by either engine. All evaluations are from
 * WHITE's point of view (positive = white is better), matching the iOS app's
 * `evalString` convention. [winChance] is white's win probability 0..100 —
 * the overlay converts it to the player's own perspective.
 */
data class TopMove(
    val rank: Int,                  // 1 = best
    val uci: String,                // "e2e4"
    val san: String = "",           // "e4" (falls back to UCI if unknown)
    val evaluationCp: Int? = null,  // centipawns, WHITE POV
    val mateIn: Int? = null,        // moves to mate, WHITE POV (positive = white mates)
    val depth: Int = 0,
    val pv: String = ""             // space-separated UCI continuation
) {
    /** "+0.34" / "-1.20" / "M3" / "-M2" (white POV), like chess-api.com. */
    val evalString: String
        get() = when {
            mateIn != null && mateIn != 0 -> {
                val n = kotlin.math.abs(mateIn!!)
                if (mateIn!! > 0) "M$n" else "-M$n"
            }
            evaluationCp != null -> String.format("%+.2f", evaluationCp!! / 100.0)
            else -> "—"
        }

    /** White's win chance in percent (0..100). */
    val winChance: Double
        get() {
            val m = mateIn
            if (m != null && m != 0) return if (m > 0) 99.5 else 0.5
            val cp = evaluationCp ?: return 50.0
            val x = cp / 100.0
            return 50.0 + 50.0 * Math.tanh(x / 4.0)
        }
}

data class EngineAnalysis(
    val topMoves: List<TopMove>,
    val bestMove: TopMove?,
    val depth: Int,
    val ponder: String = ""    // opponent's likely reply, UCI ("e7e5")
)

/**
 * Common contract for the local Stockfish binary and the built-in fallback
 * engine, so the rest of the app never cares which one is active.
 */
interface ChessEngine {
    /** Human-readable engine name for the UI ("Stockfish 19" / "Built-in engine"). */
    val name: String

    fun analyze(
        fen: String,
        multiPV: Int,
        depth: Int,
        movetimeMs: Int,
        onResult: (EngineAnalysis) -> Unit,
        onError: (Throwable) -> Unit
    )

    fun stop() {}
    fun destroy() {}
}
