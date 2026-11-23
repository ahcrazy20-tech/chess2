package com.chesshelper.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Port of OpeningsView.swift — "Opening Trainer".
 *
 * List screen: 10 classic openings with All/White/Black filters.
 * Trainer screen: the line is shown move by move and you quiz yourself on the
 * next move from memory, or reveal the answer. Also opens the line on the
 * chess.com analysis board.
 */
class OpeningsTab(
    private val ctx: Context,
    private val openInPlay: (String) -> Unit
) {

    val view: View
    private val root = LinearLayout(ctx)
    private val listPanel = LinearLayout(ctx)
    private val trainerPanel = LinearLayout(ctx)
    private var filter = Filter.ALL

    private enum class Filter(val label: String) {
        ALL("All"), WHITE("White"), BLACK("Black")
    }

    // Trainer state
    private var opening: Opening? = null
    private var currentIndex = 0
    private var correctCount = 0
    private var attemptCount = 0

    private val lineText = TextView(ctx)
    private val promptText = TextView(ctx)
    private val guessInput = EditText(ctx)
    private val feedbackText = TextView(ctx)
    private val scoreText = TextView(ctx)
    private val movesCol = LinearLayout(ctx)

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.SURFACE)
        val pad = ctx.dp(16)
        root.setPadding(pad, pad, pad, pad)

        listPanel.orientation = LinearLayout.VERTICAL
        trainerPanel.orientation = LinearLayout.VERTICAL
        trainerPanel.visibility = View.GONE

        buildList()
        buildTrainer()

        root.addView(listPanel)
        root.addView(trainerPanel)

        val scroll = ScrollView(ctx)
        scroll.addView(root)
        view = scroll
    }

    /** Intercept back: return true when the trainer screen was closed. */
    fun onBackPressed(): Boolean {
        if (trainerPanel.visibility == View.VISIBLE) {
            showList()
            return true
        }
        return false
    }

    private fun showList() {
        trainerPanel.visibility = View.GONE
        listPanel.visibility = View.VISIBLE
    }

    private fun showTrainer(op: Opening) {
        opening = op
        currentIndex = 0
        correctCount = 0
        attemptCount = 0
        guessInput.setText("")
        renderTrainer()
        listPanel.visibility = View.GONE
        trainerPanel.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // List screen
    // ------------------------------------------------------------------

    private fun buildList() {
        listPanel.addView(Ui.sectionTitle(ctx, "Opening Trainer"))

        val filters = LinearLayout(ctx)
        filters.orientation = LinearLayout.HORIZONTAL
        for (f in Filter.values()) {
            filters.addView(Ui.chip(ctx, f.label, filter == f) {
                filter = f
                renderList()
            })
            filters.addView(Ui.vSpace(ctx, 0))
        }
        listPanel.addView(filters)
        movesCol.orientation = LinearLayout.VERTICAL
        listPanel.addView(movesCol)
        renderList()
    }

    private fun renderList() {
        movesCol.removeAllViews()
        // Rebuild filter chips so the active one highlights.
        (listPanel.getChildAt(1) as? LinearLayout)?.let { filters ->
            filters.removeAllViews()
            for (f in Filter.values()) {
                filters.addView(Ui.chip(ctx, f.label, filter == f) {
                    filter = f
                    renderList()
                })
                val gap = View(ctx)
                gap.layoutParams = LinearLayout.LayoutParams(ctx.dp(6), 1)
                filters.addView(gap)
            }
        }

        val items = when (filter) {
            Filter.ALL -> OpeningsCatalog.all
            Filter.WHITE -> OpeningsCatalog.all.filter { it.whiteRepertoire }
            Filter.BLACK -> OpeningsCatalog.all.filter { !it.whiteRepertoire }
        }

        for (op in items) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.VERTICAL
            row.background = Ui.roundedBg(Ui.SURFACE_ALT, 10f)
            row.setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))

            val head = LinearLayout(ctx)
            head.orientation = LinearLayout.HORIZONTAL
            head.setGravity(Gravity.CENTER_VERTICAL)
            head.addView(Ui.text(ctx, op.eco, 10f, Ui.ORANGE, bold = true, mono = true))
            val nameLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            nameLp.marginStart = ctx.dp(8)
            val name = Ui.text(ctx, op.name, 13f, Ui.TEXT, bold = true)
            name.layoutParams = nameLp
            head.addView(name)
            val gap = View(ctx)
            head.addView(gap, LinearLayout.LayoutParams(0, 1, 1f))
            val sideTag = Ui.text(
                ctx,
                if (op.whiteRepertoire) "White" else "Black",
                9f, Ui.TEXT_DIM
            )
            sideTag.background = Ui.roundedBg(0x26FFFFFF, 4f)
            sideTag.setPadding(ctx.dp(6), ctx.dp(2), ctx.dp(6), ctx.dp(2))
            head.addView(sideTag)
            row.addView(head)

            val desc = Ui.text(ctx, op.description, 10f, Ui.TEXT_DIM)
            val dlp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dlp.topMargin = ctx.dp(4)
            desc.layoutParams = dlp
            row.addView(desc)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = ctx.dp(8)
            row.layoutParams = lp
            row.setOnClickListener { showTrainer(op) }
            movesCol.addView(row)
        }
    }

    // ------------------------------------------------------------------
    // Trainer screen
    // ------------------------------------------------------------------

    private fun buildTrainer() {
        val back = Button(ctx)
        back.text = "← All openings"
        back.setTextColor(Ui.TEXT_DIM)
        back.setBackgroundColor(0)
        back.setGravity(Gravity.START)
        back.setOnClickListener { showList() }
        trainerPanel.addView(back)

        trainerPanel.addView(Ui.text(ctx, "Line so far", 11f, Ui.TEXT_DIM))
        lineText.setTextColor(Ui.TEXT)
        lineText.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
        lineText.textSize = 13f
        trainerPanel.addView(lineText)
        trainerPanel.addView(Ui.vSpace(ctx, 10))

        promptText.setTextColor(Ui.GREEN)
        promptText.textSize = 12f
        promptText.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        trainerPanel.addView(promptText)

        guessInput.hint = "Your move (e.g. Nf3)"
        guessInput.setTextColor(Ui.TEXT)
        guessInput.setHintTextColor(0x66FFFFFF)
        guessInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        guessInput.setSingleLine(true)
        trainerPanel.addView(guessInput)

        val btnRow = LinearLayout(ctx)
        btnRow.orientation = LinearLayout.HORIZONTAL
        val checkBtn = Button(ctx)
        checkBtn.text = "Check"
        checkBtn.setTextColor(Ui.TEXT)
        checkBtn.setOnClickListener { submitGuess() }
        val revealBtn = Button(ctx)
        revealBtn.text = "Show answer"
        revealBtn.setTextColor(Ui.TEXT_DIM)
        revealBtn.setOnClickListener { revealAnswer() }
        val restartBtn = Button(ctx)
        restartBtn.text = "Restart"
        restartBtn.setTextColor(Ui.TEXT_DIM)
        restartBtn.setOnClickListener {
            currentIndex = 0; correctCount = 0; attemptCount = 0
            guessInput.setText("")
            renderTrainer()
        }
        btnRow.addView(checkBtn)
        btnRow.addView(revealBtn)
        btnRow.addView(restartBtn)
        trainerPanel.addView(btnRow)

        feedbackText.textSize = 12f
        trainerPanel.addView(feedbackText)
        scoreText.setTextColor(Ui.TEXT_DIM)
        scoreText.textSize = 11f
        trainerPanel.addView(scoreText)

        trainerPanel.addView(Ui.vSpace(ctx, 10))
        val openBtn = Button(ctx)
        openBtn.text = "↗ Open on chess.com analysis board"
        openBtn.setTextColor(Ui.CYAN)
        openBtn.setOnClickListener {
            val op = opening ?: return@setOnClickListener
            val path = op.moves.mapIndexed { i, m ->
                (if (i % 2 == 0) "${i / 2 + 1}." else "") + m
            }.joinToString(" ")
            val encoded = java.net.URLEncoder.encode(path, "UTF-8")
            openInPlay("https://www.chess.com/analysis?pgn=$encoded")
        }
        trainerPanel.addView(openBtn)
    }

    private fun renderTrainer() {
        val op = opening ?: return
        lineText.text = if (currentIndex == 0) "(starting position)" else formatLine(op)

        if (currentIndex < op.moves.size) {
            val moveNo = currentIndex / 2 + 1
            val dots = if (currentIndex % 2 == 0) "" else "…"
            val side = if (currentIndex % 2 == 0) "White" else "Black"
            promptText.text = "Move $moveNo$dots — $side to play"
            promptText.visibility = View.VISIBLE
            guessInput.visibility = View.VISIBLE
        } else {
            promptText.text = "🎉 You completed the line!"
            guessInput.visibility = View.GONE
            feedbackText.text = ""
            scoreText.text = "Score: $correctCount / $attemptCount"
        }
    }

    private fun formatLine(op: Opening): String {
        val sb = StringBuilder()
        for ((i, m) in op.moves.take(currentIndex).withIndex()) {
            if (i % 2 == 0) sb.append("${i / 2 + 1}. ")
            sb.append(m).append(' ')
        }
        return sb.toString().trim()
    }

    private fun submitGuess() {
        val op = opening ?: return
        if (currentIndex >= op.moves.size) return
        val expected = op.moves[currentIndex]
        attemptCount++
        val norm = { s: String ->
            s.replace("+", "").replace("#", "").replace("x", "").replace("=", "")
                .trim().lowercase()
        }
        if (norm(guessInput.text.toString()) == norm(expected)) {
            correctCount++
            feedbackText.setTextColor(Ui.GREEN)
            feedbackText.text = "✓ Correct!"
            currentIndex++
        } else {
            feedbackText.setTextColor(Ui.ORANGE)
            feedbackText.text = "✗ Answer was: $expected"
        }
        guessInput.setText("")
        scoreText.text = "Score: $correctCount / $attemptCount"
        renderTrainer()
    }

    private fun revealAnswer() {
        val op = opening ?: return
        if (currentIndex >= op.moves.size) return
        feedbackText.setTextColor(Ui.YELLOW)
        feedbackText.text = "Answer: ${op.moves[currentIndex]}"
        currentIndex++
        renderTrainer()
    }
}
