package com.chesshelper.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.app.Activity

/**
 * Port of ContentView.swift + PlayView.swift.
 *
 * Tabs: Play (chess.com WebView + helper overlay), Review, Openings, Puzzles,
 * Stats — plus Settings. Everything is built programmatically (no XML layouts),
 * matching the iOS structure one-to-one. Stealth gestures (shake + 3-finger tap)
 * hide the overlay instantly.
 */
class MainActivity : Activity(),
    ChessWebView.BoardListener,
    StealthManager.StealthListener {

    private lateinit var webView: ChessWebView
    private lateinit var overlay: HelperOverlay
    private lateinit var engine: EngineViewModel
    private lateinit var stealth: StealthManager

    private lateinit var topNav: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var playContainer: PanicFrameLayout
    private lateinit var bottomNav: LinearLayout

    private lateinit var reviewTab: ReviewTab
    private lateinit var openingsTab: OpeningsTab
    private lateinit var puzzlesTab: PuzzlesTab
    private lateinit var statsTab: StatsTab

    private val tabButtons = ArrayList<Button>()
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("chesshelper", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BootTrace.mark(this, "MAIN_START")

        // If the last run crashed, show the trace now (then clear it).
        // The full-screen CrashReportActivity from ChessApplication is the
        // reliable report; this dialog is kept as a fallback.
        val lastCrash = CrashHandler.readAndClear()
        if (lastCrash != null) {
            AlertDialog.Builder(this)
                .setTitle("Crash report (last run)")
                .setMessage(lastCrash.take(4000))
                .setPositiveButton("OK", null)
                .show()
        }

        EngineService.init(applicationContext)
        BootTrace.mark(this, "ENGINE_INIT_DONE")

        engine = EngineViewModel(this)

        buildUi()
        BootTrace.mark(this, "UI_BUILT")

        overlay = HelperOverlay(this, engine) { stealth.hide("manual") }
        overlay.attachTo(playContainer)
        BootTrace.mark(this, "OVERLAY_READY")

        webView.setBoardListener(this)
        webView.loadChessCom()
        BootTrace.mark(this, "WEBVIEW_LOADED")

        stealth = StealthManager(this, overlay.view, this)
        BootTrace.mark(this, "STEALTH_READY")

        showTab(0)

        // Settings can ask us to force show/hide the overlay via an intent extra.
        handleStealthIntent()

        if (!prefs.getBoolean("seenDisclaimer", false)) {
            showDisclaimer()
        }
        BootTrace.mark(this, "UI_READY")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleStealthIntent()
    }

    private fun handleStealthIntent() {
        val action = intent?.getStringExtra("stealthAction") ?: return
        if (!::stealth.isInitialized) return
        when (action) {
            "show" -> stealth.show()
            "hide" -> stealth.hide("manual")
        }
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.SURFACE)

        // Top nav: chess.com shortcuts (Play tab only).
        topNav = LinearLayout(this)
        topNav.orientation = LinearLayout.HORIZONTAL
        topNav.setBackgroundColor(0xFF1A1A1A.toInt())
        topNav.setPadding(dp(4), dp(4), dp(4), dp(4))
        addNavButton("⌂\nHome") { webView.loadChessCom() }
        addNavButton("🌐\nOnline") { webView.loadChessCom("/play/online/new") }
        addNavButton("🤖\nBots") { webView.loadChessCom("/play/computer") }
        addNavButton("🧩\nPuzzles") { webView.loadChessCom("/puzzles") }
        addNavButton("📊\nAnalysis") { webView.loadChessCom("/analysis") }
        root.addView(topNav)

        // Content area.
        content = FrameLayout(this)
        val contentLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        content.layoutParams = contentLp

        playContainer = PanicFrameLayout(this)
        playContainer.onPanic = { stealth.hide("3-finger tap") }
        webView = ChessWebView(this)
        playContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        content.addView(playContainer)

        reviewTab = ReviewTab(this)
        openingsTab = OpeningsTab(this) { url -> openInPlay(url) }
        puzzlesTab = PuzzlesTab(this) { url -> openInPlay(url) }
        statsTab = StatsTab(this)
        content.addView(reviewTab.view, fullLp())
        content.addView(openingsTab.view, fullLp())
        content.addView(puzzlesTab.view, fullLp())
        content.addView(statsTab.view, fullLp())

        root.addView(content)

        // Bottom tab bar.
        bottomNav = LinearLayout(this)
        bottomNav.orientation = LinearLayout.HORIZONTAL
        bottomNav.setBackgroundColor(0xFF1A1A1A.toInt())
        bottomNav.setPadding(dp(4), dp(4), dp(4), dp(4))
        val labels = listOf("Play", "Review", "Openings", "Puzzles", "Stats", "⚙ Settings")
        for ((i, label) in labels.withIndex()) {
            val b = Button(this)
            b.text = label
            b.textSize = 10f
            b.setTextColor(Ui.TEXT)
            b.setBackgroundColor(0)
            b.setGravity(Gravity.CENTER)
            b.typeface = Typeface.DEFAULT
            b.setPadding(0, dp(6), 0, dp(6))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            b.layoutParams = lp
            when (i) {
                5 -> b.setOnClickListener {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                else -> {
                    val tab = i
                    b.setOnClickListener { showTab(tab) }
                }
            }
            tabButtons.add(b)
            bottomNav.addView(b)
        }
        root.addView(bottomNav)

        val scrollLessRoot = root
        setContentView(scrollLessRoot)
    }

    private fun fullLp() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
    )

    private fun addNavButton(label: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = label
        b.textSize = 9f
        b.setTextColor(Ui.TEXT)
        b.setBackgroundColor(0)
        b.setGravity(Gravity.CENTER)
        b.setPadding(0, dp(4), 0, dp(4))
        b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        b.setOnClickListener { onClick() }
        topNav.addView(b)
    }

    /** Called by the Openings tab's "open on chess.com analysis board" button. */
    fun openInPlay(url: String) {
        showTab(0)
        webView.openInPlay(url)
    }

    private fun showTab(index: Int) {
        playContainer.visibility = if (index == 0) View.VISIBLE else View.GONE
        reviewTab.view.visibility = if (index == 1) View.VISIBLE else View.GONE
        openingsTab.view.visibility = if (index == 2) View.VISIBLE else View.GONE
        puzzlesTab.view.visibility = if (index == 3) View.VISIBLE else View.GONE
        statsTab.view.visibility = if (index == 4) View.VISIBLE else View.GONE
        topNav.visibility = if (index == 0) View.VISIBLE else View.GONE

        for ((i, b) in tabButtons.withIndex()) {
            b.setTextColor(if (i == index) Ui.GREEN else Ui.TEXT)
        }
    }

    // ------------------------------------------------------------------
    // Board readings
    // ------------------------------------------------------------------

    override fun onBoard(payload: BoardPayload) {
        runOnUiThread { engine.ingest(payload) }
    }

    // ------------------------------------------------------------------
    // Stealth
    // ------------------------------------------------------------------

    override fun onOverlayVisibilityChanged(visible: Boolean, reason: String?) {
        overlay.view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onScreenCaptureChanged(captured: Boolean) {
        // Could dim the board here; the overlay already auto-hides.
    }

    override fun onResume() {
        super.onResume()
        if (::stealth.isInitialized) {
            stealth.autoHideBackground = prefs.getBoolean("autoHideBackground", true)
            stealth.autoHideRecording = prefs.getBoolean("autoHideRecording", true)
            stealth.autoHideScreenshot = prefs.getBoolean("autoHideScreenshot", true)
            stealth.minimalMode = prefs.getBoolean("useMinimalMode", false)
        }
    }

    override fun onBackPressed() {
        if (openingsTab.onBackPressed()) return
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        if (prefs.getBoolean("autoHideBackground", true) && ::stealth.isInitialized) {
            stealth.hide("app backgrounded")
        }
    }

    override fun onDestroy() {
        engine.destroy()
        if (::stealth.isInitialized) stealth.destroy()
        try { webView.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle("Fair-Play Notice")
            .setMessage(
                "Using engine assistance during live online games violates Chess.com's " +
                    "Terms of Service and will get your account banned.\n\n" +
                    "Use this helper for:\n" +
                    "• Post-game review (Review tab)\n" +
                    "• Openings study (Openings tab)\n" +
                    "• Puzzles (Puzzles tab)\n" +
                    "• Playing the Computer bots\n\n" +
                    "Stealth: shake the phone or tap with 3 fingers to hide the overlay instantly."
            )
            .setCancelable(false)
            .setPositiveButton("I understand") { _, _ ->
                prefs.edit().putBoolean("seenDisclaimer", true).apply()
            }
            .show()
    }

    /**
     * Root frame that watches for 3-finger taps anywhere (including over the
     * WebView) — the Android counterpart of the iOS ThreeFingerCatcher.
     */
    class PanicFrameLayout(context: Context) : FrameLayout(context) {
        var onPanic: (() -> Unit)? = null

        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            if (ev != null &&
                ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                ev.pointerCount >= 3
            ) {
                onPanic?.invoke()
                return true
            }
            return super.dispatchTouchEvent(ev)
        }
    }
}
