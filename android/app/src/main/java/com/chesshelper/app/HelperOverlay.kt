package com.chesshelper.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Port of HelperOverlay.swift: the floating best-move panel shown over the
 * chess.com WebView. Every iOS row is here — header + turn indicator, color
 * override, best move + eval, win-chance bar, last-move grade, opening +
 * popular replies, "then plan" threat line, PV, error line and the panic button.
 */
class HelperOverlay(
    private val ctx: Context,
    private val engine: EngineViewModel,
    private val onPanicHide: () -> Unit
) : EngineViewModel.Listener {

    val view: LinearLayout = LinearLayout(ctx)
    private val expanded = true // could be toggled; kept expanded like iOS default

    // Header
    private val dot = View(ctx)
    private val title = Ui.text(ctx, "Chess Helper", 12f, Ui.TEXT, bold = true)
    private val turnPill = TextView(ctx)
    private val thinking = ProgressBar(ctx)
    private val warnDot = TextView(ctx)
    private val enableBox = CheckBox(ctx)
    private val collapseBtn = Button(ctx)

    // Color override
    private val colorRow = LinearLayout(ctx)
    private val whiteChip = TextView(ctx)
    private val blackChip = TextView(ctx)
    private val autoChip = TextView(ctx)
    private val plyLabel = Ui.text(ctx, "", 10f, Ui.TEXT_DIM, mono = true)

    // Body
    private val bestLabel = Ui.text(ctx, "You should play", 10f, Ui.TEXT_DIM)
    private val bestSan = Ui.text(ctx, "—", 22f, Ui.GREEN, bold = true)
    private val bestUci = Ui.text(ctx, "", 10f, Ui.TEXT_DIM, mono = true)
    private val evalLabel = Ui.text(ctx, "Eval", 10f, Ui.TEXT_DIM)
    private val evalValue = Ui.text(ctx, "—", 18f, Ui.TEXT, bold = true, mono = true)
    private val depthLabel = Ui.text(ctx, "depth 0", 9f, Ui.TEXT_DIM)

    private val winBar = LinearLayout(ctx)
    private val winFill = View(ctx)

    private val gradeRow = LinearLayout(ctx)
    private val gradeWho = Ui.text(ctx, "", 11f, Ui.TEXT_DIM)
    private val gradeMove = Ui.text(ctx, "", 11f, Ui.TEXT, mono = true)
    private val gradeBadge = TextView(ctx)
    private val gradeLoss = Ui.text(ctx, "", 10f, Ui.ORANGE, mono = true)

    private val openingRow = LinearLayout(ctx)
    private val openingEco = Ui.text(ctx, "", 10f, Ui.ORANGE, bold = true, mono = true)
    private val openingName = Ui.text(ctx, "", 11f, Ui.TEXT, bold = true, maxLines = 2)

    private val popularCol = LinearLayout(ctx)
    private val popularTitle = Ui.text(ctx, "Popular here", 9f, Ui.TEXT_DIM)

    private val threatRow = LinearLayout(ctx)
    private val threatText = Ui.text(ctx, "", 11f, Ui.ORANGE)

    private val pvText = Ui.text(ctx, "", 10f, Ui.TEXT_DIM, maxLines = 2)
    private val errorRow = LinearLayout(ctx)
    private val errorText = Ui.text(ctx, "", 10f, Ui.RED, maxLines = 2)

    private val panicBtn = Button(ctx)

    init {
        buildUi()
        engine.listener = this
        render()
    }

    private fun buildUi() {
        val dp = { v: Int -> ctx.dp(v) }

        view.orientation = LinearLayout.VERTICAL
        view.background = Ui.roundedBg(0xE6121212.toInt(), 16f, 0x14FFFFFF, 1, ctx)
        view.setPadding(dp(4), dp(2), dp(4), dp(6))

        // ---- header ----
        dot.background = Ui.circleBg(Ui.GREEN)
        val dotLp = LinearLayout.LayoutParams(dp(8), dp(8))
        dotLp.marginEnd = dp(8)
        dot.layoutParams = dotLp

        turnPill.textSize = 10f
        turnPill.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        val pillLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        pillLp.marginStart = dp(8)
        turnPill.layoutParams = pillLp
        turnPill.setPadding(dp(8), dp(2), dp(8), dp(2))

        thinking.isIndeterminate = true
        thinking.visibility = View.GONE
        val thLp = LinearLayout.LayoutParams(dp(16), dp(16))
        thLp.marginStart = dp(6)
        thinking.layoutParams = thLp

        warnDot.text = "!"
        warnDot.textSize = 11f
        warnDot.setTextColor(Ui.YELLOW)
        warnDot.gravity = Gravity.CENTER
        warnDot.background = Ui.circleBg(0x33FFEB3B)
        val wLp = LinearLayout.LayoutParams(dp(16), dp(16))
        wLp.marginStart = dp(6)
        warnDot.layoutParams = wLp
        warnDot.visibility = View.GONE

        enableBox.isChecked = engine.enabled
        enableBox.setOnCheckedChangeListener { _, checked ->
            engine.enabled = checked
            render()
        }

        collapseBtn.text = "▾"
        collapseBtn.textSize = 12f
        collapseBtn.setTextColor(Ui.TEXT)
        collapseBtn.setBackgroundColor(0)
        collapseBtn.setPadding(dp(6), 0, dp(6), 0)
        collapseBtn.setOnClickListener {
            val show = bodyCol.visibility == View.GONE
            bodyCol.visibility = if (show) View.VISIBLE else View.GONE
            collapseBtn.text = if (show) "▾" else "▴"
        }

        val header = LinearLayout(ctx)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(dot)
        header.addView(title)
        header.addView(turnPill)
        header.addView(thinking)
        val sp = View(ctx)
        header.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(warnDot)
        header.addView(enableBox)
        header.addView(collapseBtn)
        view.addView(header)

        // ---- body (collapsible) ----
        bodyCol.orientation = LinearLayout.VERTICAL
        view.addView(bodyCol)

        // color override row
        colorRow.orientation = LinearLayout.HORIZONTAL
        colorRow.gravity = Gravity.CENTER_VERTICAL
        val lbl = Ui.text(ctx, "I'm playing:", 10f, Ui.TEXT_DIM)
        val lblLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lblLp.marginEnd = dp(6)
        lbl.layoutParams = lblLp
        colorRow.addView(lbl)
        colorRow.addView(whiteChip)
        colorRow.addView(blackChip)
        colorRow.addView(autoChip)
        val sp2 = View(ctx)
        colorRow.addView(sp2, LinearLayout.LayoutParams(0, 1, 1f))
        colorRow.addView(plyLabel)
        val crLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        crLp.topMargin = dp(6)
        colorRow.layoutParams = crLp
        bodyCol.addView(colorRow)

        bodyCol.addView(Ui.divider(ctx))

        // best move + eval
        val leftCol = LinearLayout(ctx)
        leftCol.orientation = LinearLayout.VERTICAL
        leftCol.addView(bestLabel)
        leftCol.addView(bestSan)
        leftCol.addView(bestUci)

        val rightCol = LinearLayout(ctx)
        rightCol.orientation = LinearLayout.VERTICAL
        rightCol.gravity = Gravity.END
        rightCol.addView(evalLabel)
        rightCol.addView(evalValue)
        rightCol.addView(depthLabel)

        val bestRow = LinearLayout(ctx)
        bestRow.orientation = LinearLayout.HORIZONTAL
        bestRow.addView(leftCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bestRow.addView(rightCol)
        val brLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        brLp.topMargin = dp(8)
        bestRow.layoutParams = brLp
        val pad = dp(10)
        bestRow.setPadding(pad, 0, pad, 0)
        bodyCol.addView(bestRow)

        // win chance bar
        winBar.orientation = LinearLayout.HORIZONTAL
        winBar.background = Ui.roundedBg(0x99000000.toInt(), 4f)
        winFill.background = Ui.roundedBg(Ui.TEXT, 4f)
        winBar.addView(winFill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        winBar.addView(View(ctx), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        val wbLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
        )
        wbLp.topMargin = dp(8)
        winBar.layoutParams = wbLp
        val wbWrap = LinearLayout(ctx)
        wbWrap.orientation = LinearLayout.HORIZONTAL
        wbWrap.addView(winBar, LinearLayout.LayoutParams(0, dp(8), 1f))
        wbWrap.setPadding(pad, 0, pad, 0)
        bodyCol.addView(wbWrap)

        // grade row
        gradeRow.orientation = LinearLayout.HORIZONTAL
        gradeRow.gravity = Gravity.CENTER_VERTICAL
        gradeRow.addView(gradeWho)
        gradeRow.addView(gradeMove)
        val sp3 = View(ctx)
        gradeRow.addView(sp3, LinearLayout.LayoutParams(0, 1, 1f))
        gradeRow.addView(gradeBadge)
        gradeRow.addView(gradeLoss)
        val grLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        grLp.topMargin = dp(8)
        gradeRow.layoutParams = grLp
        gradeRow.setPadding(pad, 0, pad, 0)
        bodyCol.addView(gradeRow)

        // opening
        openingRow.orientation = LinearLayout.HORIZONTAL
        openingRow.gravity = Gravity.CENTER_VERTICAL
        val bookIcon = Ui.text(ctx, "\uD83D\uDCD6", 11f, Ui.ORANGE)
        val biLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        biLp.marginEnd = dp(4)
        bookIcon.layoutParams = biLp
        openingRow.addView(bookIcon)
        openingRow.addView(openingEco)
        openingRow.addView(openingName)
        val orLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        orLp.topMargin = dp(8)
        openingRow.layoutParams = orLp
        openingRow.setPadding(pad, 0, pad, 0)
        bodyCol.addView(openingRow)

        // popular replies
        val popWrap = LinearLayout(ctx)
        popWrap.orientation = LinearLayout.VERTICAL
        popWrap.addView(popularTitle)
        popularCol.orientation = LinearLayout.VERTICAL
        popWrap.addView(popularCol)
        val pwLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        pwLp.topMargin = dp(8)
        popWrap.layoutParams = pwLp
        popWrap.setPadding(pad, 0, pad, 0)
        bodyCol.addView(popWrap)

        // threat
        threatRow.orientation = LinearLayout.HORIZONTAL
        val warn = Ui.text(ctx, "⚠", 11f, Ui.ORANGE)
        val wnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        wnLp.marginEnd = dp(4)
        warn.layoutParams = wnLp
        threatRow.addView(warn)
        threatRow.addView(threatText)
        val trLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        trLp.topMargin = dp(6)
        threatRow.layoutParams = trLp
        threatRow.setPadding(pad, 0, pad, 0)
        bodyCol.addView(threatRow)

        // pv
        val pvWrap = LinearLayout(ctx)
        pvWrap.orientation = LinearLayout.VERTICAL
        pvWrap.addView(pvText)
        val pvLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        pvLp.topMargin = dp(6)
        pvWrap.layoutParams = pvLp
        pvWrap.setPadding(pad, 0, pad, 0)
        bodyCol.addView(pvWrap)

        // error
        errorRow.orientation = LinearLayout.HORIZONTAL
        val errIcon = Ui.text(ctx, "⊘", 11f, Ui.RED)
        val eiLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        eiLp.marginEnd = dp(4)
        errIcon.layoutParams = eiLp
        errorRow.addView(errIcon)
        errorRow.addView(errorText)
        val erLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        erLp.topMargin = dp(4)
        errorRow.layoutParams = erLp
        errorRow.setPadding(pad, 0, pad, 0)
        bodyCol.addView(errorRow)

        // panic
        panicBtn.text = "Hide"
        panicBtn.textSize = 11f
        panicBtn.setTextColor(Ui.TEXT_DIM)
        panicBtn.setBackgroundColor(0x22FFFFFF)
        panicBtn.setOnClickListener { onPanicHide() }
        val pr = LinearLayout(ctx)
        pr.orientation = LinearLayout.HORIZONTAL
        pr.gravity = Gravity.END
        pr.addView(panicBtn)
        val prLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        prLp.topMargin = dp(6)
        pr.layoutParams = prLp
        pr.setPadding(pad, 0, pad, 0)
        bodyCol.addView(pr)
    }

    private val bodyCol = LinearLayout(ctx)

    override fun onEngineStateChanged() {
        render()
    }

    /** Re-read the engine state and refresh every row. */
    fun render() {
        val dp = { v: Int -> ctx.dp(v) }

        dot.background = Ui.circleBg(if (engine.enabled) Ui.GREEN else 0xFF808080.toInt())

        turnPill.text = if (engine.isMyTurn) "▶ Your turn" else "⏸ Waiting"
        turnPill.background = Ui.roundedBg(
            if (engine.isMyTurn) 0x3300C853 else 0x22FFFFFF, 12f
        )
        turnPill.setTextColor(if (engine.isMyTurn) Ui.GREEN else Ui.TEXT_DIM)

        thinking.visibility = if (engine.isThinking) View.VISIBLE else View.GONE
        warnDot.visibility =
            if (engine.confidence != DetectionConfidence.HIGH) View.VISIBLE else View.GONE
        enableBox.isChecked = engine.enabled

        // Color chips
        refreshColorChip(whiteChip, "♔ White", engine.effectivePlayerColor == "w") {
            engine.setManualColor("w")
        }
        refreshColorChip(blackChip, "♚ Black", engine.effectivePlayerColor == "b") {
            engine.setManualColor("b")
        }
        autoChip.visibility = if (engine.manualPlayerColor != null) View.VISIBLE else View.GONE
        refreshColorChip(autoChip, "Auto", false) { engine.setManualColor(null) }
        plyLabel.text = if (engine.plyCount >= 0) "ply ${engine.plyCount}" else ""

        // Best move
        val mine = engine.isMyTurn
        bestLabel.text = if (mine) "You should play" else "Opponent's best"
        bestSan.text = if (engine.bestMoveSan.isEmpty()) "—" else engine.bestMoveSan
        bestSan.setTextColor(if (mine) Ui.GREEN else Ui.RED)
        bestUci.text = engine.bestMoveUci

        // Eval
        evalValue.text = engine.evalDisplay()
        evalValue.setTextColor(evalColor())
        depthLabel.text = "depth ${engine.depth}"

        // Win bar (my POV)
        val minePct = (engine.myWinChance() / 100.0).coerceIn(0.02, 0.98).toFloat()
        val fillLp = winFill.layoutParams as LinearLayout.LayoutParams
        fillLp.weight = minePct
        winFill.layoutParams = fillLp
        val restLp = winBar.getChildAt(1).layoutParams as LinearLayout.LayoutParams
        restLp.weight = 1f - minePct
        winBar.getChildAt(1).layoutParams = restLp

        // Grade row
        if (engine.lastMoveQuality != MoveQuality.NONE && engine.lastMoveUci.isNotEmpty()) {
            gradeRow.visibility = View.VISIBLE
            gradeWho.text = if (engine.lastMoveWasMine) "You:" else "Opp:"
            gradeMove.text = engine.lastMoveUci
            gradeBadge.text = engine.lastMoveQuality.label.trim()
            gradeBadge.setTextColor(qualityColor(engine.lastMoveQuality))
            gradeBadge.background = Ui.roundedBg(
                withAlpha(qualityColor(engine.lastMoveQuality), 0x40), 6f
            )
            gradeBadge.setPadding(dp(8), dp(2), dp(8), dp(2))
            gradeLoss.text =
                if (engine.lastMoveCentipawnLoss >= 30) "-${engine.lastMoveCentipawnLoss}cp" else ""
        } else {
            gradeRow.visibility = View.GONE
        }

        // Opening
        if (engine.openingName.isNotEmpty()) {
            openingRow.visibility = View.VISIBLE
            openingEco.text = engine.openingEco
            openingName.text = engine.openingName
        } else {
            openingRow.visibility = View.GONE
        }

        // Popular replies
        popularCol.removeAllViews()
        if (engine.openingMoves.isEmpty()) {
            popularTitle.visibility = View.GONE
        } else {
            popularTitle.visibility = View.VISIBLE
            for (m in engine.openingMoves.take(3)) {
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                val san = Ui.text(ctx, m.san, 11f, Ui.CYAN, bold = true, mono = true)
                san.layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
                val games = Ui.text(ctx, "${m.totalGames} games", 10f, Ui.TEXT_DIM)
                val glp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                glp.marginStart = dp(8)
                games.layoutParams = glp
                val wr = Ui.text(
                    ctx, String.format("W %.0f%%", m.winRate), 10f,
                    0xCC00C853.toInt(), mono = true
                )
                r.addView(san)
                r.addView(games)
                val sp = View(ctx)
                r.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
                r.addView(wr)
                popularCol.addView(r)
            }
        }

        // Threat / then-plan
        if (!engine.isMyTurn && engine.ponderSan.isNotEmpty()) {
            threatRow.visibility = View.VISIBLE
            threatText.text = "Then plan: ${engine.ponderSan}"
        } else {
            threatRow.visibility = View.GONE
        }

        // PV + error
        pvText.text = engine.pvText
        pvText.visibility = if (engine.pvText.isEmpty()) View.GONE else View.VISIBLE
        if (engine.errorMessage.isNotEmpty()) {
            errorRow.visibility = View.VISIBLE
            errorText.text = engine.errorMessage
        } else {
            errorRow.visibility = View.GONE
        }
    }

    private fun refreshColorChip(chip: TextView, label: String, active: Boolean, onClick: () -> Unit) {
        chip.text = label
        chip.textSize = 11f
        chip.setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
        chip.setTextColor(if (active) Ui.GREEN else Ui.TEXT)
        chip.background = Ui.roundedBg(if (active) 0x5900C853 else 0x1AFFFFFF, 8f)
        chip.setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = ctx.dp(6)
        chip.layoutParams = lp
        chip.setOnClickListener { onClick() }
    }

    private fun evalColor(): Int {
        val e = engine.evalDisplay()
        if (e.contains('M')) return Ui.CYAN
        val v = e.toDoubleOrNull() ?: return Ui.TEXT
        return when {
            v > 1.0 -> Ui.GREEN
            v < -1.0 -> Ui.RED
            else -> Ui.TEXT
        }
    }

    private fun qualityColor(q: MoveQuality): Int = when (q) {
        MoveQuality.BRILLIANT -> Ui.CYAN
        MoveQuality.BEST -> Ui.GREEN
        MoveQuality.GOOD -> 0xB300C853.toInt()
        MoveQuality.INACCURACY -> Ui.YELLOW
        MoveQuality.MISTAKE -> Ui.ORANGE
        MoveQuality.BLUNDER -> Ui.RED
        MoveQuality.NONE -> Ui.TEXT_DIM
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    /** Attach to a FrameLayout with bottom gravity, like the iOS overlay. */
    fun attachTo(container: FrameLayout) {
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        lp.marginStart = ctx.dp(12)
        lp.marginEnd = ctx.dp(12)
        lp.bottomMargin = ctx.dp(8)
        container.addView(view, lp)
    }
}
