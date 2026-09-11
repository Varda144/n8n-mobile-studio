#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSETS_DIR="$REPO_ROOT/android/app/src/main/assets/runtime"

echo "==> Package runtime payloads"

if [ -n "${RUNTIME_PAYLOAD_DIR:-}" ]; then
    if [ ! -d "$RUNTIME_PAYLOAD_DIR" ]; then
        echo "ERROR: RUNTIME_PAYLOAD_DIR is set to '$RUNTIME_PAYLOAD_DIR' but is not a directory." >&2
        exit 1
    fi
    echo "  Copying payloads from $RUNTIME_PAYLOAD_DIR into $ASSETS_DIR"
    cp -a "$RUNTIME_PAYLOAD_DIR"/. "$ASSETS_DIR/"
    echo "  Done."
else
    echo ""
    echo "  No RUNTIME_PAYLOAD_DIR set."
    echo ""
    echo "  To package runtimes, add the pre-built n8n and/or opencode payloads under:"
    echo "    $ASSETS_DIR/n8n/"
    echo "    $ASSETS_DIR/opencode/"
    echo ""
    echo "  Then re-run this script with RUNTIME_PAYLOAD_DIR=/path/to/payloads"
    echo "  or copy them directly into the assets directory above."
    echo ""
    echo "  Continuing with existing asset contents (may be empty placeholder .gitkeep files)."
fi

echo "==> Package step complete."
