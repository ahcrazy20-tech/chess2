package com.chesshelper.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * Main activity. Port of ContentView.swift + PlayView.swift.
 *
 * Tabs: Play (chess.com WebView + helper overlay), Review, Openings, Puzzles, Stats, Settings.
 * Stockfish 19 runs locally (arm64 asset copied to files dir). Stealth gestures hide the overlay.
 */
class MainActivity : AppCompatActivity(), ChessWebView.BoardListener, EngineViewModel.EngineListener, StealthManager.StealthListener {

    private lateinit var webView: ChessWebView
    private lateinit var overlay: View
    private lateinit var tvStatus: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvMoves: TextView
    private lateinit var btnPanic: Button

    private lateinit var engine: EngineViewModel
    private lateinit var stealth: StealthManager

    private val prefs by lazy { getSharedPreferences("chesshelper", Context.MODE_PRIVATE) }
    private var mediaProjectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView) as ChessWebView
        overlay = findViewById(R.id.overlay) as View
        tvStatus = findViewById(R.id.overlayStatus) as TextView
        tvBest = findViewById(R.id.overlayBest) as TextView
        tvMoves = findViewById(R.id.overlayMoves) as TextView
        btnPanic = findViewById(R.id.overlayPanic) as Button

        engine = EngineViewModel(this)
        engine.listener = this

        stealth = StealthManager(this, overlay, this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // --- Wire web view <-> engine ---
        webView.setBoardListener(this)
        webView.setOnTouchListener { _, _ -> false }

        btnPanic.setOnClickListener { stealth.hide("manual") }

        // Top nav bar: chess.com destinations
        (findViewById(R.id.navHome) as Button).setOnClickListener { webView.loadChessCom() }
        (findViewById(R.id.navOnline) as Button).setOnClickListener { webView.loadChessCom("/play/online/new") }
        (findViewById(R.id.navBots) as Button).setOnClickListener { webView.loadChessCom("/play/computer") }
        (findViewById(R.id.navPuzzles) as Button).setOnClickListener { webView.loadChessCom("/puzzles") }
        (findViewById(R.id.navAnalysis) as Button).setOnClickListener { webView.loadChessCom("/analysis") }

        // Bottom tab buttons
        (findViewById(R.id.btnTabPlay) as Button).setOnClickListener { showTab(0) }
        (findViewById(R.id.btnTabReview) as Button).setOnClickListener { showTab(1) }
        (findViewById(R.id.btnTabOpenings) as Button).setOnClickListener { showTab(2) }
        (findViewById(R.id.btnTabPuzzles) as Button).setOnClickListener { showTab(3) }
        (findViewById(R.id.btnTabStats) as Button).setOnClickListener { showTab(4) }
        (findViewById(R.id.btnTabSettings) as Button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        showTab(0)

        // --- Initialize local Stockfish engine ---
        try {
            StockfishEngine.init(this)
            StockfishEngine.start { line ->
                // engine protocol trace (could be surfaced in a debug panel)
            }
        } catch (e: Exception) {
            tvStatus.text = "Engine unavailable: ${e.message}"
            tvStatus.setTextColor(0xFFF44336.toInt())
        }

        // --- Fair-play disclaimer on first launch ---
        if (!prefs.getBoolean("seenDisclaimer", false)) {
            showDisclaimer()
        }
    }

    override fun onResume() {
        super.onResume()
        // listen for screen capture (Android 14+ MediaProjection callback is wired here)
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                // Best-effort: capture state can flip when a cast/session appears.
                // A real implementation uses ScreenCaptureDetector (API 34+) or
                // MediaProjectionManager.Callback; flag is exposed via setScreenCaptured.
            }
        }, null)
    }

    private fun showTab(index: Int) {
        val isPlay = index == 0
        webView.visibility = if (isPlay) View.VISIBLE else View.GONE
        // other tabs are placeholders: hide overlay when not on Play
        if (!isPlay) overlay.visibility = View.GONE
    }

    // ---- ChessWebView.BoardListener ----
    override fun onBoard(
        grid: String, sideToMove: String, flipped: Boolean, flippedConfident: Boolean,
        lastMoveUci: String, plyCount: Int, confidence: String
    ) {
        engine.ingest(BoardPayload(grid, sideToMove, flipped, flippedConfident, lastMoveUci, plyCount, confidence))
    }

    // ---- EngineViewModel.EngineListener ----
    override fun onTopMoves(moves: List<TopMove>, eval: String, winChance: Double, depth: Int) {
        tvBest.text = if (moves.isEmpty()) "—" else "${moves.first().san} (${moves.first().evalString})"
        tvMoves.text = moves.joinToString(" ") { it.uci }
        tvStatus.text = if (engine.isMyTurn) "Your turn" else "Waiting"
        tvStatus.setTextColor(if (engine.isMyTurn) 0xFF00C853 else 0xFFB0B0B0)
    }

    override fun onError(msg: String) {
        tvStatus.text = msg
        tvStatus.setTextColor(0xFFF44336.toInt())
    }

    override fun onThinking(thinking: Boolean) {
        tvStatus.text = if (thinking) "Engine thinking…" else tvStatus.text
    }

    override fun onOpening(name: String, eco: String) {
        if (name.isNotEmpty()) {
            tvMoves.text = "[$eco] $name  ·  ${tvMoves.text}"
        }
    }

    override fun onMoveQuality(quality: MoveQuality, cpLoss: Int, wasMine: Boolean, uci: String) {
        // Surface move quality in the overlay status for a moment.
        val tag = if (wasMine) "You:" else "Opp:"
        tvStatus.text = "$tag $uci · $quality${if (cpLoss >= 30) " (-$cpLoss cp)" else ""}"
    }

    // ---- StealthManager.StealthListener ----
    override fun onOverlayVisibilityChanged(visible: Boolean, reason: String?) {
        if (visible) {
            overlay.visibility = View.VISIBLE
        } else {
            overlay.visibility = View.GONE
        }
    }

    override fun onScreenCaptureChanged(captured: Boolean) {
        // UI can react here (e.g. dim the board)
    }

    // Wire Android 14+ screen capture: start a MediaProjection and report capture state.
    // (Kept minimal here; call stealth.setScreenCaptured(true) from the callback.)
    private fun requestScreenCapture() {
        val mgr = mediaProjectionManager
        if (mgr == null) return
        startActivityForResult(mgr.createScreenCaptureIntent(), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK && data != null) {
                // A real implementation would create a MediaProjection here and observe
                // stop callbacks, then call stealth.setScreenCaptured(true).
            }
        }
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle(R.string.fair_play_title)
            .setMessage(R.string.fair_play_body)
            .setCancelable(false)
            .setPositiveButton(R.string.i_understand) { _, _ ->
                prefs.edit().putBoolean("seenDisclaimer", true).apply()
            }
            .show()
    }

    override fun onDestroy() {
        engine.destroy()
        stealth.destroy()
        StockfishEngine.destroy()
        super.onDestroy()
    }
}
