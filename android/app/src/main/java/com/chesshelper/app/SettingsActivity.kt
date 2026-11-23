package com.chesshelper.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.app.Activity
import android.widget.Switch

/**
 * Port of SettingsView.swift. Stored in the "chesshelper" SharedPreferences file
 * that EngineViewModel reads. Unlike the earlier scaffold, every control now
 * actually persists its value.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout

    private lateinit var tvDepth: TextView
    private lateinit var tvMovetime: TextView
    private lateinit var tvMultiPV: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("chesshelper", Context.MODE_PRIVATE)

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.SURFACE)
        root.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        )
        val pad = dp(16)
        root.setPadding(pad, pad, pad, pad)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        buildEngineSection()
        buildStealthSection()
        buildStatusSection()
        buildAboutSection()

        val ok = Button(this)
        ok.text = "OK"
        ok.setTextColor(Ui.TEXT)
        ok.setOnClickListener { finish() }
        root.addView(ok)
    }

    // ------------------------------------------------------------------

    private fun buildEngineSection() {
        root.addView(Ui.sectionTitle(this, "Engine — local analysis"))

        tvDepth = Ui.text(this, "Search depth: ${prefs.getInt("engineDepth", 20)}", 12f, Ui.TEXT)
        root.addView(labelRow("Search depth", tvDepth))
        root.addView(seek(
            max = 15, progress = prefs.getInt("engineDepth", 20) - 10
        ) { v ->
            prefs.edit().putInt("engineDepth", v + 10).apply()
            tvDepth.text = "Search depth: ${v + 10}"
        })

        tvMovetime = Ui.text(this, "Max think time: ${prefs.getInt("engineMovetime", 2000)} ms", 12f, Ui.TEXT)
        root.addView(labelRow("Max think time", tvMovetime))
        root.addView(seek(
            max = 18, progress = (prefs.getInt("engineMovetime", 2000) - 500) / 250
        ) { v ->
            val ms = 500 + v * 250
            prefs.edit().putInt("engineMovetime", ms).apply()
            tvMovetime.text = "Max think time: $ms ms"
        })

        tvMultiPV = Ui.text(this, "Candidate moves: ${prefs.getInt("multiPV", 3)}", 12f, Ui.TEXT)
        root.addView(labelRow("Candidate moves", tvMultiPV))
        root.addView(seek(
            max = 4, progress = prefs.getInt("multiPV", 3) - 1
        ) { v ->
            prefs.edit().putInt("multiPV", v + 1).apply()
            tvMultiPV.text = "Candidate moves: ${v + 1}"
        })

        root.addView(
            Ui.text(
                this,
                "Higher depth = stronger but slower. Depth 20 ≈ ~3400 Elo with Stockfish 19.",
                11f, Ui.TEXT_DIM
            )
        )
    }

    private fun buildStealthSection() {
        root.addView(Ui.sectionTitle(this, "Stealth — Panic gestures"))
        root.addView(Ui.text(this, "• Shake device to hide overlay (always active)", 11f, Ui.TEXT_DIM))
        root.addView(Ui.text(this, "• 3-finger tap to hide overlay (always active)", 11f, Ui.TEXT_DIM))
        root.addView(Ui.vSpace(this, 6))

        root.addView(switchRow("Hide when app is backgrounded", "autoHideBackground", true))
        root.addView(switchRow("Hide when screen is recorded/mirrored", "autoHideRecording", true))
        root.addView(switchRow("Hide when screenshot is taken", "autoHideScreenshot", true))
        root.addView(switchRow("Use minimal mode (tiny dot)", "useMinimalMode", false))
    }

    private fun buildStatusSection() {
        root.addView(Ui.sectionTitle(this, "Status"))
        val showing = prefs.getBoolean("overlayVisible", true)
        root.addView(labelRow("Overlay visible", Ui.text(this, if (showing) "Yes" else "No", 12f, Ui.TEXT)))
        root.addView(labelRow(
            "Last hide reason",
            Ui.text(this, prefs.getString("lastHideReason", "") ?: "", 12f, Ui.TEXT)
        ))

        val showBtn = Button(this)
        showBtn.text = "Force show overlay"
        showBtn.setTextColor(Ui.GREEN)
        showBtn.setOnClickListener {
            val i = IntentBridge.overlayIntent(this, true)
            startActivity(i)
        }
        root.addView(showBtn)

        val hideBtn = Button(this)
        hideBtn.text = "Force hide overlay"
        hideBtn.setTextColor(Ui.RED)
        hideBtn.setOnClickListener {
            val i = IntentBridge.overlayIntent(this, false)
            startActivity(i)
        }
        root.addView(hideBtn)
    }

    private fun buildAboutSection() {
        root.addView(Ui.sectionTitle(this, "About"))
        val engineName = if (EngineService.usingStockfish) "Stockfish 19 (on-device)" else "Built-in engine"
        root.addView(labelRow("Version", Ui.text(this, "2.1.0 (21)", 12f, Ui.TEXT)))
        root.addView(labelRow("Engine", Ui.text(this, engineName, 12f, Ui.TEXT)))
        root.addView(labelRow("Build type", Ui.text(this, "Signed APK", 12f, Ui.TEXT)))

        root.addView(Ui.vSpace(this, 8))
        val privacy = Ui.text(
            this,
            "Engine analysis runs 100% on-device. Only the Review/Stats tabs and the " +
                "optional opening lookup touch the network (chess.com public API / Lichess explorer).",
            11f, Ui.TEXT_DIM
        )
        root.addView(privacy)
        root.addView(Ui.vSpace(this, 8))
    }

    // ------------------------------------------------------------------

    private fun labelRow(label: String, value: View): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val l = Ui.text(this, label, 12f, Ui.TEXT_DIM)
        row.addView(l, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(value)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(6)
        row.layoutParams = lp
        return row
    }

    private fun seek(max: Int, progress: Int, onChange: (Int) -> Unit): View {
        val sb = SeekBar(this)
        sb.max = max
        sb.progress = progress
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        return sb
    }

    private fun switchRow(label: String, key: String, default: Boolean): View {
        val sw = Switch(this)
        sw.text = label
        sw.setTextColor(Ui.TEXT)
        sw.isChecked = prefs.getBoolean(key, default)
        sw.typeface = Typeface.DEFAULT
        sw.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        sw.layoutParams = lp
        return sw
    }

    /** dp helper — the top-level Context.dp extension lives in Ui.kt. */

    /**
     * Small helper so Settings can tell MainActivity to force show/hide the
     * overlay through a simple intent extra (MainActivity is singleTop).
     */
    object IntentBridge {
        fun overlayIntent(ctx: Context, show: Boolean): android.content.Intent {
            val i = android.content.Intent(ctx, MainActivity::class.java)
            i.putExtra("stealthAction", if (show) "show" else "hide")
            return i
        }
    }
}
