# Chess Helper — Android (S26 Ultra Optimized)

Port of the iOS `ChessHelper` (SwiftUI + Stockfish 16) to Android (Kotlin + Jetpack Compose + WebView) — tuned for the **Samsung Galaxy S26 Ultra**.

> **S26 Ultra target specs** — 6.9″ LTPO 3.0 Dynamic AMOLED 3120×1440 (~506 ppi) 1–120 Hz adaptive, Snapdragon 8 Elite for Galaxy / Exynos 2600 (arm64-v8a), 12/16 GB RAM, One UI 7 (Android 15/16), S Pen, 5000 mAh. The APK is arm64-only for peak performance.

---

## What it does (same as iOS)

- Loads **chess.com** in a full-screen `WebView` (hardware-accelerated, 120 Hz, edge-to-edge)
- Injects `reader.js` (“reader v3”) that scrapes the board by reading `.piece` / `piece` / `[data-piece]` nodes, handles all three chess.com layouts (legacy `square-52`, `data-square`, `translate%`), detects orientation (`flipped`) and counts ply / last move via DOM + diff
- Sends a **64-char grid** (`rnbqkbnr…`) + `sideToMove` + `flipped` + `lastMoveUCI` + `plyCount` over `window.fenBridge.postMessage(JSON.stringify(payload))`
- Kotlin `EngineViewModel` turns the grid into a **FEN** (`buildFenBoard` + `deriveCastlingRights`), debounces 300 ms, then asks the local `StockfishEngine` for **MultiPV 1–5** (default 3) at depth `10…25` (`20` default, `2000 ms` cap). Shows top-3 moves, eval, win-chance bar, move quality (`!!` → `??`), and offline **ECO opening name** via `LocalOpeningBook` (30 entries, no network)
- `HelperOverlay` renders the same stealth panel as iOS: green/gray “Your turn / Waiting”, thinking spinner, confidence warning, minimal **dot** mode, color override (White/Black/Auto), win bar, and error strip
- 6 tabs via `NavigationBar`: **Play / Review / Openings / Puzzles / Stats / Settings** — non-Play tabs call `AppRouter.openInPlay(url)` to navigate the *Play* WebView without leaving the tab (`navigateTrigger` pattern copied from Swift)
- Stealth: **shake** (accelerometer `Δ>12 m/s²` double-tap) + **3-finger tap** (Compose pointerInput) + auto-hide on **background / screen recording / screenshot** + minimal-mode tiny dot (mirrors `StealthManager.swift`)
- `adblock.css` hides Google/house ads + chess.com upsell/cookie strips so the board gets maximal 1440p room

**Zero external requests** — engine + opening book run 100% on-device.

---

## Project layout

```
android/
├── settings.gradle.kts
├── build.gradle.kts            # root — Kotlin 2.0.20 + AGP 8.6.1
├── gradle.properties            # S26 Ultra perf flags
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.10
├── app/
│   ├── build.gradle.kts        # arm64-v8a only, compileSdk 35, targetSdk 35, minSdk 26
│   ├── src/main/
│   │   ├── AndroidManifest.xml # edge-to-edge, 120Hz, largeScreens, S Pen
│   │   ├── assets/
│   │   │   ├── reader.js       # v3 robust scraper (patched for Android bridge at runtime)
│   │   │   └── adblock.css
│   │   ├── java/com/chesshelper/app/
│   │   │   ├── ChessApp.kt     # Application — warm Stockfish early
│   │   │   ├── MainActivity.kt # enableEdgeToEdge() + 120Hz hint + cutout handling
│   │   │   ├── data/
│   │   │   │   ├── EngineViewModel.kt   # ingest() → FEN → debounce → Stockfish
│   │   │   │   ├── StockfishEngine.kt   # JNI → Process fallback → Mock
│   │   │   │   ├── BoardPayload.kt      # grid + sideToMove + flipped + …
│   │   │   │   ├── LocalOpeningBook.kt  # 30 openings offline
│   │   │   │   ├── TopMove.kt / ChessApi.kt / Pgn.kt
│   │   │   └── ui/
│   │   │       ├── ContentView.kt       # Tab scaffold
│   │   │       ├── PlayView.kt          # WebView + overlay + nav bar
│   │   │       ├── ChessWebView.kt      # WebView + fenBridge + reader injection + adblock
│   │   │       ├── HelperOverlay.kt     # full panel / minimal dot
│   │   │       ├── StealthManager.kt    # shake + 3-finger + recording detection
│   │   │       ├── AppRouter.kt
│   │   │       ├── Review/Openings/Puzzles/Stats (via ReviewView.kt)
│   │   │       ├── SettingsView.kt      # DataStore prefs, S26 Ultra tuning notes
│   │   │       └── theme/Theme.kt       # Material3 dark, green primary
│   │   └── res/values/…
│   └── proguard-rules.pro
└── .github/workflows/build-apk.yml # CI — builds debug + unsigned release APK
```

---

## S26 Ultra optimizations

| Area | Detail |
|------|--------|
| **Display** | `enableEdgeToEdge()` + transparent status/nav + `WindowCompat` cutout handling → uses full 6.9″ 19.5:9 canvas. `preferredDisplayModeId = 0` lets One UI pick 120 Hz; WebView `LAYER_TYPE_HARDWARE` keeps 1440p board crisp. |
| **SoC** | Stockfish `Threads=6` `Hash=128` — tuned for Snapdragon 8 Elite’s 2+6 cores + 12/16 GB RAM. iOS used 3/64; we bump for the bigger thermal headroom of S26 Ultra (vapor chamber). Sustained analyze stays cool. |
| **One UI 7** | `targetSdk 35`, `compileSdk 35`, supports adaptive `NavigationBar` (phone) → `NavigationRail` on DeX/tablet, `configChanges` keeps WebView alive on rotation/fold. |
| **S Pen** | WebView already handles `TOOL_TYPE_STYLUS`; overlay’s tiny dot is S Pen-friendly (14→18 dp). Hover preview works without code. |
| **Storage / install** | `abiFilters "arm64-v8a"` only — smaller APK, no x86 bloat. S26 Ultra is arm64-only. |
| **Performance** | R8 fullMode, resource shrinking, `desugar_jdk_libs` for coroutines, `core-splashscreen` for instant launch, 300 ms debounce so 120 Hz ticks don’t flood engine. |

If you’re on a smaller device the APK still runs (minSdk 26) — S26 Ultra just gets the best defaults.

---

## Building

### Prereqs

- **Android Studio Ladybug+** (Koala+ ok) with **JDK 17**
- **Android SDK 35** (`sdkmanager "platforms;android-35" "build-tools;35.0.0"`)
- For *real* Stockfish: **NDK 27** (`sdkManager --install "ndk;27.0.12077973"`) — otherwise the Mock engine is used and the UI still works.

### Quick start (Mock engine — zero NDK, instant)

```bash
cd android
# If gradle wrapper jar is missing (Studio usually generates it):
gradle wrapper --gradle-version 8.10

# Debug APK (installs on S26 Ultra immediately, mock returns plausible e2e4/d2d4)
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release (unsigned, R8 optimized, still mock)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release-unsigned.apk
```

> The **Mock** engine returns deterministic center moves (`e2e4`, `d2d4`, …) scaled so the overlay, win bar, and move-quality logic can be tested without a binary. Replace it for tournament-strength play.

### Bundling real Stockfish 16 (recommended for S26 Ultra)

**Option A — prebuilt `.so` (fastest)**

1. Grab an arm64 `libstockfish.so` with JNI wrapper (e.g. from `stockfish-android` or your own NDK build).
2. Place it at:
   ```
   android/app/src/main/jniLibs/arm64-v8a/libstockfish.so
   ```
3. Rebuild — `StockfishEngine` will auto-detect `nativeLibraryDir/libstockfish.so` and use **JNI mode**.

**Option B — build from source (reproducible)**

```bash
git clone --depth 1 --branch sf_16 https://github.com/official-stockfish/Stockfish.git /tmp/stockfish
cd /tmp/stockfish/src
export NDK=$ANDROID_HOME/ndk/27.0.12077973
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export CC=$TOOLCHAIN/bin/aarch64-linux-android33-clang++
make -j$(nproc) build ARCH=armv8 COMP=ndk COMPCC="$CC" ndk TARGET=arm64-v8a
# The Makefile outputs `stockfish` executable; for JNI wrap it or copy as executable:
mkdir -p $REPO/android/app/src/main/jniLibs/arm64-v8a
cp stockfish $REPO/android/app/src/main/jniLibs/arm64-v8a/libstockfish.so
# Then gradle will bundle it. For pure executable fallback, also:
cp stockfish $REPO/android/app/src/main/assets/stockfish  # fallback ProcessBuilder path
```

`StockfishEngine.kt` tries in order: **JNI → process executable → Mock**. So even if you only copy the executable, the Process fallback will launch it (on Android 10+ you may need `android:extractNativeLibs="true"` which is default).

Verify:

```bash
./gradlew assembleRelease
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep stockfish
```

### Signing for sideload to S26 Ultra

```bash
keytool -genkey -v -keystore chesshelper.jks -keyalg RSA -keysize 2048 -validity 10000 -alias chesshelper
# then
./gradlew assembleRelease -Pandroid.injected.signing.store.file=$(pwd)/chesshelper.jks \
  -Pandroid.injected.signing.store.password=... \
  -Pandroid.injected.signing.key.alias=chesshelper \
  -Pandroid.injected.signing.key.password=...
# OR sign manually:
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore chesshelper.jks app-release-unsigned.apk chesshelper
zipalign -v 4 app-release-unsigned.apk chesshelper-s26ultra.apk
```

Enable **Settings → Security → Install unknown apps → My Files / Chrome → Allow**, then tap the APK. Or:

```bash
adb install chesshelper-s26ultra.apk
```

---

## GitHub Actions

`.github/workflows/build-apk.yml` builds both **debug** and **release (unsigned)** on every push/PR and uploads artifacts. For a tagged release `v*` it attaches APKs to the GitHub Release.

- Uses `gradle/actions/setup-gradle@v4` + JDK 17 — no local wrapper jar needed.
- Real Stockfish step is **commented out** by default so CI stays fast without NDK. Uncomment the “Build Stockfish 16 for Android arm64” step to ship a strong engine.
- Artifacts retained 30/90 days.

Download from **Actions → Build Android APK → Artifacts**.

---

## Differences from iOS

| Feature | iOS | Android |
|---------|-----|---------|
| WebView | WKWebView + `window.webkit.messageHandlers` | `android.webkit.WebView` + `@JavascriptInterface fenBridge` — `reader.js` is patched at injection time (`JSON.stringify`) |
| Engine | ChessKitEngine (embedded Stockfish 16 via SPM) | `StockfishEngine` object: tries `System.loadLibrary("stockfish")` JNI, then `ProcessBuilder` on binary, then **Mock** |
| Persist | `@AppStorage` / `UserDefaults` | `DataStore` preferences (`engineDepth`, `movetime`, `multiPV`, stealth toggles) |
| Stealth detect | `UIScreen.capturedDidChange` + ` UIApplication.willResignActive` + shake extension on `UIWindow` | `SensorManager` (accelerometer) + `Lifecycle` callbacks + `MediaProjection`/`ContentObserver` stubs |
| Gestures | `UITapGestureRecognizer` 3-finger | Compose `pointerInput` (3-pointer) + `detectTapGestures`; shake via sensor |
| Theming | `.preferredColorScheme(.dark)` | `MaterialTheme` dark scheme, `green` primary, `enableEdgeToEdge` |
| Signing | Unsigned IPA for TrollStore/Sideloadly | Unsigned APK → `jarsigner` + `zipalign` for S26 Ultra sideload |
| Build | `xcodegen` + `xcodebuild` on `macos-15` | `gradle assemble*` on `ubuntu-latest` |

Logic for **FEN building, castling heuristic, win-chance sigmoid, move-quality thresholds, opening lookup, PGN, and debounce** is 1:1.

---

## Fair-Play Note

Same disclaimer as iOS: using engine help in live Online games breaks chess.com ToS and risks a ban. Use **Review / Openings / Puzzles / vs Computer** for study — that’s what the tabs are for. The disclaimer card shows on first launch.

---

## Troubleshooting S26 Ultra

- **120 Hz not smooth?** Ensure **Settings → Display → Motion smoothness → Adaptive** is on. The app doesn’t lock refresh — it follows system.
- **Board looks tiny at 1440p?** Chess.com’s responsive layout adapts; pinch-zoom is enabled. The overlay’s `adblock.css` frees vertical space.
- **Engine weak (Mock)?** Check logcat: `adb logcat -s StockfishEngine`. If you see “Mock mode”, bundle `libstockfish.so` as above.
- **WebView blank?** Check `adb logcat -s chromium` and ensure `INTERNET` permission granted (it is). Try “Home” nav button to reload `https://www.chess.com/`.
- **Shake too sensitive / not triggering?** Threshold is `Δ 12 m/s²` double-shake within ~1 s — adjust `StealthManager.kt:delta > 12f`.
- **S Pen not drawing?** WebView consumes stylus; long-press the overlay’s tiny dot to toggle minimal mode.

---

## License

Same as the iOS repo — private helper. Do not use to cheat in rated play. Keep Stockfish under GPL-3.0.
