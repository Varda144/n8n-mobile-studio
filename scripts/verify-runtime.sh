#!/usr/bin/env bash
# Verify that what the APK claims to embed actually exists, and that it is what
# the app expects. Run by scripts/prepare-runtime.sh and by the release workflow.
#
# This is the honesty gate of the project:
#   * `packaged: true` requires the payload archive (matching digest, declared
#     entry point inside it) and, for Node, a real engine binary for every
#     declared ABI;
#   * `packaged: false` requires that no stale payload is present, so a build can
#     never ship a payload the manifest does not describe;
#   * the pins in RuntimePins.kt (what the Kotlin code targets) must match the
#     manifest (what was built).
#
# Set ALLOW_UNPACKAGED=1 to report unpackaged runtimes as a warning instead of a
# failure (used by the debug build, which is allowed to ship without payloads).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

[ -f "$MANIFEST" ] || die "manifest not found at $MANIFEST"

REPORT="$LOG_DIR/runtime-verify.txt"
info "verifying packaged runtimes ($(basename "$MANIFEST"))"

if python3 "$SCRIPT_DIR/lib/verify_manifest.py" "$REPO_ROOT" "$MANIFEST" | tee "$REPORT"; then
    exit 0
fi

if [ "${ALLOW_UNPACKAGED:-0}" = "1" ]; then
    warn "runtime verification reported problems; continuing because ALLOW_UNPACKAGED=1"
    grep -E '^(FAIL|  )' "$REPORT" | head -20 || true
    exit 0
fi

die "runtime verification failed (see the annotations above)"
