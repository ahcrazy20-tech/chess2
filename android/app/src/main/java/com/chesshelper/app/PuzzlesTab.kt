package com.chesshelper.app

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Port of PuzzlesView.swift — "Puzzle Rating Tracker".
 *
 * Log every puzzle attempt (theme, rating, seconds, solved), see accuracy and
 * average rating, per-tactic breakdown and recent history. Data is stored as
 * JSON in SharedPreferences ("puzzle_attempts_v1") — fully local.
 */
class PuzzlesTab(
    private val ctx: Context,
    private val openInPlay: (String) -> Unit
) {

    val view: View
    private val overviewCol = LinearLayout(ctx)
    private val themeCol = LinearLayout(ctx)
    private val recentCol = LinearLayout(ctx)

    private val prefs = ctx.getSharedPreferences("chesshelper", Context.MODE_PRIVATE)
    private val attempts = ArrayList<PuzzleStore.PuzzleAttempt>()

    init {
        attempts.addAll(PuzzleStore.decode(prefs.getString("puzzle_attempts_v1", "") ?: ""))

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.SURFACE)
        val pad = ctx.dp(16)
        root.setPadding(pad, pad, pad, pad)

        root.addView(Ui.sectionTitle(ctx, "Puzzles"))

        root.addView(Ui.text(ctx, "Overview", 11f, Ui.TEXT_DIM))
        overviewCol.orientation = LinearLayout.VERTICAL
        root.addView(overviewCol)

        root.addView(Ui.sectionTitle(ctx, "Practice"))
        root.addView(practiceButton("🧩 Solve a puzzle on Lichess") {
            openInPlay("https://lichess.org/training")
        })
        root.addView(practiceButton("⚡ Puzzle Rush on chess.com") {
            openInPlay("https://www.chess.com/puzzles/rush")
        })
        root.addView(practiceButton("✎ Log a puzzle attempt") { showLogDialog() })

        root.addView(Ui.sectionTitle(ctx, "By tactic"))
        themeCol.orientation = LinearLayout.VERTICAL
        root.addView(themeCol)

        root.addView(Ui.sectionTitle(ctx, "Recent attempts"))
        recentCol.orientation = LinearLayout.VERTICAL
        root.addView(recentCol)

        val clearBtn = Button(ctx)
        clearBtn.text = "Clear history"
        clearBtn.setTextColor(Ui.RED)
        clearBtn.setOnClickListener {
            attempts.clear()
            save()
            render()
        }
        root.addView(clearBtn)

        val scroll = ScrollView(ctx)
        scroll.addView(root)
        view = scroll

        render()
    }

    private fun practiceButton(label: String, onClick: () -> Unit): View {
        val b = Button(ctx)
        b.text = label
        b.setTextColor(Ui.CYAN)
        b.setBackgroundColor(0)
        b.setGravity(Gravity.START)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun save() {
        prefs.edit().putString("puzzle_attempts_v1", PuzzleStore.encode(attempts)).apply()
    }

    private fun render() {
        // Overview
        overviewCol.removeAllViews()
        val total = attempts.size
        val solved = attempts.count { it.solved }
        val acc = PuzzleStore.accuracy(attempts)
        val avg = PuzzleStore.avgRating(attempts)
        overviewCol.addView(statRow("Total puzzles", "$total"))
        overviewCol.addView(statRow("Solved", "$solved"))
        overviewCol.addView(statRow("Accuracy", String.format("%.0f%%", acc)))
        overviewCol.addView(statRow("Avg puzzle rating", "$avg"))

        // Themes
        themeCol.removeAllViews()
        val themes = PuzzleStore.byTheme(attempts)
        if (themes.isEmpty()) {
            themeCol.addView(Ui.text(ctx, "No attempts logged yet.", 11f, Ui.TEXT_DIM))
        }
        for (t in themes) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setGravity(Gravity.CENTER_VERTICAL)
            row.addView(
                Ui.text(ctx, t.theme.replaceFirstChar { it.uppercase() }, 12f, Ui.TEXT),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(Ui.text(ctx, "${t.solved}/${t.count}", 11f, Ui.TEXT_DIM, mono = true))
            val pctColor = when {
                t.pct >= 70 -> Ui.GREEN
                t.pct >= 50 -> Ui.YELLOW
                else -> Ui.ORANGE
            }
            val pct = Ui.text(ctx, String.format("%.0f%%", t.pct), 11f, pctColor, bold = true)
            val plp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            plp.marginStart = ctx.dp(10)
            pct.layoutParams = plp
            row.addView(pct)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = ctx.dp(4)
            row.layoutParams = lp
            themeCol.addView(row)
        }

        // Recent
        recentCol.removeAllViews()
        for (a in attempts.take(20)) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setGravity(Gravity.CENTER_VERTICAL)
            val icon = Ui.text(ctx, if (a.solved) "✔" else "✘", 12f, if (a.solved) Ui.GREEN else Ui.RED, bold = true)
            val ilp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            ilp.marginEnd = ctx.dp(8)
            icon.layoutParams = ilp
            row.addView(icon)
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(ctx, a.theme.replaceFirstChar { it.uppercase() }, 12f, Ui.TEXT))
            col.addView(Ui.text(ctx, "Rated ${a.rating} • ${a.seconds}s", 10f, Ui.TEXT_DIM))
            row.addView(col)
            recentCol.addView(row)
        }
    }

    private fun statRow(label: String, value: String): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(
            Ui.text(ctx, label, 12f, Ui.TEXT_DIM),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(Ui.text(ctx, value, 12f, Ui.TEXT, bold = true))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = ctx.dp(2)
        row.layoutParams = lp
        return row
    }

    private fun showLogDialog() {
        val form = LinearLayout(ctx)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), 0)

        var chosenTheme = PuzzleStore.THEMES[0]
        val themeBtn = Button(ctx)
        themeBtn.text = "Tactic: ${chosenTheme.replaceFirstChar { it.uppercase() }}"
        themeBtn.setTextColor(Ui.TEXT)
        themeBtn.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Tactic theme")
                .setItems(PuzzleStore.THEMES.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()) { _, which ->
                    chosenTheme = PuzzleStore.THEMES[which]
                    themeBtn.text = "Tactic: ${chosenTheme.replaceFirstChar { it.uppercase() }}"
                }
                .show()
        }
        form.addView(themeBtn)

        val ratingInput = EditText(ctx)
        ratingInput.hint = "Puzzle rating (e.g. 1500)"
        ratingInput.setTextColor(Ui.TEXT)
        ratingInput.setHintTextColor(0x66FFFFFF)
        ratingInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        ratingInput.setSingleLine(true)
        form.addView(ratingInput)

        val secondsInput = EditText(ctx)
        secondsInput.hint = "Seconds spent (e.g. 30)"
        secondsInput.setTextColor(Ui.TEXT)
        secondsInput.setHintTextColor(0x66FFFFFF)
        secondsInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        secondsInput.setSingleLine(true)
        form.addView(secondsInput)

        val solvedInput = EditText(ctx)
        solvedInput.hint = "Solved? type yes or no"
        solvedInput.setTextColor(Ui.TEXT)
        solvedInput.setHintTextColor(0x66FFFFFF)
        solvedInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        solvedInput.setSingleLine(true)
        solvedInput.setText("yes")
        form.addView(solvedInput)

        AlertDialog.Builder(ctx)
            .setTitle("Log Attempt")
            .setView(form)
            .setNegativeButton("Cancel") { _, _ -> }
            .setPositiveButton("Save") { _, _ ->
                val rating = ratingInput.text.toString().toIntOrNull() ?: 1500
                val seconds = secondsInput.text.toString().toIntOrNull() ?: 0
                val solved = solvedInput.text.toString().trim().lowercase().let {
                    it == "yes" || it == "y" || it == "true" || it == "1"
                }
                attempts.add(
                    0,
                    PuzzleStore.PuzzleAttempt(
                        dateISO = PuzzleStore.nowISO(),
                        theme = chosenTheme,
                        rating = rating,
                        solved = solved,
                        seconds = seconds
                    )
                )
                save()
                render()
            }
            .show()
    }
}
