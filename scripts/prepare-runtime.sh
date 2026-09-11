#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "==> Runtime manifest check"

MANIFEST="$REPO_ROOT/android/app/src/main/assets/runtime/manifest.json"

if [ ! -f "$MANIFEST" ]; then
    echo "ERROR: manifest.json not found at $MANIFEST" >&2
    exit 1
fi

if command -v python3 >/dev/null 2>&1; then
    python3 -c "
import json, sys
m = json.load(open('$MANIFEST'))
for name, r in m.get('runtimes', {}).items():
    status = 'ENABLED' if r.get('enabled') else 'disabled'
    packaged = 'packaged' if r.get('packaged') else 'not-packaged'
    port = r.get('port', '?')
    print(f'  {name}: {status}, {packaged}, port {port}')
"
else
    echo "  (python3 not available — printing raw)"
    cat "$MANIFEST"
fi

echo ""
echo "==> Gradle wrapper check"

GRADLEW="$REPO_ROOT/android/gradlew"
WRAPPER_JAR="$REPO_ROOT/android/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLEW" ]; then
    echo "ERROR: gradlew not found at $GRADLEW" >&2
    exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "ERROR: gradle-wrapper.jar not found at $WRAPPER_JAR" >&2
    exit 1
fi

echo "  gradlew: OK"
echo "  gradle-wrapper.jar: OK"
echo ""
echo "==> All checks passed."
