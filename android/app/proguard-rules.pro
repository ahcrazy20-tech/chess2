# Keep Stockfish JNI and chess lib
-keep class com.chesshelper.app.data.** { *; }
-keep class com.github.bhlangonijr.** { *; }
-keep class androidx.webkit.** { *; }

# Keep JS bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
