package com.chesshelper.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Port of ReviewView.swift — "Blunder Review".
 *
 * Enter a chess.com username → fetch the latest month's games → pick one →
 * every move is checked with the local engine and any move that lost ≥50
 * centipawns is listed with its severity and the best alternative.
 *
 * Bug fix vs the iOS version: the loss sign is now attributed to the side that
 * actually moved (the Swift code had the multiplier inverted, so it graded the
 * wrong player's mistakes).
 */
class ReviewTab(private val ctx: Context) {

    val view: View
    private val usernameInput = EditText(ctx)
    private val loadBtn = Button(ctx)
    private val statusText = Ui.text(ctx, "", 11f, Ui.TEXT_DIM)
    private val gamesCol = LinearLayout(ctx)
    private val progressWrap = LinearLayout(ctx)
    private val progressBar = ProgressBar(ctx)
    private val progressLabel = Ui.text(ctx, "0%", 10f, Ui.TEXT_DIM, mono = true)
    private val resultsCol = LinearLayout(ctx)
    private val newBtn = Button(ctx)

    private var games: List<ChessComApi.GameSummary> = emptyList()
    private var busy = false

    private data class MoveReview(
        val ply: Int,
        val san: String,
        val bestMoveSan: String,
        val lossCentipawns: Int,
        val accuracy: Double = 100.0,
        val brilliant: Boolean = false
    ) {
        val severity: String
            get() = when {
                brilliant -> "!! Brilliant"
                lossCentipawns >= 300 -> "?? Blunder"
                lossCentipawns >= 100 -> "? Mistake"
                lossCentipawns >= 50 -> "?! Inaccuracy"
                else -> "OK"
            }

        val color: Int
            get() = when {
                brilliant -> Ui.CYAN
                lossCentipawns >= 300 -> Ui.RED
                lossCentipawns >= 100 -> Ui.ORANGE
                lossCentipawns >= 50 -> Ui.YELLOW
                else -> Ui.GREEN
            }
    }

    init {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.SURFACE)
        val pad = ctx.dp(16)
        root.setPadding(pad, pad, pad, pad)

        root.addView(Ui.sectionTitle(ctx, "Blunder Review"))

        root.addView(Ui.text(ctx, "Your chess.com username", 11f, Ui.TEXT_DIM))
        usernameInput.hint = "e.g. hikaru"
        usernameInput.setTextColor(Ui.TEXT)
        usernameInput.setHintTextColor(0x66FFFFFF)
        usernameInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        usernameInput.setSingleLine(true)
        root.addView(usernameInput)

        loadBtn.text = "Load recent games"
        loadBtn.setTextColor(Ui.TEXT)
        loadBtn.setOnClickListener { loadGames() }
        root.addView(loadBtn)

        root.addView(statusText)
        root.addView(Ui.vSpace(ctx, 8))

        gamesCol.orientation = LinearLayout.VERTICAL
        root.addView(gamesCol)

        progressWrap.orientation = LinearLayout.HORIZONTAL
        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressWrap.addView(
            progressBar,
            LinearLayout.LayoutParams(0, ctx.dp(16), 1f)
        )
        val plp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        plp.marginStart = ctx.dp(8)
        progressLabel.layoutParams = plp
        progressWrap.addView(progressLabel)
        progressWrap.visibility = View.GONE
        root.addView(progressWrap)

        resultsCol.orientation = LinearLayout.VERTICAL
        root.addView(resultsCol)

        newBtn.text = "↺ Review another game"
        newBtn.setTextColor(Ui.GREEN)
        newBtn.visibility = View.GONE
        newBtn.setOnClickListener {
            resultsCol.removeAllViews()
            newBtn.visibility = View.GONE
            gamesCol.visibility = View.VISIBLE
        }
        root.addView(newBtn)

        val scroll = ScrollView(ctx)
        scroll.addView(root)
        view = scroll
    }

    // ------------------------------------------------------------------

    private fun loadGames() {
        if (busy) return
        val user = usernameInput.text.toString().trim()
        if (user.isEmpty()) {
            statusText.text = "Type a username first."
            return
        }
        busy = true
        statusText.text = "Fetching archives…"
        gamesCol.removeAllViews()
        resultsCol.removeAllViews()
        loadBtn.isEnabled = false

        Thread({
            val result = try {
                val list = ChessComApi.recentGames(user, 20)
                Pair(list, "Loaded ${list.size} recent games")
            } catch (e: Exception) {
                Pair(emptyList(), "Error: ${e.message}")
            }
            (ctx as? android.app.Activity)?.runOnUiThread {
                busy = false
                loadBtn.isEnabled = true
                games = result.first
                statusText.text = result.second
                renderGames()
            }
        }, "review-load").apply { isDaemon = true; start() }
    }

    private fun renderGames() {
        gamesCol.removeAllViews()
        if (games.isEmpty()) return
        gamesCol.addView(Ui.sectionTitle(ctx, "Pick a game to review"))
        for (g in games) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.VERTICAL
            row.background = Ui.roundedBg(Ui.SURFACE_ALT, 10f)
            row.setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
            val title = Ui.text(ctx, g.title(), 12f, Ui.TEXT, bold = true)
            val sub = Ui.text(ctx, formatDate(g.endTimeMillis), 10f, Ui.TEXT_DIM)
            row.addView(title)
            row.addView(sub)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = ctx.dp(8)
            row.layoutParams = lp
            row.setOnClickListener { startReview(g) }
            gamesCol.addView(row)
        }
    }

    private fun startReview(game: ChessComApi.GameSummary) {
        if (busy) return
        busy = true
        gamesCol.visibility = View.GONE
        resultsCol.removeAllViews()
        progressWrap.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Reviewing ${game.title()}…"

        Thread({
            val states = PgnReader.replay(game.pgn)
            if (states.size <= 1) {
                (ctx as? android.app.Activity)?.runOnUiThread {
                    busy = false
                    progressWrap.visibility = View.GONE
                    statusText.text = "Couldn't parse PGN."
                    gamesCol.visibility = View.VISIBLE
                }
                return@Thread
            }

            val results = ArrayList<MoveReview>()
            val whiteAcc = ArrayList<Double>()
            val blackAcc = ArrayList<Double>()
            var brilliantCount = 0
            for (i in 0 until states.size - 1) {
                val before = states[i]
                val after = states[i + 1]

                val a1 = EngineService.analyzeBlocking(before.fen, multiPV = 3, depth = 12, movetimeMs = 400)
                val a2 = EngineService.analyzeBlocking(after.fen, multiPV = 1, depth = 12, movetimeMs = 400)
                if (a1 != null && a2 != null) {
                    // All evals are WHITE POV, so flip for the player who moved.
                    val moverSign = if (i % 2 == 0) 1.0 else -1.0
                    val beforeMover = parseEval(a1.bestMove?.evalString ?: "0") * moverSign
                    val afterMover = parseEval(a2.bestMove?.evalString ?: "0") * moverSign
                    val loss = Math.round((beforeMover - afterMover) * 100.0).toInt()

                    // Published Lichess-style accuracy curve (chess.com's CAPS2
                    // is private, so this is an honest approximation).
                    val drop = winPercent(beforeMover * 100.0) - winPercent(afterMover * 100.0)
                    val acc = moveAccuracy(drop)
                    (if (i % 2 == 0) whiteAcc else blackAcc).add(acc)

                    // "Brilliant" heuristic: the engine had one clearly superior
                    // move and the player found it without losing evaluation.
                    val bestCp = parseEval(a1.topMoves.firstOrNull()?.evalString ?: "0") * moverSign
                    val secondCp = parseEval(a1.topMoves.getOrNull(1)?.evalString ?: "0") * moverSign
                    val gap = kotlin.math.abs(secondCp - bestCp) * 100.0
                    val playedUci = after.moveUci
                    val foundBest = playedUci.isEmpty() ||
                        playedUci == a1.bestMove?.uci ||
                        playedUci == a1.topMoves.firstOrNull()?.uci
                    val brilliant = foundBest && loss <= 30 && gap >= 150.0
                    if (brilliant) brilliantCount++

                    val bestSan = a1.bestMove?.uci?.let { sanOf(before.fen, it) } ?: "?"
                    results.add(
                        MoveReview(i + 1, after.moveSAN, bestSan, loss, acc, brilliant)
                    )
                }
                val pct = ((i + 1) * 100 / (states.size - 1))
                val done = results.toList()
                val wa = whiteAcc.toList()
                val ba = blackAcc.toList()
                val bc = brilliantCount
                (ctx as? android.app.Activity)?.runOnUiThread {
                    progressBar.progress = pct
                    progressLabel.text = "$pct%"
                    renderResults(done, reviewing = true, wa, ba, bc)
                }
            }

            val finalResults = results.toList()
            val finalWhite = whiteAcc.toList()
            val finalBlack = blackAcc.toList()
            val finalBrilliant = brilliantCount
            (ctx as? android.app.Activity)?.runOnUiThread {
                busy = false
                progressWrap.visibility = View.GONE
                statusText.text = "Review complete."
                renderResults(finalResults, false, finalWhite, finalBlack, finalBrilliant)
                newBtn.visibility = View.VISIBLE
            }
        }, "review-analyze").apply { isDaemon = true; start() }
    }

    private fun renderResults(
        all: List<MoveReview>,
        reviewing: Boolean,
        whiteAcc: List<Double> = emptyList(),
        blackAcc: List<Double> = emptyList(),
        brilliantCount: Int = 0
    ) {
        resultsCol.removeAllViews()
        if (!reviewing) {
            val w = if (whiteAcc.isEmpty()) "—" else "%.1f%%".format(whiteAcc.average())
            val b = if (blackAcc.isEmpty()) "—" else "%.1f%%".format(blackAcc.average())
            resultsCol.addView(
                Ui.text(
                    ctx, "Accuracy (approx): White $w · Black $b", 13f, Ui.GREEN, bold = true
                )
            )
            resultsCol.addView(
                Ui.text(ctx, "Brilliant moves (approx): $brilliantCount", 11f, Ui.CYAN)
            )
            resultsCol.addView(Ui.vSpace(ctx, 6))

            val bad = all.filter { it.lossCentipawns >= 50 || it.brilliant }
            if (bad.isEmpty()) {
                resultsCol.addView(
                    Ui.text(ctx, "✓ Clean game! No inaccuracies ≥50cp found.", 13f, Ui.GREEN, bold = true)
                )
                return
            }
            resultsCol.addView(Ui.sectionTitle(ctx, "Brilliant / inaccuracies / blunders"))
            for (m in bad) {
                val row = LinearLayout(ctx)
                row.orientation = LinearLayout.HORIZONTAL
                row.setGravity(Gravity.CENTER_VERTICAL)
                row.background = Ui.roundedBg(Ui.SURFACE_ALT, 10f)
                row.setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))

                val moveNo = (m.ply + 1) / 2
                val dots = if (m.ply % 2 == 1) "." else "…"
                val left = LinearLayout(ctx)
                left.orientation = LinearLayout.VERTICAL
                left.addView(Ui.text(ctx, "Move $moveNo$dots  ${m.san}", 12f, Ui.TEXT, bold = true))
                left.addView(
                    Ui.text(
                        ctx, "Best was ${m.bestMoveSan} • lost ${m.lossCentipawns}cp",
                        10f, Ui.TEXT_DIM
                    )
                )
                row.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.text(ctx, m.severity, 11f, m.color, bold = true))

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = ctx.dp(6)
                row.layoutParams = lp
                resultsCol.addView(row)
            }
        }
    }

    /** Public Lichess logistic model: centipawns (from mover's side) → win %. */
    private fun winPercent(centipawns: Double): Double =
        100.0 / (1.0 + Math.exp(-0.00368208 * centipawns))

    /** Published accuracy curve. Chess.com's CAPS2 formula is not public. */
    private fun moveAccuracy(winPointsLost: Double): Double {
        val raw = 103.1668 * Math.exp(-0.04354 * maxOf(0.0, winPointsLost)) - 3.1669
        return raw.coerceIn(0.0, 100.0)
    }

    private fun parseEval(s: String): Double {
        if (s.startsWith("M")) return 30.0
        if (s.startsWith("-M")) return -30.0
        return s.toDoubleOrNull() ?: 0.0
    }

    private fun formatDate(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.US)
        return sdf.format(java.util.Date(millis))
    }
}
