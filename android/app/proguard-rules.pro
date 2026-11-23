# Keep these rules if minification is ever enabled.
-keep class com.chesshelper.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn org.jetbrains.annotations.**
