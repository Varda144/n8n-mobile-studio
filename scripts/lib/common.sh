#!/usr/bin/env bash
# Shared helpers for the runtime payload build.
#
# The payload build cross-compiles Node.js for Android and packages n8n on top
# of it. It is the only place allowed to decide what "embedded" means, and it
# must never leave the repository in a state where the manifest claims a runtime
# that the APK does not actually contain.
#
# Sourced by:
#   scripts/prepare-runtime.sh        (orchestrator)
#   scripts/build-node-android.sh     (Node engine -> jniLibs/<abi>/libnode.so)
#   scripts/build-n8n-payload.sh      (npm tree -> assets/runtime/n8n/payload.zip)
#   scripts/build-opencode-payload.sh (source build -> assets/runtime/opencode/payload.zip)
#   scripts/verify-runtime.sh         (manifest <-> artifacts cross-check)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# Pins. These MUST match kotlin/com/n8n/mobile/studio/runtime/RuntimePins.kt;
# scripts/verify-runtime.sh fails the build when they drift.
# ---------------------------------------------------------------------------
NODE_VERSION="${NODE_VERSION:-24.9.0}"
N8N_VERSION="${N8N_VERSION:-2.38.7}"
OPENCODE_VERSION="${OPENCODE_VERSION:-1.18.30}"

# n8n 2.x requires Node >= 24; the Android API level is the minSdk of the app
# (26) because the NDK sysroot must not expose symbols older devices lack.
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-26}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-27.0.12077973}"

DEFAULT_ABI="${DEFAULT_ABI:-arm64-v8a}"

APP_DIR="$REPO_ROOT/android/app/src/main"
JNI_DIR="$APP_DIR/jniLibs"
ASSETS_RUNTIME_DIR="$APP_DIR/assets/runtime"
BUILD_DIR="$REPO_ROOT/.runtime-build"
LOG_DIR="${LOG_DIR:-$BUILD_DIR/logs}"
PAYLOAD_ARCHIVE_NAME="${PAYLOAD_ARCHIVE_NAME:-payload.zip}"

mkdir -p "$BUILD_DIR" "$LOG_DIR"

# Names used by the other scripts in this directory (kept in one place so a
# renamed directory cannot silently point a check at nothing).
MANIFEST="$ASSETS_RUNTIME_DIR/manifest.json"
JNI_LIBS="$JNI_DIR"
ASSETS_DIR="$ASSETS_RUNTIME_DIR"

C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_BLUE=$'\033[34m'

info()  { printf '%s==>%s %s\n' "$C_BLUE$C_BOLD" "$C_RESET" "$*"; }
ok()    { printf '%s ok %s %s\n' "$C_GREEN$C_BOLD" "$C_RESET" "$*"; }

# Warnings and failures are republished as GitHub annotations: the payload build
# runs inside a Gradle Exec task whose output is not part of the exception chain,
# so without this a failed cross-compile would say nothing at all.
warn() {
    printf '%swarn%s %s\n' "$C_YELLOW$C_BOLD" "$C_RESET" "$*" >&2
    annotate warning "$*"
}

die() {
    printf '%sFAIL%s %s\n' "$C_RED$C_BOLD" "$C_RESET" "$*" >&2
    annotate_error "runtime payload build: $*"
    exit 1
}

# GitHub shows these in the run summary even when the raw log is out of reach
# (the project is developed from a phone, where the log download endpoint is
# often unavailable).
annotate() {
    local level="$1"; shift
    [ -n "${GITHUB_ACTIONS:-}" ] || return 0
    printf '::%s::%s\n' "$level" "$*"
}
annotate_error() { annotate error "$*"; }

# Run a step, tee its output into .runtime-build/logs/, and turn a non-zero exit
# into both a hard failure and a GitHub annotation.
run_step() {
    local name="$1"; shift
    local log="$LOG_DIR/${name}.log"
    info "$name: $*"
    if ! "$@" >"$log" 2>&1; then
        warn "step '$name' failed; last 40 log lines from $log:"
        tail -n 40 "$log" >&2 || true
        annotate_error "$name failed: $(tail -n 3 "$log" | tr '\n' ' ')"
        return 1
    fi
    ok "$name"
}

log_tail() { tail -n "${2:-20}" "$LOG_DIR/${1}.log" 2>/dev/null || true; }

# The first real error in a build log, not the last line: make reports its own
# bookkeeping last ("Error 2", intermediate files), the cause is further up. Each
# excerpt line is also published as its own annotation, because that is the only
# part of a Gradle Exec failure a phone can read.
log_excerpt() {
    local log="$1" max="${2:-4}" line
    grep -nE 'error:|fatal error|undefined reference|No such file or directory|recipe for target|Error [0-9]+$' \
        "$log" 2>/dev/null | head -n "$max" || true
}

annotate_excerpt() {
    local label="$1" log="$2" max="${3:-4}" line
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        annotate_error "$label: $line"
    done < <(log_excerpt "$log" "$max")
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

size_of() { wc -c <"$1" | tr -d '[:space:]'; }

# ---------------------------------------------------------------------------
# NDK discovery. The build needs a real NDK: nodejs' android-configure refuses to
# run without one, and there is no way to cross-compile the engine without it.
# ---------------------------------------------------------------------------
ndk_host_tag() {
    case "$(uname -s)" in
        Linux)  echo "linux-x86_64" ;;
        Darwin) echo "darwin-x86_64" ;;
        *)      die "unsupported build host: $(uname -s)" ;;
    esac
}

find_ndk() {
    if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        echo "$ANDROID_NDK_HOME"; return 0
    fi
    if [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
        echo "$ANDROID_NDK_ROOT"; return 0
    fi
    local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [ -z "$sdk" ]; then
        for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" /usr/local/lib/android/sdk /opt/android-sdk; do
            [ -d "$candidate" ] && sdk="$candidate" && break
        done
    fi
    if [ -n "$sdk" ] && [ -d "$sdk/ndk" ]; then
        local newest
        newest="$(ls -1 "$sdk/ndk" 2>/dev/null | sort -V | tail -n 1 || true)"
        if [ -n "$newest" ]; then
            echo "$sdk/ndk/$newest"; return 0
        fi
    fi
    if [ -n "$sdk" ] && [ -x "$sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
        warn "no NDK found; installing ndk;$ANDROID_NDK_VERSION with sdkmanager"
        yes | "$sdk/cmdline-tools/latest/bin/sdkmanager" --install "ndk;$ANDROID_NDK_VERSION" >/dev/null 2>&1 || true
        if [ -d "$sdk/ndk/$ANDROID_NDK_VERSION" ]; then
            echo "$sdk/ndk/$ANDROID_NDK_VERSION"; return 0
        fi
    fi
    return 1
}

require_ndk() {
    local ndk
    ndk="$(find_ndk || true)"
    [ -n "$ndk" ] || die "Android NDK not found. Set ANDROID_NDK_HOME (or install ndk;$ANDROID_NDK_VERSION). Cross-compiling Node for Android is impossible without it."
    [ -d "$ndk/toolchains/llvm/prebuilt/$(ndk_host_tag)" ] || die "NDK at $ndk has no llvm toolchain for $(ndk_host_tag)"
    echo "$ndk"
}

# Map an Android ABI to the triples/paths the NDK and Node's build use.
abi_to_node_arch() {
    case "$1" in
        arm64-v8a)   echo "arm64" ;;
        armeabi-v7a) echo "arm" ;;
        x86_64)      echo "x86_64" ;;
        x86)         echo "x86" ;;
        *) die "unsupported ABI: $1 (expected arm64-v8a, armeabi-v7a, x86_64 or x86)" ;;
    esac
}

abi_to_triple() {
    case "$1" in
        arm64-v8a)   echo "aarch64-linux-android" ;;
        armeabi-v7a) echo "armv7a-linux-androideabi" ;;
        x86_64)      echo "x86_64-linux-android" ;;
        x86)         echo "i686-linux-android" ;;
        *) die "unsupported ABI: $1" ;;
    esac
}

# `android-configure` is a Python shim that only accepts 3.9–3.13 and refuses to
# run otherwise; runners often ship a newer default. Build a shim directory that
# exposes an acceptable interpreter under the names the shim looks for, and pass
# it in PATH so upstream's own logic (including its patch mode) still runs.
export_android_python() {
    local candidate version bin
    for candidate in python3.13 python3.12 python3.11 python3.10 python3.9 python3 python; do
        bin="$(command -v "$candidate" 2>/dev/null || true)"
        [ -n "$bin" ] || continue
        version="$("$bin" -c 'import sys; print("%d.%d" % sys.version_info[:2])' 2>/dev/null || true)"
        case "$version" in
            3.9|3.10|3.11|3.12|3.13) ANDROID_PYTHON="$bin"; break ;;
        esac
    done
    [ -n "${ANDROID_PYTHON:-}" ] || die "no Python 3.9–3.13 available; node's android-configure cannot run"
    local shim_dir="$BUILD_DIR/python-shim"
    rm -rf "$shim_dir"; mkdir -p "$shim_dir"
    for name in python3.13 python3.12 python3.11 python3.10 python3.9; do
        ln -sf "$ANDROID_PYTHON" "$shim_dir/$name"
    done
    ln -sf "$ANDROID_PYTHON" "$shim_dir/python3"
    export PATH="$shim_dir:$PATH"
    info "android-configure will use $ANDROID_PYTHON ($("$ANDROID_PYTHON" -V 2>&1))"
}

# Export CC/CXX/AR/... so node-gyp (better-sqlite3 for n8n) uses the NDK.
export_android_toolchain() {
    local abi="$1" ndk="$2"
    local host_tag toolchain triple
    host_tag="$(ndk_host_tag)"
    toolchain="$ndk/toolchains/llvm/prebuilt/$host_tag"
    triple="$(abi_to_triple "$abi")"

    export ANDROID_NDK_HOME="$ndk"
    export ANDROID_NDK_PATH="$ndk"
    export TOOLCHAIN="$toolchain"
    export TARGET_TRIPLE="$triple"
    export CC="$toolchain/bin/${triple}${ANDROID_API_LEVEL}-clang"
    export CXX="$toolchain/bin/${triple}${ANDROID_API_LEVEL}-clang++"
    export AR="$toolchain/bin/llvm-ar"
    export RANLIB="$toolchain/bin/llvm-ranlib"
    export STRIP="$toolchain/bin/llvm-strip"
    export LD="$toolchain/bin/ld.lld"
    # node-gyp's Android path reads these through node's common.gypi.
    export GYP_DEFINES="android_ndk_path=$ndk android_target_arch=$(abi_to_node_arch "$abi") host_os=linux OS=android target_arch=$(abi_to_node_arch "$abi")"
    export CFLAGS="-fPIC -D__ANDROID_API__=$ANDROID_API_LEVEL"
    export CXXFLAGS="$CFLAGS"
    export LDFLAGS="-L$toolchain/sysroot/usr/lib/$triple/$ANDROID_API_LEVEL"
    [ -x "$CC" ] || die "compiler for $abi not found at $CC (NDK $ndk, API $ANDROID_API_LEVEL)"
}

# npm's spelling of the same architectures (used by node-gyp).
node_arch_to_npm_arch() {
    case "$1" in
        arm64-v8a)   echo "arm64" ;;
        armeabi-v7a) echo "arm" ;;
        x86_64)      echo "x64" ;;
        x86)         echo "ia32" ;;
        *) die "unsupported ABI: $1" ;;
    esac
}

# ELF machine name reported by `readelf -h`, used to prove that a compiled
# artifact really targets the ABI it claims (and to drop foreign binaries).
abi_to_elf_machine() {
    case "$1" in
        arm64-v8a)   echo "AArch64" ;;
        armeabi-v7a) echo "ARM" ;;
        x86_64)      echo "X86-64" ;;
        x86)         echo "Intel 80386" ;;
        *) die "unsupported ABI: $1" ;;
    esac
}

elf_machine_of() {
    readelf -h "$1" 2>/dev/null | awk -F: '/Machine:/ {gsub(/^[ \t]+/, "", $2); print $2}' | head -n 1
}

assert_elf_machine() {
    local file="$1" abi="$2" expected actual
    expected="$(abi_to_elf_machine "$abi")"
    actual="$(elf_machine_of "$file")"
    [ "$actual" = "$expected" ] || die "$file is $actual, expected $expected for $abi"
}

write_meta_json() {
    # write_meta_json <component> <json>
    local component="$1" json="$2"
    mkdir -p "$BUILD_DIR/$component"
    printf '%s\n' "$json" >"$BUILD_DIR/$component/meta.json"
    ok "wrote .runtime-build/$component/meta.json"
}
