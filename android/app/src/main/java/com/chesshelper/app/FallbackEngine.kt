package com.chesshelper.app

import java.util.concurrent.Executors

/**
 * A small but real chess engine written in pure Kotlin — negamax with
 * alpha-beta pruning and material + piece-square-table evaluation.
 *
 * Why: the Stockfish binary ships inside the APK when we can fetch it, but if
 * it is ever missing (or fails to start on a new device) the app keeps working
 * with this built-in engine instead of showing an error. It plays roughly club
 * level (shallow search), which is plenty for the overlay, blunder review and
 * move-quality grading to stay functional.
 */
object FallbackEngine : ChessEngine {

    override val name: String get() = "Built-in engine"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fallback-engine").apply { isDaemon = true }
    }

    private const val MATE = 100000

    private val PIECE_VALUE = intArrayOf(
        0, 100, 320, 330, 500, 900, 20000 // ., P, N, B, R, Q, K
    )

    // Piece-square tables (white's point of view, index 0 = a1).
    private val PST_PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, -20, -20, 10, 10, 5,
        5, -5, -10, 0, 0, -10, -5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val PST_KNIGHT = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )
    private val PST_BISHOP = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )
    private val PST_ROOK = intArrayOf(
        0, 0, 0, 5, 5, 0, 0, 0,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        5, 10, 10, 10, 10, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val PST_QUEEN = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -10, 5, 5, 5, 5, 5, 0, -10,
        0, 0, 5, 5, 5, 5, 0, -5,
        -5, 0, 5, 5, 5, 5, 0, -5,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )
    private val PST_KING = intArrayOf(
        20, 30, 10, 0, 0, 10, 30, 20,
        20, 20, 0, 0, 0, 0, 20, 20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30
    )

    private fun pstFor(piece: Char): IntArray = when (piece.uppercaseChar()) {
        'P' -> PST_PAWN
        'N' -> PST_KNIGHT
        'B' -> PST_BISHOP
        'R' -> PST_ROOK
        'Q' -> PST_QUEEN
        else -> PST_KING
    }

    private fun pieceIndex(piece: Char): Int = when (piece.uppercaseChar()) {
        'P' -> 1; 'N' -> 2; 'B' -> 3; 'R' -> 4; 'Q' -> 5; 'K' -> 6; else -> 0
    }

    /** Static evaluation in centipawns from WHITE's point of view. */
    fun evaluate(board: Board): Int {
        var score = 0
        for (i in 0 until 64) {
            val p = board.squares[i]
            if (p == '.') continue
            val pst = pstFor(p)
            val mirrored = if (p.isUpperCase()) i else (7 - (i / 8)) * 8 + (i % 8)
            val term = PIECE_VALUE[pieceIndex(p)] + pst[mirrored]
            score += if (p.isUpperCase()) term else -term
        }
        return score
    }

    /**
     * Negamax with alpha-beta. Returns score from the side-to-move's POV.
     * Mate scores are ±(MATE - ply).
     */
    private fun negamax(board: Board, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        if (depth <= 0) {
            val e = evaluate(board)
            return if (board.whiteToMove) e else -e
        }
        val moves = board.legalMoves()
        if (moves.isEmpty()) {
            return if (board.inCheck(board.whiteToMove)) -(MATE - ply) else 0
        }
        // Move ordering: captures first (cheap heuristic).
        val ordered = moves.sortedByDescending { mv ->
            val victim = board.squares[mv.to]
            if (victim != '.') PIECE_VALUE[pieceIndex(victim)] else 0
        }
        var best = Int.MIN_VALUE + 1
        var a = alpha
        for (mv in ordered) {
            val child = board.copy()
            child.make(mv)
            val score = -negamax(child, depth - 1, -beta, -a, ply + 1)
            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break
        }
        return best
    }

    override fun analyze(
        fen: String,
        multiPV: Int,
        depth: Int,
        movetimeMs: Int,
        onResult: (EngineAnalysis) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        executor.execute {
            try {
                val board = Board.fromFEN(fen)
                val rootMoves = board.legalMoves()
                if (rootMoves.isEmpty()) {
                    // Mate or stalemate right now.
                    val mated = board.inCheck(board.whiteToMove)
                    val cp = if (mated) {
                        if (board.whiteToMove) -MATE + 1 else MATE - 1
                    } else 0
                    onResult(
                        EngineAnalysis(
                            topMoves = emptyList(),
                            bestMove = TopMove(1, "", "", cp, null, 0),
                            depth = 0
                        )
                    )
                    return@execute
                }

                // Search a bit deeper with the fast engine, but never freeze the UI.
                val searchDepth = depth.coerceIn(1, 3)
                val scored = ArrayList<Pair<Move, Int>>(rootMoves.size)
                var alpha = Int.MIN_VALUE + 1
                for (mv in rootMoves) {
                    val child = board.copy()
                    child.make(mv)
                    // Is the opponent mated by this move?
                    val replyCount = child.legalMoves().size
                    val score = if (replyCount == 0) {
                        if (child.inCheck(child.whiteToMove)) MATE - 1 else 0
                    } else {
                        -negamax(child, searchDepth - 1, Int.MIN_VALUE + 1, -alpha, 1)
                    }
                    scored.add(Pair(mv, score))
                    if (score > alpha) alpha = score
                }
                scored.sortByDescending { it.second }

                val whiteToMove = board.whiteToMove
                val top = scored.take(multiPV.coerceAtLeast(1)).mapIndexed { idx, (mv, score) ->
                    val whiteCp = if (whiteToMove) score else -score
                    val mate: Int? = if (kotlin.math.abs(score) >= MATE - 50) {
                        val plies = MATE - kotlin.math.abs(score)
                        val movesToMate = (plies + 1) / 2
                        val sign = if (score > 0) (if (whiteToMove) 1 else -1)
                        else (if (whiteToMove) -1 else 1)
                        sign * movesToMate
                    } else null
                    TopMove(
                        rank = idx + 1,
                        uci = mv.uci(),
                        san = board.toSAN(mv),
                        evaluationCp = if (mate == null) whiteCp else null,
                        mateIn = mate,
                        depth = searchDepth,
                        pv = mv.uci()
                    )
                }
                onResult(EngineAnalysis(top, top.firstOrNull(), searchDepth))
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }
}
