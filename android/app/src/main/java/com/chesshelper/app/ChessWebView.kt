package com.chesshelper.app

import android.content.Context
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebView that loads chess.com and reads the board back to the engine via a
 * JavaScript bridge. Port of the PlayView web-view + reader.js idea.
 */
class ChessWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var boardListener: BoardListener? = null

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        addJavascriptInterface(InnerBridge(), "ChessHelper")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBoardReader()
            }
        }
    }

    fun setBoardListener(l: BoardListener) {
        boardListener = l
    }

    fun loadChessCom(path: String = "") {
        val url = if (path.isNotEmpty()) "https://www.chess.com$path" else "https://www.chess.com/"
        loadUrl(url)
    }

    /** Inject a board reader. Selectors must track the live chess.com DOM. */
    private fun injectBoardReader() {
        val js = """
            (function(){
              try {
                // TODO: adapt these selectors to the current chess.com markup.
                var grid = Array(64).fill('.');
                var side = 'w';
                var flipped = false;
                var confident = true;
                var lastMove = '';
                var ply = 0;
                var conf = 'high';
                window.ChessHelper.onBoard(grid.join(''), side, flipped, confident, lastMove, ply, conf);
              } catch (e) {
                window.ChessHelper.onBoard(
                  'rnbqkbnrpppppppp................................................PPPPPPPPRNBQKBNR',
                  'w', false, true, '', 0, 'unknown');
              }
            })()
        """.trimIndent()
        loadUrl("javascript:$js")
    }

    interface BoardListener {
        fun onBoard(
            grid: String,
            sideToMove: String,
            flipped: Boolean,
            flippedConfident: Boolean,
            lastMoveUci: String,
            plyCount: Int,
            confidence: String
        )
    }

    private inner class InnerBridge {
        @JavascriptInterface
        fun onBoard(
            grid: String,
            sideToMove: String,
            flipped: Boolean,
            flippedConfident: Boolean,
            lastMoveUci: String,
            plyCount: Int,
            confidence: String
        ) {
            boardListener?.onBoard(grid, sideToMove, flipped, flippedConfident, lastMoveUci, plyCount, confidence)
        }
    }
}
