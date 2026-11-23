package com.chesshelper.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Full-screen, shareable report shown when the previous launch did not finish. */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = intent.getStringExtra("log") ?: "(no log)"

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = Button(this).apply {
            text = "Chess Helper did not start last time — tap SHARE and send this log"
            isEnabled = false
            setBackgroundColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(12, 16, 12, 16)
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val logBox = EditText(this).apply {
            setText(log)
            setTextIsSelectable(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isFocusable = true
            setTextColor(Color.rgb(220, 235, 220))
            setBackgroundColor(Color.rgb(18, 18, 18))
            textSize = 11f
            gravity = Gravity.TOP
        }
        val scroll = ScrollView(this).apply { addView(logBox) }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "COPY"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Chess Helper crash log", log))
                Toast.makeText(this@CrashReportActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "SHARE"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Chess Helper crash log")
                    putExtra(Intent.EXTRA_TEXT, log)
                }
                startActivity(Intent.createChooser(send, "Send crash log"))
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "CLOSE"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
    }
}
