package com.chesshelper.app

/**
 * Convert a UCI move to SAN in the position given by [fen]. Falls back to the
 * raw UCI when anything goes wrong. Used by the overlay and Blunder Review so
 * engine output is shown in proper notation ("Nf3", not "g1f3").
 */
fun sanOf(fen: String, uci: String): String {
    if (uci.isEmpty()) return ""
    return try {
        val board = Board.fromFEN(fen)
        val mv = board.parseUCI(uci) ?: return uci
        val san = board.toSAN(mv)
        if (san.isEmpty()) uci else san
    } catch (e: Exception) {
        uci
    }
}
