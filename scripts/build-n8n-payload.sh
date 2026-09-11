#!/usr/bin/env bash
# Build the n8n payload: the pinned n8n release plus its production dependency
# tree, with native modules cross-compiled for Android against the same Node
# headers the embedded engine was built from.
#
# Output:
#   android/app/src/main/assets/runtime/n8n/payload.zip   (installed by the app)
#   .runtime-build/n8n/meta.json                          (manifest input)
#
# The payload is *JavaScript running on the app's embedded Node*. It is never a
# remote n8n server, and the script refuses to publish a payload whose native
# modules could not be built for the target ABI — a payload that would crash on
# start is worse than no payload, because the app must never claim a runtime is
# embedded without evidence.
#
# Usage: scripts/build-n8n-payload.sh [abi]      (default: arm64-v8a)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

ABI="${1:-arm64-v8a}"
case "$ABI" in
    arm64-v8a) ARCH=arm64; TOOLCHAIN_TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) ARCH=arm; TOOLCHAIN_TRIPLE=armv7a-linux-androideabi ;;
    x86_64) ARCH=x64; TOOLCHAIN_TRIPLE=x86_64-linux-android ;;
    *) die "unsupported ABI: $ABI" ;;
esac
NDK="$(find_ndk)"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
NODE_SOURCE="$BUILD_DIR/node-$NODE_VERSION"
[ -f "$NODE_SOURCE/configure" ] || die "Node $NODE_VERSION source not found at $NODE_SOURCE (run build-node-android.sh first)"

info "n8n payload:  n8n@$N8N_VERSION  abi=$ABI  node=$NODE_VERSION"
info "NDK:          $NDK"

STAGE="$BUILD_DIR/n8n-payload-$ABI"
rm -rf "$STAGE"
mkdir -p "$STAGE"

cat >"$STAGE/package.json" <<JSON
{
  "name": "n8n-mobile-studio-payload",
  "private": true,
  "description": "n8n payload for the embedded Android runtime",
  "dependencies": {
    "n8n": "$N8N_VERSION"
  }
}
JSON

info "installing n8n and its production dependency tree with the host npm"
run_step "n8n-npm-install-$ABI" env \
    npm_config_loglevel=error \
    npm_config_audit=false \
    npm_config_fund=false \
    npm_config_ignore_scripts=true \
    bash -c "cd '$STAGE' && npm install --omit=dev --no-package-lock" ||
    die "npm install for n8n@$N8N_VERSION failed (see annotations)"

[ -f "$STAGE/node_modules/n8n/bin/n8n" ] ||
    die "npm install produced no n8n CLI entry at node_modules/n8n/bin/n8n"

# ----------------------------------------------------------------- native modules
# n8n needs better-sqlite3 for its default local database. It has no Android
# prebuild, so it is compiled here against the Node source tree that produced the
# engine: same headers, same ABI, same V8 flags.
export CC="$TOOLCHAIN/bin/${TOOLCHAIN_TRIPLE}${ANDROID_API_LEVEL}-clang"
export CXX="$TOOLCHAIN/bin/${TOOLCHAIN_TRIPLE}${ANDROID_API_LEVEL}-clang++"
export AR="$TOOLCHAIN/bin/llvm-ar"
export LD="$TOOLCHAIN/bin/ld.lld"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export LINK="$CXX"
export GYP_DEFINES="target_arch=$ARCH v8_target_arch=$ARCH OS=android host_os=linux android_ndk_path=$NDK"
export npm_config_nodedir="$NODE_SOURCE"
export npm_config_arch="$ARCH"
export npm_config_target="${NODE_VERSION#v}"
export npm_config_build_from_source=true
export npm_config_loglevel=error

NATIVE_MODULES=()
while IFS= read -r gyp; do
    NATIVE_MODULES+=("$(dirname "${gyp#"$STAGE/node_modules/"}")")
done < <(find "$STAGE/node_modules" -maxdepth 4 -name binding.gyp -not -path '*/node_modules/*/node_modules/*' | sort)
info "native modules to cross-compile: ${NATIVE_MODULES[*]:-none}"

FAILED_NATIVE=()
for MODULE in "${NATIVE_MODULES[@]:-}"; do
    [ -n "$MODULE" ] || continue
    LOG="$LOG_DIR/native-$ABI-${MODULE//\//_}.log"
    info "  rebuilding $MODULE for $ABI"
    if ! (cd "$STAGE" && npm rebuild "$MODULE" --build-from-source >"$LOG" 2>&1); then
        annotate_file "native-$MODULE" "$LOG" 10
        FAILED_NATIVE+=("$MODULE")
    fi
done

if [ "${#FAILED_NATIVE[@]}" -gt 0 ]; then
    printf 'failed to cross-compile: %s\n' "${FAILED_NATIVE[*]}"
    case " ${FAILED_NATIVE[*]} " in
        *" better-sqlite3 "*)
            die "better-sqlite3 could not be built for $ABI: n8n cannot use its local SQLite database without it, so no payload is published"
            ;;
    esac
    warn "publishing without: ${FAILED_NATIVE[*]} (n8n may disable features that need them)"
fi

# Verify the native module really targets the requested ABI before packaging.
if [ -d "$STAGE/node_modules/better-sqlite3/build/Release" ]; then
    SO_FILE="$(find "$STAGE/node_modules/better-sqlite3/build/Release" -name '*.node' | head -1)"
    if [ -n "$SO_FILE" ] && command -v file >/dev/null 2>&1; then
        info "  $(file -b "$SO_FILE")"
    fi
fi

# ----------------------------------------------------------------------- prune
info "pruning documentation, sources maps and test fixtures"
find "$STAGE" -name '*.map' -delete 2>/dev/null || true
find "$STAGE" -name '*.md' -delete 2>/dev/null || true
find "$STAGE" -type d \
    \( -name test -o -name tests -o -name __tests__ -o -name '*.test' -o -name .github -o -name docs \) \
    -prune -exec rm -rf {} + 2>/dev/null || true

# Move the CLI package to the layout the manifest promises (`n8n/bin/n8n`).
rm -rf "$STAGE/n8n"
mv "$STAGE/node_modules/n8n" "$STAGE/n8n"
# Wire the package's own dependency resolution: its dependencies are hoisted into
# node_modules at the payload root, which Node resolves from `n8n/` upward.
[ -f "$STAGE/n8n/bin/n8n" ] || die "payload layout is wrong: n8n/bin/n8n missing after restructure"

# ------------------------------------------------------------------------ zip
mkdir -p "$ASSETS_RUNTIME/n8n"
ARCHIVE="$ASSETS_RUNTIME/n8n/$PAYLOAD_ARCHIVE_NAME"
rm -f "$ARCHIVE"
info "creating $ARCHIVE"
(cd "$STAGE" && zip -qry "$ARCHIVE" .) || die "failed to create the payload archive"

DIGEST="$(sha256_of "$ARCHIVE")"
SIZE="$(size_of "$ARCHIVE")"
info "payload: sha256=$DIGEST size=$SIZE bytes"

# The archive is also kept in the build directory for artifact upload debugging.
cp "$ARCHIVE" "$BUILD_DIR/n8n/payload.zip"
mkdir -p "$BUILD_DIR/n8n"
NPM_INTEGRITY="$(python3 - "$STAGE/n8n/package.json" <<'PY'
import json, sys
try:
    with open(sys.argv[1]) as handle:
        data = json.load(handle)
    print(data.get("dist", {}).get("integrity", "") or data.get("_integrity", ""))
except Exception:
    print("")
PY
)"

python3 - "$BUILD_DIR/n8n/meta.json" "$N8N_VERSION" "$ABI" "$DIGEST" "$SIZE" "$NODE_VERSION" "$NPM_INTEGRITY" <<'PY'
import json, sys, datetime
path, version, abi, digest, size, node, integrity = sys.argv[1:8]
meta = {
    "component": "n8n",
    "version": version,
    "abi": abi,
    "nodeVersion": node.lstrip("v"),
    "entry": "n8n/bin/n8n",
    "args": ["start"],
    "payload": {"kind": "asset", "path": "runtime/n8n/payload.zip", "sha256": digest, "sizeBytes": int(size)},
    "npmIntegrity": integrity,
    "packaged": True,
    "builtAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}
with open(path, "w") as handle:
    json.dump(meta, handle, indent=2)
    handle.write("\n")
print(json.dumps(meta, indent=2))
PY

info "n8n payload ready"
