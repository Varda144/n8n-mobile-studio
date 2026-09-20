#!/usr/bin/env bash
#
# Publish a short report of the payload build to the Actions run summary.
#
# Why this exists: the raw job log is large and is downloaded through a host that
# is not reachable from every environment this project is worked from (a phone).
# The step summary, by contrast, is rendered in the run's own page, so the facts a
# person needs — what was built, what it weighs, what failed and which log line
# explains it — are visible without fetching anything.
#
# Used by scripts/prepare-runtime.sh. Safe to run outside CI (writes nothing).
#
# Usage:
#   scripts/lib/ci-summary.sh [--outcome success|failure] [--detail "text"]
set -euo pipefail

outcome="success"
detail=""
while [ $# -gt 0 ]; do
    case "$1" in
        --outcome) outcome="${2:-success}"; shift 2 ;;
        --detail) detail="${2:-}"; shift 2 ;;
        *) shift ;;
    esac
done

SUMMARY="${GITHUB_STEP_SUMMARY:-}"
[ -n "$SUMMARY" ] || exit 0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MANIFEST="$REPO_ROOT/android/app/src/main/assets/runtime/manifest.json"
BUILD_DIR="$REPO_ROOT/.runtime-build"
LOG_DIR="$BUILD_DIR/logs"

{
    printf '## Runtime payload build: %s\n\n' "$outcome"

    if [ "$outcome" = "failure" ] && [ -n "$detail" ]; then
        printf '```\n%s\n```\n\n' "$detail"
    fi

    # What the build produced, straight from the manifest the app will read.
    if [ -f "$MANIFEST" ]; then
        python3 - "$MANIFEST" <<'PY'
import json, pathlib, sys

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text())
node = manifest.get("node") or {}
print(f"manifest schema {manifest.get('schemaVersion')}, built {manifest.get('builtAt', 'unknown')}")
print()
print("| runtime | packaged | version | size |")
print("| --- | --- | --- | --- |")
for abi, payload in sorted((node.get("abis") or {}).items()):
    size = payload.get("sizeBytes", 0) / (1024 * 1024)
    print(f"| node engine ({abi}) | {'yes' if node.get('packaged') else 'no'} | {node.get('version', '')} | {size:.1f} MiB |")
for name, entry in sorted((manifest.get("runtimes") or {}).items()):
    payload = entry.get("payload") or {}
    size = payload.get("sizeBytes", 0) / (1024 * 1024)
    print(f"| {name} | {'yes' if entry.get('packaged') else 'no'} | {entry.get('version', '')} | {size:.1f} MiB |")
print()
for note in manifest.get("notes", []):
    print(f"- {note}")
PY
    else
        printf 'No runtime manifest was produced.\n'
    fi

    # When something failed, the interesting lines of the logs that were touched.
    if [ "$outcome" = "failure" ] && [ -d "$LOG_DIR" ]; then
        printf '\n### Log tails\n\n'
        for log in "$LOG_DIR"/*.log; do
            [ -f "$log" ] || continue
            printf '**%s**\n\n```\n%s\n```\n\n' \
                "$(basename "$log")" \
                "$(grep -vE '^[[:space:]]*$' "$log" | tail -n 12)"
        done
    fi
} >>"$SUMMARY"
