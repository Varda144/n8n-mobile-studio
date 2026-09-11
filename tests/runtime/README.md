# tests/runtime

Runtime contract, lifecycle and payload tests.

## Automated, every push

| Check | Where | What it proves |
| --- | --- | --- |
| `tools/compile-check/run.sh` | any machine with a JDK (fetches one from PyPI if needed) | the runtime core, terminal, storage and service code type-checks and its unit tests pass without Android |
| `./gradlew test` | CI (`test.yml`) | the same code plus the Compose/Android side compiles and the JVM tests pass |
| `scripts/selftest-runtime-gate.sh` | any machine with python3 | the manifest gate rejects every broken payload claim (archive missing/tampered/entry absent/digest missing/engine missing) |
| `scripts/verify-runtime.sh` | payload build and CI | what the manifest claims is really inside the APK, and the pins agree with `RuntimePins.kt` |

Unit tests live in `android/app/src/test/kotlin/com/n8n/mobile/studio/runtime/`:

* `RuntimePolicyTest` — start/stop/restart, backoff, health gating, idle stop and
  low-memory eviction decisions;
* `RuntimeInstallerTest` — digest verification, zip-slip refusal, atomic
  activation, rollback pruning;
* `RuntimePathsTest` — sandbox containment and version ordering;
* `RuntimeManifestTest` — parses the *shipped* manifest, rejects malformed ones,
  and refuses to let an unpackaged runtime carry a digest claim.

## Manual, on a device

These need a phone (arm64) and a payload-enabled APK:

1. Install the debug APK from the `n8n-mobile-studio-debug` artifact.
2. Open **LOCAL**: n8n should move NOT_INSTALLED → (install) → RUNNING, and
   Settings should show the engine ABI, the `node --version` probe and the
   installed payload version.
3. Open `http://127.0.0.1:5678` in the phone's browser — the *same* instance the
   tile shows (same pid, same logs).
4. Kill the app from recents: n8n must keep running (foreground service), and the
   notification must offer stop/restart.
5. Reboot: the runtime must come back (or report a concrete reason it does not).
6. Fill the device storage and re-install a payload: the installer must refuse
   with OUT_OF_SPACE rather than leave a half-installed runtime.

OpenCode is expected to report NOT_PACKAGED until the payload build can produce a
Node-runnable server bundle; the app must never fall back to a remote service.
