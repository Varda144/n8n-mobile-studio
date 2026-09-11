#!/usr/bin/env bash
# Cross-compile Node.js for Android and install it as a jniLib.
#
# Android refuses to execute app-owned files unless they ship as native
# libraries, so the runtime engine is built as an executable and packaged under
# `jniLibs/<abi>/libnode.so` (with `extractNativeLibs` enabled, it lands in
# `ApplicationInfo.nativeLibraryDir`, which the app is allowed to exec).
#
# Uses upstream Node's own Android support (`./android-configure`), which is the
# supported way to build Node for Android and is what React Native and
# nodejs-mobile build on. Nothing here patches Node's source beyond the patch
# file upstream ships for the V8 trap handler.
#
# Usage: scripts/build-node-android.sh [abi ...]      (default: arm64-v8a)
# Env:   NODE_VERSION (default v24.9.0), ANDROID_API_LEVEL (default 26),
#        ANDROID_NDK_HOME (auto-detected / installed via sdkmanager)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

ABIS=("$@")
[ "${#ABIS[@]}" -eq 0 ] && ABIS=("arm64-v8a")

arch_for_abi() {
    case "$1" in
        arm64-v8a) echo "arm64" ;;
        armeabi-v7a) echo "arm" ;;
        x86_64) echo "x86_64" ;;
        x86) echo "x86" ;;
        *) die "unsupported ABI: $1" ;;
    esac
}

NDK="$(find_ndk)"
info "NDK:          $NDK"
info "Node:         $NODE_VERSION"
info "Android API:  $ANDROID_API_LEVEL"
info "ABIs:         ${ABIS[*]}"

SOURCE_DIR="$BUILD_DIR/node-$NODE_VERSION"
if [ ! -d "$SOURCE_DIR" ]; then
    mkdir -p "$BUILD_DIR"
    # GitHub's source tarball is used because it is reachable from every
    # environment this project builds in (nodejs.org dist is the fallback).
    URL="https://github.com/nodejs/node/archive/refs/tags/$NODE_VERSION.tar.gz"
    mkdir -p "$SOURCE_DIR"
    run_step "fetch-node-source" bash -c \
        "curl -fsSL '$URL' | tar xz -C '$SOURCE_DIR' --strip-components=1" ||
        run_step "fetch-node-source-dist" bash -c \
            "curl -fsSL 'https://nodejs.org/dist/$NODE_VERSION/node-$NODE_VERSION.tar.xz' | tar xJ -C '$SOURCE_DIR' --strip-components=1"
fi
[ -f "$SOURCE_DIR/configure" ] || die "Node source tree at $SOURCE_DIR looks incomplete"

mkdir -p "$JNI_LIBS"

for ABI in "${ABIS[@]}"; do
    ARCH="$(arch_for_abi "$ABI")"
    info "building Node for $ABI ($ARCH)"
    WORK="$BUILD_DIR/node-build-$ABI"
    rm -rf "$WORK"
    cp -a "$SOURCE_DIR" "$WORK"

    # The patches directory ships with Node itself; applying the trap-handler
    # patch is what upstream's android-configure documents.
    (cd "$WORK" && ./android-configure patch >"$LOG_DIR/node-patch-$ABI.log" 2>&1 || true)

    # Build flags, in one place so they are auditable:
    #   --without-intl   : drops ICU (~30 MB) and needs no ICU data on device
    #   --openssl-no-asm : set by android-configure; ARM asm needs per-ABI tuning
    #   --without-npm    : payloads are installed at build time, not on device
    #   --without-corepack
    # RPATH=$ORIGIN lets the binary find lib/node libs next to itself in
    # nativeLibraryDir, which is the only directory Android lets it exec from.
    if ! (cd "$WORK" && \
            LDFLAGS="-Wl,-rpath,\$ORIGIN" \
            ./android-configure "$NDK" "$ANDROID_API_LEVEL" "$ARCH" \
                >"$LOG_DIR/node-configure-$ABI.log" 2>&1 && \
            LDFLAGS="-Wl,-rpath,\$ORIGIN" make -j"$(nproc)" \
                >"$LOG_DIR/node-make-$ABI.log" 2>&1); then
        annotate_file "node-make-$ABI" "$LOG_DIR/node-make-$ABI.log" 10
        annotate_file "node-configure-$ABI" "$LOG_DIR/node-configure-$ABI.log" 6
        die "Node build failed for $ABI (see annotations above)"
    fi

    BINARY="$WORK/out/Release/node"
    [ -x "$BINARY" ] || die "expected $BINARY after building $ABI"

    # Prove the artifact is really an ARM64/ARM Android executable before it is
    # packaged: a host binary sneaking into jniLibs would fail on device.
    if command -v file >/dev/null 2>&1; then
        FILE_INFO="$(file -b "$BINARY")"
        info "  $FILE_INFO"
        case "$ABI:$FILE_INFO" in
            arm64-v8a:*aarch64*|arm64-v8a:*ARM\ aarch64*) ;;
            armeabi-v7a:*ARM*|armeabi-v7a:*armv7*) ;;
            x86_64:*x86-64*) ;;
            *) warn "built binary for $ABI does not look like $ABI: $FILE_INFO" ;;
        esac
    fi

    mkdir -p "$JNI_LIBS/$ABI"
    install -m 0755 "$BINARY" "$JNI_LIBS/$ABI/libnode.so"

    # libc++_shared.so: the NDK's C++ runtime, packaged next to libnode.so so the
    # runner resolves it via $ORIGIN at exec time.
    TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
    TRIPLE="$(case "$ABI" in
        arm64-v8a) echo aarch64-linux-android ;;
        armeabi-v7a) echo arm-linux-androideabi ;;
        x86_64) echo x86_64-linux-android ;;
        x86) echo i686-linux-android ;;
    esac)"
    for CANDIDATE in \
        "$TOOLCHAIN/sysroot/usr/lib/$TRIPLE/libc++_shared.so" \
        "$TOOLCHAIN/sysroot/usr/lib/$TRIPLE/$ANDROID_API_LEVEL/libc++_shared.so"; do
        if [ -f "$CANDIDATE" ]; then
            install -m 0755 "$CANDIDATE" "$JNI_LIBS/$ABI/libc++_shared.so"
            info "  packaged libc++_shared.so from $CANDIDATE"
            break
        fi
    done

    NODE_BINARY_SHA="$(sha256_of "$BINARY")"
    NODE_BINARY_SIZE="$(size_of "$BINARY")"
    printf '%s\n' "$NODE_BINARY_SHA" >"$JNI_LIBS/$ABI/libnode.so.sha256"
    printf '%s\n' "$NODE_BINARY_SIZE" >"$JNI_LIBS/$ABI/libnode.so.size"
    info "  sha256=$NODE_BINARY_SHA size=$NODE_BINARY_SIZE"
done

info "Node payload ready: $(ls -1 "$JNI_LIBS")"
