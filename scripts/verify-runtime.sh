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
    # The manifest may be truthful and still describe an APK that embeds nothing.
    # Release builds say which runtimes they require for real:
    #   REQUIRE_PACKAGED="node n8n"                    (default)
    #   REQUIRE_PACKAGED="node n8n opencode"           once OpenCode is buildable
    #   REQUIRE_PACKAGED=""                            nothing required (debug)
    REQUIRED="${REQUIRE_PACKAGED-node n8n}"
    missing=""
    for component in $REQUIRED; do
        packaged="$(python3 - "$MANIFEST" "$component" <<'PYX'
import json, sys
manifest = json.load(open(sys.argv[1]))
component = sys.argv[2]
if component == "node":
    print("true" if (manifest.get("node") or {}).get("packaged") else "false")
else:
    print("true" if ((manifest.get("runtimes") or {}).get(component) or {}).get("packaged") else "false")
PYX
)"
        [ "$packaged" = "true" ] || missing="$missing $component"
    done
    if [ -z "$missing" ]; then
        ok "all required runtimes are packaged"
        exit 0
    fi
    if [ "${ALLOW_UNPACKAGED:-0}" = "1" ]; then
        warn "not packaged:$missing (allowed because ALLOW_UNPACKAGED=1)"
        exit 0
    fi
    annotate_error "required runtimes are not packaged:$missing"
    die "required runtimes are not packaged:$missing — this APK would ship without them"
fi

if [ "${ALLOW_UNPACKAGED:-0}" = "1" ]; then
    warn "runtime verification reported problems; continuing because ALLOW_UNPACKAGED=1"
    grep -E '^(FAIL|  )' "$REPORT" | head -20 || true
    exit 0
fi

die "runtime verification failed (see the annotations above)"
