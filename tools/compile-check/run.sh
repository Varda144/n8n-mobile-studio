#!/usr/bin/env bash
# Offline compile + unit-test check for the runtime core.
#
# Why this exists: the Android app can only be built by Gradle against Maven and
# the Android SDK. When that is unreachable (offline development), this script
# still type-checks the pure-JVM runtime core and *runs* its unit tests using a
# JDK fetched from PyPI and the Kotlin compiler fetched from npm.
#
# Scope: core/, runtime/, terminal/ and the non-Compose parts of the Android glue.
# It is a development aid, not a replacement: Gradle in CI remains authoritative,
# and Compose UI code is only compiled there.
#
# Usage:
#   tools/compile-check/run.sh              # compile + run tests
#   tools/compile-check/run.sh --compile    # compile only
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CACHE="${N8N_STUDIO_CHECK_CACHE:-/tmp/n8n-studio-check-toolchain}"
KOTLIN_VERSION="${N8N_STUDIO_KOTLIN_VERSION:-2.4.20}"
OUT="$CACHE/out"
CLASSES="$CACHE/classes"
TEST_CLASSES="$CACHE/test-classes"

# ---------------------------------------------------------------- 1. toolchain
JDK_HOME="$CACHE/jdk"
KOTLINC_HOME="$CACHE/kotlinc"

if [ ! -x "$JDK_HOME/bin/java" ]; then
    echo "==> fetching JDK (pip: jdk4py)"
    mkdir -p "$CACHE/pip-target"
    pip3 install --quiet --target "$CACHE/pip-target" jdk4py >/dev/null
    JDK_JAVA="$(find "$CACHE/pip-target" -path '*java-runtime/bin/java' -type f | head -1)"
    [ -n "$JDK_JAVA" ] || { echo "no JDK found in jdk4py package"; exit 1; }
    ln -sfn "$(dirname "$(dirname "$JDK_JAVA")")" "$JDK_HOME"
fi

if [ ! -x "$KOTLINC_HOME/bin/kotlinc" ]; then
    echo "==> fetching Kotlin compiler $KOTLIN_VERSION (npm)"
    mkdir -p "$CACHE/kotlinc-download"
    curl -sSL -o "$CACHE/kotlin-compiler.tgz" \
        "https://registry.npmjs.org/kotlin-compiler/-/kotlin-compiler-$KOTLIN_VERSION.tgz"
    tar xzf "$CACHE/kotlin-compiler.tgz" -C "$CACHE/kotlinc-download"
    ln -sfn "$CACHE/kotlinc-download/package" "$KOTLINC_HOME"
fi

export JAVA_HOME="$JDK_HOME"
export PATH="$JDK_HOME/bin:$PATH"
STDLIB="$KOTLINC_HOME/lib/kotlin-stdlib.jar"
COROUTINES="$KOTLINC_HOME/lib/kotlinx-coroutines-core-jvm.jar"

echo "==> java:     $("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
echo "==> kotlinc:  $("$KOTLINC_HOME/bin/kotlinc" -version 2>&1 | tail -1)"

# ---------------------------------------------------------------- 2. sources
MAIN_SOURCES=()
while IFS= read -r pattern; do
    [ -z "$pattern" ] && continue
    # shellcheck disable=SC2086
    for file in $ROOT/$pattern; do
        [ -f "$file" ] && MAIN_SOURCES+=("$file")
    done
done < "$HERE/sources.txt"

STUBS=()
for file in "$HERE"/stubs/*.kt; do
    STUBS+=("$file")
done

TEST_SOURCES=()
while IFS= read -r file; do
    TEST_SOURCES+=("$file")
done < <(find "$ROOT/android/app/src/test/kotlin" -name '*.kt' 2>/dev/null | sort)

echo "==> compiling ${#MAIN_SOURCES[@]} runtime sources + ${#STUBS[@]} stubs"
rm -rf "$CLASSES" && mkdir -p "$CLASSES" "$OUT"
"$KOTLINC_HOME/bin/kotlinc" \
    -nowarn -jvm-target 17 -Xallow-kotlin-package \
    -classpath "$STDLIB:$COROUTINES" \
    -d "$CLASSES" \
    "${MAIN_SOURCES[@]}" "${STUBS[@]}" "$HERE/TestRunner.kt" 2>&1 | tee "$CACHE/compile.log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
    echo "==> COMPILE FAILED"
    exit "$status"
fi

if [ "${1:-}" = "--compile" ]; then
    echo "==> compile OK"
    exit 0
fi

# ---------------------------------------------------------------- 3. tests
if [ "${#TEST_SOURCES[@]}" -eq 0 ]; then
    echo "==> no tests found"
    exit 0
fi

echo "==> compiling ${#TEST_SOURCES[@]} test sources"
rm -rf "$TEST_CLASSES" && mkdir -p "$TEST_CLASSES"
"$KOTLINC_HOME/bin/kotlinc" \
    -nowarn -jvm-target 17 -Xallow-kotlin-package \
    -classpath "$STDLIB:$COROUTINES:$CLASSES" \
    -d "$TEST_CLASSES" \
    "${TEST_SOURCES[@]}" 2>&1 | tee "$CACHE/test-compile.log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
    echo "==> TEST COMPILE FAILED"
    exit "$status"
fi

echo "==> running tests"
"$JDK_HOME/bin/java" -cp "$STDLIB:$COROUTINES:$CLASSES:$TEST_CLASSES" \
    com.n8n.mobile.studio.check.TestRunner "$TEST_CLASSES" "$TEST_CLASSES"
