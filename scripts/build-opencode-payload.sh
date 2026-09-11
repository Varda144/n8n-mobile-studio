#!/usr/bin/env bash
# Attempt to build an Android-runnable OpenCode payload, and report the truth.
#
# OpenCode (anomalyco/opencode, formerly sst/opencode) is distributed as Bun
# single-file binaries: the npm package `opencode-ai` is a launcher whose
# optionalDependencies are `opencode-<platform>-<arch>` builds for
# linux/darwin/windows, glibc and musl. There is no Android/bionic build, and the
# server sources target the Bun runtime (bun:sqlite, Bun.serve, Bun.spawn).
#
# This script therefore does not fabricate a payload. It:
#   1. resolves the pinned upstream source and inspects it,
#   2. attempts a Node build *only if* upstream ships a Node-compatible entry,
#   3. smoke-tests any bundle it produces on the host Node (the same engine
#      version the app embeds),
#   4. writes meta.json with `packaged: true` only when that smoke test passes,
#      otherwise records the concrete blockers and leaves the runtime unpackaged.
#
# The consequence in the app is deliberate and visible: OpenCode reports
# NOT_PACKAGED (with the reason from this script's meta.json) instead of silently
# degrading to a remote service or a stub server.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

REPO="https://github.com/anomalyco/opencode"
SRC="$BUILD_DIR/opencode-src"
STAGE="$BUILD_DIR/opencode-payload"
META="$BUILD_DIR/opencode/meta.json"
mkdir -p "$BUILD_DIR/opencode"

write_meta() {
    local packaged="$1" reason="$2" digest="${3:-}" size="${4:-0}" entry="${5:-}"
    python3 - "$META" "$OPENCODE_VERSION" "$packaged" "$reason" "$digest" "$size" "$entry" <<'PY'
import json, sys, datetime
path, version, packaged, reason, digest, size, entry = sys.argv[1:8]
meta = {
    "component": "opencode",
    "version": version,
    "packaged": packaged == "true",
    "unavailableReason": reason,
    "entry": entry or "opencode/server.js",
    "args": ["serve", "--port", "{port}", "--hostname", "127.0.0.1"],
    "payload": {
        "kind": "asset",
        "path": "runtime/opencode/payload.zip",
        "sha256": digest,
        "sizeBytes": int(size or 0),
    },
    "builtAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}
with open(path, "w") as handle:
    json.dump(meta, handle, indent=2)
    handle.write("\n")
print(json.dumps(meta, indent=2))
PY
}

# 1. upstream facts -----------------------------------------------------------------
if [ ! -d "$SRC/.git" ]; then
    run_step "opencode-clone" git clone --depth 1 --branch "v$OPENCODE_VERSION" "$REPO" "$SRC" ||
        run_step "opencode-clone-default" git clone --depth 1 "$REPO" "$SRC" ||
        { write_meta false "could not clone $REPO at v$OPENCODE_VERSION"; exit 0; }
fi

BUN_USAGE="$(grep -rl --include='*.ts' --include='*.tsx' --include='*.json' -E 'bun:sqlite|Bun\.serve|Bun\.spawn|bun:ffi' "$SRC" 2>/dev/null | head -5 | tr '\n' ' ' || true)"
NODE_ENTRY="$(python3 - "$SRC" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
for candidate in root.glob("packages/*/package.json"):
    try:
        data = json.loads(candidate.read_text())
    except Exception:
        continue
    engines = data.get("engines", {}) or {}
    bin_field = data.get("bin") or {}
    bins = list(bin_field.values()) if isinstance(bin_field, dict) else [bin_field]
    scripts = data.get("scripts", {}) or {}
    node_ok = "node" in engines and "bun" not in engines
    has_build = any(key.startswith("build") for key in scripts)
    if bins and node_ok and has_build:
        print(json.dumps({"dir": str(candidate.parent), "bin": bins[0]}))
        break
PY
)"

if [ -n "$NODE_ENTRY" ]; then
    info "upstream looks Node-buildable: $NODE_ENTRY"
    PKG_DIR="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["dir"])' "$NODE_ENTRY")"
    if run_step "opencode-install" bash -c "cd '$PKG_DIR' && npm install --no-audit --no-fund --loglevel=error" &&
        run_step "opencode-build" bash -c "cd '$PKG_DIR' && npm run build --if-present"; then
        if [ -d "$PKG_DIR/dist" ]; then
            info "bundling the built server for Node"
            rm -rf "$STAGE" && mkdir -p "$STAGE/opencode"
            cp -a "$PKG_DIR/dist/." "$STAGE/opencode/" || true
            # Smoke test on the host Node: the same engine version the app embeds.
            ENTRY_FILE="$STAGE/opencode/server.js"
            [ -f "$ENTRY_FILE" ] || ENTRY_FILE="$(find "$STAGE/opencode" -maxdepth 2 -name '*.js' | head -1)"
            if [ -n "$ENTRY_FILE" ] && node -e "
const { spawn } = require('child_process');
const child = spawn(process.execPath, [process.argv[1], 'serve', '--port', '8791', '--hostname', '127.0.0.1'], { stdio: 'inherit' });
setTimeout(() => { fetch('http://127.0.0.1:8791/global/health').then(r => { child.kill('SIGKILL'); process.exit(r.ok ? 0 : 1); }).catch(() => { child.kill('SIGKILL'); process.exit(1); }); }, 5000);
" "$ENTRY_FILE"; then
                mkdir -p "$ASSETS_RUNTIME/opencode"
                ARCHIVE="$ASSETS_RUNTIME/opencode/$PAYLOAD_ARCHIVE_NAME"
                (cd "$STAGE" && zip -qry "$ARCHIVE" .)
                write_meta true "" "$(sha256_of "$ARCHIVE")" "$(size_of "$ARCHIVE")" "opencode/server.js"
                info "OpenCode payload built and smoke-tested against host Node"
                exit 0
            fi
            warn "the built bundle did not answer /global/health on the host Node"
        fi
    fi
fi

# 2. honest report -----------------------------------------------------------------
REASON="no Android/bionic artifact exists upstream and the server targets the Bun runtime"
[ -n "$BUN_USAGE" ] && REASON="$REASON (Bun API usage in: ${BUN_USAGE})"
[ -z "$NODE_ENTRY" ] && REASON="$REASON; no package in the pinned source declares a Node engine plus a build script"
warn "OpenCode payload NOT built: $REASON"
echo "::warning::OPENCODE_NOT_PACKAGED: $REASON"
write_meta false "$REASON"
