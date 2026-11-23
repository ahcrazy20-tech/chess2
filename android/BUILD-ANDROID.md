# Chess Helper — Android port (build notes)

Android port of the iOS `ChessHelper` app. It runs Stockfish locally on-device, shows a
top-moves overlay while you play on chess.com, and includes stealth gestures (shake to hide,
screen-capture/background auto-hide, minimal mode), an offline opening book, and PGN reading.

## Target device

Samsung Galaxy S26 Ultra → **Android 16**, **arm64-v8a** only. `minSdk 24`, `targetSdk 35`
(target 35 keeps the build on the current stable toolchain and installs fine on Android 16).

## Build in CI (recommended)

Workflow: [`ci/build-apk.yml`](../ci/build-apk.yml) → copy to `.github/workflows/build-apk.yml`.
It runs on every push to `main` / `arena/**`, on tags `v*`, and on manual dispatch, and it
produces two artifacts in `dist/`:

- `ChessHelper-<version>-release-signed.apk` ← install this one
- `ChessHelper-<version>-debug.apk` ← for `adb logcat` debugging (package id `…​.debug`)

The job installs JDK 17, Android SDK `platforms;android-35` + `build-tools;35.0.0`,
Gradle 8.9, downloads the official Stockfish ARM64 binary from the
`official-stockfish/Stockfish` GitHub release and drops it in
`app/src/main/jniLibs/arm64-v8a/libstockfish.so`, then runs
`gradle assembleDebug assembleRelease`.

## Build locally

```bash
cd android
gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease      # app/build/outputs/apk/release/app-release.apk (signed)
```

Prerequisites: JDK 17, Android SDK `platforms;android-35`, `build-tools;35.0.0`
(`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`).
No NDK is required — Stockfish is not compiled here.

## Stockfish binary

The engine ships as a native library so it can actually be executed:

```
android/app/src/main/jniLibs/arm64-v8a/libstockfish.so
```

That file is the `stockfish` binary from
<https://github.com/official-stockfish/Stockfish/releases> →
`stockfish-android-arm64-universal.tar.gz`. Naming it `lib*.so` and placing it in `jniLibs`
makes Android extract it to the app's `nativeLibraryDir`, which is the only place an app may
`exec` a bundled binary on Android 10+ (`jniLibs.useLegacyPackaging = true` in
`app/build.gradle` is what forces the extraction). Do not put it in `assets/` — exec from the
app data dir is blocked by SELinux since Android 10.

`StockfishEngine.init()` looks for the native lib first and only then falls back to
`assets/stockfish`, so a locally dropped asset still works for experiments.

## Signing

`android/keystore/chesshelper-release.p12` — PKCS#12, alias / password `chesshelper`,
self-signed, valid ~30 years. It is a **test key** committed on purpose so CI output is
installable and updatable (same signature across runs). Replace it with your own keystore
before distributing, and change the `signingConfigs.release` block in `app/build.gradle`.

## Configuration highlights (`android/app/build.gradle`)

- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`
- `ndk { abiFilters.add("arm64-v8a") }` — S26 Ultra is ARM64 only
- `versionCode = 21`, `versionName = "2.0.1"`
- AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.9, `android.useAndroidX=true`

## What's ported vs. what's a stub

Ported from the iOS app:
- `EngineViewModel` — debounced board ingestion, Stockfish analysis, move quality
  (brilliant/best/good/inaccuracy/mistake/blunder), centipawn loss, opening lookup.
- `StockfishEngine` — UCI process wrapper (threads=3, hash=64, multiPV configurable).
- `OpeningBook` — offline ECO book (same entries as `LocalOpeningBook.swift`).
- `PgnReader` — PGN move extraction.
- `StealthManager` — shake detection, backgrounding auto-hide, screen-capture hook.
- `HelperOverlay` equivalent — top-moves panel + panic button in `activity_main.xml`.
- `SettingsActivity` — depth/movetime/multiPV + stealth toggles (SharedPreferences).
- `ChessWebView` — loads chess.com, JS bridge reports the board.

Not yet implemented (stubs / placeholders): Review, Openings, Puzzles, Stats tabs;
SAN→UCI conversion (SAN falls back to UCI); real chess.com DOM scraper (the injected
`injectBoardReader()` currently reports the start position); screenshot auto-hide below
Android 14.

## Backend analyze service (optional)

`server.py` is a Flask + python-chess + Stockfish `/analyze` HTTP service (see
`README_SECURITY.md`). The Android app does not need it — it uses its own local engine.

## Security / fair play

See `README_SECURITY.md`. This helper is for analysis/review; using engine assistance during
live online games violates Chess.com's Terms of Service and can get your account banned.
