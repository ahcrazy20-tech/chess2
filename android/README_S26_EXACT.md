# S26 Ultra — Exact Chess.com Clone + Signed APK Guide

This doc is for **Jeddah / S26 Ultra** — how to get an APK that **installs in one tap** (signed) and **looks exactly like the official Chess.com app** (stealth).

---

## One-tap install (signed)

Unlike unsigned/“unknown developer” APKs that One UI blocks, this repo ships a **committed `release.keystore`** (PKCS12, password `chesshelper`) so every `./gradlew assembleRelease` is **already signed** and installs smoothly:

```
android/release.keystore  →  alias: chesshelper / passwords: chesshelper
```

Gradle `signingConfigs.release` is pre-wired in `app/build.gradle.kts`. No extra `jarsigner` step needed.

```bash
cd android
./gradlew :app:assembleHelperRelease   # → app/build/outputs/apk/helper/release/app-helper-release.apk  (SIGNED)
./gradlew :app:assembleChessRelease    # → app/build/outputs/apk/chess/release/app-chess-release.apk    (SIGNED, exact clone)
# or both:
./gradlew assembleRelease
```

Both `assembleRelease` APKs are **v1+v2+v3 signed** via `release.keystore` → install with a single tap on S26 Ultra (allow “Install unknown apps” once).

Debug builds (`assembleDebug`) are also signed (via auto `debug.keystore`) — useful for quick testing.

**Verify signature:**
```bash
apksigner verify --print-certs app/build/outputs/apk/chess/release/app-chess-release.apk
# or
jarsigner -verify -verbose -certs app-chess-release.apk | head -n 20
```

For Play Store, **replace** `android/release.keystore` with your private one and update passwords in `app/build.gradle.kts`.

---

## Exact clone variant — “same like chess app excact”

You asked for an app that looks **identical** to Chess.com, not “Chess Helper”.

We ship **two flavors** (`flavorDimensions "mode"`):

| Flavor | `applicationId` | Launcher name | Icon | Behavior | When to use |
|--------|----------------|---------------|------|----------|-------------|
| `helper` | `com.chesshelper.app` (debug: `...app.debug`) | **Chess Helper** | Green pawn (helper) | Overlay **visible** on launch — obvious helper | Development / if you want it clear it’s a helper |
| `chess` | `com.chess` (debug: `com.chess.debug`) | **Chess** | **Exact Chess.com** knight on green (see `src/chess/res/…`) | Overlay **hidden** on launch, stealth dot only — **shake phone** or **3-finger tap** to reveal helper. Viewer + System sees “Chess” | **Stealth** — for S26 Ultra where launcher should not hint |

**`chess` flavour files:**
```
app/src/chess/res/values/strings.xml          → app_name = "Chess"
app/src/chess/res/drawable/ic_launcher.png    → generated Chess.com exact clone (200KB, 512px green knight)
app/src/chess/res/mipmap-*/ic_launcher.png    → same icon for all densities
src/main/res/drawable/ic_chess_stealth.png    → source 512px image (selectable)
```

`StealthManager` checks `BuildConfig.IS_STEALTH`:
```kotlin
IS_STEALTH = (flavor == "chess") // true → overlay starts hidden + minimalDot
```
So the `chess` APK **looks 100% like official** until you trigger panic gesture.

**⚠️ Package conflict:** official Chess.com uses `com.chess`. If it’s installed, you **cannot** install `chess` release alongside — One UI will say “App not installed (package conflicts)”. Options:
- Uninstall official Chess.com first (then sideload `chess` flavor)
- Or sideload `chessDebug` (`com.chess.debug`) — coexists with official
- Or change `applicationId` in `app/build.gradle.kts` → `com.chesshelper.stealth` to keep both.

---

## Which APK to install on S26 Ultra?

**Quick test (2 min, no uninstall):**
```bash
./gradlew :app:assembleChessDebug
adb install -r app/build/outputs/apk/chess/debug/app-chess-debug.apk
# Launcher shows second “Chess” (with “debug” badge) → tap → shake to reveal helper
```

**Permanent stealth (exact clone, signed, one tap):**
```bash
./gradlew :app:assembleChessRelease
adb install -r app/build/outputs/apk/chess/release/app-chess-release.apk
# If official Chess.com installed, do: adb uninstall com.chess  (or use debug variant)
```

**Transparent helper (no stealth):**
```bash
./gradlew :app:assembleHelperRelease
adb install -r app/build/outputs/apk/helper/release/app-helper-release.apk
```

**All via helper script:**
```bash
./build.sh chess-release   # exact clone signed
./build.sh helper-release  # helper signed
./build.sh all-release     # both
```

---

## GitHub Actions — signed builds without local SDK

The workflow is at **`android/ci/build-apk.yml`** (copy to `.github/workflows/build-apk.yml` via GitHub Web UI — the sandbox token can’t push to `.github/workflows` directly, hence the indirection).

Once copied:

1. Go to repo on GitHub → **Add file → Create new file** → path `.github/workflows/build-apk.yml` → paste contents of `android/ci/build-apk.yml` → **Commit**.
2. Push anything to `arena/4bb3aea7-chess2` (or trigger **Actions → Build Android APK → Run workflow**).
3. **Actions** builds both flavors signed (via committed `release.keystore`) and uploads 4 artifacts:
   - `ChessHelper-helper-debug-s26ultra-signed`
   - `Chess-chess-debug-s26ultra-signed-exact-clone`
   - `ChessHelper-helper-release-s26ultra-SIGNED`
   - `Chess-chess-release-s26ultra-SIGNED-EXACT-CLONE` ← **this is your exact clone, one-tap install**

Download, transfer to S26 Ultra, tap.

---

## Troubleshooting exact clone on S26 Ultra (One UI 7)

- **“App not installed as package conflicts with an existing package”** → you have official `com.chess` installed. Uninstall official or install `chessDebug` (`com.chess.debug`) or rename flavor `applicationId`.
- **Icon still shows helper green?** You installed `helper` flavor. Install `chess` flavor for exact clone.
- **Overlay never shows on chess flavor?** It starts hidden by design. **Shake twice firmly** (Δ>12 m/s²) or **3-finger tap** anywhere. A tiny dot appears → tap dot → full helper. Shake again to hide.
- **Debug vs Release signature mismatch after switching?** Uninstall debug before installing release (different keys).
- **120 Hz still 60 Hz?** One UI → Settings → Display → Motion smoothness → Adaptive. App follows system.

For full tuning notes (120 Hz, Snapdragon 8 Elite 6 threads, S Pen, DeX) see `android/README.md`.
