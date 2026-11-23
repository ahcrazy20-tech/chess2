# Chess Helper

On-device chess analysis helper — a local Stockfish engine with a top-moves overlay,
an offline opening book, PGN reading and stealth gestures (shake / background /
screen-capture auto-hide).

Two implementations live in this repo:

| Folder | What it is | Build |
| --- | --- | --- |
| `Sources/`, `project.yml` | iOS/SwiftUI app (XcodeGen) | `.github/workflows/build-ipa.yml` (unsigned IPA, macOS runner) |
| `android/` | Android/Kotlin port (arm64-v8a, S26 Ultra / Android 16) | `.github/workflows/build-apk.yml` (signed APKs) |

## Getting the Android APK

1. Open the **Actions** tab → **Build Android APK (signed, arm64-v8a)** → newest run.
2. Download the **chesshelper-apks** artifact and unzip it.
3. `ChessHelper-<version>-release-signed.apk` is the one to install on the phone
   (enable "install unknown apps" for your file manager / browser first).

The workflow bundles the official Stockfish ARM64 binary from the
`official-stockfish/Stockfish` GitHub release into the APK as
`lib/arm64-v8a/libstockfish.so`, so the engine works offline with no extra files.

> **Note about the workflow file.** GitHub only runs workflows in `.github/workflows/`.
> The canonical copy currently lives in [`ci/build-apk.yml`](ci/build-apk.yml) because
> the automation used to write this repo has no `workflows` permission. Copy it to
> `.github/workflows/build-apk.yml` once (web UI: *Add file → Create new file*, paste,
> commit) and CI takes over from there.

## Signing

`android/keystore/chesshelper-release.p12` is a **throw-away test key**
(alias `chesshelper`, store/key password `chesshelper`, PKCS#12). It only exists so CI can
produce an installable, updatable APK. Replace it with your own key before publishing
the app anywhere — anyone with this file can sign an "update" for it.

## Local build

```bash
cd android
# needs JDK 17 + Android SDK platform 35 / build-tools 35.0.0
gradle assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease        # signed with the test key above
```

Details in [`android/BUILD-ANDROID.md`](android/BUILD-ANDROID.md).

## Fair play

Engine assistance during live online games violates Chess.com's Terms of Service and can
get your account banned — the app shows this disclaimer on first launch. See
[`README_SECURITY.md`](README_SECURITY.md). Use it for analysis/review, not for live play.
