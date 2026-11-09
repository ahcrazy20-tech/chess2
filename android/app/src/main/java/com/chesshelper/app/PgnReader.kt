package com.chesshelper.app

/**
 * PGN utilities. Port of PGN.swift move extraction.
 *
 * Note: the iOS version also carries a small in-Swift board to step SAN moves to FENs.
 * On Android we keep move extraction here and defer board/FEN stepping to Stockfish
 * (`position fen <fen>` + `go`) so the engine stays the single source of truth.
 */
object PgnReader {
    fun extractMoves(pgn: String): List<String> {
        var s = pgn
        s = s.replace(Regex("\\[[^\\]]*\\]"), "")        // strip tags
        s = s.replace(Regex("\\{[^}]*\\}"), "")          // strip comments
        s = s.replace(Regex("\\$\\d+"), "")              // strip nag glyphs
        for (r in listOf("1-0", "0-1", "1/2-1/2", "*")) {
            s = s.replace(r, "")
        }
        s = s.replace(Regex("\\d+\\.(\\.\\.)?"), "")     // strip move numbers
        return s.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
}
