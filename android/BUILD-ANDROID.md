# Chess Helper — Android port (APK)

A complete Android port of the iOS `ChessHelper` app in this repo. Everything the
SwiftUI app does, the APK does — plus several upgrades (see the table below).

## Feature matrix — iOS ↔ Android

| Feature (iOS source)                        | Android implementation                    | Status |
|---------------------------------------------|-------------------------------------------|--------|
| Play tab: chess.com WebView (PlayView)      | `ChessWebView` + `MainActivity`           | ✅ full |
| Board reader (reader.js v3)                 | `assets/reader.js` (dual bridge)          | ✅ full — all 3 chess.com DOM formats, flip consensus, ply count, diff-based last move |
| Helper overlay (HelperOverlay.swift)        | `HelperOverlay`                           | ✅ full — best move SAN/UCI, eval, depth, win-chance bar, move grade, opening + popular replies, "then plan", PV, panic hide |
| Engine analysis (EngineViewModel.swift)     | `EngineViewModel` + `EngineService`       | ✅ full — debounce, move quality, opening lookup, color override, manual/auto |
| Local Stockfish (StockfishEngine.swift)     | `StockfishEngine` (UCI, Stockfish 19)     | ✅ upgraded — runs from `nativeLibraryDir` (Android 10+ W^X safe), white-POV scores |
| — (no equivalent)                           | `FallbackEngine`                          | 🆕 pure-Kotlin engine — the app keeps working even without the Stockfish binary |
| Blunder Review (ReviewView.swift)           | `ReviewTab`                               | ✅ full + fixed the inverted loss-sign bug of the Swift version |
| Opening Trainer (OpeningsView.swift)        | `OpeningsTab`                             | ✅ full — 10 openings, quiz, filters, open-on-chess.com |
| Puzzle Tracker (PuzzlesView.swift)          | `PuzzlesTab`                              | ✅ full — log attempts, per-theme stats, history |
| Weakness Detector (StatsView.swift)         | `StatsTab`                                | ✅ full — same coaching insights |
| Settings (SettingsView.swift)               | `SettingsActivity`                        | ✅ full + fixes the unsaved-switches bug of the scaffold |
| Stealth (StealthManager.swift)              | `StealthManager` + `PanicFrameLayout`     | ✅ full — shake, 3-finger tap, background auto-hide |
| Opening book (LocalOpeningBook.swift)       | `OpeningBook`                             | ✅ full |
| PGN replay (PGN.swift)                      | `PgnReader` + `Board`                     | ✅ upgraded — real legal move generation, SAN output, FEN round-trip |
| Ad-block CSS (adblock_css.css)              | `assets/adblock_css.css`                  | ✅ full |
| Fair-play disclaimer                        | `MainActivity.showDisclaimer()`           | ✅ kept |

## Target devices

- **Samsung Galaxy S26 Ultra** → arm64-v8a (Stockfish 19 binary, ~3400 Elo)
- Any Android 8.0+ phone (`minSdk 26`, needed for modern Kotlin codegen); armeabi-v7a and x86_64 are also built,
  with the built-in engine as fallback when no binary matches.

## Prerequisites

- JDK 17, Android SDK platform 35, build-tools 35.0.0
- Gradle 8.9 (or Android Studio, which brings its own)

```
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

## The Stockfish binary

`CI` downloads the official Stockfish 19 Android binaries automatically
(`workflows/build-apk.yml` — move it to `.github/workflows/` to activate) and packs them as:

```
android/app/src/main/jniLibs/arm64-v8a/libstockfish.so
android/app/src/main/jniLibs/armeabi-v7a/libstockfish.so
```

To build locally with real Stockfish, download
`stockfish-android-arm64-universal.tar.gz` (and `…-armv7-neon.tar.gz`) from
https://github.com/official-stockfish/Stockfish/releases and extract the binary
to the paths above. **If the binary is missing, the app still works** using the
built-in `FallbackEngine` (weaker play, full UI).

Why `libstockfish.so` in jniLibs? Since Android 10, apps targeting API 29+ may
not execute files from the writable app-data dir. `jniLibs` with
`useLegacyPackaging = true` are extracted to `nativeLibraryDir`, which is
executable — the standard trick used by Termux & co.

## Build

```
cd android
gradle assembleRelease        # → app/build/outputs/apk/release/app-release.apk (signed)
gradle assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk (signed)
adb install -r app/build/outputs/apk/release/app-release.apk
```

Both build types are signed with the community keystore at
`android/release.keystore` (store/key password: `chesshelper`, alias
`chesshelper`). Replace it with your own keystore before a Play Store upload.

## CI

`workflows/build-apk.yml` builds both APKs on every push (branches
`main` and `arena/**`), verifies the signatures, and uploads the artifacts.
Pushing a `v*` tag additionally attaches the APKs to a GitHub release.

## Local SDK-less release pipeline (v2.1.2+)

The CI workflow above is the primary path. When no Android SDK is available,
the release APK can also be produced entirely from standalone tools — and after
v2.1.1's launch crash, this **must** use real tooling end to end (hand-rolled
dex/AXML/zip writers proved unsafe on Android 16 / One UI 8):

1. Compile Kotlin → class files (`kotlinc -jvm-target 1.8 -Xlambdas=class
   -Xsam-conversions=class -Xstring-concat=inline` — the flags keep Kotlin 2.x
   invokedynamic out of the bytecode).
2. Dex with AOSP `dx` (`--min-sdk-version=26`), then **round-trip the dex
   through apktool (baksmali → smali → dexlib2 assembler)** so the shipped dex
   is written by a modern, spec-perfect writer. The final dex must contain
   **zero `call_site_id` / `method_handle` map items** (verify with the map
   table) — dx-encoded invokedynamic is what crashed v2.1.0/v2.1.1 at startup.
3. Build the APK with **apktool** (its aapt2 writes the binary manifest) and
   **4-byte-align** every stored entry (`classes.dex` must start on an aligned
   offset or ART refuses to mmap it and the Application class never loads).
4. Sign with Google's **apksigner** (`--v1-signing-enabled false`, v2+v3) and
   finish with `apksigner verify --verbose` + an androguard package/label/main
   sanity check.

`android/tools/make_apk.py` remains as a reference/fallback packager but is not
used for shipped builds.

## Architecture notes

- **One engine interface** (`ChessEngine`): `StockfishEngine` (UCI subprocess)
  or `FallbackEngine` (negamax + alpha-beta in Kotlin). `EngineService` picks
  the best available and silently degrades if Stockfish fails at runtime.
- **Evaluation convention**: everything is *white point-of-view* (`TopMove`,
  `EngineAnalysis`); the overlay converts to the player's perspective.
- **UI is fully programmatic** (no XML layouts) — one less layer to break.
- The web reader posts JSON to `window.ChessHelper.onBoard(json)` (Android) and
  `window.webkit…fenBridge` (iOS) so the same `reader.js` serves both platforms.

## Security / fair play

See `README_SECURITY.md`. This helper is for analysis, review and study; using
engine assistance during live online games violates Chess.com's Terms of Service
and can get your account banned. The fair-play notice shows on first launch.
