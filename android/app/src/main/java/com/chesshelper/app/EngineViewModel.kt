package com.chesshelper.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

enum class MoveQuality { BRILLIANT, BEST, GOOD, INACCURACY, MISTAKE, BLUNDER, NONE }

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

interface EngineListener {
    fun onTopMoves(moves: List<TopMove>, eval: String, winChance: Double, depth: Int)
    fun onError(msg: String)
    fun onThinking(thinking: Boolean)
    fun onOpening(name: String, eco: String)
    fun onMoveQuality(quality: MoveQuality, cpLoss: Int, wasMine: Boolean, uci: String)
}

/**
 * Port of EngineViewModel.swift: debounces board ingestions, asks Stockfish,
 * computes move quality + opening, and fans results out to the UI on the main thread.
 */
class EngineViewModel(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chesshelper", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()

    var listener: EngineListener? = null

    // Engine outputs
    var currentFEN: String = ""
    var topMoves: List<TopMove> = emptyList()
    var evaluation: String = "—"
    var winChance: Double = 50.0
    var depth: Int = 0
    var isThinking: Boolean = false
    var errorMessage: String = ""

    // Detection state
    var detectedPlayerColor: String = "w"
    var manualPlayerColor: String? = null
    var sideToMove: String = "w"
    var lastMoveUci: String = ""
    var plyCount: Int = -1
    var confidence: DetectionConfidence = DetectionConfidence.UNKNOWN
    val sanHistory: MutableList<String> = mutableListOf()

    // Move quality
    var lastMoveQuality: MoveQuality = MoveQuality.NONE
    var lastMoveCentipawnLoss: Int = 0
    var lastMoveWasMine: Boolean = false

    // Opening
    var openingName: String = ""
    var openingECO: String = ""

    // Controls
    var enabled: Boolean = true

    val bestMove: TopMove? get() = topMoves.firstOrNull()
    val effectivePlayerColor: String get() = manualPlayerColor ?: detectedPlayerColor
    val isMyTurn: Boolean get() = effectivePlayerColor == sideToMove

    private var lastAnalyzedFEN: String = ""
    private var previousEvalWhitePOV: Double? = null
    private var debounceRunnable: Runnable? = null
    private var analysisRunnable: Runnable? = null
    private var analysisCancelled = false

    fun setManualColor(color: String?) {
        manualPlayerColor = color
        if (!currentFEN.isEmpty()) queueAnalyze(currentFEN, immediate = true)
    }

    fun flipColor() {
        setManualColor(if (effectivePlayerColor == "w") "b" else "w")
    }

    /** Called by the web view when it reads a board from chess.com (port of ingest()). */
    fun ingest(payload: BoardPayload) {
        if (!enabled) return

        sideToMove = payload.sideToMove
        lastMoveUci = payload.lastMoveUci
        plyCount = payload.plyCount
        confidence = parseConfidence(payload.detectionConfidence)

        if (payload.flippedConfident) {
            detectedPlayerColor = if (payload.flipped) "b" else "w"
        }

        val fenBoard = buildFENBoard(payload.grid)
        val castling = deriveCastlingRights(payload.grid)
        val fullmove = maxOf(1, (payload.plyCount / 2) + 1)
        val fen = "$fenBoard ${payload.sideToMove} $castling - 0 $fullmove"

        if (fen != currentFEN) {
            currentFEN = fen
            queueAnalyze(fen)
        }
    }

    private fun queueAnalyze(fen: String, immediate: Boolean = false) {
        handler.removeCallbacks(debounceRunnable)
        debounceRunnable = Runnable {
            runAnalysis(fen)
        }
        if (immediate) {
            debounceRunnable?.run()
        } else {
            debounceRunnable?.let { handler.postDelayed(it, 300) }
        }
    }

    private fun runAnalysis(fen: String) {
        if (fen == lastAnalyzedFEN) return
        lastAnalyzedFEN = fen

        analysisCancelled = false
        analysisRunnable = Runnable {
            try {
                val userDepth = prefs.getInt("engineDepth", 20)
                val userMovetime = prefs.getInt("engineMovetime", 2000)
                val userMultiPV = prefs.getInt("multiPV", 3)
                if (analysisCancelled) return@execute
                val d = if (userDepth > 0) userDepth else 20
                val mt = if (userMovetime > 0) userMovetime else 2000
                val mpv = if (userMultiPV > 0) userMultiPV else 3

                StockfishEngine.analyze(
                    fen = fen, multiPV = mpv, depth = d, movetimeMs = mt,
                    onResult = { analysis ->
                        handler.post {
                            if (analysis.topMoves.isEmpty()) return@post
                            topMoves = analysis.topMoves
                            evaluation = analysis.bestMove?.evalString ?: "—"
                            winChance = analysis.bestMove?.winChance ?: 50.0
                            depth = analysis.depth
                            isThinking = false
                            errorMessage = ""

                            val whitePOV = evalToWhitePOV(analysis.bestMove)
                            computeMoveQuality(currentEvalWhitePOV = whitePOV)
                            previousEvalWhitePOV = whitePOV
                            updateOpening()

                            listener?.onTopMoves(topMoves, evaluation, winChance, depth)
                        }
                    },
                    onError = { e ->
                        handler.post {
                            errorMessage = "Engine: ${e.message}"
                            isThinking = false
                            listener?.onError(errorMessage)
                        }
                    }
                )
            } catch (e: Exception) {
                handler.post {
                    errorMessage = "Engine: ${e.message}"
                    isThinking = false
                    listener?.onError(errorMessage)
                }
            }
        }
        isThinking = true
        errorMessage = ""
        handler.post { listener?.onThinking(true) }
        analysisRunnable?.let { bg.execute(it) }
    }

    private fun evalToWhitePOV(move: TopMove?): Double {
        if (move == null) return 0.0
        val mate = move.mateIn
        if (mate != null && mate != 0) return if (mate > 0) 30.0 else -30.0
        val cp = (move.evaluationCp ?: 0) / 100.0
        return if (sideToMove == "w") cp else -cp
    }

    private fun computeMoveQuality(currentEvalWhitePOV: Double) {
        val prev = previousEvalWhitePOV
        if (prev == null || lastMoveUci.isEmpty()) {
            lastMoveQuality = MoveQuality.NONE
            lastMoveCentipawnLoss = 0
            return
        }
        val sideMultiplier = if (sideToMove == "w") -1.0 else 1.0
        val lossPawns = (prev - currentEvalWhitePOV) * sideMultiplier
        val lossCP = (lossPawns * 100).roundToInt()
        lastMoveCentipawnLoss = maxOf(0, lossCP)
        val mover = if (sideToMove == "w") "b" else "w"
        lastMoveWasMine = (mover == effectivePlayerColor)

        lastMoveQuality = when {
            lossCP < -30 -> MoveQuality.BRILLIANT
            lossCP in 0..15 -> MoveQuality.BEST
            lossCP in 16..49 -> MoveQuality.GOOD
            lossCP in 50..99 -> MoveQuality.INACCURACY
            lossCP in 100..299 -> MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
        listener?.onMoveQuality(lastMoveQuality, lastMoveCentipawnLoss, lastMoveWasMine, lastMoveUci)
    }

    private fun updateOpening() {
        val info = OpeningBook.match(sanHistory)
        if (info != null) {
            openingName = info.name
            openingECO = info.eco
        } else {
            openingName = ""
            openingECO = ""
        }
    }

    // ---- FEN helpers (port of the Swift private funcs) ----

    private fun buildFENBoard(grid64: String): String {
        val chars = grid64.toCharArray()
        if (chars.size != 64) return "8/8/8/8/8/8/8/8"
        val rows = mutableListOf<String>()
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

    private fun parseConfidence(s: String): DetectionConfidence = when (s.lowercase()) {
        "high" -> DetectionConfidence.HIGH
        "medium" -> DetectionConfidence.MEDIUM
        "low" -> DetectionConfidence.LOW
        else -> DetectionConfidence.UNKNOWN
    }

    fun destroy() {
        handler.removeCallbacks(debounceRunnable)
        StockfishEngine.destroy()
    }
}
