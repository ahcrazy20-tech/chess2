package com.chesshelper.app

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * WebView that loads chess.com and reads the board back to the engine through a
 * JavaScript bridge. Port of ChessWebView.swift + reader.js.
 *
 * - `assets/adblock_css.css` is injected at document start (hides ads/upsells).
 * - `assets/reader.js` (the full port of the iOS reader with all three chess.com
 *   DOM formats, flip consensus detection, ply counting and diff-based last-move
 *   detection) runs at document end and posts JSON payloads through
 *   `window.ChessHelper.onBoard(json)`.
 */
class ChessWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    interface BoardListener {
        fun onBoard(payload: BoardPayload)
    }

    private var boardListener: BoardListener? = null
    private val readerJs: String = loadAsset("reader.js")
    private val adblockCss: String = loadAsset("adblock_css.css")

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        addJavascriptInterface(InnerBridge(), "ChessHelper")

        setWebViewClient(object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectAdblock()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBoardReader()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Keep all navigation inside this WebView (incl. target=_blank links).
                if (url != null && url.startsWith("http")) {
                    view?.loadUrl(url)
                    return true
                }
                return false
            }
        })
    }

    fun setBoardListener(l: BoardListener?) {
        boardListener = l
    }

    fun loadChessCom(path: String = "") {
        val url = if (path.isNotEmpty()) "https://www.chess.com$path" else "https://www.chess.com/"
        loadUrl(url)
    }

    fun openInPlay(urlString: String) {
        if (urlString.startsWith("http")) loadUrl(urlString)
    }

    private fun injectAdblock() {
        if (adblockCss.isEmpty()) return
        val json = JSONObject.quote(adblockCss)
        val js = """
            (function(){
              try {
                var s = document.createElement('style');
                s.textContent = $json;
                (document.head || document.documentElement).appendChild(s);
              } catch (e) {}
            })();
        """.trimIndent()
        evaluateJavascript(js, null)
    }

    private fun injectBoardReader() {
        if (readerJs.isEmpty()) {
            Log.w("ChessWebView", "reader.js missing from assets")
            return
        }
        evaluateJavascript(readerJs, null)
        evaluateJavascript(
            "window.__ch_startReader && window.__ch_startReader();", null
        )
    }

    private fun loadAsset(name: String): String {
        return try {
            context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e("ChessWebView", "failed to load asset $name: ${e.message}")
            ""
        }
    }

    private inner class InnerBridge {
        @JavascriptInterface
        fun onBoard(json: String) {
            try {
                val o = JSONObject(json)
                val payload = BoardPayload(
                    grid = o.optString("grid"),
                    sideToMove = o.optString("sideToMove", "w"),
                    flipped = o.optBoolean("flipped", false),
                    flippedConfident = o.optBoolean("flippedConfident", false),
                    lastMoveUci = o.optString("lastMoveUCI"),
                    plyCount = o.optInt("plyCount", -1),
                    detectionConfidence = o.optString("detectionConfidence", "unknown")
                )
                post { boardListener?.onBoard(payload) }
            } catch (e: Exception) {
                Log.w("ChessWebView", "bad board payload: ${e.message}")
            }
        }
    }
}
