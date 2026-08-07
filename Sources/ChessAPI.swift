import Foundation

/// All external network calls have been REMOVED.
/// - Engine: uses local StockfishEngine (Stockfish 16 bundled)
/// - Openings: uses LocalOpeningBook (offline)
///
/// This file exists only as a thin shim so old callers still compile.
enum ChessAPI {

    struct EngineResultCompat {
        let move: String
        let san: String
        let ponder: String
        let evalString: String
        let winChance: Double
        let depth: Int
        let pvText: String
    }

    /// Compatibility shim for old code that expected a single best move.
    /// Runs the local engine and returns the #1 move formatted the old way.
    static func bestMove(fen: String, depth: Int = 20) async throws -> EngineResultCompat {
        let analysis = try await StockfishEngine.shared.analyze(
            fen: fen,
            multiPV: 1,
            depth: depth,
            movetime: 2000
        )
        guard let best = analysis.bestMove else {
            throw NSError(domain: "ChessAPI", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "Engine returned no move"])
        }
        return EngineResultCompat(
            move: best.uci,
            san: best.san,
            ponder: "",
            evalString: best.evalString,
            winChance: best.winChance,
            depth: analysis.depth,
            pvText: ""
        )
    }

    /// Opening lookup using the local offline book. Never touches the network.
    struct OpeningInfoCompat {
        let name: String
        let eco: String
        let topMoves: [OpeningMove]
    }

    static func opening(sanMoves: [String]) -> OpeningInfoCompat? {
        guard let entry = LocalOpeningBook.match(sanMoves: sanMoves) else { return nil }
        return OpeningInfoCompat(name: entry.name, eco: entry.eco, topMoves: [])
    }

    /// Legacy signature (FEN-based). We can't match by FEN offline reliably,
    /// so we return nil and rely on move-list matching in the ViewModel.
    static func opening(fen: String) async -> OpeningInfoCompat? {
        return nil
    }
}

/// Kept for existing UI references
struct OpeningMove: Identifiable {
    let id = UUID()
    let san: String
    let white: Int
    let draws: Int
    let black: Int
    var totalGames: Int { white + draws + black }
    var winRate: Double {
        totalGames == 0 ? 0 : Double(white) / Double(totalGames) * 100
    }
}
