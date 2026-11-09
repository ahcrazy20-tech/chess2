package com.chesshelper.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.chesshelper.app.data.BoardPayload
import com.chesshelper.app.data.EngineViewModel
import org.json.JSONObject

/**
 * Chess.com WebView + JS bridge — Android port of iOS ChessWebView (WKWebView + fenBridge).
 *
 * Loads https://www.chess.com and injects:
 *  - reader.js (robust board scraping, same logic as Resources/reader.js)
 *  - adblock_css.css (hides ads/upsell banners — optional)
 *
 * Uses addJavascriptInterface("fenBridge") so JS calls window.fenBridge.postMessage(payload).
 * S26 Ultra tuning:
 *  - Hardware acceleration + 120Hz rendering hint
 *  - Wide viewport, DOM storage, mixed content allowed for chess.com CDN
 *  - LAYER_TYPE_HARDWARE for smooth 1440p board panning
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChessWebView(
    engine: EngineViewModel,
    router: AppRouter,
    stealth: StealthManager? = null
) {
    val context = LocalContext.current

    // Load JS/CSS assets from app/src/main/assets
    val rawReaderJs = remember {
        try { context.assets.open("reader.js").bufferedReader().readText() } catch (_: Exception) { fallbackReaderJs }
    }
    // Patch iOS WebKit bridge to Android JavascriptInterface bridge.
    // iOS: window.webkit.messageHandlers.fenBridge.postMessage(payload) where payload is object
    // Android: window.fenBridge.postMessage(JSON.stringify(payload)) where payload must be String
    val readerJs = remember(rawReaderJs) {
        rawReaderJs
            .replace("window.webkit.messageHandlers.fenBridge.postMessage(payload)", "window.fenBridge.postMessage(JSON.stringify(payload))")
            .replace("window.webkit.messageHandlers.fenBridge.postMessage(payload);", "window.fenBridge.postMessage(JSON.stringify(payload));")
            .replace("window.webkit.messageHandlers.fenBridge", "window.fenBridge")
    }
    val adblockCss = remember {
        try { context.assets.open("adblock.css").bufferedReader().readText() } catch (_: Exception) { "" }
    }

    val bridge = remember {
        FenBridge { payloadString ->
            try {
                // payloadString is JSON object from JS
                val obj = JSONObject(payloadString)
                val grid = obj.optString("grid", obj.optString("gridString", ""))
                if (grid.length != 64) return@FenBridge
                val payload = BoardPayload(
                    grid = grid,
                    sideToMove = obj.optString("sideToMove", "w"),
                    flipped = obj.optBoolean("flipped", false),
                    flippedConfident = obj.optBoolean("flippedConfident", false),
                    lastMoveUCI = obj.optString("lastMoveUCI", ""),
                    plyCount = obj.optInt("plyCount", -1),
                    detectionConfidence = obj.optString("detectionConfidence", "low")
                )
                engine.ingest(payload)
            } catch (e: Exception) {
                // ignore malformed
            }
        }
    }

    // Determine initial URL and observe router changes
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(router.navigateTrigger) {
        // When router requests navigation, loadUrl in existing WebView
        webViewRef[0]?.let { wv ->
            val url = router.chessUrl
            if (wv.url != url) wv.loadUrl(url)
        }
        onDispose {}
    }

    // S26 Ultra: hardware back button navigates WebView history before exiting app
    BackHandler(enabled = true) {
        val wv = webViewRef[0]
        if (wv != null && wv.canGoBack()) wv.goBack() else {
            // Let activity handle (exit). We try to finish via context as Activity
            (context as? android.app.Activity)?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // S26 Ultra: hardware layer + high refresh
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                // Performance settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = userAgentString?.replace("; wv", "") // hide WebView hint from chess.com
                }
                // Allow file:// for local assets if needed
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_NEVER

                addJavascriptInterface(bridge, "fenBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        // Could block known ad hosts here as extra adblock — keep simple.
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // Inject adblock CSS early
                        if (adblockCss.isNotEmpty()) {
                            val cssEsc = adblockCss.replace("`", "\\`")
                            view?.evaluateJavascript(
                                """(function(){var s=document.createElement('style');s.textContent=`$cssEsc`;document.documentElement.appendChild(s);})();""", null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject reader.js — idempotent via window.__ch_v3 guard
                        view?.evaluateJavascript(readerJs) { _ ->
                            // Kick the reader loop
                            view.evaluateJavascript("try{window.__ch_startReader && window.__ch_startReader();}catch(e){}", null)
                        }
                    }
                }

                // Initial load
                loadUrl(router.chessUrl)
                webViewRef[0] = this
            }
        },
        update = { wv ->
            // Ensure router navigation is reflected even if recomposition missed DisposableEffect
            if (wv.url != router.chessUrl && router.navigateTrigger > 0) {
                wv.loadUrl(router.chessUrl)
            }
        },
        onRelease = { wv ->
            wv.removeJavascriptInterface("fenBridge")
            wv.destroy()
        }
    )
}

class FenBridge(private val onPayload: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(payload: String) {
        onPayload(payload)
    }
}

// Fallback reader.js — compact version if assets missing. Real file lives in app/src/main/assets/reader.js
private const val fallbackReaderJs = """
(function(){
  if(window.__ch_v3) return; window.__ch_v3=true;
  const PIECE_MAP={wp:'P',wn:'N',wb:'B',wr:'R',wq:'Q',wk:'K',bp:'p',bn:'n',bb:'b',br:'r',bq:'q',bk:'k'};
  function findBoard(){return document.querySelector('wc-chess-board, chess-board, cg-board, .board-layout-chessboard');}
  function readGrid(board){
    const grid=Array.from({length:8},()=>Array(8).fill('.'));
    const nodes=board.querySelectorAll('.piece, piece, [data-piece]');
    if(!nodes.length) return null;
    nodes.forEach(n=>{
      let piece=null,file=null,rank=null;
      const cls=(n.className||'').toString();
      cls.split(/\s+/).forEach(c=>{if(PIECE_MAP[c]) piece=PIECE_MAP[c];});
      if(!piece){const dp=n.getAttribute&&n.getAttribute('data-piece'); if(dp&&PIECE_MAP[dp]) piece=PIECE_MAP[dp];}
      cls.split(/\s+/).forEach(c=>{const m=/^square-(\d)(\d)${'$'}/.exec(c); if(m){file=+m[1]; rank=+m[2];}});
      if(file===null){const ds=n.getAttribute&&n.getAttribute('data-square'); if(ds&&ds.length===2){file=ds.charCodeAt(0)-96; rank=parseInt(ds[1],10);}}
      if(piece&&file&&rank) grid[8-rank][file-1]=piece;
    });
    return grid;
  }
  function detectFlipped(board){
    if(board.classList&&board.classList.contains('flipped')) return true;
    const o=board.getAttribute('orientation'); if(o==='black') return true; if(o==='white') return false;
    return false;
  }
  let prevGrid=null, lastPayload='';
  function tick(){
    const board=findBoard(); if(!board) return;
    const grid=readGrid(board); if(!grid) return;
    const gridStr=grid.map(r=>r.join('')).join('');
    const flipped=detectFlipped(board);
    const payload={grid:gridStr, sideToMove:'w', flipped:flipped, flippedConfident:true, lastMoveUCI:'', plyCount:-1, detectionConfidence:'low'};
    const key=JSON.stringify(payload);
    if(key!==lastPayload){lastPayload=key; try{window.fenBridge.postMessage(JSON.stringify(payload));}catch(e){}}
    prevGrid=grid;
  }
  window.__ch_startReader=function(){ if(window.__ch_timer) return; window.__ch_timer=setInterval(tick,500); new MutationObserver(tick).observe(document.documentElement,{childList:true,subtree:true,attributes:true}); };
  window.__ch_startReader();
})();
"""
