import Foundation
import SwiftUI

enum MoveQuality: String {
    case brilliant  = "!! Brilliant"
    case best       = "!  Best"
    case good       = "   Good"
    case inaccuracy = "?! Inaccuracy"
    case mistake    = "?  Mistake"
    case blunder    = "?? Blunder"
    case none       = ""

    var color: Color {
        switch self {
        case .brilliant:  return .cyan
        case .best:       return .green
        case .good:       return .green.opacity(0.7)
        case .inaccuracy: return .yellow
        case .mistake:    return .orange
        case .blunder:    return .red
        case .none:       return .clear
        }
    }
}

enum DetectionConfidence: String { case high, medium, low, unknown }

@MainActor
final class EngineViewModel: ObservableObject {
    // Engine outputs (top-3 supported)
    @Published var currentFEN: String = ""
    @Published var topMoves: [TopMove] = []
    @Published var evaluation: String = "—"
    @Published var winChance: Double = 50
    @Published var depth: Int = 0
    @Published var isThinking: Bool = false
    @Published var errorMessage: String = ""

    // Detection state
    @Published var detectedPlayerColor: String = "w"
    @Published var manualPlayerColor: String? = nil
    @Published var sideToMove: String = "w"
    @Published var lastMoveUCI: String = ""
    @Published var plyCount: Int = -1
    @Published var confidence: DetectionConfidence = .unknown
    @Published var sanHistory: [String] = []

    // Move quality
    @Published var lastMoveQuality: MoveQuality = .none
    @Published var lastMoveCentipawnLoss: Int = 0
    @Published var lastMoveWasMine: Bool = false

    // Opening
    @Published var openingName: String = ""
    @Published var openingECO: String = ""

    // Controls
    @Published var enabled: Bool = true

    // Convenience
    var bestMove: TopMove? { topMoves.first }

    // Bookkeeping
    private var previousEvalWhitePOV: Double? = nil
    private var lastAnalyzedFEN: String = ""
    private var analysisTask: Task<Void, Never>?
    private var debounceTask: Task<Void, Never>?

    var effectivePlayerColor: String { manualPlayerColor ?? detectedPlayerColor }
    var isMyTurn: Bool { effectivePlayerColor == sideToMove }

    func setManualColor(_ color: String?) {
        manualPlayerColor = color
        if !currentFEN.isEmpty { queueAnalyze(fen: currentFEN, immediate: true) }
    }

    func flipColor() {
        setManualColor(effectivePlayerColor == "w" ? "b" : "w")
    }

    // MARK: - Board ingestion (from reader.js)
    struct BoardPayload {
        let grid: String
        let sideToMove: String
        let flipped: Bool
        let flippedConfident: Bool
        let lastMoveUCI: String
        let plyCount: Int
        let detectionConfidence: String
    }

    func ingest(_ payload: BoardPayload) {
        guard enabled else { return }

        sideToMove = payload.sideToMove
        lastMoveUCI = payload.lastMoveUCI
        plyCount = payload.plyCount
        confidence = DetectionConfidence(rawValue: payload.detectionConfidence) ?? .unknown

        if payload.flippedConfident {
            detectedPlayerColor = payload.flipped ? "b" : "w"
        }

        let fenBoard = buildFENBoard(from: payload.grid)
        let castling = deriveCastlingRights(from: payload.grid)
        let fullmove = max(1, (payload.plyCount / 2) + 1)
        let fen = "\(fenBoard) \(payload.sideToMove) \(castling) - 0 \(fullmove)"

        if fen != currentFEN {
            currentFEN = fen
            queueAnalyze(fen: fen)
        }
    }

    // MARK: - Debounce + analyze
    private func queueAnalyze(fen: String, immediate: Bool = false) {
        debounceTask?.cancel()
        debounceTask = Task { [weak self] in
            if !immediate {
                try? await Task.sleep(nanoseconds: 300_000_000)
            }
            if Task.isCancelled { return }
            await self?.runAnalysis(fen: fen)
        }
    }

    private func runAnalysis(fen: String) async {
        guard fen != lastAnalyzedFEN else { return }
        lastAnalyzedFEN = fen

        analysisTask?.cancel()
        isThinking = true
        errorMessage = ""

        let userDepth = UserDefaults.standard.integer(forKey: "engineDepth")
        let userMovetime = UserDefaults.standard.integer(forKey: "engineMovetime")
        let userMultiPV = UserDefaults.standard.integer(forKey: "multiPV")

        let depth = userDepth > 0 ? userDepth : 20
        let movetime = userMovetime > 0 ? userMovetime : 2000
        let multiPV = userMultiPV > 0 ? userMultiPV : 3

        analysisTask = Task { [weak self] in
            guard let self = self else { return }
            do {
                let analysis = try await StockfishEngine.shared.analyze(
                    fen: fen, multiPV: multiPV, depth: depth, movetime: movetime
                )
                if Task.isCancelled { return }
                await MainActor.run {
                    self.topMoves = analysis.topMoves
                    self.evaluation = analysis.bestMove?.evalString ?? "—"
                    self.winChance = analysis.bestMove?.winChance ?? 50
                    self.depth = analysis.depth
                    self.isThinking = false

                    let whitePOV = self.evalToWhitePOV(analysis.bestMove)
                    self.computeMoveQuality(currentEvalWhitePOV: whitePOV)
                    self.previousEvalWhitePOV = whitePOV

                    self.updateOpening()
                }
            } catch {
                if Task.isCancelled { return }
                await MainActor.run {
                    self.errorMessage = "Engine: \(error.localizedDescription)"
                    self.isThinking = false
                }
            }
        }
    }

    private func evalToWhitePOV(_ move: TopMove?) -> Double {
        guard let move = move else { return 0 }
        if let m = move.mateIn, m != 0 { return m > 0 ? 30 : -30 }
        let cp = Double(move.evaluationCp ?? 0) / 100.0
        // Engine returns score from side-to-move POV; convert to white POV
        return sideToMove == "w" ? cp : -cp
    }

    private func computeMoveQuality(currentEvalWhitePOV cur: Double) {
        guard let prev = previousEvalWhitePOV, !lastMoveUCI.isEmpty else {
            lastMoveQuality = .none; lastMoveCentipawnLoss = 0; return
        }
        let sideThatJustMoved: Double = (sideToMove == "w") ? -1 : 1
        let lossPawns = (prev - cur) * sideThatJustMoved
        let lossCP = Int((lossPawns * 100).rounded())
        lastMoveCentipawnLoss = max(0, lossCP)

        let mover = (sideToMove == "w") ? "b" : "w"
        lastMoveWasMine = (mover == effectivePlayerColor)

        switch lossCP {
        case ..<(-30):    lastMoveQuality = .brilliant
        case (-30)...15:  lastMoveQuality = .best
        case 16...49:     lastMoveQuality = .good
        case 50...99:     lastMoveQuality = .inaccuracy
        case 100...299:   lastMoveQuality = .mistake
        default:          lastMoveQuality = .blunder
        }
    }

    private func updateOpening() {
        if let info = ChessAPI.opening(sanMoves: sanHistory) {
            openingName = info.name
            openingECO = info.eco
        } else {
            openingName = ""
            openingECO = ""
        }
    }

    // MARK: - FEN helpers
    private func buildFENBoard(from grid64: String) -> String {
        let chars = Array(grid64)
        guard chars.count == 64 else { return "8/8/8/8/8/8/8/8" }
        var rows: [String] = []
        for r in 0..<8 {
            var row = ""; var empty = 0
            for c in 0..<8 {
                let ch = chars[r * 8 + c]
                if ch == "." { empty += 1 }
                else { if empty > 0 { row += "\(empty)"; empty = 0 }; row.append(ch) }
            }
            if empty > 0 { row += "\(empty)" }
            rows.append(row)
        }
        return rows.joined(separator: "/")
    }

    private func deriveCastlingRights(from grid64: String) -> String {
        let chars = Array(grid64)
        guard chars.count == 64 else { return "-" }
        func piece(_ file: Int, rankFromTop row: Int) -> Character { chars[row * 8 + file] }
        var s = ""
        let whiteKingHome = piece(4, rankFromTop: 7) == "K"
        if whiteKingHome && piece(7, rankFromTop: 7) == "R" { s += "K" }
        if whiteKingHome && piece(0, rankFromTop: 7) == "R" { s += "Q" }
        let blackKingHome = piece(4, rankFromTop: 0) == "k"
        if blackKingHome && piece(7, rankFromTop: 0) == "r" { s += "k" }
        if blackKingHome && piece(0, rankFromTop: 0) == "r" { s += "q" }
        return s.isEmpty ? "-" : s
    }
}
