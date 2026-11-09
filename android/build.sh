#!/usr/bin/env bash
# Quick build for S26 Ultra — helper vs exact chess clone, both signed.
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-helper-debug}"
# Modes:
#   helper-debug   → com.chesshelper.app.debug (green helper icon, debug key)
#   helper-release → com.chesshelper.app (green, SIGNED via release.keystore) — sideload ready
#   chess-debug    → com.chess.debug (exact Chess.com clone icon/name, debug key)
#   chess-release  → com.chess (exact clone, SIGNED via release.keystore) — LOOKS LIKE OFFICIAL
#   all-debug      → both debug
#   all-release    → both release (SIGNED)
#   real           → same as release but checks for libstockfish.so

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ] && ! command -v gradle >/dev/null 2>&1; then
  echo "⚠️  No gradle wrapper jar and no system gradle."
  echo "   Option A: Open this folder in Android Studio → Run (Studio generates wrapper)."
  echo "   Option B: gradle wrapper --gradle-version 8.10"
  exit 1
fi
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then GRADLE="./gradlew"; chmod +x ./gradlew || true; else GRADLE="gradle"; fi

case "$MODE" in
  helper-debug)  echo "🔨 helperDebug (Chess Helper, debug-signed)…"; $GRADLE :app:assembleHelperDebug ;;
  helper-release) echo "🔨 helperRelease (SIGNED via release.keystore)…"; $GRADLE :app:assembleHelperRelease; echo "✅ $(find app/build/outputs/apk/helper/release -name '*.apk' | head -n1)" ;;
  chess-debug)   echo "🔨 chessDebug (EXACT Chess clone, debug-signed)…"; $GRADLE :app:assembleChessDebug ;;
  chess-release) echo "🔨 chessRelease (EXACT clone, SIGNED) — looks like official Chess.com…"; $GRADLE :app:assembleChessRelease; echo "✅ $(find app/build/outputs/apk/chess/release -name '*.apk' | head -n1)" ;;
  all-debug)     echo "🔨 allDebug (both flavors)…"; $GRADLE assembleDebug ;;
  all-release)   echo "🔨 allRelease (BOTH SIGNED)…"; $GRADLE assembleRelease ;;
  real)          echo "🔨 allRelease + check Stockfish…"; [ -f app/src/main/jniLibs/arm64-v8a/libstockfish.so ] || echo "⚠️  No libstockfish.so → mock engine will be used. Place it at app/src/main/jniLibs/arm64-v8a/libstockfish.so for real strength."; $GRADLE assembleRelease ;;
  *) echo "Usage: $0 [helper-debug|helper-release|chess-debug|chess-release|all-debug|all-release|real]"; exit 1;;
esac

echo ""
echo "📱 Install on S26 Ultra (allow unknown apps first):"
echo "   adb install -r app/build/outputs/apk/<flavor>/**/*.apk"
echo "   Or copy APK to phone → tap → Install"
echo ""
echo "Flavors:"
echo "  helper → com.chesshelper.app — green 'Chess Helper' icon, obvious helper"
echo "  chess  → com.chess — EXACT clone icon/name 'Chess' (stealth). Shake or 3-finger to reveal overlay."
