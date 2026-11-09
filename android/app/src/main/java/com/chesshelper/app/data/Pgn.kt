package com.chesshelper.app.data

/**
 * Minimal PGN helper — port of Sources/PGN.swift
 * Builds PGN strings for sharing / review tab and can extract SAN lists for opening lookup.
 */
object Pgn {

    data class Game(
        val event: String = "ChessHelper Game",
        val site: String = "chess.com",
        val date: String = currentDate(),
        val white: String = "White",
        val black: String = "Black",
        val result: String = "*",
        val sanMoves: List<String> = emptyList(),
        val fenStart: String? = null
    ) {
        fun toPgn(): String {
            val headers = buildList {
                add("""[Event "$event"]""")
                add("""[Site "$site"]""")
                add("""[Date "$date"]""")
                add("""[White "$white"]""")
                add("""[Black "$black"]""")
                add("""[Result "$result"]""")
                if (fenStart != null) {
                    add("""[FEN "$fenStart"]""")
                    add("""[SetUp "1"]""")
                }
            }.joinToString("\n")
            val moves = sanMoves.mapIndexed { idx, san ->
                if (idx % 2 == 0) "${idx/2 + 1}. $san" else san
            }.joinToString(" ")
            return "$headers\n\n$moves $result\n"
        }
    }

    private fun currentDate(): String {
        val c = java.util.Calendar.getInstance()
        return String.format("%04d.%02d.%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH)+1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    /**
     * Extract SANs from a raw PGN move text. Loose parser — enough for opening book.
     */
    fun extractSanMoves(pgnMovesText: String): List<String> {
        // Remove comments { }, variations ( ), NAGs $1, result markers
        var t = pgnMovesText
        t = t.replace(Regex("\\{[^}]*\\}"), " ")
        t = t.replace(Regex("\\([^)]*\\)"), " ")
        t = t.replace(Regex("\\$\\d+"), " ")
        t = t.replace(Regex("1-0|0-1|1/2-1/2|\\*"), " ")
        // Remove move numbers "1." etc
        t = t.replace(Regex("\\d+\\.+"), " ")
        return t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.matches(Regex("[a-hKQRBNxO0\\-+#=]+")) }
    }
}
