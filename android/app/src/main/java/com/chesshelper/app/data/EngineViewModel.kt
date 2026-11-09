package com.chesshelper.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port of Sources/EngineViewModel.swift
 *
 * Responsibilities:
 *  - ingest(BoardPayload) from ChessWebView JS bridge → build FEN → debounce → Stockfish analyze
 *  - expose topMoves, evaluation, winChance, depth, isThinking
 *  - compute lastMoveQuality (brilliant/best/etc) vs previous eval
 *  - offline opening lookup via LocalOpeningBook
 *  - effectivePlayerColor handling (auto vs manual override)
 */
class EngineViewModel : ViewModel() {

    // Engine outputs (Player POV — same as iOS)
    var currentFEN by mutableStateOf("")
        private set
    var topMoves by mutableStateOf<List<TopMove>>(emptyList())
        private set
    var evaluation by mutableStateOf("—")
        private set
    var winChance by mutableStateOf(50.0)
        private set
    var depth by mutableStateOf(0)
        private set
    var isThinking by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set

    // Detection state — mirrors @Published vars in Swift
    var detectedPlayerColor by mutableStateOf("w")
        private set
    var manualPlayerColor by mutableStateOf<String?>(null)
        private set
    var sideToMove by mutableStateOf("w")
        private set
    var lastMoveUCI by mutableStateOf("")
        private set
    var plyCount by mutableStateOf(-1)
        private set
    var confidence by mutableStateOf(DetectionConfidence.unknown)
        private set
    var sanHistory by mutableStateOf<List<String>>(emptyList())
        private set

    // Move quality
    var lastMoveQuality by mutableStateOf(MoveQuality.none)
        private set
    var lastMoveCentipawnLoss by mutableStateOf(0)
        private set
    var lastMoveWasMine by mutableStateOf(false)
        private set

    // Opening
    var openingName by mutableStateOf("")
        private set
    var openingECO by mutableStateOf("")
        private set

    var enabled by mutableStateOf(true)

    val bestMove: TopMove? get() = topMoves.firstOrNull()
    val effectivePlayerColor: String get() = manualPlayerColor ?: detectedPlayerColor
    val isMyTurn: Boolean get() = effectivePlayerColor == sideToMove

    // --- derived for overlay win bar: convert top eval to “my” win chance
    // If it's opponent's turn, the bar shows opponent's chance, but iOS shows from my POV?
    // We keep side-to-move semantics and let overlay decide. For now expose myWinChance.
    val myWinChance: Double get() {
        val bm = bestMove ?: return 50.0
        val cp = bm.evaluationCp ?: return bm.winChance
        val whitePov = if (sideToMove == "w") cp else -cp
        val myPov = if (effectivePlayerColor == "w") whitePov else -whitePov
        // sigmoid already in TopMove.winChance is side-to-move; recompute for my POV
        if (bm.mateIn != null && bm.mateIn != 0) {
            val myMate = if (effectivePlayerColor == sideToMove) bm.mateIn else -bm.mateIn!!
            return if (myMate > 0) 99.0 else 1.0
        }
        val x = myPov / 100.0
        return 50 + 50 * kotlin.math.tanh(x / 4.0)
    }

    private var previousEvalWhitePOV: Double? = null
    private var lastAnalyzedFEN: String = ""
    private var analysisJob: Job? = null
    private var debounceJob: Job? = null

    fun setManualColor(color: String?) {
        manualPlayerColor = color
        if (currentFEN.isNotEmpty()) queueAnalyze(currentFEN, immediate = true)
    }

    fun flipColor() {
        setManualColor(if (effectivePlayerColor == "w") "b" else "w")
    }

    fun ingest(payload: BoardPayload) {
        if (!enabled) return

        sideToMove = payload.sideToMove
        lastMoveUCI = payload.lastMoveUCI
        plyCount = payload.plyCount
        confidence = DetectionConfidence.from(payload.detectionConfidence)

        if (payload.flippedConfident) {
            detectedPlayerColor = if (payload.flipped) "b" else "w"
        }

        val grid = payload.effectiveGrid
        if (grid.length != 64) {
            // ignore malformed
            return
        }
        val fenBoard = buildFenBoard(grid)
        val castling = deriveCastlingRights(grid)
        val fullmove = maxOf(1, (payload.plyCount / 2) + 1)
        val fen = "$fenBoard ${payload.sideToMove} $castling - 0 $fullmove"

        if (fen != currentFEN) {
            currentFEN = fen
            queueAnalyze(fen)
        }
    }

    private fun queueAnalyze(fen: String, immediate: Boolean = false) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            if (!immediate) delay(300)
            runAnalysis(fen)
        }
    }

    private suspend fun runAnalysis(fen: String) {
        if (fen == lastAnalyzedFEN) return
        lastAnalyzedFEN = fen

        analysisJob?.cancel()
        isThinking = true
        errorMessage = ""

        // Read prefs — fallback to defaults matching iOS UserDefaults
        // For now use hardcoded defaults; DataStore integration done in SettingsView.
        val depthPref = 20
        val movetimePref = 2000
        val multiPVPref = 3

        analysisJob = viewModelScope.launch {
            try {
                val analysis = StockfishEngine.analyze(
                    fen = fen,
                    multiPV = multiPVPref,
                    depth = depthPref,
                    movetime = movetimePref
                )
                // For now keep UCI as SAN (reliable). Future: convert via chesslib if needed.
                val withSan = analysis.topMoves.map { it.copy(san = it.uci) }

                topMoves = withSan
                evaluation = analysis.bestMove?.evalString ?: "—"
                winChance = analysis.bestMove?.winChance ?: 50.0
                depth = analysis.depth
                isThinking = false

                val whitePov = evalToWhitePOV(analysis.bestMove)
                computeMoveQuality(whitePov)
                previousEvalWhitePOV = whitePov
                updateOpening()
            } catch (e: Exception) {
                errorMessage = "Engine: ${e.message}"
                isThinking = false
            }
        }
    }

    private fun evalToWhitePOV(move: TopMove?): Double {
        if (move == null) return 0.0
        move.mateIn?.let { if (it != 0) return if (it > 0) 30.0 else -30.0 }
        val cp = (move.evaluationCp ?: 0) / 100.0
        return if (sideToMove == "w") cp else -cp
    }

    private fun computeMoveQuality(currentEvalWhitePOV: Double) {
        val prev = previousEvalWhitePOV ?: run {
            lastMoveQuality = MoveQuality.none
            return
        }
        if (lastMoveUCI.isEmpty()) {
            lastMoveQuality = MoveQuality.none
            return
        }
        // Who just moved? Opposite of sideToMove
        val mover = if (sideToMove == "w") "b" else "w"
        lastMoveWasMine = mover == effectivePlayerColor

        // Centipawn loss from mover's POV
        val moverPrev = if (mover == "w") prev else -prev
        val moverCur  = if (mover == "w") currentEvalWhitePOV else -currentEvalWhitePOV
        val loss = ((moverPrev - moverCur) * 100).toInt().coerceAtLeast(0)
        lastMoveCentipawnLoss = loss
        lastMoveQuality = MoveQuality.fromLoss(loss)
    }

    private fun updateOpening() {
        // Derive SAN history from plyCount & currentFEN? We approximate via sanHistory already pushed from PGN.
        // For now use empty; real history comes from PGN rebuilding if we parse move stack.
        // Fallback: try to match using plyCount-agnostic empty — so we rely on external sanHistory updates.
        val match = LocalOpeningBook.match(sanHistory)
        openingName = match?.name ?: ""
        openingECO = match?.eco ?: ""
    }

    fun pushSanHistory(sanMoves: List<String>) {
        sanHistory = sanMoves
        updateOpening()
    }

    // ---- FEN helpers (ports of EngineViewModel.swift buildFENBoard / deriveCastlingRights) ----

    fun buildFenBoard(grid: String): String {
        // grid is 64 chars row-major a8..h1, '.' empty, 'KQRBNP' white etc.
        val rows = mutableListOf<String>()
        for (r in 0 until 8) {
            val row = grid.substring(r*8, r*8+8)
            var empty = 0
            val sb = StringBuilder()
            for (ch in row) {
                if (ch == '.') empty++
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(ch)
                }
            }
            if (empty > 0) sb.append(empty)
            rows.add(sb.toString())
        }
        return rows.joinToString("/")
    }

    fun deriveCastlingRights(grid: String): String {
        // Very simple heuristic ports iOS: if original king/rook still on start squares, assume rights.
        // This avoids illegal FEN while keeping engine happy even if rights are approximate.
        // Start position grid for reference
        // rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR → row-major "rnbqkbnrpppppppp................................PPPPPPPPRNBQKBNR"
        var rights = ""
        // White: King e1 (index 60), rook a1 (56), h1 (63)
        // Black: King e8 (4), rook a8 (0), h8 (7)
        fun charAt(idx: Int): Char = grid.getOrNull(idx) ?: '.'
        if (charAt(60) == 'K') {
            if (charAt(63) == 'R') rights += "K"
            if (charAt(56) == 'R') rights += "Q"
        }
        if (charAt(4) == 'k') {
            if (charAt(7) == 'r') rights += "k"
            if (charAt(0) == 'r') rights += "q"
        }
        return rights.ifEmpty { "-" }
    }

    override fun onCleared() {
        super.onCleared()
        StockfishEngine.stop()
    }
}
