# ChessHelper ProGuard rules (minification is disabled by default, kept for safety).

# The JavaScript bridge must not be stripped or renamed.
-keepclassmembers class com.chesshelper.app.ChessWebView$InnerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# StockfishEngine / FallbackEngine are referenced reflectively only by design.
-keep class com.chesshelper.app.StockfishEngine { *; }
-keep class com.chesshelper.app.FallbackEngine { *; }
