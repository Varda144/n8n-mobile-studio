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

## GitHub Actions

Both `build-debug.yml` and `runtime-build.yml` workflows:

1. Check out the repo
2. Set up JDK 17 + Gradle 8.13 + Android SDK
3. Install `platforms;android-36` and `build-tools;35.0.0`
4. Run `./gradlew assembleDebug`
5. Upload the APK as a build artifact

The `runtime-build.yml` workflow additionally runs `scripts/prepare-runtime.sh` before the build to validate the runtime manifest and Gradle wrapper.
