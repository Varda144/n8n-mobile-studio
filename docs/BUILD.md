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
