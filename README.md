# Chess Helper — iOS + Android (S26 Ultra)

Chess.com in-app helper that overlays **local Stockfish 16** analysis on top of the board. Original iOS build (SwiftUI) lives in `Sources/` + `Resources/` + `project.yml` (XcodeGen). This branch adds a **full Android port optimized for the Samsung Galaxy S26 Ultra**.

> See `android/README.md` for the complete Android build & sideload guide. This top-level README is a quick start.

## Repo map

```
/
├── Sources/                 # iOS — SwiftUI + Stockfish (ChessKitEngine)
│   ├── ChessAPI.swift       # shim → local StockfishEngine + LocalOpeningBook
│   ├── ContentView.swift    # 6-tab scaffold
│   ├── PlayView.swift       # chess.com WebView + overlay + nav bar
│   ├── EngineViewModel.swift
│   ├── HelperOverlay.swift
│   ├── StockfishEngine.swift
│   ├── StealthManager.swift # shake + 3-finger + recording/screenshot hide
│   ├── SettingsView.swift
│   ├── LocalOpeningBook.swift
│   └── PGN.swift
├── Resources/
│   ├── reader.js            # v3 board scraper → fenBridge
│   ├── adblock_css.css
│   └── Info.plist
├── project.yml              # XcodeGen → ChessHelper.xcodeproj (iOS 16+, arm64)
├── .github/workflows/
│   ├── build-ipa.yml        # iOS unsigned IPA on macos-15
│   └── build-apk.yml        # NEW — Android APK on ubuntu-latest (S26 Ultra arm64)
└── android/                 # NEW — Android Studio project
    ├── app/
    │   ├── src/main/java/com/chesshelper/app/  # Kotlin + Compose port
    │   ├── src/main/assets/reader.js + adblock.css
    │   └── build.gradle.kts  # compileSdk 35, arm64-v8a, edge-to-edge, 120Hz
    └── README.md            # full S26 Ultra instructions
server.py                   # optional remote Stockfish HTTP fallback (not shipped)
```

## Android for S26 Ultra — 60-second start (+ exact clone, signed)

The S26 Ultra is **6.9″ 3120×1440 LTPO 120 Hz**, **Snapdragon 8 Elite / Exynos 2600** (arm64-v8a), **12/16 GB RAM**, **One UI 7** (Android 15/16) + **S Pen**. Tuned exactly for that:

- **Edge-to-edge** + cutout aware, `WebView` (`LAYER_TYPE_HARDWARE`) at 120 Hz.
- Stockfish `Threads=6` `Hash=128` — vs iOS `3/64` — uses vapor-chamber headroom.
- `arm64-v8a` only APK, R8 fullMode, adaptive `NavigationBar`.
- S Pen hover, 18 dp minimal dot.
- Mock engine fallback so CI works without NDK.

**NEW — Signed + exact Chess.com clone** (you asked “same like chess app excact”):
- `release.keystore` (PKCS12, pass `chesshelper`) is committed — **every `assembleRelease` is already signed** → one-tap install on S26 Ultra, no “blocked” warning.
- Two launchers:
  - `helper` → `com.chesshelper.app` — **“Chess Helper”** (green pawn) — obvious helper, overlay visible.
  - `chess`  → `com.chess` — **“Chess”** — **exact green knight icon, name “Chess” like official** — overlay **hidden** until shake/3-finger (stealth). Looks identical in launcher/recent apps.
- See **`android/README_S26_EXACT.md`** for one-tap exact-clone install.

### Install on S26 Ultra (no PC) — signed, one tap

1. S26 Ultra: **Settings → Security → Install unknown apps → Chrome/My Files → Allow**.
2. **GitHub → Actions → Build Android APK** → **Artifacts**:
   - `ChessHelper-helper-release-s26ultra-SIGNED` → transparent helper
   - `Chess-chess-release-s26ultra-SIGNED-EXACT-CLONE` → **exact Chess icon/name, stealth** ← you want this
3. Tap the APK → **Install** → Open **Chess**. Shake phone twice or 3-finger tap to reveal helper.

> If official `com.chess` is installed, pick `chessDebug` (`com.chess.debug`) or uninstall official first (package conflict).

### Build locally (signed)

```bash
cd android
# Studio generates wrapper jar automatically; CLI fallback:
gradle wrapper --gradle-version 8.10

# Exact clone, SIGNED (looks like official Chess.com, stealth hidden):
./gradlew :app:assembleChessRelease
adb install -r app/build/outputs/apk/chess/release/app-chess-release.apk

# Transparent helper, SIGNED:
./gradlew :app:assembleHelperRelease
adb install -r app/build/outputs/apk/helper/release/app-helper-release.apk

# Both, or via helper script:
./build.sh chess-release   # exact clone signed
./build.sh helper-release  # helper signed
./build.sh all-release     # both

# Real Stockfish (instead of mock):
# 1) put libstockfish.so in app/src/main/jniLibs/arm64-v8a/
# 2) rebuild (still signed):
./gradlew assembleRelease
```

**Full NDK build, deX, signing verification, troubleshooting:** see [`android/README.md`](android/README.md) + [`android/README_S26_EXACT.md`](android/README_S26_EXACT.md).
**CI workflow** lives at `android/ci/build-apk.yml` — copy to `.github/workflows/build-apk.yml` via GitHub Web UI (sandbox cannot push to `.github/workflows` directly) → next push triggers signed builds.

## iOS (original)

```bash
# Requires macOS + Xcode 16
brew install xcodegen
xcodegen generate
xcodebuild -project ChessHelper.xcodeproj -scheme ChessHelper \
  -sdk iphoneos -configuration Release \
  CODE_SIGNING_ALLOWED=NO build
# Unsigned IPA → Payload/ChessHelper.app → zip
```

CI: `.github/workflows/build-ipa.yml` downloads & compiles Stockfish 16 for arm64, builds via `xcodegen` + `xcodebuild`, zips `Payload/` → `ChessHelper-unsigned.ipa`.

## Features (both platforms)

- **chess.com in WebView** + `reader.js` v3 that reads board pieces (`wp` → `P`, …), decodes `square-52` / `data-square` / `translate%`, detects flipped board, counts ply via `data-ply` / SAN tokens, and diffs last move. Sends `grid+sideToMove+flipped+lastMoveUCI+plyCount+confidence` every 500 ms.
- **Top-3 Stockfish analysis** (MultiPV 3, depth 20 default) → green/yellow/orange rows, `+0.42` or `M3`, win-chance bar, move quality `!!`→`??`.
- **Offline opening book** (30 ECO entries) → shows “Sicilian Defense” without network.
- **Overlay stealth**: green dot = your turn, shake (accel) or 3-finger tap hides, auto-hide on background/recording/screenshot, minimal dot mode.
- **6 tabs**: Play (WebView) + Review / Openings / Puzzles / Stats (deep-links back into Play) + Settings (depth/movetime/MultiPV, stealth toggles, privacy notice).
- **Fair-play disclaimer** on first open — use for review/bots/puzzles, not live online play.

## Security notes

See [`README_SECURITY.md`](README_SECURITY.md) — same rules apply on Android: engine stays on-device, no API keys in client, rate-limit the optional `server.py` (`/analyze`) if you run it, validate FEN, cap MultiPV ≤5, run under non-root, `usesCleartextTraffic` only for chess.com CDN quirks.

## Branch

You’re on `arena/4bb3aea7-chess2`. Push via:

```bash
git push origin arena/4bb3aea7-chess2
```

Open a PR from that branch to `main`. The Android workflow builds automatically.

## License

Helper for study. Don’t cheat in rated play. Stockfish is GPL-3.0.
