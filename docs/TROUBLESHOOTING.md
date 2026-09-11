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
