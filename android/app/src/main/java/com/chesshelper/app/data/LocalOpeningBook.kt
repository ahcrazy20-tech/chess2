package com.chesshelper.app.data

/**
 * Offline opening book — direct port of Sources/LocalOpeningBook.swift
 * Covers the 30 most common openings so the overlay can show ECO + name without network.
 */
object LocalOpeningBook {

    data class Entry(val eco: String, val name: String, val moves: List<String>)

    val entries = listOf(
        // 1.e4
        Entry("B00", "King's Pawn Opening", listOf("e4")),
        Entry("C20", "King's Pawn Game", listOf("e4","e5")),
        Entry("C50", "Italian Game", listOf("e4","e5","Nf3","Nc6","Bc4")),
        Entry("C60", "Ruy Lopez", listOf("e4","e5","Nf3","Nc6","Bb5")),
        Entry("B01", "Scandinavian Defense", listOf("e4","d5")),
        Entry("B06", "Modern Defense", listOf("e4","g6")),
        Entry("C00", "French Defense", listOf("e4","e6")),
        Entry("B10", "Caro-Kann Defense", listOf("e4","c6")),
        Entry("B20", "Sicilian Defense", listOf("e4","c5")),
        Entry("C23", "Bishop's Opening", listOf("e4","e5","Bc4")),
        Entry("C44", "King's Knight Opening", listOf("e4","e5","Nf3")),
        // 1.d4
        Entry("D00", "Queen's Pawn Opening", listOf("d4")),
        Entry("D06", "Queen's Gambit", listOf("d4","d5","c4")),
        Entry("D37", "Queen's Gambit Declined", listOf("d4","d5","c4","e6","Nc3","Nf6")),
        Entry("D02", "London System", listOf("d4","Nf6","Bf4")),
        Entry("A45", "Indian Game", listOf("d4","Nf6")),
        Entry("E60", "King's Indian Defense", listOf("d4","Nf6","c4","g6")),
        Entry("E20", "Nimzo-Indian Defense", listOf("d4","Nf6","c4","e6","Nc3","Bb4")),
        Entry("E12", "Queen's Indian Defense", listOf("d4","Nf6","c4","e6","Nf3","b6")),
        Entry("A50", "Benoni / Indian systems", listOf("d4","Nf6","c4","c5")),
        Entry("A80", "Dutch Defense", listOf("d4","f5")),
        Entry("D70", "Grünfeld Defense", listOf("d4","Nf6","c4","g6","Nc3","d5")),
        // Flank
        Entry("A10", "English Opening", listOf("c4")),
        Entry("A04", "Réti Opening", listOf("Nf3")),
        Entry("A00", "Uncommon Opening", listOf())
    )

    fun match(sanMoves: List<String>): Entry? {
        if (sanMoves.isEmpty()) return null
        var best: Entry? = null
        var bestLen = 0
        for (e in entries) {
            if (e.moves.size <= sanMoves.size && e.moves.size > bestLen) {
                if (sanMoves.take(e.moves.size) == e.moves) {
                    best = e
                    bestLen = e.moves.size
                }
            }
        }
        return best
    }
}
