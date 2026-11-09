package com.chesshelper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.chesshelper.app.ui.ChessHelperApp
import com.chesshelper.app.ui.theme.ChessHelperTheme

/**
 * MainActivity — entry point for Chess Helper on Android.
 *
 * S26 Ultra optimizations:
 *  - enableEdgeToEdge() + transparent bars → use full 6.9" 3120x1440 canvas
 *  - WindowCompat handling for display cutout (center punch-hole)
 *  - 120 Hz: WebView and Compose both run at display refresh rate by default on One UI 7
 *  - configChanges handles rotation without recreating WebView (keeps chess.com session alive)
 *  - S Pen: WebView already supports stylus hover; no extra code needed but we keep generic motion events
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // S26 Ultra: edge-to-edge from first frame, no flicker. One UI 7 requires explicit call.
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Optional: hint for 120 Hz on S26 Ultra. Compose/WebView automatically match display mode,
        // but we ensure window allows peak refresh.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes.preferredDisplayModeId = 0 // 0 = system picks best (120Hz on S26 Ultra)
        }

        setContent {
            ChessHelperTheme(darkTheme = true) {
                ChessHelperApp()
            }
        }
    }

    // Stealth: shake detection via accelerometer would normally be here.
    // We implement shake via SensorManager in StealthManager (see ui/StealthManager.kt).
    // Keep activity clean — no overrides needed because Compose handles gestures.
}
