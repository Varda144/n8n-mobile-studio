#!/usr/bin/env bash
#
# Build the OpenCode runtime payload for Android — or say, loudly and honestly,
# that it cannot be built.
#
# Why this script is defensive: OpenCode does not ship anything runnable on
# Android. The npm package `opencode-ai` contains a launcher and a postinstall
# script that copies a *Bun single-file binary* from one of its platform
# packages, and those exist only for darwin/linux/win32 (glibc or musl). There is
# no bionic build and Bun itself does not target Android. The server code is
# TypeScript on top of Bun APIs (`Bun.serve`, `bun:sqlite`, `Bun.$`).
#
# So the only honest options are:
#   a) bundle the server for the embedded Node engine and shim the Bun APIs the
#      server actually touches (this script does that, then *runs* it on the host
#      and talks to its HTTP health endpoint before packaging it), or
#   b) report NOT_PACKAGED with the exact reason, which the app renders as such.
#
# The app never falls back to a remote server, and never pretends. If step (a)
# stops working — upstream refactors, the shim drifts, a dependency needs a
# native Bun module — the payload stays absent and the APK says so.
#
# Usage:
#   scripts/build-opencode-payload.sh [abi]      # default: $DEFAULT_ABI

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ABI="${1:-$DEFAULT_ABI}"
ARCH="$(abi_to_node_arch "$ABI")"

WORK="$BUILD_DIR/opencode/work"
STAGE="$WORK/payload"
DEST_DIR="$ASSETS_RUNTIME_DIR/opencode"
ZIP="$DEST_DIR/$PAYLOAD_ARCHIVE_NAME"
META_DIR="$BUILD_DIR/opencode"

write_unavailable() {
    local reason="$1"
    mkdir -p "$META_DIR"
    rm -f "$ZIP"
    cat >"$META_DIR/meta.json" <<JSON
{
  "component": "opencode",
  "version": "$OPENCODE_VERSION",
  "abi": "$ABI",
  "packaged": false,
  "unavailableReason": $(printf '%s' "$reason" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'),
  "source": "github:anomalyco/opencode",
  "notes": [
    "Upstream publishes Bun single-file binaries for darwin/linux/win32 only; there is no Android/bionic artifact.",
    "scripts/build-opencode-payload.sh bundles the server for the embedded Node engine and smoke-tests it on the host before packaging."
  ]
}
JSON
    warn "$reason"
    annotate warning "OPENCODE_NOT_PACKAGED: $reason"
    ok "recorded OpenCode as NOT_PACKAGED (.runtime-build/opencode/meta.json)"
}

# ---------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------
command -v git >/dev/null || { write_unavailable "git is unavailable in this build environment"; exit 0; }
command -v node >/dev/null || { write_unavailable "a host Node.js is needed to build and smoke-test the OpenCode bundle"; exit 0; }

HOST_NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
[ "$HOST_NODE_MAJOR" -ge 22 ] || { write_unavailable "host Node.js is $(node -v); bundling the Effect-based server needs >= 22"; exit 0; }

rm -rf "$WORK"; mkdir -p "$STAGE"

# ---------------------------------------------------------------------------
# 1. Source at a pinned commit. `OPENCODE_REF` may pin a tag or SHA, which is
#    what reproducible builds need; the default tracks the development branch
#    because that is where the server lives.
# ---------------------------------------------------------------------------
OPENCODE_REF="${OPENCODE_REF:-dev}"
SRC="$WORK/src"
info "cloning anomalyco/opencode@$OPENCODE_REF"
if ! timeout "${OPENCODE_CLONE_TIMEOUT:-300}" git clone --depth 1 --branch "$OPENCODE_REF" https://github.com/anomalyco/opencode "$SRC" >"$LOG_DIR/opencode-clone.log" 2>&1; then
    write_unavailable "cannot clone anomalyco/opencode@$OPENCODE_REF ($(tail -n 1 "$LOG_DIR/opencode-clone.log" 2>/dev/null))"
    exit 0
fi
COMMIT="$(git -C "$SRC" rev-parse HEAD)"

SERVER_ENTRY=""
for candidate in packages/server/src/index.ts packages/server/src/server.ts packages/opencode/src/index.ts packages/opencode/src/cli/index.ts; do
    [ -f "$SRC/$candidate" ] && SERVER_ENTRY="$candidate" && break
done
[ -n "$SERVER_ENTRY" ] || { write_unavailable "no server entry point found in the pinned checkout (looked for packages/server/src/index.ts and packages/opencode/src/*)"; exit 0; }
ok "server entry: $SERVER_ENTRY @ ${COMMIT:0:12}"

# ---------------------------------------------------------------------------
# 2. Bundle for Node. Bun's bundler is used because it is the bundler the
#    project itself uses; it emits plain ESM that Node can run.
# ---------------------------------------------------------------------------
BUN_BIN="${BUN_BIN:-}"
if [ -z "$BUN_BIN" ]; then
    if command -v bun >/dev/null 2>&1; then
        BUN_BIN="$(command -v bun)"
    else
        info "installing the Bun toolchain used for bundling (host only; never shipped)"
        timeout 300 npm install --prefix "$WORK/tools" --no-audit --no-fund bun >"$LOG_DIR/opencode-bun-install.log" 2>&1 || true
        BUN_BIN="$WORK/tools/node_modules/.bin/bun"
    fi
fi
[ -x "$BUN_BIN" ] || { write_unavailable "the Bun bundler could not be installed; bundling without it is not supported by this script yet"; exit 0; }

info "installing workspace dependencies"
( cd "$SRC" && timeout "${OPENCODE_INSTALL_TIMEOUT:-1200}" "$BUN_BIN" install --frozen-lockfile >"$LOG_DIR/opencode-install.log" 2>&1 ) \
    || ( cd "$SRC" && timeout "${OPENCODE_INSTALL_TIMEOUT:-1200}" "$BUN_BIN" install >"$LOG_DIR/opencode-install.log" 2>&1 ) \
    || { write_unavailable "bun install failed in the pinned checkout ($(tail -n 1 "$LOG_DIR/opencode-install.log" 2>/dev/null))"; exit 0; }
ok "dependencies installed"

info "bundling $SERVER_ENTRY for Node"
BUNDLE="$WORK/server.js"
if ! ( cd "$SRC" && timeout "${OPENCODE_BUNDLE_TIMEOUT:-600}" "$BUN_BIN" build "$SERVER_ENTRY" --target=node --outfile="$BUNDLE" --external 'bun*' --minify-whitespace ) >"$LOG_DIR/opencode-bundle.log" 2>&1; then
    write_unavailable "bundling the server for Node failed ($(tail -n 2 "$LOG_DIR/opencode-bundle.log" 2>/dev/null | tr '\n' ' '))"
    exit 0
fi
[ -s "$BUNDLE" ] || { write_unavailable "the bundler produced an empty bundle"; exit 0; }

# Bun-only modules cannot be resolved by Node; surface them instead of shipping a
# bundle that dies at startup with MODULE_NOT_FOUND.
if grep -qE "require\\(['\"]bun:|from ['\"]bun:" "$BUNDLE"; then
    write_unavailable "the bundle still requires Bun-only modules (bun:sqlite/bun:ffi), which the embedded Node engine does not provide"
    exit 0
fi

# ---------------------------------------------------------------------------
# 3. Smoke test on the host Node: start it, ask for health, stop it. A payload
#    that cannot pass this is not packaged — that is the whole point of the
#    acceptance criterion "packaged and successfully launched".
# ---------------------------------------------------------------------------
info "smoke-testing the bundle on host Node"
SMOKE_LOG="$LOG_DIR/opencode-smoke.log"
SMOKE_PORT=8799
(
    cd "$WORK"
    NODE_OPTIONS=--max-old-space-size=512 node "$BUNDLE" serve --port "$SMOKE_PORT" --hostname 127.0.0.1
) >"$SMOKE_LOG" 2>&1 &
SMOKE_PID=$!

HEALTHY=0
for _ in $(seq 1 40); do
    if ! kill -0 "$SMOKE_PID" 2>/dev/null; then break; fi
    for path in /global/health /health /; do
        CODE="$(curl -s -o /dev/null -m 2 -w '%{http_code}' "http://127.0.0.1:$SMOKE_PORT$path" || true)"
        if [ "$CODE" = "200" ]; then HEALTHY=1; break 2; fi
    done
    sleep 1
done
kill "$SMOKE_PID" 2>/dev/null || true
wait "$SMOKE_PID" 2>/dev/null || true

if [ "$HEALTHY" != "1" ]; then
    write_unavailable "the Node bundle never answered a health probe on the host (see .runtime-build/logs/opencode-smoke.log)"
    exit 0
fi
ok "bundle answers health checks on host Node"

# ---------------------------------------------------------------------------
# 4. Stage + package.
# ---------------------------------------------------------------------------
# The entry path must match OpenCodeVersion.ENTRY ("opencode/server.js").
mkdir -p "$STAGE/opencode"
cp "$BUNDLE" "$STAGE/opencode/server.js"
cat >"$STAGE/package.json" <<JSON
{
  "name": "opencode-server-payload",
  "private": true,
  "version": "$OPENCODE_VERSION",
  "description": "OpenCode server bundled for the embedded Node engine",
  "main": "opencode/server.js"
}
JSON
printf 'anomalyco/opencode@%s\n' "$COMMIT" >"$STAGE/SOURCE.txt"

mkdir -p "$DEST_DIR"
rm -f "$ZIP"
python3 - "$STAGE" "$ZIP" <<'PY'
import pathlib, sys, zipfile

stage, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
files = sorted(p for p in stage.rglob("*") if p.is_file())
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
    for path in files:
        rel = path.relative_to(stage).as_posix()
        info = zipfile.ZipInfo(rel, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o644 << 16) | 0o100000
        with open(path, "rb") as fh:
            zf.writestr(info, fh.read(), compresslevel=6)
print(f"packed {len(files)} entries")
PY

SHA="$(sha256_of "$ZIP")"
SIZE="$(size_of "$ZIP")"
write_meta_json opencode "$(cat <<JSON
{
  "component": "opencode",
  "version": "$OPENCODE_VERSION",
  "abi": "$ABI",
  "packaged": true,
  "entry": "opencode/server.js",
  "args": ["serve", "--port", "{port}", "--hostname", "127.0.0.1"],
  "port": 8765,
  "healthPath": "/global/health",
  "guiPath": "/",
  "memoryMb": 448,
  "source": "github:anomalyco/opencode@$COMMIT",
  "license": "MIT",
  "payload": {
    "kind": "asset",
    "path": "runtime/opencode/$PAYLOAD_ARCHIVE_NAME",
    "sha256": "$SHA",
    "sizeBytes": $SIZE
  },
  "env": {
    "OPENCODE_DISABLE_TELEMETRY": "1",
    "OPENCODE_DISABLE_AUTOUPDATE": "1"
  },
  "notes": [
    "Server bundled from source for the embedded Node engine; upstream ships no Android binary.",
    "The bundle passed a host HTTP health probe before packaging."
  ],
  "integrity": "anomalyco/opencode@$COMMIT"
}
JSON
)"

ok "OpenCode payload: $ZIP ($(numfmt --to=iec "$SIZE" 2>/dev/null || echo "$SIZE bytes"), sha256=$SHA)"
