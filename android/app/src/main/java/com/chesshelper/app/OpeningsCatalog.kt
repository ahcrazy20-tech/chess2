package com.chesshelper.app

/**
 * Port of OpeningsView.swift's catalog: 10 classic openings with their main
 * lines, used by the Opening Trainer tab. Works fully offline.
 */
data class Opening(
    val id: String,
    val name: String,
    val eco: String,
    val moves: List<String>,        // SAN sequence, e.g. ["e4","e5","Nf3"]
    val whiteRepertoire: Boolean,
    val description: String
)

object OpeningsCatalog {
    val all: List<Opening> = listOf(
        Opening(
            "italian", "Italian Game", "C50",
            listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "c3", "Nf6", "d3", "d6"),
            true, "Classical, quiet opening with clear plans. Great for beginners."
        ),
        Opening(
            "ruy", "Ruy López (Spanish)", "C60",
            listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7"),
            true, "The Spanish Opening. Long-term positional pressure."
        ),
        Opening(
            "queens_gambit", "Queen's Gambit Declined", "D30",
            listOf("d4", "d5", "c4", "e6", "Nc3", "Nf6", "Bg5", "Be7", "e3", "O-O"),
            true, "Solid queenside opening favored by classical world champions."
        ),
        Opening(
            "london", "London System", "D02",
            listOf("d4", "d5", "Nf3", "Nf6", "Bf4", "c5", "e3", "Nc6", "c3", "e6"),
            true, "Easy-to-learn system; same setup vs almost anything."
        ),
        Opening(
            "sicilian_najdorf", "Sicilian Defense: Najdorf", "B90",
            listOf("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "a6"),
            false, "Sharpest, most-studied opening in chess. Fischer's favorite."
        ),
        Opening(
            "caro_kann", "Caro-Kann Defense", "B12",
            listOf("e4", "c6", "d4", "d5", "e5", "Bf5", "Nf3", "e6", "Be2", "c5"),
            false, "Solid, reliable defense against 1.e4. Karpov's weapon."
        ),
        Opening(
            "kings_indian", "King's Indian Defense", "E60",
            listOf("d4", "Nf6", "c4", "g6", "Nc3", "Bg7", "e4", "d6", "Nf3", "O-O"),
            false, "Aggressive counter to 1.d4. Kingside pawn storms."
        ),
        Opening(
            "french", "French Defense", "C00",
            listOf("e4", "e6", "d4", "d5", "Nc3", "Nf6", "Bg5", "Be7", "e5", "Nfd7"),
            false, "Solid pawn chain defense. Strategic and closed."
        ),
        Opening(
            "scandinavian", "Scandinavian Defense", "B01",
            listOf("e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "d4", "Nf6", "Nf3", "c6"),
            false, "Quick equalizer; simple ideas for club players."
        ),
        Opening(
            "english", "English Opening", "A10",
            listOf("c4", "e5", "Nc3", "Nf6", "g3", "Bb4", "Bg2", "O-O", "Nf3", "Nc6"),
            true, "Flank opening; flexible move-order specialist."
        )
    )
}
