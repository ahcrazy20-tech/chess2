package com.chesshelper.app.data

/**
 * Shim mirroring Sources/ChessAPI.swift but for Android.
 * iOS originally had network calls; they are removed — we use local Stockfish + LocalOpeningBook.
 * This file keeps compatibility so older ViewModels can call ChessAPI.bestMove / opening.
 */
object ChessApi {

    data class EngineResultCompat(
        val move: String,
        val san: String,
        val ponder: String,
        val evalString: String,
        val winChance: Double,
        val depth: Int,
        val pvText: String
    )

    suspend fun bestMove(fen: String, depth: Int = 20): EngineResultCompat {
        val analysis = StockfishEngine.analyze(fen, multiPV = 1, depth = depth, movetime = 2000)
        val best = analysis.bestMove ?: throw IllegalStateException("Engine returned no move")
        return EngineResultCompat(
            move = best.uci,
            san = best.san,
            ponder = "",
            evalString = best.evalString,
            winChance = best.winChance,
            depth = analysis.depth,
            pvText = ""
        )
    }

    data class OpeningInfoCompat(val name: String, val eco: String, val topMoves: List<OpeningMove> = emptyList())

    fun opening(sanMoves: List<String>): OpeningInfoCompat? {
        val e = LocalOpeningBook.match(sanMoves) ?: return null
        return OpeningInfoCompat(e.name, e.eco)
    }

    suspend fun opening(fen: String): OpeningInfoCompat? = null // FEN lookup not offline
}

data class OpeningMove(
    val san: String,
    val white: Int = 0,
    val draws: Int = 0,
    val black: Int = 0
) {
    val totalGames: Int get() = white + draws + black
    val winRate: Double get() = if (totalGames == 0) 0.0 else white.toDouble() / totalGames * 100
}
