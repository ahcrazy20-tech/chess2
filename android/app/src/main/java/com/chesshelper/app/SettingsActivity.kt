package com.chesshelper.app

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * Settings screen. Port of SettingsView.swift. Stored in the same
 * "chesshelper" SharedPreferences file the EngineViewModel reads.
 */
class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences

    private lateinit var root: LinearLayout
    private lateinit var sbDepth: SeekBar
    private lateinit var sbMovetime: SeekBar
    private lateinit var sbMultiPV: SeekBar
    private lateinit var tvDepth: TextView
    private lateinit var tvMovetime: TextView
    private lateinit var tvMultiPV: TextView
    private lateinit var swBg: SwitchCompat
    private lateinit var swRec: SwitchCompat
    private lateinit var swShot: SwitchCompat
    private lateinit var swMinimal: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("chesshelper", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            padding = dp(16)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        fun section(title: String) {
            val t = TextView(this).apply {
                text = title
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(8))
                setTextColor(0xFF00C853.toInt())
            }
            root.addView(t)
        }

        fun row(label: String, view: View) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val l = TextView(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(0xFFFFFFFF.toInt())
            }
            view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            container.addView(l)
            container.addView(view)
            root.addView(container)
        }

        section("Engine — Stockfish 19 (local)")

        tvDepth = TextView(this).apply { text = "Search depth: ${prefs.getInt("engineDepth", 20)}"; setTextColor(0xFFFFFFFF.toInt()) }
        sbDepth = SeekBar(this).apply {
            max = 15; progress = prefs.getInt("engineDepth", 20) - 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.edit().putInt("engineDepth", progress + 10).apply(); tvDepth.text = "Search depth: ${progress + 10}" }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        row("Search depth", tvDepth)
        root.addView(sbDepth)

        tvMovetime = TextView(this).apply { text = "Max think time: ${prefs.getInt("engineMovetime", 2000)} ms"; setTextColor(0xFFFFFFFF.toInt()) }
        sbMovetime = SeekBar(this).apply {
            max = 18; progress = (prefs.getInt("engineMovetime", 2000) - 500) / 250
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.edit().putInt("engineMovetime", 500 + progress * 250).apply(); tvMovetime.text = "Max think time: ${500 + progress * 250} ms" }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        row("Max think time", tvMovetime)
        root.addView(sbMovetime)

        tvMultiPV = TextView(this).apply { text = "Candidate moves: ${prefs.getInt("multiPV", 3)}"; setTextColor(0xFFFFFFFF.toInt()) }
        sbMultiPV = SeekBar(this).apply {
            max = 4; progress = prefs.getInt("multiPV", 3) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.edit().putInt("multiPV", progress + 1).apply(); tvMultiPV.text = "Candidate moves: ${progress + 1}" }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        row("Candidate moves", tvMultiPV)
        root.addView(sbMultiPV)

        val hint = TextView(this).apply {
            text = "Higher depth = stronger but slower. Depth 20 ≈ ~3400 Elo."
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(hint)

        section("Stealth — Panic gestures")

        swBg = SwitchCompat(this).apply { text = "Hide when app is backgrounded"; isChecked = prefs.getBoolean("autoHideBackground", true) }
        swRec = SwitchCompat(this).apply { text = "Hide when screen is recorded/mirrored"; isChecked = prefs.getBoolean("autoHideRecording", true) }
        swShot = SwitchCompat(this).apply { text = "Hide when screenshot is taken"; isChecked = prefs.getBoolean("autoHideScreenshot", true) }
        swMinimal = SwitchCompat(this).apply { text = "Use minimal mode (tiny dot)"; isChecked = prefs.getBoolean("useMinimalMode", false) }

        row("", swBg)
        row("", swRec)
        row("", swShot)
        row("", swMinimal)

        section("Status")
        addTextRow("Overlay visible", "—")
        addTextRow("Screen captured", "—")
        addTextRow("Last hide reason", prefs.getString("lastHideReason", ""))

        section("About")
        addTextRow("Version", "2.0.0 (20)")
        addTextRow("Engine", "Stockfish 19 (local)")
        addTextRow("Build type", "TrollStore-style (unsigned)")

        val privacy = TextView(this).apply {
            text = "Zero external requests — analysis runs 100% on-device. Nothing sent to any server."
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(privacy)

        val btn = Button(this).apply {
            text = "OK"
            setOnClickListener { finish() }
        }
        root.addView(btn)
    }

    private fun addTextRow(label: String, value: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val l = TextView(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 14f
        }
        val v = TextView(this).apply {
            text = value
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.END
        }
        container.addView(l)
        container.addView(v)
        root.addView(container)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {}

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    private fun dp(d: Int): Int = (d * resources.displayMetrics.density).toInt()
}
