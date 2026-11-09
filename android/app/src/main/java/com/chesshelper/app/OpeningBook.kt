package com.chesshelper.app

/**
 * Offline opening book. Port of LocalOpeningBook.swift — matches by SAN move prefix,
 * never touches the network.
 */
object OpeningBook {
    data class Entry(val eco: String, val name: String, val moves: List<String>)

    private val entries = listOf(
        // 1.e4
        Entry("B00", "King's Pawn Opening", listOf("e4")),
        Entry("C20", "King's Pawn Game", listOf("e4", "e5")),
        Entry("C40", "King's Knight Opening", listOf("e4", "e5", "Nf3")),
        Entry("C44", "Scotch Game", listOf("e4", "e5", "Nf3", "Nc6", "d4")),
        Entry("C50", "Italian Game", listOf("e4", "e5", "Nf3", "Nc6", "Bc4")),
        Entry("C60", "Ruy López", listOf("e4", "e5", "Nf3", "Nc6", "Bb5")),
        Entry("C65", "Ruy López: Berlin", listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6")),
        Entry("C78", "Ruy López: Morphy", listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4")),
        // Sicilian
        Entry("B20", "Sicilian Defense", listOf("e4", "c5")),
        Entry("B27", "Sicilian: Open", listOf("e4", "c5", "Nf3")),
        Entry("B30", "Sicilian: Rossolimo", listOf("e4", "c5", "Nf3", "Nc6", "Bb5")),
        Entry("B50", "Sicilian: Najdorf setup", listOf("e4", "c5", "Nf3", "d6")),
        Entry("B90", "Sicilian: Najdorf", listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "a6")),
        Entry("B22", "Sicilian: Alapin", listOf("e4", "c5", "c3")),
        // French / Caro
        Entry("C00", "French Defense", listOf("e4", "e6")),
        Entry("C02", "French: Advance", listOf("e4", "e6", "d4", "d5", "e5")),
        Entry("B12", "Caro-Kann Defense", listOf("e4", "c6")),
        Entry("B18", "Caro-Kann: Classical", listOf("e4", "c6", "d4", "d5", "Nc3", "dxe4", "Nxe4", "Bf5")),
        // Pirc / Modern / Scandi / Alekhine
        Entry("B07", "Pirc Defense", listOf("e4", "d6", "d4", "Nf6", "Nc3", "g6")),
        Entry("B01", "Scandinavian Defense", listOf("e4", "d5")),
        Entry("B02", "Alekhine's Defense", listOf("e4", "Nf6")),
        // 1.d4
        Entry("A40", "Queen's Pawn Opening", listOf("d4")),
        Entry("D02", "London System", listOf("d4", "d5", "Nf3", "Nf6", "Bf4")),
        Entry("D00", "Queen's Pawn Game", listOf("d4", "d5")),
        Entry("D06", "Queen's Gambit", listOf("d4", "d5", "c4")),
        Entry("D20", "Queen's Gambit Accepted", listOf("d4", "d5", "c4", "dxc4")),
        Entry("D30", "Queen's Gambit Declined", listOf("d4", "d5", "c4", "e6")),
        Entry("D10", "Slav Defense", listOf("d4", "d5", "c4", "c6")),
        // Indian
        Entry("A45", "Indian Game", listOf("d4", "Nf6")),
        Entry("E60", "King's Indian Defense", listOf("d4", "Nf6", "c4", "g6")),
        Entry("E20", "Nimzo-Indian Defense", listOf("d4", "Nf6", "c4", "e6", "Nc3", "Bb4")),
        Entry("E12", "Queen's Indian Defense", listOf("d4", "Nf6", "c4", "e6", "Nf3", "b6")),
        Entry("A50", "Benoni / Indian systems", listOf("d4", "Nf6", "c4", "c5")),
        Entry("A80", "Dutch Defense", listOf("d4", "f5")),
        Entry("D70", "Grünfeld Defense", listOf("d4", "Nf6", "c4", "g6", "Nc3", "d5")),
        // Flank
        Entry("A10", "English Opening", listOf("c4")),
        Entry("A04", "Réti Opening", listOf("Nf3")),
        Entry("A00", "Uncommon Opening", emptyList())
    )

    fun match(sanMoves: List<String>): Entry? {
        if (sanMoves.isEmpty()) return null
        var best: Entry? = null
        var bestLen = 0
        for (entry in entries) {
            if (entry.moves.size <= sanMoves.size && entry.moves.size > bestLen) {
                if (sanMoves.subList(0, entry.moves.size) == entry.moves) {
                    best = entry
                    bestLen = entry.moves.size
                }
            }
        }
        return best
    }
}
