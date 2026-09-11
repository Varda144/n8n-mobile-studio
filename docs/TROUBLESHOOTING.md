# Troubleshooting

## Missing gradle-wrapper.jar

**Symptom**: `./gradlew` fails with "Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain".

**Fix**: Ensure `android/gradle/wrapper/gradle-wrapper.jar` exists. If missing, regenerate with:
```bash
cd android
gradle wrapper --gradle-version 8.13
```

## material-icons-extended version mismatch

**Symptom**: Build fails with unresolved dependency for `material-icons-extended`.

**Fix**: Ensure the version in `app/build.gradle` matches the Compose BOM or other Compose dependencies. Current pinned version: `1.7.8`.

## sdkmanager licenses not accepted

**Symptom**: `sdkmanager` fails with "License not accepted".

**Fix**:
```bash
yes | sdkmanager --licenses
```
Or accept individually:
```bash
sdkmanager 'platforms;android-36' 'build-tools;35.0.0'
```

## Flutter directory in repo root

**Symptom**: `flutter/` directory exists in the repo from a prior prototype. Gradle may attempt to parse it.

**Fix**: Harmless if not referenced by `settings.gradle`. Ignore it or remove the directory if it causes noise in IDE indexing.

## OutOfMemoryError during build

**Symptom**: `java.lang.OutOfMemoryError: Java heap space` during compilation.

**Fix**: Increase Gradle JVM heap in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```
GitHub Actions runners have 7 GB RAM; 4 GB for Gradle is safe.

## Wrapper port 443 connection refused in CI

**Symptom**: Gradle wrapper download fails behind a proxy or with DNS issues.

**Fix**: The `gradle/actions/setup-gradle@v4` action handles wrapper downloads. If it fails, pin `gradle-version` explicitly in the action config (already done: `8.13`).

## The APK build fails in `:app:prepareRuntimePayloads`

The payload build reports the reason as a workflow annotation (`gh run view`), so
start there. The usual causes:

* **No NDK.** `ANDROID_NDK_HOME` is unset and the SDK has no `ndk/` directory.
  Install `ndk;27.0.12077973` (or set the variable).
* **`android-configure` refuses the Python version.** Upstream accepts 3.9–3.13
  only; the build selects one of those and shims it into `PATH`, but a machine
  with none of them cannot cross-compile the engine.
* **A native module failed to cross-compile.** The failing module's log is in
  `.runtime-build/logs/n8n-native-<module>.log`. The build refuses to package an
  n8n that cannot open its SQLite database.
* **`sqlite3` was dropped from the n8n tree.** Same reason.

## The APK builds but the runtimes report NOT_PACKAGED

Either the branch is not listed in `runtime-payload.request` (payload builds are
opt-in, so ordinary pushes stay fast), or `scripts/verify-runtime.sh` downgraded
the claim because an artifact was missing — check the build annotations and
`.runtime-build/logs/runtime-verify.txt`.

## The runtime is installed but never reaches RUNNING

The app only reports RUNNING after the process answers its loopback health probe.
Read the component log (`LOCAL → LOGS`, or `local-runtime/logs/<component>.log`
inside the app's private storage): a missing shared library, a payload built for
another ABI, or a port already in use all show up there, and the status message
on the tile names the failure.
