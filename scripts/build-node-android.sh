#!/usr/bin/env bash
#
# Cross-compile the Node.js engine for Android.
#
# Why this is done with upstream's own build system instead of shipping a
# prebuilt arm64 binary: Android refuses to execute files that did not arrive
# through the APK's lib/<abi>/ directory, so the engine has to be produced as an
# ELF for exactly these ABIs, linked against the NDK sysroot of minSdk 26. The
# result is installed as `jniLibs/<abi>/libnode.so`; it is an executable despite
# the .so name, which is the one place Android lets an app put executable code
# that survives app-data sandboxing and SELinux.
#
# Usage:
#   scripts/build-node-android.sh [abi ...]     # default: $DEFAULT_ABI
#
# Environment:
#   NODE_VERSION   pinned Node release (default 24.9.0, matches RuntimePins.kt)
#   ANDROID_NDK_HOME   NDK location; otherwise discovered from the SDK
#   JOBS           make -j (default: nproc)
#
# Output:
#   android/app/src/main/jniLibs/<abi>/libnode.so
#   android/app/src/main/jniLibs/<abi>/libc++_shared.so   (when the engine needs it)
#   .runtime-build/node/meta.json                          (digests for the manifest)

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ABIS=("$@")
[ ${#ABIS[@]} -gt 0 ] || ABIS=("$DEFAULT_ABI")

NDK="$(require_ndk)"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"

# Node's build runs its own tools (torque, mksnapshot, bytecode generators) on the
# build machine. android-configure exports only the target CC/CXX, and the make
# generator takes the host compiler from CC_host/CXX_host — so they are set here
# explicitly, or host tools can end up compiled by the Android toolchain.
export CC_host="${CC_host:-$(command -v cc || command -v gcc)}"
export CXX_host="${CXX_host:-$(command -v c++ || command -v g++)}"
export LINK_host="${LINK_host:-$CXX_host}"
export AR_host="${AR_host:-$(command -v ar)}"
[ -x "$CC_host" ] && [ -x "$CXX_host" ] || die "no host C/C++ compiler found (needed for Node's build tools)"
info "host toolchain: $CC_host / $CXX_host"
SRC_DIR="$BUILD_DIR/node-src"

info "Node $NODE_VERSION for Android, ABI(s): ${ABIS[*]}"
info "NDK: $NDK"
info "jobs: $JOBS"

# ---------------------------------------------------------------------------
# 1. Fetch the pinned Node source. Prefer the git tag so the exact commit is
#    reproducible; fall back to the release tarball when only HTTPS is available.
# ---------------------------------------------------------------------------
if [ ! -d "$SRC_DIR/.git" ] && [ ! -f "$SRC_DIR/configure.py" ]; then
    rm -rf "$SRC_DIR"
    if git clone --depth 1 --branch "v$NODE_VERSION" https://github.com/nodejs/node "$SRC_DIR" \
        >"$LOG_DIR/node-clone.log" 2>&1; then
        ok "cloned nodejs/node v$NODE_VERSION"
    else
        warn "git clone of nodejs/node failed: $(tail -n 2 "$LOG_DIR/node-clone.log" 2>/dev/null | tr '\n' ' ')"
        info "trying the release tarball instead"
        mkdir -p "$SRC_DIR"
        curl -fsSL "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION.tar.xz" -o "$BUILD_DIR/node.tar.xz" \
            || die "cannot download node v$NODE_VERSION (git clone also failed; see .runtime-build/logs/node-clone.log)"
        tar -xJf "$BUILD_DIR/node.tar.xz" -C "$SRC_DIR" --strip-components=1 \
            || die "cannot unpack node tarball"
    fi
fi
[ -f "$SRC_DIR/configure.py" ] || die "node source tree at $SRC_DIR is incomplete"

# ---------------------------------------------------------------------------
# Source patches
#
# Two edits stand between V8 and a bionic build, and both are verified rather
# than merely attempted — a patch that silently does not apply is worse than no
# patch at all, because the build then fails somewhere unrelated:
#
#  1. V8's WebAssembly trap handler is unsupported on Android. Node ships a patch
#     for that (`android-configure patch`), but it was written for V8 12 and no
#     longer applies to V8 13: the file gained Loong64 and RISC-V clauses and the
#     arm64-simulator clause changed. Left enabled, the arm64-on-x64 host build
#     links simulator trap-handler code that only exists when the handler is on,
#     and dies with three undefined references far from the cause.
#  2. V8 decides <execinfo.h> offers backtrace_symbols() by detecting a glibc-like
#     libc, and bionic ships an <execinfo.h> that declares none of those functions.
# ---------------------------------------------------------------------------
run_step "v8-trap-handler-patch" python3 "$SCRIPTS_DIR/lib/patch-v8-trap-handler.py" \
    "$SRC_DIR/deps/v8/src/trap-handler/trap-handler.h" \
    || die "cannot disable V8's trap handler for Android"

grep -q "#define V8_TRAP_HANDLER_SUPPORTED false" "$SRC_DIR/deps/v8/src/trap-handler/trap-handler.h" \
    || die "V8's trap handler is still enabled; the Android build cannot link"

run_step "v8-execinfo-patch" python3 "$SCRIPTS_DIR/lib/patch-v8-execinfo.py" \
    "$SRC_DIR/deps/v8/src/base/debug/stack_trace_posix.cc" \
    || die "cannot patch V8's stack trace source for bionic"

for ABI in "${ABIS[@]}"; do
    ARCH="$(abi_to_node_arch "$ABI")"

    info "configuring Node for $ABI ($ARCH)"
    export_android_python
    # android-configure sets CC/CXX to the NDK wrappers and runs ./configure with
    # --dest-os=android. It must be re-run per ABI in a clean out dir.
    (
        cd "$SRC_DIR"
        export ANDROID_NDK_HOME="$NDK"
        export GYP_DEFINES="android_ndk_path=$NDK"
        # `out/` is also what node-gyp reads when cross-compiling native modules
        # against these headers, so it keeps its default name.
        rm -rf "$SRC_DIR/out"
        # No `android-configure patch` here: its trap-handler patch no longer
        # applies to this V8 (see the source patches above), and it reports success
        # either way, which is how the failure came to look like a linker problem.
        ./android-configure "$NDK" "$ANDROID_API_LEVEL" "$ARCH" >"$LOG_DIR/node-configure-$ABI.log" 2>&1 \
            || {
                annotate_log_tail "android-configure $ABI" "$LOG_DIR/node-configure-$ABI.log" 6
                warn "android-configure failed: $(tail -n 3 "$LOG_DIR/node-configure-$ABI.log" 2>/dev/null | tr '\n' ' | ')"
                exit 1
            }
        # V=1 keeps the failing compile command in the log: knowing which compiler
        # and which include paths produced an error is the difference between a
        # fix and another guess.
        # configure's own exit status is swallowed by android-configure, so the
        # generated build files are what tells us whether it worked.
        if [ ! -f "$SRC_DIR/out/Makefile" ] || [ ! -f "$SRC_DIR/config.gypi" ]; then
            annotate_log_tail "android-configure $ABI" "$LOG_DIR/node-configure-$ABI.log" 10
            die "configure for $ABI produced no out/Makefile or config.gypi"
        fi
        ok "configured Node for $ABI"

        if ! make -j "$JOBS" V=1 >"$LOG_DIR/node-make-$ABI.log" 2>&1; then
            FAILING_LOG="$LOG_DIR/node-make-$ABI.log"
            annotate_excerpt "node build $ABI error" "$FAILING_LOG" 4
            FAILING_FILE="$(grep -oE '\.\./[^ :]+\.(cc|cpp|c)' "$FAILING_LOG" | head -n 1 || true)"
            if [ -n "$FAILING_FILE" ]; then
                annotate_log_matches "node build $ABI command" "$FAILING_LOG" "$(basename "$FAILING_FILE" | sed 's/\./\\./g')" 1
            fi
            annotate_log_tail "node build $ABI tail" "$FAILING_LOG" 8
            warn "make failed: $(log_excerpt "$FAILING_LOG" 2 | tr '\n' ' | ')"
            exit 1
        fi
    ) || die "Node build for $ABI failed (logs in $LOG_DIR/node-*-$ABI.log)"

    BINARY="$SRC_DIR/out/Release/node"
    [ -f "$BINARY" ] || BINARY="$(find "$SRC_DIR/out" -maxdepth 3 -name node -type f -perm -u+x 2>/dev/null | head -n 1)"
    [ -n "$BINARY" ] && [ -f "$BINARY" ] || die "Node build for $ABI produced no executable (expected $SRC_DIR/out/Release/node)"

    DEST="$JNI_DIR/$ABI"
    mkdir -p "$DEST"
    install -m 0755 "$BINARY" "$DEST/libnode.so"
    assert_elf_machine "$DEST/libnode.so" "$ABI"

    # Node's build leaves debug sections in, and on arm64 that is most of the file
    # (~100 MiB vs ~45 MiB stripped). The engine is shipped inside an APK, so the
    # symbols are pure cost for the user's download and install.
    STRIP_BIN="${STRIP:-}"
    if [ -z "$STRIP_BIN" ] || [ ! -x "$STRIP_BIN" ]; then
        STRIP_BIN="$(find_ndk | xargs -I{} echo "{}/toolchains/llvm/prebuilt/$(ndk_host_tag)/bin/llvm-strip")"
    fi
    if [ -x "$STRIP_BIN" ]; then
        BEFORE="$(size_of "$DEST/libnode.so")"
        if "$STRIP_BIN" --strip-unneeded "$DEST/libnode.so" 2>"$LOG_DIR/node-strip-$ABI.log"; then
            AFTER="$(size_of "$DEST/libnode.so")"
            ok "$ABI: stripped engine $(numfmt --to=iec "$BEFORE" 2>/dev/null || echo "$BEFORE") -> $(numfmt --to=iec "$AFTER" 2>/dev/null || echo "$AFTER")"
        else
            warn "stripping the engine for $ABI failed (keeping symbols): $(tail -n 2 "$LOG_DIR/node-strip-$ABI.log" 2>/dev/null | tr '\n' ' ')"
        fi
    else
        warn "no llvm-strip found for the engine; the APK will be considerably larger"
    fi

    # Android's linker runs binaries from lib/<abi>/ with the app's native
    # library path as the first search directory, so a NEEDED libc++_shared.so
    # placed next to the engine is found without LD_LIBRARY_PATH games.
    NEEDED="$(readelf -d "$DEST/libnode.so" 2>/dev/null | grep -o 'libc++_shared.so' | head -n 1 || true)"
    if [ -n "$NEEDED" ]; then
        TOOLCHAIN="$(find_ndk | xargs -I{} echo "{}/toolchains/llvm/prebuilt/$(ndk_host_tag)")"
        TRIPLE="$(abi_to_triple "$ABI")"
        for CANDIDATE in \
            "$TOOLCHAIN/sysroot/usr/lib/$TRIPLE/libc++_shared.so" \
            "$TOOLCHAIN/sysroot/usr/lib/$TRIPLE/${ANDROID_API_LEVEL}/libc++_shared.so"; do
            if [ -f "$CANDIDATE" ]; then
                install -m 0755 "$CANDIDATE" "$DEST/libc++_shared.so"
                break
            fi
        done
        # Older NDKs ship the runtimes in the legacy lib dir; ndk r27 keeps them
        # in sysroot/usr/lib/<triple>/ with an API-leveled symlink.
        if [ ! -f "$DEST/libc++_shared.so" ]; then
            FOUND="$(find "$TOOLCHAIN/sysroot/usr/lib" -name 'libc++_shared.so' -path "*$TRIPLE*" 2>/dev/null | head -n 1)"
            [ -n "$FOUND" ] && install -m 0755 "$FOUND" "$DEST/libc++_shared.so"
        fi
        [ -f "$DEST/libc++_shared.so" ] || die "engine for $ABI needs libc++_shared.so but the NDK sysroot scan found none"
        ok "$ABI: engine + libc++_shared.so installed"
    else
        rm -f "$DEST/libc++_shared.so"
        ok "$ABI: engine installed (static C++ runtime)"
    fi

    # Record what was actually produced. The manifest generator only trusts this
    # file, never a hand-written claim.
    SIZE="$(size_of "$DEST/libnode.so")"
    DIGEST="$(sha256_of "$DEST/libnode.so")"
    printf '%s  %s  %s bytes\n' "$ABI" "$DIGEST" "$SIZE" >>"$BUILD_DIR/node/abis.txt"
    ok "$ABI: libnode.so $(numfmt --to=iec "$SIZE" 2>/dev/null || echo "$SIZE bytes") sha256=$DIGEST"
done

# ---------------------------------------------------------------------------
# 2. Emit the Node section of the runtime manifest.
# ---------------------------------------------------------------------------
mkdir -p "$BUILD_DIR/node"
{
    printf '{\n'
    printf '  "version": "%s",\n' "$NODE_VERSION"
    printf '  "distOs": "android",\n'
    printf '  "flavor": "android-executable",\n'
    printf '  "library": "libnode.so",\n'
    printf '  "apiLevel": %s,\n' "$ANDROID_API_LEVEL"
    printf '  "abis": {\n'
    first=1
    for ABI in "${ABIS[@]}"; do
        [ $first -eq 1 ] || printf ',\n'
        first=0
        printf '    "%s": { "sha256": "%s", "sizeBytes": %s }' \
            "$ABI" "$(sha256_of "$JNI_DIR/$ABI/libnode.so")" "$(size_of "$JNI_DIR/$ABI/libnode.so")"
    done
    printf '\n  }\n'
    printf '}\n'
} >"$BUILD_DIR/node/node.json"

# Non-empty extra libs, derived from what is actually next to the engine.
EXTRA="$(cd "$JNI_DIR" && ls */libc++_shared.so 2>/dev/null | sed 's|.*/||' | sort -u | tr '\n' ' ' | sed 's/ $//')"
python3 - "$BUILD_DIR/node/node.json" "$EXTRA" <<'PY'
import json, sys, pathlib
path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text())
data["extraLibs"] = sys.argv[2].split() if sys.argv[2].strip() else []
path.write_text(json.dumps(data, indent=2) + "\n")
PY

ok "Node engine ready: $BUILD_DIR/node/node.json"
