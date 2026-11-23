package com.chesshelper.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

enum class MoveQuality(val label: String) {
    BRILLIANT("!! Brilliant"),
    BEST("!  Best"),
    GOOD("   Good"),
    INACCURACY("?! Inaccuracy"),
    MISTAKE("?  Mistake"),
    BLUNDER("?? Blunder"),
    NONE("")
}

enum class DetectionConfidence { HIGH, MEDIUM, LOW, UNKNOWN }

data class BoardPayload(
    val grid: String,                  // 64 chars, '.' = empty, KQRBNPkqrbnp
    val sideToMove: String,            // "w" | "b"
    val flipped: Boolean,
    val flippedConfident: Boolean,
    val lastMoveUci: String,
    val plyCount: Int,
    val detectionConfidence: String    // "high" | "medium" | "low" | "unknown"
)

/**
 * Port of EngineViewModel.swift: debounces board readings from the web view,
 * runs the engine, grades the last move (brilliant…blunder), and resolves the
 * opening name (offline book + optional Lichess explorer).
 *
 * Evaluation convention: everything is WHITE point-of-view except
 * [winChanceDisplay] helpers, which convert to the player's perspective like
 * the iOS overlay does.
 */
class EngineViewModel(context: Context) {

    interface Listener {
        /** Fired on the main thread whenever any engine output field changes. */
        fun onEngineStateChanged()
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chesshelper", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    var listener: Listener? = null

    // ---- Engine outputs (WHITE POV) ----
    var currentFEN: String = ""
        private set
    var topMoves: List<TopMove> = emptyList()
        private set
    var bestMoveSan: String = ""
        private set
    var bestMoveUci: String = ""
        private set
    var ponderSan: String = ""
        private set
    var evaluation: String = "—"          // white POV ("+0.34" / "M3")
        private set
    var winChance: Double = 50.0          // white POV 0..100
        private set
    var depth: Int = 0
        private set
    var pvText: String = ""
        private set
    var isThinking: Boolean = false
        private set
    var errorMessage: String = ""
        private set

    // ---- Detection state ----
    var detectedPlayerColor: String = "w"
    var manualPlayerColor: String? = null
    var sideToMove: String = "w"
        private set
    var lastMoveUci: String = ""
        private set
    var plyCount: Int = -1
        private set
    var confidence: DetectionConfidence = DetectionConfidence.UNKNOWN
        private set

    // ---- Move quality ----
    var lastMoveQuality: MoveQuality = MoveQuality.NONE
        private set
    var lastMoveCentipawnLoss: Int = 0
        private set
    var lastMoveWasMine: Boolean = false
        private set

    // ---- Opening ----
    var openingName: String = ""
        private set
    var openingEco: String = ""
        private set
    var openingMoves: List<LichessExplorer.PopularMove> = emptyList()
        private set

    // ---- Controls ----
    var enabled: Boolean = true

    /** SAN history maintained from the reader's UCI moves (for the local book). */
    private val sanHistory = ArrayList<String>()
    private var lastBoard: Board? = null

    private var previousEvalWhitePov: Double? = null
    private var lastAnalyzedFen: String = ""
    private var analysisSeq = 0
    private var openingSeq = 0
    private var debounceRunnable: Runnable? = null

    val effectivePlayerColor: String get() = manualPlayerColor ?: detectedPlayerColor
    val isMyTurn: Boolean get() = effectivePlayerColor == sideToMove

    /** Win chance from MY point of view (0..100). */
    fun myWinChance(): Double =
        if (effectivePlayerColor == "w") winChance else 100.0 - winChance

    /** Evaluation string from MY point of view ("+0.34" / "-M2"). */
    fun evalDisplay(): String {
        val e = evaluation
        if (e.startsWith("M") || e.startsWith("-M")) {
            val mine = if (effectivePlayerColor == "w") !e.startsWith("-") else e.startsWith("-")
            return if (mine) e.trimStart('-') else "-${e.trimStart('-')}"
        }
        val v = e.toDoubleOrNull() ?: return e
        val mine = if (effectivePlayerColor == "w") v else -v
        return String.format("%+.2f", mine)
    }

    fun setManualColor(color: String?) {
        manualPlayerColor = color
        if (currentFEN.isNotEmpty()) queueAnalyze(currentFEN, immediate = true)
        fire()
    }

    fun flipColor() {
        setManualColor(if (effectivePlayerColor == "w") "b" else "w")
    }

    // ------------------------------------------------------------------
    // Ingest (from the web view reader)
    // ------------------------------------------------------------------

    fun ingest(payload: BoardPayload) {
        if (!enabled) return

        sideToMove = payload.sideToMove
        lastMoveUci = payload.lastMoveUci
        plyCount = payload.plyCount
        confidence = when (payload.detectionConfidence.lowercase()) {
            "high" -> DetectionConfidence.HIGH
            "medium" -> DetectionConfidence.MEDIUM
            "low" -> DetectionConfidence.LOW
            else -> DetectionConfidence.UNKNOWN
        }
        if (payload.flippedConfident) {
            detectedPlayerColor = if (payload.flipped) "b" else "w"
        }

        val fenBoard = buildFenBoard(payload.grid)
        val castling = deriveCastlingRights(payload.grid)
        val ep = deriveEpTarget(payload.grid, payload.lastMoveUci)
        val fullmove = maxOf(1, (payload.plyCount / 2) + 1)
        val fen = "$fenBoard ${payload.sideToMove} $castling $ep 0 $fullmove"

        if (fen != currentFEN) {
            currentFEN = fen
            trackSanHistory(fen, payload)
            queueAnalyze(fen)
        } else {
            fire()
        }
    }

    /** Keep a SAN record of the game so the offline opening book can match. */
    private fun trackSanHistory(fen: String, payload: BoardPayload) {
        val board = try {
            Board.fromFEN(fen)
        } catch (e: Exception) {
            return
        }
        // Detect a fresh game.
        if (payload.plyCount in 0..1) {
            sanHistory.clear()
            lastBoard = null
        }
        val prev = lastBoard
        if (prev != null && payload.lastMoveUci.length >= 4) {
            // The last move was played by the side NOT to move on the new board.
            val moverWhite = board.whiteToMove == false
            val work = prev.copy()
            work.whiteToMove = moverWhite
            val mv = work.parseUCI(payload.lastMoveUci)
            if (mv != null) {
                sanHistory.add(work.toSAN(mv))
            }
        }
        lastBoard = board
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    private fun queueAnalyze(fen: String, immediate: Boolean = false) {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { runAnalysis(fen) }
        debounceRunnable = r
        if (immediate) r.run() else handler.postDelayed(r, 300)
    }

    private fun runAnalysis(fen: String) {
        if (fen == lastAnalyzedFen) return
        lastAnalyzedFen = fen

        analysisSeq += 1
        val seq = analysisSeq
        isThinking = true
        errorMessage = ""
        fire()

        val depthPref = prefs.getInt("engineDepth", 20)
        val movetimePref = prefs.getInt("engineMovetime", 2000)
        val multiPvPref = prefs.getInt("multiPV", 3)

        Thread({
            val analysis = EngineService.analyzeBlocking(
                fen = fen,
                multiPV = multiPvPref.coerceIn(1, 5),
                depth = depthPref.coerceIn(10, 25),
                movetimeMs = movetimePref.coerceIn(200, 5000)
            )
            handler.post {
                if (seq != analysisSeq) return@post   // stale
                isThinking = false
                if (analysis == null) {
                    errorMessage = "Engine: no analysis returned"
                    fire()
                    return@post
                }
                applyAnalysis(fen, analysis)
            }
        }, "engine-vm").apply { isDaemon = true; start() }

        // Opening lookup in parallel.
        lookupOpening(fen, sanHistory.toList())
    }

    private fun applyAnalysis(fen: String, analysis: EngineAnalysis) {
        topMoves = analysis.topMoves
        val best = analysis.bestMove
        if (best != null) {
            bestMoveUci = best.uci
            bestMoveSan = sanOf(fen, best.uci)
            ponderSan = sanOf(fen, analysis.ponder, opposite = true)
            evaluation = best.evalString
            winChance = best.winChance
        }
        depth = analysis.depth
        pvText = analysis.topMoves.firstOrNull()?.pv?.take(80) ?: ""
        errorMessage = ""

        val evalWhitePov = evalStringToWhitePov(evaluation)
        computeMoveQuality(evalWhitePov)
        previousEvalWhitePov = evalWhitePov
        fire()
    }

    /** Convert a UCI move to SAN using the position [fen]. */
    private fun sanOf(fen: String, uci: String, opposite: Boolean = false): String {
        if (uci.isEmpty()) return ""
        return try {
            val board = Board.fromFEN(fen)
            if (opposite) board.whiteToMove = !board.whiteToMove
            val mv = board.parseUCI(uci) ?: return uci
            val san = board.toSAN(mv)
            if (san.isEmpty()) uci else san
        } catch (e: Exception) {
            uci
        }
    }

    private fun evalStringToWhitePov(s: String): Double {
        if (s.startsWith("M")) return 30.0
        if (s.startsWith("-M")) return -30.0
        return s.toDoubleOrNull() ?: 0.0
    }

    private fun computeMoveQuality(currentEvalWhitePov: Double) {
        val prev = previousEvalWhitePov
        if (prev == null || lastMoveUci.isEmpty()) {
            lastMoveQuality = MoveQuality.NONE
            lastMoveCentipawnLoss = 0
            return
        }
        // Side that just moved: opposite of side to move.
        val sideThatJustMoved = if (sideToMove == "w") -1.0 else 1.0
        val lossPawns = (prev - currentEvalWhitePov) * sideThatJustMoved
        val lossCP = Math.round(lossPawns * 100.0).toInt()
        lastMoveCentipawnLoss = maxOf(0, lossCP)

        val mover = if (sideToMove == "w") "b" else "w"
        lastMoveWasMine = mover == effectivePlayerColor

        lastMoveQuality = when {
            lossCP < -30 -> MoveQuality.BRILLIANT
            lossCP <= 15 -> MoveQuality.BEST
            lossCP <= 49 -> MoveQuality.GOOD
            lossCP <= 99 -> MoveQuality.INACCURACY
            lossCP <= 299 -> MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
    }

    private fun lookupOpening(fen: String, history: List<String>) {
        openingSeq += 1
        val seq = openingSeq

        // Local book first (instant).
        val local = OpeningBook.match(history)
        handler.post {
            if (seq != openingSeq) return@post
            if (local != null) {
                openingName = local.name
                openingEco = local.eco
            }
            fire()
        }

        // Network explorer (best effort).
        Thread({
            val info = LichessExplorer.lookup(fen)
            handler.post {
                if (seq != openingSeq) return@post
                if (info != null) {
                    if (info.name.isNotEmpty()) {
                        openingName = info.name
                        openingEco = info.eco
                    }
                    openingMoves = info.topMoves
                } else {
                    openingMoves = emptyList()
                }
                fire()
            }
        }, "opening-lookup").apply { isDaemon = true; start() }
    }

    private fun fire() {
        handler.post { listener?.onEngineStateChanged() }
    }

    // ------------------------------------------------------------------
    // FEN helpers (port of the Swift private funcs) + one upgrade:
    // en-passant is derived from the last move when possible.
    // ------------------------------------------------------------------

    private fun buildFenBoard(grid64: String): String {
        val chars = grid64.toCharArray()
        if (chars.size != 64) return "8/8/8/8/8/8/8/8"
        val rows = ArrayList<String>(8)
        for (r in 0 until 8) {
            var row = ""
            var empty = 0
            for (c in 0 until 8) {
                val ch = chars[r * 8 + c]
                if (ch == '.') {
                    empty++
                } else {
                    if (empty > 0) { row += empty; empty = 0 }
                    row += ch
                }
            }
            if (empty > 0) row += empty
            rows.add(row)
        }
        return rows.joinToString("/")
    }

    private fun deriveCastlingRights(grid64: String): String {
        val chars = grid64.toCharArray()
        if (chars.size != 64) return "-"
        fun piece(file: Int, row: Int) = chars[row * 8 + file]
        var s = ""
        if (piece(4, 7) == 'K') {
            if (piece(7, 7) == 'R') s += "K"
            if (piece(0, 7) == 'R') s += "Q"
        }
        if (piece(4, 0) == 'k') {
            if (piece(7, 0) == 'r') s += "k"
            if (piece(0, 0) == 'r') s += "q"
        }
        return if (s.isEmpty()) "-" else s
    }

    /** Derive an en-passant square from a double pawn push (upgrade over iOS). */
    private fun deriveEpTarget(grid64: String, lastMoveUci: String): String {
        if (lastMoveUci.length < 4) return "-"
        val chars = grid64.toCharArray()
        if (chars.size != 64) return "-"
        val from = Board.squareIndex(lastMoveUci.substring(0, 2)) ?: return "-"
        val to = Board.squareIndex(lastMoveUci.substring(2, 4)) ?: return "-"
        val piece = chars[to]
        val delta = to - from
        if ((piece == 'P' && delta == 16) || (piece == 'p' && delta == -16)) {
            return Board.squareName((from + to) / 2)
        }
        return "-"
    }

    fun destroy() {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
    }
}
