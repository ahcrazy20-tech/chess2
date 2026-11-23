package com.chesshelper.app

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Port of StatsView.swift — "Weakness Detector".
 *
 * Pulls the latest month of chess.com games, categorizes losses (phase, color,
 * time class, short games) and turns them into human-readable coaching tips.
 */
class StatsTab(private val ctx: Context) {

    val view: View
    private val usernameInput = EditText(ctx)
    private val analyzeBtn = Button(ctx)
    private val statusText = Ui.text(ctx, "", 11f, Ui.TEXT_DIM)
    private val insightsCol = LinearLayout(ctx)
    private val breakdownCol = LinearLayout(ctx)
    private var busy = false

    init {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.SURFACE)
        val pad = ctx.dp(16)
        root.setPadding(pad, pad, pad, pad)

        root.addView(Ui.sectionTitle(ctx, "Weakness Detector"))
        root.addView(Ui.text(ctx, "Your chess.com username", 11f, Ui.TEXT_DIM))
        usernameInput.hint = "username"
        usernameInput.setTextColor(Ui.TEXT)
        usernameInput.setHintTextColor(0x66FFFFFF)
        usernameInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        usernameInput.setSingleLine(true)
        root.addView(usernameInput)

        analyzeBtn.text = "Analyze my games"
        analyzeBtn.setTextColor(Ui.TEXT)
        analyzeBtn.setOnClickListener { analyze() }
        root.addView(analyzeBtn)

        root.addView(statusText)
        root.addView(Ui.vSpace(ctx, 8))

        insightsCol.orientation = LinearLayout.VERTICAL
        root.addView(insightsCol)
        breakdownCol.orientation = LinearLayout.VERTICAL
        root.addView(breakdownCol)

        val scroll = ScrollView(ctx)
        scroll.addView(root)
        view = scroll
    }

    private fun analyze() {
        if (busy) return
        val user = usernameInput.text.toString().trim()
        if (user.isEmpty()) {
            statusText.text = "Type a username first."
            return
        }
        busy = true
        analyzeBtn.isEnabled = false
        statusText.text = "Fetching games…"
        insightsCol.removeAllViews()
        breakdownCol.removeAllViews()

        Thread({
            try {
                val games = ChessComApi.recentGames(user, 25)
                if (games.isEmpty()) {
                    finish("No games found", emptyList(), emptyList())
                    return@Thread
                }
                statusText.text = "Analyzing ${games.size} games…"
                val stats = StatsInsights.collect(games, user)
                val insights = StatsInsights.generateInsights(stats)
                finish("Analyzed ${stats.size} games", insights, stats)
            } catch (e: Exception) {
                finish("Error: ${e.message}", emptyList(), emptyList())
            }
        }, "stats-analyze").apply { isDaemon = true; start() }
    }

    private fun finish(
        status: String,
        insights: List<String>,
        stats: List<StatsInsights.GameStats>
    ) {
        (ctx as? android.app.Activity)?.runOnUiThread {
            busy = false
            analyzeBtn.isEnabled = true
            statusText.text = status

            insightsCol.removeAllViews()
            if (insights.isNotEmpty()) {
                insightsCol.addView(Ui.sectionTitle(ctx, "Insights"))
                for (line in insights) {
                    val t = Ui.text(ctx, line, 12f, Ui.TEXT)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = ctx.dp(8)
                    t.layoutParams = lp
                    insightsCol.addView(t)
                }
            }

            breakdownCol.removeAllViews()
            if (stats.isNotEmpty()) {
                breakdownCol.addView(Ui.sectionTitle(ctx, "Games breakdown"))
                breakdownCol.addView(row("Total", "${stats.size}"))
                breakdownCol.addView(row("As White", "${stats.count { it.asWhite }}"))
                breakdownCol.addView(row("As Black", "${stats.count { !it.asWhite }}"))
                val avg = stats.map { it.plyCount }.average().toInt()
                breakdownCol.addView(row("Avg length", "$avg plies"))
            }
        }
    }

    private fun row(label: String, value: String): View {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        l.addView(
            Ui.text(ctx, label, 12f, Ui.TEXT_DIM),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        l.addView(Ui.text(ctx, value, 12f, Ui.TEXT, bold = true))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = ctx.dp(2)
        l.layoutParams = lp
        return l
    }
}
