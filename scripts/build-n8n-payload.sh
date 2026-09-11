#!/usr/bin/env bash
#
# Build the n8n runtime payload for Android.
#
# Steps, in order:
#   1. install the pinned n8n production tree from npm with --ignore-scripts, so
#      nothing runs arbitrary postinstall code during the build;
#   2. cross-compile the native modules n8n needs (SQLite bindings) against the
#      Android Node engine headers from scripts/build-node-android.sh;
#   3. prune documentation, tests, sourcemaps and other platforms' prebuilds;
#   4. zip the tree deterministically into
#      android/app/src/main/assets/runtime/n8n/payload.zip;
#   5. re-open the archive and record its digest, size and entry point in
#      .runtime-build/n8n/meta.json, which scripts/generate-runtime-manifest.py
#      converts into the packaged manifest. A payload without that record is
#      invisible to the app, which is the point: no manifest entry, no claim.
#
# Usage:
#   scripts/build-n8n-payload.sh [abi]        # default: $DEFAULT_ABI

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ABI="${1:-$DEFAULT_ABI}"
ARCH="$(abi_to_node_arch "$ABI")"
NDK="$(require_ndk)"

NODE_SRC="$BUILD_DIR/node-src"
[ -f "$NODE_SRC/out/Release/node" ] || die "build the Node engine first (scripts/build-node-android.sh $ABI): $NODE_SRC/out/Release/node is missing"
[ -f "$JNI_DIR/$ABI/libnode.so" ] || die "no engine at $JNI_DIR/$ABI/libnode.so"

WORK="$BUILD_DIR/n8n/work"
STAGE="$WORK/payload"
rm -rf "$WORK"
mkdir -p "$STAGE"

info "n8n $N8N_VERSION payload for $ABI (Node $NODE_VERSION engine)"

# ---------------------------------------------------------------------------
# 1. Production tree from npm.
# ---------------------------------------------------------------------------
run_step "n8n-npm-install" bash -c "
    cd '$WORK'
    printf '{\"name\":\"n8n-payload\",\"private\":true}\n' > package.json
    npm install --omit=dev --ignore-scripts --no-audit --no-fund --loglevel=warn 'n8n@$N8N_VERSION'
" || die "npm install for n8n@$N8N_VERSION failed (see $LOG_DIR/n8n-npm-install.log)"

mv "$WORK/node_modules" "$STAGE/node_modules"
[ -f "$STAGE/node_modules/n8n/bin/n8n" ] || die "n8n package has no bin/n8n entry point"
ENTRY="node_modules/n8n/bin/n8n"
# npm needs a package root to rebuild modules inside the staged tree.
printf '{"name":"n8n-payload","private":true}\n' >"$STAGE/package.json"
ok "n8n tree installed ($(find "$STAGE" -type f | wc -l) files before prune)"

# ---------------------------------------------------------------------------
# 2. Native modules.
#
# n8n stores its database through SQLite; those bindings are the only compiled
# code in the payload. They are rebuilt from source against the Android engine,
# never taken from the prebuilt binaries npm would ship for glibc/musl.
# ---------------------------------------------------------------------------
export_android_toolchain "$ABI" "$NDK"
export npm_config_nodedir="$NODE_SRC"
export npm_config_build_from_source=true
export npm_config_arch="$(node_arch_to_npm_arch "$ABI")"
export npm_config_target="$NODE_VERSION"
export npm_config_platform=android
export npm_config_ignore_scripts=true
export npm_config_loglevel=warn

# `sqlite3` (node-sqlite3, a dependency of n8n) is what n8n uses for DB_TYPE=sqlite;
# it has no prebuilt Android binary, so it must be cross-compiled here.
NATIVE_REQUIRED=(sqlite3)
NATIVE_FOUND=()
mapfile -t NATIVE_FOUND < <(
    find "$STAGE/node_modules" -maxdepth 5 -name binding.gyp -printf '%h\n' 2>/dev/null \
        | sed "s|$STAGE/node_modules/||" | sed 's|.*/node_modules/||' | sort -u
)

if [ ${#NATIVE_FOUND[@]} -gt 0 ]; then
    info "native modules found: ${NATIVE_FOUND[*]}"
    for MODULE in "${NATIVE_FOUND[@]}"; do
        if run_step "n8n-native-$MODULE" bash -c "
            cd '$STAGE'
            npm rebuild '$MODULE' --build-from-source --nodedir='$NODE_SRC' --arch='$(node_arch_to_npm_arch "$ABI")'
        "; then
            BINARY="$(find "$STAGE/node_modules" -path "*${MODULE}*" -name '*.node' -print -quit 2>/dev/null || true)"
            if [ -n "$BINARY" ]; then
                ok "$MODULE: $(basename "$BINARY") built for $ABI"
            else
                warn "$MODULE rebuilt but produced no .node binary"
            fi
        else
            if printf '%s\n' "${NATIVE_REQUIRED[@]}" | grep -qx "$MODULE"; then
                die "required native module '$MODULE' failed to cross-compile for $ABI; refusing to ship an n8n payload that cannot open its database"
            fi
            warn "optional native module '$MODULE' could not be built for $ABI; it will be removed from the payload"
            rm -rf "$STAGE/node_modules/$MODULE"
        fi
    done
else
    die "no native module found in the n8n tree: DB_TYPE=sqlite cannot work without one"
fi

for MODULE in "${NATIVE_REQUIRED[@]}"; do
    printf '%s\n' "${NATIVE_FOUND[@]}" | grep -qx "$MODULE" \
        || die "required native module '$MODULE' is not present in the n8n tree; the payload would fail at runtime"
    find "$STAGE/node_modules" -path "*${MODULE}*" -name '*.node' -print -quit | grep -q . \
        || die "required native module '$MODULE' produced no compiled binary for $ABI"
done

# Remove platform-specific prebuilds for every OS we are not shipping.
find "$STAGE/node_modules" -type d \( \
        -name 'prebuilds' -o -name 'darwin-*' -o -name 'win32-*' -o -name 'linux-x64' \
    \) -prune -exec rm -rf {} + 2>/dev/null || true
# Prove every compiled artifact targets this ABI; anything else is a prebuilt
# for another platform and would crash the runtime at require() time.
find "$STAGE/node_modules" -type f -name '*.node' | while read -r binary; do
    machine="$(elf_machine_of "$binary")"
    expected="$(abi_to_elf_machine "$ABI")"
    if [ "$machine" != "$expected" ]; then
        warn "removing foreign binary $(echo "$binary" | sed "s|$STAGE/||") (ELF machine: ${machine:-not an ELF})"
        rm -f "$binary"
    fi
done

# ---------------------------------------------------------------------------
# 3. Promote n8n to the payload root.
#
# The app launches `n8n/bin/n8n`; keeping npm's full tree would make the entry
# path depend on npm's hoisting decisions. Node resolves `require()` from the
# script upwards, so `n8n/` at the root still sees the shared `node_modules/`.
# ---------------------------------------------------------------------------
if [ -f "$STAGE/node_modules/n8n/bin/n8n" ] && [ ! -d "$STAGE/n8n" ]; then
    mv "$STAGE/node_modules/n8n" "$STAGE/n8n"
    ok "promoted node_modules/n8n -> n8n (entry: $ENTRY)"
fi
[ -f "$STAGE/$ENTRY" ] || die "expected entry $ENTRY after pruning"

# ---------------------------------------------------------------------------
# 4. Prune.
# ---------------------------------------------------------------------------
info "pruning (docs, tests, maps, types)"
python3 - "$STAGE" <<'PY'
import os, re, shutil, sys, pathlib

root = pathlib.Path(sys.argv[1])
removed = files = 0
drop_dirs = {"test", "tests", "__tests__", "spec", "example", "examples", "docs", "doc", ".github", "coverage"}
drop_files = re.compile(r"\.(md|markdown|map|ts|tsx|flow|d\.ts|snap|log)$", re.I)
keep_files = re.compile(r"(LICENSE|package\.json|readme)", re.I)

for path in sorted(root.rglob("*"), key=lambda p: len(p.parts), reverse=True):
    if not path.exists():
        continue
    rel = path.relative_to(root)
    if path.is_dir():
        if path.name.lower() in drop_dirs and not any(
            keep in str(rel).lower() for keep in ("node_modules/@n8n/",)
        ):
            shutil.rmtree(path, ignore_errors=True)
            removed += 1
        continue
    if drop_files.search(path.name) and not keep_files.search(path.name):
        path.unlink(missing_ok=True)
        files += 1

print(f"pruned {removed} directories and {files} files")
PY

# n8n resolves its own version and assets from package.json; strip nothing else.
printf '%s\n' "$N8N_VERSION" >"$STAGE/node_modules/n8n/.n8n-payload-version"
ok "payload staged: $(du -sh "$STAGE" | awk '{print $1}')"

# ---------------------------------------------------------------------------
# 4. Deterministic zip.
# ---------------------------------------------------------------------------
DEST_DIR="$ASSETS_RUNTIME_DIR/n8n"
mkdir -p "$DEST_DIR"
ZIP="$DEST_DIR/$PAYLOAD_ARCHIVE_NAME"
rm -f "$ZIP"

info "writing $ZIP"
python3 - "$STAGE" "$ZIP" <<'PY'
import os, pathlib, sys, zipfile

stage, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
files = sorted(p for p in stage.rglob("*") if p.is_file())
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
    for path in files:
        rel = path.relative_to(stage).as_posix()
        info = zipfile.ZipInfo(rel, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o644 << 16) | 0o100000
        if rel.startswith("node_modules/n8n/bin/"):
            info.external_attr = (0o755 << 16) | 0o100000
        with open(path, "rb") as fh:
            zf.writestr(info, fh.read(), compresslevel=6)
print(f"packed {len(files)} entries")
PY

# ---------------------------------------------------------------------------
# 5. Verify the archive the way the app will read it, then record the facts.
# ---------------------------------------------------------------------------
python3 - "$ZIP" "$ENTRY" <<'PY' || die "payload archive failed self-verification"
import sys, zipfile
archive, entry = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(archive) as zf:
    bad = zf.testzip()
    if bad is not None:
        sys.exit(f"corrupt entry: {bad}")
    names = set(zf.namelist())
    if entry not in names:
        sys.exit(f"entry {entry} missing from archive")
    if any(name.startswith("/") or ".." in name.split("/") for name in names):
        sys.exit("archive contains an unsafe path")
print(f"archive ok: {len(names)} entries, entry {entry}")
PY

SHA="$(sha256_of "$ZIP")"
SIZE="$(size_of "$ZIP")"
UNPACKED="$(du -sb "$STAGE" | awk '{print $1}')"
INTEGRITY="$(python3 - "$STAGE/node_modules/n8n/package.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1])).get("version", ""))
PY
)"

write_meta_json n8n "$(cat <<JSON
{
  "component": "n8n",
  "version": "$N8N_VERSION",
  "abi": "$ABI",
  "entry": "$ENTRY",
  "args": ["start"],
  "port": 5678,
  "healthPath": "/healthz",
  "guiPath": "/",
  "memoryMb": 640,
  "source": "npm:n8n@$N8N_VERSION",
  "license": "SEE LICENSE IN LICENSE.md (n8n Sustainable Use License; review before redistributing an APK that embeds this payload)",
  "unpackedBytes": $UNPACKED,
  "payload": {
    "kind": "asset",
    "path": "runtime/n8n/$PAYLOAD_ARCHIVE_NAME",
    "sha256": "$SHA",
    "sizeBytes": $SIZE
  },
  "env": {
    "DB_TYPE": "sqlite",
    "DB_SQLITE_POOL_SIZE": "2",
    "EXECUTIONS_MODE": "regular",
    "N8N_RUNNERS_ENABLED": "true",
    "N8N_DIAGNOSTICS_ENABLED": "false",
    "N8N_UMAMI_ENABLED": "false",
    "N8N_BLOCK_ENV_ACCESS_IN_NODE": "false",
    "N8N_COMPRESSION_NODE_ENABLED": "false"
  },
  "notes": [
    "Native SQLite bindings are cross-compiled against the engine in this APK.",
    "Payload redistributes n8n under its Sustainable Use License; see the upstream LICENSE.md.",
    "The payload is installed into the app sandbox on first start and verified by digest."
  ],
  "integrity": "$INTEGRITY"
}
JSON
)"

ok "n8n payload: $ZIP ($(numfmt --to=iec "$SIZE" 2>/dev/null || echo "$SIZE bytes"), sha256=$SHA)"
