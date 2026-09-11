#!/usr/bin/env bash
#
# Self-test for the honesty gate of the runtime payload pipeline.
#
# It fabricates payload artifacts (a fake n8n zip, a fake Node engine), proves
# that the manifest generator and scripts/verify-runtime.sh accept them, then
# breaks each claim in turn and proves the verifier rejects it:
#
#   1. payload archive deleted          -> rejected
#   2. one byte appended to the archive -> digest + size mismatch -> rejected
#   3. entry point missing from the zip -> rejected
#   4. digest removed from the manifest -> rejected
#   5. engine library deleted           -> rejected
#   6. untouched artifacts              -> accepted
#
# The point: no APK can claim an embedded runtime that is not really in it. This
# runs in seconds with no NDK, no npm, and no network, so CI can run it on every
# push; scripts/prepare-runtime.sh runs the same verifier against real payloads.
#
# Usage: scripts/selftest-runtime-gate.sh [--keep]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

KEEP=0
[ "${1:-}" = "--keep" ] && KEEP=1

TMP="$BUILD_DIR/gate-selftest"
rm -rf "$TMP"
mkdir -p "$TMP"

# Keep the repository's real artifacts out of the way for the duration of the run.
STASH="$TMP/stash"
mkdir -p "$STASH/assets/runtime/n8n" "$STASH/jniLibs"
[ -f "$MANIFEST" ] && cp "$MANIFEST" "$STASH/manifest.json"
for candidate in "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME" "$BUILD_DIR/n8n/meta.json" "$BUILD_DIR/node/node.json"; do
    [ -e "$candidate" ] && cp -a "$candidate" "$STASH/" 2>/dev/null || true
done
if [ -d "$JNI_DIR/arm64-v8a" ]; then cp -a "$JNI_DIR/arm64-v8a" "$STASH/jniLibs/" 2>/dev/null || true; fi

cleanup() {
    # Restore whatever was there before, so a self-test never leaves fake payloads
    # behind for a real build to pick up.
    rm -rf "$ASSETS_RUNTIME_DIR/n8n" "$JNI_DIR/arm64-v8a" "$BUILD_DIR/n8n" "$BUILD_DIR/node"
    if [ -f "$STASH/manifest.json" ]; then cp "$STASH/manifest.json" "$MANIFEST"; else rm -f "$MANIFEST"; fi
    [ -f "$STASH/meta.json" ] && mkdir -p "$BUILD_DIR/n8n" && cp "$STASH/meta.json" "$BUILD_DIR/n8n/meta.json"
    [ -f "$STASH/node.json" ] && mkdir -p "$BUILD_DIR/node" && cp "$STASH/node.json" "$BUILD_DIR/node/node.json"
    [ -d "$STASH/jniLibs/arm64-v8a" ] && mkdir -p "$JNI_DIR" && cp -a "$STASH/jniLibs/arm64-v8a" "$JNI_DIR/"
    [ "$KEEP" = "1" ] || rm -rf "$TMP"
}
trap cleanup EXIT

PASS=0
FAIL=0
expect_pass() { if "$@" >"$TMP/last.log" 2>&1; then PASS=$((PASS + 1)); ok "$1: accepted as expected"; else FAIL=$((FAIL + 1)); warn "$1: unexpectedly rejected"; sed -n '1,20p' "$TMP/last.log" >&2; fi; }
expect_fail() { if "$@" >"$TMP/last.log" 2>&1; then FAIL=$((FAIL + 1)); warn "$1: NOT rejected — the gate is open"; else PASS=$((PASS + 1)); ok "$1: rejected as expected"; fi; }

# ---------------------------------------------------------------------------
# Fabricate a payload: `n8n/bin/n8n` inside a zip, plus a 6 MiB "engine".
# ---------------------------------------------------------------------------
mkdir -p "$ASSETS_RUNTIME_DIR/n8n" "$BUILD_DIR/n8n" "$BUILD_DIR/node" "$JNI_DIR/arm64-v8a"
python3 - "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME" "$JNI_DIR/arm64-v8a/libnode.so" <<'PY'
import pathlib, sys, zipfile
archive, engine = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("n8n/bin/n8n", "#!/usr/bin/env node\nconsole.log('fake n8n')\n")
    zf.writestr("n8n/package.json", '{"name":"n8n","version":"0.0.0-selftest"}\n')
needle = b"selftest engine placeholder\n"
with engine.open("wb") as handle:
    handle.write(needle * (6 * 1024 * 1024 // len(needle)))
PY

write_fake_meta() {
    local sha size engine_sha engine_size
    sha="$(sha256_of "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME")"
    size="$(size_of "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME")"
    engine_sha="$(sha256_of "$JNI_DIR/arm64-v8a/libnode.so")"
    engine_size="$(size_of "$JNI_DIR/arm64-v8a/libnode.so")"
    cat >"$BUILD_DIR/n8n/meta.json" <<JSON
{
  "component": "n8n",
  "version": "2.38.7",
  "abi": "arm64-v8a",
  "packaged": true,
  "entry": "n8n/bin/n8n",
  "args": ["start"],
  "port": 5678,
  "healthPath": "/healthz",
  "guiPath": "/",
  "memoryMb": 512,
  "payload": {
    "kind": "asset",
    "path": "runtime/n8n/$PAYLOAD_ARCHIVE_NAME",
    "sha256": "$sha",
    "sizeBytes": $size
  }
}
JSON
    cat >"$BUILD_DIR/node/node.json" <<JSON
{
  "version": "24.9.0",
  "distOs": "android",
  "flavor": "android-executable",
  "library": "libnode.so",
  "apiLevel": 26,
  "extraLibs": [],
  "abis": { "arm64-v8a": { "sha256": "$engine_sha", "sizeBytes": $engine_size } }
}
JSON
}

verify_gate() { python3 "$SCRIPT_DIR/lib/verify_manifest.py" "$REPO_ROOT" "$MANIFEST"; }
regen() { python3 "$SCRIPT_DIR/generate-runtime-manifest.py" >"$TMP/regen.log" 2>&1; }
regen_and_verify() { regen || return 1; verify_gate; }

# 1. Naming every claim truthfully must be accepted.
write_fake_meta
regen >/dev/null 2>&1 || { warn "generator failed on fabricated artifacts:"; tail -n 10 "$TMP/regen.log" >&2; exit 1; }
grep -q '"packaged": true' "$MANIFEST" || { warn "generator did not mark the fabricated payload as packaged"; exit 1; }
expect_pass verify_gate
expect_pass regen_and_verify

# 2. A payload archive that vanished.
mv "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME" "$TMP/payload.zip"
expect_fail verify_gate
expect_fail regen
mv "$TMP/payload.zip" "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME"

# 3. One byte appended: digest and size must disagree with the manifest.
printf 'tampered' >>"$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME"
expect_fail verify_gate
regen >/dev/null 2>&1 || true
expect_fail verify_gate
write_fake_meta
regen >/dev/null 2>&1
expect_pass verify_gate

# 4. An entry point that is not inside the archive.
python3 - "$ASSETS_RUNTIME_DIR/n8n/$PAYLOAD_ARCHIVE_NAME" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a") as zf:
    pass
PY
python3 - "$MANIFEST" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text())
data["runtimes"]["n8n"]["entry"] = "n8n/bin/does-not-exist"
path.write_text(json.dumps(data, indent=2) + "\n")
PY
expect_fail verify_gate

# 5. A digest the manifest lost.
write_fake_meta
regen >/dev/null 2>&1
python3 - "$MANIFEST" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text())
data["runtimes"]["n8n"]["payload"]["sha256"] = ""
path.write_text(json.dumps(data, indent=2) + "\n")
PY
expect_fail verify_gate

# 6. An engine library that was not built.
write_fake_meta
regen >/dev/null 2>&1
mv "$JNI_DIR/arm64-v8a/libnode.so" "$TMP/libnode.so"
expect_fail verify_gate
mv "$TMP/libnode.so" "$JNI_DIR/arm64-v8a/libnode.so"
# The manifest generated before the removal is valid again: the gate rejects the
# claim, not the artifacts.
expect_pass regen_and_verify

printf '\n%s: %d checks passed, %d failed\n' "runtime gate self-test" "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || die "the runtime honesty gate does not hold"
