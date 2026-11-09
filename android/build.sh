#!/usr/bin/env bash
# Quick build for S26 Ultra — works on macOS/Linux with Android SDK + JDK 17.
# Usage:
#   ./build.sh            # debug mock (no NDK needed)
#   ./build.sh release    # unsigned release (mock)
#   ./build.sh real       # release with bundled libstockfish.so if present
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-debug}"

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ] && ! command -v gradle >/dev/null 2>&1; then
  echo "⚠️  No gradle wrapper jar and no system gradle. Install Android Studio or run:"
  echo "    gradle wrapper --gradle-version 8.10"
  echo "Or open this folder in Android Studio and hit Run — Studio will generate the wrapper."
  exit 1
fi

if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  GRADLE="./gradlew"
  chmod +x ./gradlew || true
else
  GRADLE="gradle"
fi

case "$MODE" in
  debug)
    echo "🔨 Building DEBUG (mock engine — works without NDK) for S26 Ultra..."
    $GRADLE assembleDebug
    echo "✅ APK: app/build/outputs/apk/debug/app-debug.apk"
    ;;
  release)
    echo "🔨 Building RELEASE unsigned (mock)..."
    $GRADLE assembleRelease
    echo "✅ APK: app/build/outputs/apk/release/app-release-unsigned.apk"
    ;;
  real)
    echo "🔨 Building RELEASE with real Stockfish (expects app/src/main/jniLibs/arm64-v8a/libstockfish.so)..."
    if [ ! -f "app/src/main/jniLibs/arm64-v8a/libstockfish.so" ]; then
      echo "⚠️  libstockfish.so not found — falling back to mock but still building."
      echo "   To bundle real engine, put the .so at:"
      echo "   app/src/main/jniLibs/arm64-v8a/libstockfish.so"
    fi
    $GRADLE assembleRelease
    echo "✅ APK: app/build/outputs/apk/release/app-release-unsigned.apk"
    unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep -i stockfish || echo "(mock — no stockfish bundled)"
    ;;
  *)
    echo "Usage: $0 [debug|release|real]"
    exit 1
    ;;
esac

echo ""
echo "📱 Install on S26 Ultra:"
echo "   adb install -r app/build/outputs/apk/${MODE}/app-*.apk"
echo "   Or transfer APK to phone and tap to sideload (allow unknown apps)."
