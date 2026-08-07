import Foundation

/// A tiny offline opening book. Removes the need to query Lichess during play.
/// Matches by move sequence prefix (SAN).
enum LocalOpeningBook {

    struct Entry {
        let eco: String
        let name: String
        let moves: [String]
    }

    static let entries: [Entry] = [
        // 1.e4
        Entry(eco: "B00", name: "King's Pawn Opening", moves: ["e4"]),
        Entry(eco: "C20", name: "King's Pawn Game", moves: ["e4","e5"]),
        Entry(eco: "C40", name: "King's Knight Opening", moves: ["e4","e5","Nf3"]),
        Entry(eco: "C44", name: "Scotch Game", moves: ["e4","e5","Nf3","Nc6","d4"]),
        Entry(eco: "C50", name: "Italian Game", moves: ["e4","e5","Nf3","Nc6","Bc4"]),
        Entry(eco: "C60", name: "Ruy López", moves: ["e4","e5","Nf3","Nc6","Bb5"]),
        Entry(eco: "C65", name: "Ruy López: Berlin", moves: ["e4","e5","Nf3","Nc6","Bb5","Nf6"]),
        Entry(eco: "C78", name: "Ruy López: Morphy", moves: ["e4","e5","Nf3","Nc6","Bb5","a6","Ba4"]),
        // Sicilian
        Entry(eco: "B20", name: "Sicilian Defense", moves: ["e4","c5"]),
        Entry(eco: "B27", name: "Sicilian: Open", moves: ["e4","c5","Nf3"]),
        Entry(eco: "B30", name: "Sicilian: Rossolimo", moves: ["e4","c5","Nf3","Nc6","Bb5"]),
        Entry(eco: "B50", name: "Sicilian: Najdorf setup", moves: ["e4","c5","Nf3","d6"]),
        Entry(eco: "B90", name: "Sicilian: Najdorf", moves: ["e4","c5","Nf3","d6","d4","cxd4","Nxd4","Nf6","Nc3","a6"]),
        Entry(eco: "B22", name: "Sicilian: Alapin", moves: ["e4","c5","c3"]),
        // French / Caro
        Entry(eco: "C00", name: "French Defense", moves: ["e4","e6"]),
        Entry(eco: "C02", name: "French: Advance", moves: ["e4","e6","d4","d5","e5"]),
        Entry(eco: "B12", name: "Caro-Kann Defense", moves: ["e4","c6"]),
        Entry(eco: "B18", name: "Caro-Kann: Classical", moves: ["e4","c6","d4","d5","Nc3","dxe4","Nxe4","Bf5"]),
        // Pirc / Modern / Scandi / Alekhine
        Entry(eco: "B07", name: "Pirc Defense", moves: ["e4","d6","d4","Nf6","Nc3","g6"]),
        Entry(eco: "B01", name: "Scandinavian Defense", moves: ["e4","d5"]),
        Entry(eco: "B02", name: "Alekhine's Defense", moves: ["e4","Nf6"]),
        // 1.d4
        Entry(eco: "A40", name: "Queen's Pawn Opening", moves: ["d4"]),
        Entry(eco: "D02", name: "London System", moves: ["d4","d5","Nf3","Nf6","Bf4"]),
        Entry(eco: "D00", name: "Queen's Pawn Game", moves: ["d4","d5"]),
        Entry(eco: "D06", name: "Queen's Gambit", moves: ["d4","d5","c4"]),
        Entry(eco: "D20", name: "Queen's Gambit Accepted", moves: ["d4","d5","c4","dxc4"]),
        Entry(eco: "D30", name: "Queen's Gambit Declined", moves: ["d4","d5","c4","e6"]),
        Entry(eco: "D10", name: "Slav Defense", moves: ["d4","d5","c4","c6"]),
        // Indian
        Entry(eco: "A45", name: "Indian Game", moves: ["d4","Nf6"]),
        Entry(eco: "E60", name: "King's Indian Defense", moves: ["d4","Nf6","c4","g6"]),
        Entry(eco: "E20", name: "Nimzo-Indian Defense", moves: ["d4","Nf6","c4","e6","Nc3","Bb4"]),
        Entry(eco: "E12", name: "Queen's Indian Defense", moves: ["d4","Nf6","c4","e6","Nf3","b6"]),
        Entry(eco: "A50", name: "Benoni / Indian systems", moves: ["d4","Nf6","c4","c5"]),
        Entry(eco: "A80", name: "Dutch Defense", moves: ["d4","f5"]),
        Entry(eco: "D70", name: "Grünfeld Defense", moves: ["d4","Nf6","c4","g6","Nc3","d5"]),
        // Flank
        Entry(eco: "A10", name: "English Opening", moves: ["c4"]),
        Entry(eco: "A04", name: "Réti Opening", moves: ["Nf3"]),
        Entry(eco: "A00", name: "Uncommon Opening", moves: [])
    ]

    /// Find the best-matching opening for the given SAN move list.
    static func match(sanMoves: [String]) -> Entry? {
        guard !sanMoves.isEmpty else { return nil }
        var best: Entry?
        var bestLen = 0
        for entry in entries where entry.moves.count <= sanMoves.count {
            if entry.moves.count > bestLen && Array(sanMoves.prefix(entry.moves.count)) == entry.moves {
                best = entry
                bestLen = entry.moves.count
            }
        }
        return best
    }
}
