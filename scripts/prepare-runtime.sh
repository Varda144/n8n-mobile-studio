#!/usr/bin/env bash
# Build the runtime payloads that get embedded in the APK.
#
# This is the script `.github/workflows/runtime-build.yml` runs before
# `assembleDebug`, and it is the only place payloads are produced. It builds:
#
#   1. the Node.js engine for Android  -> android/app/src/main/jniLibs/<abi>/libnode.so
#   2. the n8n JavaScript payload      -> android/app/src/main/assets/runtime/n8n/payload.zip
#   3. an OpenCode payload, if upstream can be built for Node (otherwise the
#      runtime stays honestly unpackaged, with the reason recorded)
#   4. assets/runtime/manifest.json describing exactly what exists
#
# Nothing here is committed: payloads and engines are build outputs (see
# .gitignore) because they are tens of megabytes of pinned third-party code.
#
# Environment knobs:
#   RUNTIME_ABIS="arm64-v8a x86_64"   ABIs to build (default arm64-v8a)
#   SKIP_NODE_BUILD=1                 reuse an existing jniLibs engine
#   SKIP_N8N=1 / SKIP_OPENCODE=1      skip one payload
#   NODE_VERSION, N8N_VERSION, OPENCODE_VERSION, ANDROID_API_LEVEL, ANDROID_NDK_VERSION
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

ABIS="${RUNTIME_ABIS:-arm64-v8a}"

summary() {
    local outcome="$1" detail="${2:-}"
    if [ -n "$detail" ]; then
        bash "$SCRIPTS_DIR/lib/ci-summary.sh" --outcome "$outcome" --detail "$detail" || true
    else
        bash "$SCRIPTS_DIR/lib/ci-summary.sh" --outcome "$outcome" || true
    fi
}

# Any failure below publishes what went wrong to the run summary before exiting, so
# the reason is readable in the Actions UI without fetching the job log.
fail() {
    summary failure "$1"
    die "$1"
}

info "runtime payload build"
info "  repo:   $REPO_ROOT"
info "  node:   $NODE_VERSION (android $ANDROID_API_LEVEL)"
info "  n8n:    $N8N_VERSION"
info "  opencode: $OPENCODE_VERSION"
info "  abis:   $ABIS"

# --------------------------------------------------------------- manifest sanity
MANIFEST="$MANIFEST" python3 - <<'PY' || die "existing manifest is not readable JSON"
import json, os
with open(os.environ["MANIFEST"]) as handle:
    data = json.load(handle)
assert data.get("schemaVersion"), "manifest has no schemaVersion"
PY
info "existing manifest parses"

# ------------------------------------------------------------------- 1. Node
if [ "${SKIP_NODE_BUILD:-0}" = "1" ]; then
    info "SKIP_NODE_BUILD=1: keeping the engine already in jniLibs"
    ls -1 "$JNI_LIBS" 2>/dev/null || warn "no jniLibs directory"
else
    # shellcheck disable=SC2086
    # shellcheck disable=SC2086
    bash "$SCRIPTS_DIR/build-node-android.sh" $ABIS \
        || fail "Node engine build failed — no payload will be published (see .runtime-build/logs/node-*.log)"
fi

# ------------------------------------------------------------------- 2. n8n
if [ "${SKIP_N8N:-0}" = "1" ]; then
    info "SKIP_N8N=1: n8n payload left as-is"
else
    N8N_ABI="$(echo "$ABIS" | awk '{print $1}')"
    bash "$SCRIPTS_DIR/build-n8n-payload.sh" "$N8N_ABI" \
        || fail "n8n payload build failed (see .runtime-build/logs/n8n-*.log)"
fi

# --------------------------------------------------------------- 3. OpenCode
if [ "${SKIP_OPENCODE:-0}" = "1" ]; then
    info "SKIP_OPENCODE=1: OpenCode payload left as-is"
else
    bash "$SCRIPTS_DIR/build-opencode-payload.sh" \
        || warn "OpenCode payload build did not produce a payload (recorded as NOT_PACKAGED)"
fi

# --------------------------------------------------------------- 4. manifest
info "regenerating the packaged runtime manifest"
python3 "$SCRIPTS_DIR/generate-runtime-manifest.py" || fail "manifest generation failed"

# ------------------------------------------------------------------ 5. verify
bash "$SCRIPTS_DIR/verify-runtime.sh" || fail "runtime verification failed: a runtime the build claims to embed is not really there"

summary success
info "runtime payload build finished"
