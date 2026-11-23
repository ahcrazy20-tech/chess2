package com.chesshelper.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Trivial launcher screen. It loads before any engine, WebView or game UI, so
 * if this appears the app's Kotlin/dex/Application layer is healthy. It also
 * shows the previous run's startup trace, which is otherwise lost when the
 * app dies too early to open a crash-report activity.
 */
class BootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BootTrace.mark(this, "BOOT_ACTIVITY_START")

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val title = TextView(this).apply {
            text = "Chess Helper 2.1.6 — boot check"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(24, 40, 24, 16)
        }
        root.addView(title)

        val trace = BootTrace.read(this)
        val logView = TextView(this).apply {
            text = if (trace.isBlank()) {
                "No trace from a previous run yet.\n\nTap START CHESS HELPER below, then reopen this app if it crashes."
            } else {
                "LAST RUN TRACE:\n$trace"
            }
            textSize = 12f
            setTextColor(Color.rgb(200, 230, 200))
            setTextIsSelectable(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            setPadding(24, 8, 24, 8)
        }
        root.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "START CHESS HELPER"
            setOnClickListener {
                BootTrace.mark(this@BootActivity, "START_TAPPED")
                startActivity(Intent(this@BootActivity, MainActivity::class.java))
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "SHARE LOG"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Chess Helper startup trace")
                    putExtra(Intent.EXTRA_TEXT, trace)
                }
                startActivity(Intent.createChooser(send, "Send startup trace"))
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)

        setContentView(root)
        BootTrace.mark(this, "BOOT_ACTIVITY_READY")
    }
}
