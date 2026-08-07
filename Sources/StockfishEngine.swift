import Foundation
import ChessKitEngine

/// A move returned by Stockfish, matching the shape used across the app.
struct TopMove: Identifiable, Equatable {
    let id = UUID()
    let rank: Int              // 1 = best, 2 = 2nd best, 3 = 3rd best
    let uci: String            // "e2e4"
    let san: String            // "e4" (may be UCI if we can't convert)
    let evaluationCp: Int?     // centipawns from white POV
    let mateIn: Int?           // mate distance if applicable
    let depth: Int

    var evalString: String {
        if let m = mateIn, m != 0 { return "M\(abs(m))" }
        if let cp = evaluationCp { return String(format: "%+.2f", Double(cp) / 100.0) }
        return "—"
    }

    var winChance: Double {
        // Sigmoid on centipawns (side-to-move POV)
        if let m = mateIn, m != 0 { return m > 0 ? 99 : 1 }
        guard let cp = evaluationCp else { return 50 }
        let x = Double(cp) / 100.0
        return 50 + 50 * tanh(x / 4.0)
    }
}

struct EngineAnalysis {
    let topMoves: [TopMove]
    let bestMove: TopMove?
    let depth: Int
}

/// Local Stockfish engine using ChessKitEngine (bundled Stockfish 16 binary).
/// Runs entirely on-device. Zero network requests.
@MainActor
final class StockfishEngine {

    static let shared = StockfishEngine()

    private let engine: Engine
    private var isReady = false
    private var currentTask: Task<EngineAnalysis, Error>?

    private init() {
        self.engine = Engine(type: .stockfish)
    }

    /// Start the engine (call once at app launch).
    func start() async {
        guard !isReady else { return }
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            engine.start { [weak self] in
                self?.isReady = true
                cont.resume()
            }
        }
        // Set search options for good performance on iPhone 11 Pro Max (A13)
        engine.send(command: .setoption(id: "Threads", value: "3"))
        engine.send(command: .setoption(id: "Hash", value: "64"))
        engine.send(command: .setoption(id: "MultiPV", value: "3"))
    }

    /// Analyze a position and return top-N candidate moves.
    /// - Parameters:
    ///   - fen: Position in FEN notation
    ///   - multiPV: How many candidate moves to return (1...5)
    ///   - depth: Search depth (higher = stronger but slower). Full strength = 20+.
    ///   - movetime: Max time in ms (safety cap)
    func analyze(fen: String,
                 multiPV: Int = 3,
                 depth: Int = 20,
                 movetime: Int = 2000) async throws -> EngineAnalysis {

        if !isReady { await start() }

        // Cancel any in-flight analysis
        currentTask?.cancel()

        let task = Task<EngineAnalysis, Error> { [engine] in
            return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<EngineAnalysis, Error>) in

                var pvBuckets: [Int: (uci: String, san: String, cp: Int?, mate: Int?, depth: Int)] = [:]
                var currentDepth = 0
                var didResume = false

                // Listen to engine responses
                engine.receiveResponse = { response in
                    switch response {
                    case .info(let info):
                        let pv = info.multipv ?? 1
                        currentDepth = max(currentDepth, info.depth ?? 0)
                        let firstMove = info.pv?.first ?? ""
                        let cp = info.score?.cp
                        let mate = info.score?.mate
                        if !firstMove.isEmpty {
                            pvBuckets[pv] = (uci: firstMove,
                                             san: firstMove,   // ChessKitEngine returns UCI; SAN conversion done later
                                             cp: cp,
                                             mate: mate,
                                             depth: info.depth ?? currentDepth)
                        }

                    case .bestmove:
                        guard !didResume else { return }
                        didResume = true
                        let sorted = pvBuckets.sorted { $0.key < $1.key }
                        let moves: [TopMove] = sorted.enumerated().map { (idx, kv) in
                            TopMove(rank: idx + 1,
                                    uci: kv.value.uci,
                                    san: kv.value.san,
                                    evaluationCp: kv.value.cp,
                                    mateIn: kv.value.mate,
                                    depth: kv.value.depth)
                        }
                        let analysis = EngineAnalysis(topMoves: moves,
                                                      bestMove: moves.first,
                                                      depth: currentDepth)
                        cont.resume(returning: analysis)

                    default:
                        break
                    }
                }

                // Send position + go
                engine.send(command: .position(.fen(fen)))
                engine.send(command: .go(depth: depth, movetime: movetime))
            }
        }

        currentTask = task
        return try await task.value
    }

    func stop() {
        engine.send(command: .stop)
    }
}
