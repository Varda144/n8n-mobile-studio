#!/usr/bin/env bash
# Shared helpers for the runtime payload build scripts.
#
# These scripts run inside GitHub Actions (see .github/workflows/runtime-build.yml)
# where the job log is not always readable from the phone-first development setup
# this project is built with. Every helper therefore publishes failures as
# GitHub *annotations* (`::error::`), which `gh run view` can show, in addition to
# writing a full log file under .runtime-build/logs/.

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
BUILD_DIR="${BUILD_DIR:-$REPO_ROOT/.runtime-build}"
LOG_DIR="$BUILD_DIR/logs"
MANIFEST="$REPO_ROOT/android/app/src/main/assets/runtime/manifest.json"
ASSETS_RUNTIME="$REPO_ROOT/android/app/src/main/assets/runtime"
JNI_LIBS="$REPO_ROOT/android/app/src/main/jniLibs"

# Pinned versions. Changing these is a deliberate act: the manifest records the
# exact pins, and scripts/verify-runtime.sh fails if code and manifest disagree.
NODE_VERSION="${NODE_VERSION:-v24.9.0}"
N8N_VERSION="${N8N_VERSION:-2.38.7}"
OPENCODE_VERSION="${OPENCODE_VERSION:-1.18.30}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-26}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-27.0.12077973}"

# Payload archive name inside assets/runtime/<component>/ (RuntimePins.PAYLOAD_ARCHIVE).
PAYLOAD_ARCHIVE_NAME="${PAYLOAD_ARCHIVE_NAME:-payload.zip}"

mkdir -p "$LOG_DIR"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*"; }
die() {
    printf '\033[1;31merror:\033[0m %s\n' "$*" >&2
    # One annotation per line so the message survives into the run summary.
    printf '%s\n' "$*" | tr -d '\r' | head -c 900 | while IFS= read -r line; do
        [ -n "$line" ] && echo "::error::$line"
    done
    exit 1
}

# annotate_file <label> <file> — surface the tail of a log as annotations.
annotate_file() {
    local label="$1" file="$2" count="${3:-8}"
    [ -f "$file" ] || return 0
    tail -n "$count" "$file" | tr -d '\r' | sed 's/%/%25/g; s/\r//g' | while IFS= read -r line; do
        [ -n "$line" ] && printf '::error::%s: %s\n' "$label" "${line:0:900}"
    done
}

# run_step <name> <command...> — run with a log file, annotate on failure.
run_step() {
    local name="$1"
    shift
    local log="$LOG_DIR/$name.log"
    info "$name"
    if "$@" >"$log" 2>&1; then
        tail -n 3 "$log" | sed 's/^/    /'
        return 0
    fi
    local status=$?
    echo "::group::$name failed (exit $status)"
    tail -n 30 "$log"
    echo "::endgroup::"
    annotate_file "$name" "$log" 8
    return $status
}

# find_ndk — locate an installed Android NDK, installing one via sdkmanager if
# needed (the CI job only installs platform + build-tools).
find_ndk() {
    if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        echo "$ANDROID_NDK_HOME"
        return 0
    fi
    if [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
        echo "$ANDROID_NDK_ROOT"
        return 0
    fi
    local roots=()
    [ -n "${ANDROID_HOME:-}" ] && roots+=("$ANDROID_HOME/ndk")
    [ -n "${ANDROID_SDK_ROOT:-}" ] && roots+=("$ANDROID_SDK_ROOT/ndk")
    roots+=("$HOME/Android/Sdk/ndk" "/usr/local/lib/android/sdk/ndk")
    local root candidate
    for root in "${roots[@]}"; do
        [ -d "$root" ] || continue
        candidate="$(find "$root" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
        [ -n "$candidate" ] && [ -d "$candidate/toolchains/llvm/prebuilt" ] && { echo "$candidate"; return 0; }
    done

    command -v sdkmanager >/dev/null 2>&1 || die "no Android NDK and no sdkmanager available"
    info "installing NDK $ANDROID_NDK_VERSION via sdkmanager (this takes a few minutes)"
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager --install "ndk;$ANDROID_NDK_VERSION" >"$LOG_DIR/ndk-install.log" 2>&1 ||
        { annotate_file "ndk-install" "$LOG_DIR/ndk-install.log"; die "sdkmanager could not install NDK $ANDROID_NDK_VERSION"; }
    for root in "${roots[@]}"; do
        [ -d "$root/$ANDROID_NDK_VERSION" ] && { echo "$root/$ANDROID_NDK_VERSION"; return 0; }
    done
    die "NDK installed but not found on disk"
}

# sha256_of <file>
sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

size_of() { wc -c <"$1" | tr -d ' '; }
