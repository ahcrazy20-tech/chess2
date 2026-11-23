package com.chesshelper.app

/**
 * PGN utilities. Port of PGN.swift:
 *  - [extractMoves]: strips tags/comments/NAGs/move numbers, returns SAN tokens.
 *  - [replay]: plays the moves through a real [Board] and returns the FEN after
 *    every ply (used by Blunder Review and the Weakness Detector).
 */
object PgnReader {

    data class BoardState(
        val fen: String,
        val moveSAN: String,
        val moveUci: String
    )

    fun extractMoves(pgn: String): List<String> {
        var s = pgn
        s = s.replace(Regex("\\[[^\\]]*\\]"), "")       // tag pairs
        s = s.replace(Regex("\\{[^}]*\\}"), "")          // comments
        s = s.replace(Regex(";[^\\n]*"), "")             // rest-of-line comments
        s = s.replace(Regex("\\$\\d+"), "")              // NAGs
        s = s.replace(Regex("\\([^)]*\\)"), "")          // (variations) — non-nested
        s = s.replace(Regex("\\d+\\.(\\.\\.)?"), "")     // move numbers
        for (r in listOf("1-0", "0-1", "1/2-1/2", "*")) {
            s = s.replace(r, " ")
        }
        return s.split(Regex("\\s+")).filter {
            it.isNotEmpty() && it != "..." 
        }
    }

    /** Replay [pgn] and return the initial state plus one state per applied move. */
    fun replay(pgn: String): List<BoardState> {
        val moves = extractMoves(pgn)
        val board = Board.initial()
        val states = ArrayList<BoardState>(moves.size + 1)
        states.add(BoardState(board.fen(), "", ""))
        for (san in moves) {
            val mv = board.parseSAN(san) ?: break
            val uci = mv.uci()
            board.make(mv)
            states.add(BoardState(board.fen(), san, uci))
        }
        return states
    }

    /** Count playable half-moves in a PGN (used for game-length statistics). */
    fun plyCount(pgn: String): Int {
        var count = 0
        val board = Board.initial()
        for (san in extractMoves(pgn)) {
            val mv = board.parseSAN(san) ?: break
            board.make(mv)
            count++
        }
        return count
    }
}
