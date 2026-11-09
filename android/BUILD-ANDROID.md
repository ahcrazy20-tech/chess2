# Chess Helper — Android port (build notes)

This is an Android port of the iOS `ChessHelper` app in this repo (SwiftUI/Tuist). It is a
chess helper that runs Stockfish locally on-device, shows a top-moves overlay while you play
on chess.com, and includes stealth gestures (shake to hide, screen-capture/background auto-hide,
minimal mode), an offline opening book, and PGN reading.

## Target device

Samsung Galaxy S26 Ultra → **Android 16 (API level 36)**, **arm64-v8a** only.

## Prerequisites (install on a machine with internet to Google's SDK mirrors)

- Android Studio (latest) or the Android command-line tools
- JDK 17
- SDK platforms: `platforms;android-36`  (compileSdk / targetSdk = 36)
- SDK build-tools: `build-tools;36.0.0` (or latest 36.x)
- NDK: `ndk;27.x` or newer (arm64-v8a)
- `sdkmanager`/`avdmanager` from `cmdline-tools`

Example:
```
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" \
           "ndk;27.0.12077973" "cmdline-tools;latest"
```

## Critical manual step — Stockfish binary (cannot be bundled here)

The sandbox that produced this scaffold cannot reach Google's release storage, so the
Stockfish arm64 binary is NOT included. You must add it yourself before building:

1. Download the official Stockfish Android arm64 binary from GitHub releases:
   https://github.com/official-stockfish/Stockfish/releases
   Asset: `stockfish-android-arm64-universal.tar.gz`
2. Extract the `stockfish` binary inside.
3. Place it at:
   `android/app/src/main/assets/stockfish`
4. Make sure it is executable (it will be `setExecutable(true)` at install time by the app).

`StockfishEngine.init(Context)` copies `assets/stockfish` into the app's private files dir
and launches it over UCI — exactly like the iOS version (Stockfish 19 here; the iOS app used
Stockfish 16 via ChessKitEngine).

## Build

```
cd android
# (first time) generate a Gradle wrapper if you don't have gradle installed
gradle wrapper --gradle-version 8.4

# Debug build (installs to a connected S26 Ultra / emulator via adb)
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release build
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
# Sign it with your release keystore before Play Store upload.
```

## Configuration highlights (`android/app/build.gradle`)

- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`
- `ndk { abiFilters.add("arm64-v8a") }` — S26 Ultra is ARM64 only
- `versionCode = 20`, `versionName = "2.0.0"` (matches the iOS app)
- Release build type signs with `signingConfigs.debug` — replace with your release
  signingConfig for Play Store.

## What's ported vs. what's a stub

Ported from the iOS app:
- `EngineViewModel` — debounced board ingestion, Stockfish analysis, move quality
  (brilliant/best/good/inaccuracy/mistake/blunder), centipawn loss, opening lookup.
- `StockfishEngine` — UCI process wrapper (threads=3, hash=64, multiPV=3).
- `OpeningBook` — offline ECO book (same entries as `LocalOpeningBook.swift`).
- `PgnReader` — PGN move extraction (board stepping deferred to Stockfish FEN commands).
- `StealthManager` — shake detection, backgrounding auto-hide, screen-capture hook.
- `HelperOverlay` equivalent — top-moves panel + minimal dot + panic button.
- `Settings` — depth/movetime/multiPV + stealth toggles (SharedPreferences).
- `ChessWebView` — loads chess.com, JS bridge reports the board.

Not yet implemented (stubs / placeholders): Review, Openings, Puzzles, Stats tabs; SAN→UCI
conversion (san falls back to UCI); screenshot auto-hide below Android 14 (no public
broadcast — use FLAG_SECURE / ScreenCaptureDetector on API 34+).

## Backend analyze service (optional)

The repo also contains `server.py`, a Flask + python-chess + Stockfish analyze HTTP server
(`POST /analyze` with a FEN). It is the server-side counterpart mentioned in
`README_SECURITY.md`. It needs a Stockfish binary on the host (`STOCKFISH_PATH`) and is not
required by the Android app (the app uses its own local engine).

Run it (on a host that has stockfish installed):
```
pip install python-chess flask
STOCKFISH_PATH=/usr/bin/stockfish python server.py
```

## Security / fair play

See `README_SECURITY.md`. This helper is for analysis/review; using engine assistance during
live online games violates Chess.com's Terms of Service and can get your account banned.
Stealth mode is on by default; use responsibly.
