# Chess Helper ♟

A chess study companion — originally built for iOS (SwiftUI), now fully ported
to **Android** as an installable **APK**. Analyze positions, review your
blunders, drill openings and track your puzzle progress, with a local Stockfish
19 engine running 100% on-device.

## The app

Five tabs (same on iOS and Android):

| Tab | What it does |
|-----|--------------|
| **Play** | Opens chess.com in an embedded browser and overlays a live helper panel: best move (SAN + UCI), eval, depth, win-chance bar, move-quality grade (!!, !, ?!, ?, ??), opening name + popular replies, "then plan" threat line and the principal variation. |
| **Review** | *Blunder Review* — type your chess.com username, pick a recent game, and every move is checked with the engine. Moves that lost ≥50cp are listed with severity and the best alternative. |
| **Openings** | *Opening Trainer* — 10 classic openings (Italian, Ruy López, QGD, London, Najdorf, Caro-Kann, KID, French, Scandinavian, English) with a quiz mode: guess the next move from memory. One tap opens the line on the chess.com analysis board. |
| **Puzzles** | *Puzzle Rating Tracker* — log every attempt (theme, rating, time, solved), see accuracy, per-tactic stats and history. |
| **Stats** | *Weakness Detector* — pulls your latest chess.com games and generates coaching insights (color imbalance, weak phase, time-class performance, early losses). |

Plus: dark theme, settings (engine depth / think time / MultiPV), and stealth
gestures (shake or 3-finger tap hides the overlay instantly).

## Download the APK

A prebuilt, signed APK ships in [`dist/`](dist/): **`ChessHelper-v2.1.0-release.apk`** — download and sideload it.

Further artifacts are produced by GitHub Actions on every push
(a ready-to-run workflow ships at [`workflows/build-apk.yml`](workflows/build-apk.yml) — copy it to `.github/workflows/` to activate CI):

- `ChessHelper-v2.1.0-release.apk` — signed release (recommended)
- `ChessHelper-v2.1.0-debug.apk` — signed debug build

Install with `adb install -r ChessHelper-v2.1.0-release.apk` or just open the
file on your phone. Full build instructions: [`android/BUILD-ANDROID.md`](android/BUILD-ANDROID.md).

## Engine

- **Stockfish 19** (official arm64/armv7 Android binaries) runs locally as a UCI
  subprocess — no network, no telemetry, ~3400 Elo.
- If the binary is missing on a device, a built-in pure-Kotlin engine
  (`FallbackEngine`) keeps everything functional.

## Repository layout

```
Sources/                 iOS app (SwiftUI) — the original IPA project
project.yml              XcodeGen spec for the iOS build
android/                 Android app (Kotlin) — complete port
  BUILD-ANDROID.md       Android build & architecture guide
  release.keystore       community signing keystore (password: chesshelper)
server.py                optional Flask analyze service (see README_SECURITY.md)
chess-helper.manifest.json  base64 manifest of the iOS project files
workflows/         CI: build-apk.yml (move to .github/workflows/ to activate)
dist/              Prebuilt signed APK (ChessHelper-v2.1.0-release.apk)
.github/workflows/ The repo-provided build-ipa.yml (iOS)
```

## Fair play ⚠️

Using engine assistance during live online games violates Chess.com's Terms of
Service and will get your account banned. Use this helper for post-game review,
openings study, puzzles and playing against bots. The app shows a fair-play
notice on first launch. See [`README_SECURITY.md`](README_SECURITY.md).
