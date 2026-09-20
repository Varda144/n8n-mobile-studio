# Build

## Prerequisites

- **JDK 17** (Temurin or equivalent)
- **Android SDK** with:
  - `platforms;android-36`
  - `build-tools;35.0.0`

## Debug build

```bash
cd android
chmod +x gradlew
./gradlew assembleDebug
```

Output APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Release build

```bash
cd android
./gradlew assembleRelease
```

Output APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

Release signing requires keystore secrets configured in `gradle.properties` or environment variables.

## Builds with the embedded runtimes

An APK can contain the Node engine and the n8n payload (see
`docs/RUNTIME_PAYLOADS.md`). That happens when the branch is listed in
`runtime-payload.request` **and** an assemble task is requested:

```bash
printf 'my-branch\n' > runtime-payload.request
cd android && ./gradlew assembleDebug      # tens of minutes: cross-compiles Node
```

Payloads are produced before assets and native libraries are merged, and the
build fails if a runtime it advertises is not really in the APK. `./gradlew test`
and `./gradlew lint` never build payloads, and branches not listed get a fast
app-only APK whose runtimes report NOT_PACKAGED.

Requirements for a payload build: NDK (auto-installed from the Android SDK if
missing), Node.js on the build host, `npm`, `python3`, `make`, `curl` and `git`.

### Source patches the engine build depends on

Cross-compiling Node for Android needs two edits to the V8 sources, and both are
verified rather than merely attempted — a patch that silently does not apply turns
into an unrelated failure much further downstream:

| Patch | Why | Applied by |
| --- | --- | --- |
| `V8_TRAP_HANDLER_SUPPORTED false` | The WebAssembly trap handler is unsupported on Android. Left enabled, an arm64-on-x64 build links simulator code that only exists when the handler is on, and dies with undefined `trap_handler`/`v8_internal_simulator_ProbeMemory` references. | `scripts/lib/patch-v8-trap-handler.py` |
| `<execinfo.h>` guard excludes Android | Bionic ships the header without `backtrace_symbols()`, so V8 must use its pointer-only fallback. | `scripts/lib/patch-v8-execinfo.py` |

Node's own `android-configure patch` covers the first case upstream, but its patch
was written for the V8 12 line and no longer applies to V8 13 (the file gained
Loong64 and RISC-V clauses and the arm64-simulator clause changed). It fails while
still printing that it tried, which is why the build now patches the header itself,
asserts the result before configuring, and refuses to continue otherwise.

## GitHub Actions

Every push runs `lint`, `security` and `test`, plus `build-debug.yml`, which
builds `:app:assembleDebug` with Gradle 8.13 on JDK 17 and uploads the APK as the
`n8n-mobile-studio-debug` artifact. On a branch listed in `runtime-payload.request`
that same job builds the runtimes first, so the artifact is the full APK.

Because failed job logs are not always reachable from a phone, the build reports
its own failures: `android/gradlew` (used by lint/test) and a
`gradle.buildFinished` hook in `android/build.gradle` (used by the APK workflow,
which invokes Gradle directly) republish compiler and task errors as `::error::`
annotations, visible with `gh run view`.

`runtime-build.yml` (push to `main` or manual dispatch) builds the payloads and
verifies the manifest without producing an APK.

## Speeding up payload builds (optional)

The Node cross-compile and the n8n install dominate a payload build. Both are
pinned and deterministic, so caching their outputs turns a rebuild from about an
hour into a couple of minutes. Applying
[`docs/ci/node-and-n8n-cache.patch`](ci/node-and-n8n-cache.patch) to
`.github/workflows/build-debug.yml` does exactly that:

```bash
git apply docs/ci/node-and-n8n-cache.patch
```

The patch is kept as a patch rather than applied here because pushing changes to
`.github/workflows/` requires a token with the `workflows` scope, which the
automation that maintains this branch does not have. The cache keys include
`RuntimePins.kt` and the payload build script, so a pin or recipe change misses the
cache and rebuilds instead of shipping a stale payload.
