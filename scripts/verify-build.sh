#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "==> Check prerequisites"

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java is not installed or not on PATH." >&2
    exit 1
fi

echo "  java: $(java -version 2>&1 | head -1)"

GRADLEW="$REPO_ROOT/android/gradlew"
if [ ! -f "$GRADLEW" ]; then
    echo "ERROR: gradlew not found at $GRADLEW" >&2
    exit 1
fi

echo ""
echo "==> Running assembleDebug"
chmod +x "$GRADLEW"
(cd "$REPO_ROOT/android" && ./gradlew assembleDebug --no-daemon)

APK_DIR="$REPO_ROOT/android/app/build/outputs/apk/debug"
if [ -d "$APK_DIR" ]; then
    APK=$(find "$APK_DIR" -name '*.apk' | head -1)
    if [ -n "$APK" ]; then
        echo ""
        echo "==> APK produced: $APK"
    else
        echo ""
        echo "WARNING: APK directory exists but no .apk files found inside." >&2
    fi
else
    echo ""
    echo "WARNING: APK output directory not found at $APK_DIR" >&2
fi

echo "==> Build verification complete."
