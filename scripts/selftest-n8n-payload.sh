#!/usr/bin/env bash
#
# Exercise the packaging half of scripts/build-n8n-payload.sh on a synthetic tree.
#
# The packaging steps — promoting n8n to the payload root, pruning, writing the
# archive and recording the manifest entry — are where a payload build can be wrong
# in ways nothing else notices: an archive whose entry point is not inside it, a
# manifest that names a path the app cannot launch, or a compiled module silently
# dropped. All of that used to be discovered only by a payload build that had spent
# an hour cross-compiling Node first.
#
# This runs in seconds, needs neither npm nor an NDK, and checks the same code path:
# `--from-stage` skips the install and the cross-compile, and every output goes to a
# temporary directory, so a real payload can never be overwritten by a test.
#
# Usage: scripts/selftest-n8n-payload.sh

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

PASSED=0
FAILED=0

check() {
    # check <description> <condition...>
    local description="$1"; shift
    if "$@"; then
        printf '\033[32m ok \033[0m %s\n' "$description"
        PASSED=$((PASSED + 1))
    else
        printf '\033[31m FAIL \033[0m %s\n' "$description"
        FAILED=$((FAILED + 1))
    fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

STAGE="$TMP/payload"
OUT="$TMP/out"
META="$TMP/meta"

# --------------------------------------------------------------------------- tree
# A tree shaped like the one npm produces: the n8n package inside node_modules,
# its entry point, and the one native module that has to be there for the runtime
# to open its database.
mkdir -p "$STAGE/node_modules/n8n/bin" "$STAGE/node_modules/n8n/docs" \
         "$STAGE/node_modules/n8n/__tests__" "$STAGE/node_modules/n8n/lib" \
         "$STAGE/node_modules/sqlite3/lib"
cat >"$STAGE/node_modules/n8n/bin/n8n" <<'SH'
#!/usr/bin/env node
console.log('n8n');
SH
chmod 0644 "$STAGE/node_modules/n8n/bin/n8n"
printf '{"name":"n8n","version":"2.38.7","bin":{"n8n":"bin/n8n"}}\n' >"$STAGE/node_modules/n8n/package.json"
printf 'documentation that must not ship\n' >"$STAGE/node_modules/n8n/docs/index.md"
printf 'test that must not ship\n' >"$STAGE/node_modules/n8n/__tests__/n8n.test.js"
printf 'source map that must not ship\n' >"$STAGE/node_modules/n8n/lib/index.js.map"
printf '{"name":"sqlite3","version":"5.1.7"}\n' >"$STAGE/node_modules/sqlite3/package.json"
printf '{"targets":[{"target_name":"node_sqlite3"}]}\n' >"$STAGE/node_modules/sqlite3/binding.gyp"
# A real ELF header for aarch64, so the build's own cross-architecture check has
# something true to agree with.
python3 - "$STAGE/node_modules/sqlite3/lib/node_sqlite3.node" <<'PY'
import struct, sys
header = bytearray(64)
header[0:4] = b"\x7fELF"
header[4] = 2          # 64-bit
header[5] = 1          # little endian
header[6] = 1          # version
struct.pack_into("<H", header, 16, 3)      # ET_DYN
struct.pack_into("<H", header, 18, 0xB7)   # EM_AARCH64
struct.pack_into("<I", header, 20, 1)
struct.pack_into("<H", header, 52, 64)     # e_ehsize
open(sys.argv[1], "wb").write(bytes(header) + b"\x00" * 64)
PY

LOG="$TMP/build.log"
if PAYLOAD_OUT_DIR="$OUT" PAYLOAD_META_DIR="$META" \
    bash "$SCRIPTS_DIR/build-n8n-payload.sh" arm64-v8a --from-stage "$STAGE" >"$LOG" 2>&1; then
    check "the packaging run succeeds" true
else
    check "the packaging run succeeds" false
    sed 's/^/    | /' "$LOG" | tail -n 20
fi

ARCHIVE="$OUT/$PAYLOAD_ARCHIVE_NAME"
[ -f "$ARCHIVE" ] && check "it writes $PAYLOAD_ARCHIVE_NAME" true || check "it writes $PAYLOAD_ARCHIVE_NAME" false
[ -f "$META/meta.json" ] && check "it records meta.json" true || check "it records meta.json" false

# --------------------------------------------------------------- what it contains
python3 - "$ARCHIVE" "$META/meta.json" "$N8N_VERSION" <<'PY'
import hashlib, json, pathlib, sys, zipfile

archive, meta_path, pinned = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
failures = []


def check(description, condition):
    print(("  ok   " if condition else "  FAIL ") + description)
    if not condition:
        failures.append(description)


if not archive.is_file() or not meta_path.is_file():
    print("  FAIL nothing was produced to inspect")
    raise SystemExit(1)

with zipfile.ZipFile(archive) as zf:
    names = zf.namelist()
    meta = json.loads(meta_path.read_text())

    check("the entry point is inside the archive", "n8n/bin/n8n" in names)
    check("the promoted package root is used, not npm's directory",
          not any(name.startswith("node_modules/n8n/") for name in names))
    check("the database binding ships with the payload",
          "node_modules/sqlite3/lib/node_sqlite3.node" in names)
    check("the binding's package metadata ships too",
          "node_modules/sqlite3/package.json" in names)
    check("documentation is pruned", not any(name.startswith("n8n/docs/") for name in names))
    check("tests are pruned", not any("__tests__" in name for name in names))
    check("source maps are pruned", not any(name.endswith(".js.map") for name in names))
    check("package.json is kept", "n8n/package.json" in names)
    check("the version breadcrumb is inside the package root",
          "n8n/.n8n-payload-version" in names)
    check("the entry point keeps its executable bit",
          (zf.getinfo("n8n/bin/n8n").external_attr >> 16) & 0o777 == 0o755)
    check("the archive carries no absolute or traversing path",
          not any(name.startswith("/") or ".." in name.split("/") for name in names))

    check("meta.json names the entry the archive really has", meta["entry"] in names)
    check("meta.json records the pinned version", meta["version"] == pinned)
    check("meta.json records the ABI", meta["abi"] == "arm64-v8a")
    check("meta.json points at the archive the build produced",
          meta["payload"]["path"] == "runtime/n8n/payload.zip")
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    check("meta.json records the archive's digest", meta["payload"]["sha256"] == digest)
    check("meta.json records the archive's size", meta["payload"]["sizeBytes"] == archive.stat().st_size)
    check("meta.json records the component", meta["component"] == "n8n")
    check("meta.json records the version the package itself claims",
          meta["integrity"] == "2.38.7")
    check("meta.json records how the runtime is launched", meta["args"] == ["start"])

raise SystemExit(1 if failures else 0)
PY
if [ $? -eq 0 ]; then
    PASSED=$((PASSED + 1))
else
    FAILED=$((FAILED + 1))
fi

# The same run must also work when the tree has already been promoted — that is what
# re-packaging an existing payload looks like. (The run above promoted the package in
# place, which is why these fixtures copy it from $STAGE/n8n.)
SECOND="$TMP/promoted"
mkdir -p "$SECOND"
cp -r "$STAGE/n8n" "$SECOND/n8n"
mkdir -p "$SECOND/node_modules"
cp -r "$STAGE/node_modules/sqlite3" "$SECOND/node_modules/sqlite3"
if PAYLOAD_OUT_DIR="$TMP/out2" PAYLOAD_META_DIR="$TMP/meta2" \
    bash "$SCRIPTS_DIR/build-n8n-payload.sh" arm64-v8a --from-stage "$SECOND" >"$TMP/build2.log" 2>&1 \
    && python3 -c "
import json, pathlib, sys, zipfile
archive = pathlib.Path('$TMP/out2/$PAYLOAD_ARCHIVE_NAME')
meta = json.loads(pathlib.Path('$TMP/meta2/meta.json').read_text())
names = zipfile.ZipFile(archive).namelist()
sys.exit(0 if meta['entry'] == 'n8n/bin/n8n' and meta['entry'] in names else 1)
"; then
    check "an already-promoted tree packages correctly too" true
else
    check "an already-promoted tree packages correctly too" false
    sed 's/^/    | /' "$TMP/build2.log" | tail -n 10
fi

# And a tree without the database binding must be refused, not packaged.
BROKEN="$TMP/broken"
mkdir -p "$BROKEN"
cp -r "$STAGE/n8n" "$BROKEN/n8n"
if PAYLOAD_OUT_DIR="$TMP/out3" PAYLOAD_META_DIR="$TMP/meta3" \
    bash "$SCRIPTS_DIR/build-n8n-payload.sh" arm64-v8a --from-stage "$BROKEN" >"$TMP/build3.log" 2>&1; then
    check "a tree without the SQLite binding is refused" false
else
    check "a tree without the SQLite binding is refused" true
    grep -q "sqlite3" "$TMP/build3.log" \
        && check "and the refusal names the missing module" true \
        || check "and the refusal names the missing module" false
fi

echo
echo "n8n payload packaging self-test: $PASSED checks passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
