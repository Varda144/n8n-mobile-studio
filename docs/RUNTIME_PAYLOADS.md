# Runtime payloads

"Embedded" is a claim, not an intention. This document describes how the two
runtimes get into the APK, and the checks that stop the build from pretending.

## What goes into the APK

| Artifact | Path in the APK | Produced by |
| --- | --- | --- |
| Node.js engine | `lib/arm64-v8a/libnode.so` (+ `libc++_shared.so` when needed) | `scripts/build-node-android.sh` |
| n8n | `assets/runtime/n8n/payload.zip` | `scripts/build-n8n-payload.sh` |
| OpenCode | `assets/runtime/opencode/payload.zip` | `scripts/build-opencode-payload.sh` |
| Contract | `assets/runtime/manifest.json` | `scripts/generate-runtime-manifest.py` |

Payload sources are build outputs and are git-ignored: they are hundreds of
megabytes of pinned third-party code. The manifest — digests, sizes, entry
points, ports, health paths — is committed, so the app always has a contract to
read and a reviewer can see what a build claims.

## Building

```bash
# everything, for the default ABI (arm64-v8a)
scripts/prepare-runtime.sh

# knobs
RUNTIME_ABIS="arm64-v8a x86_64" scripts/prepare-runtime.sh
SKIP_NODE_BUILD=1 scripts/prepare-runtime.sh     # reuse an existing engine
SKIP_N8N=1 SKIP_OPENCODE=1 scripts/prepare-runtime.sh
REQUIRE_PACKAGED="node n8n opencode" scripts/verify-runtime.sh
```

The build needs a real Android NDK (`ANDROID_NDK_HOME`, otherwise discovered
from the SDK, otherwise installed with `sdkmanager`), `npm`, `python3`, `make`,
`curl`, `git` and a C toolchain. It cannot run without an NDK: there is no way to
cross-compile the engine without one.

## How the pieces are built

1. **Node engine.** Upstream's own `./android-configure patch <NDK> <API> <arch>`
   runs `./configure --dest-os=android --openssl-no-asm --cross-compiling`, then
   `make -j`. The resulting `out/Release/node` is installed as
   `jniLibs/<abi>/libnode.so`. Android only executes app files that arrive through
   the APK's `lib/<abi>/`, so this is the only way to run a real Node on device —
   and it is why the app does not need a launcher shim. The engine is verified to
   target the right ELF machine before it is accepted.
2. **n8n.** `npm install --omit=dev --ignore-scripts n8n@<pin>`; the native
   modules it needs (`sqlite3`) are then rebuilt from source against the engine's
   headers with the NDK toolchain, and every compiled artifact is checked with
   `readelf` so a glibc/musl prebuild can never sneak into the payload. The n8n
   package is promoted to a stable path (`n8n/bin/n8n`), docs/tests/sourcemaps are
   pruned, and the tree is zipped deterministically.
3. **OpenCode.** Upstream ships Bun single-file binaries for darwin/linux/win32
   only — there is no Android/bionic build, and the server uses Bun APIs. The
   build therefore bundles the server for the embedded Node engine, starts that
   bundle on the build host and requires it to answer an HTTP health probe before
   it is packaged. Any failure (clone, install, bundle, smoke test, lingering
   `bun:` imports) records `packaged: false` with the concrete reason, which the
   app renders as NOT_PACKAGED. It never substitutes a stub or a remote server.
4. **Manifest.** The generator reads the endpoint/launch contract from the Kotlin
   sources, derives `packaged` from the artifacts on disk (archive present, digest
   and size matching, entry point inside the archive, engine compiled per ABI) and
   fails on any disagreement with `RuntimePins.kt`.

## The gate

`scripts/verify-runtime.sh` runs at the end of every payload build and in CI:

* `packaged: true` must be backed by an archive of the declared digest, size and
  entry point, and (for Node) by an engine plus its shared libraries per ABI,
  large enough to be a real engine;
* a payload that exists while the manifest says `packaged: false` is rejected as
  stale;
* the pins in `RuntimePins.kt` must match what the payload build produced;
* `REQUIRE_PACKAGED` (default `node n8n`) lists the runtimes an APK must really
  contain — an APK build on a branch in `runtime-payload.request` fails if they
  are missing.

`scripts/selftest-runtime-gate.sh` proves the gate bites by fabricating payloads,
accepting them, then breaking one claim at a time (archive deleted, one byte
appended, entry point absent, digest missing, engine deleted) and requiring a
rejection each time. It runs in seconds without a network or an NDK.

## When a payload build runs

Assemble tasks (`assembleDebug`, `assembleRelease`) build payloads only on the
branches listed in `runtime-payload.request`; the payload build runs from Gradle
before assets and native libraries are merged, so the APK that comes out is the
APK that was checked. `test` and `lint` never trigger it. On any other branch the
APK ships without payloads, and the app says so.

## Verifying a built APK

```bash
unzip -l android/app/build/outputs/apk/debug/app-debug.apk | grep -E 'libnode|payload.zip|manifest.json'
python3 scripts/lib/verify_manifest.py . android/app/src/main/assets/runtime/manifest.json
```

A payload-enabled APK contains `lib/arm64-v8a/libnode.so`, the payload archives
and a manifest whose `packaged` flags are `true` for exactly what is inside.
