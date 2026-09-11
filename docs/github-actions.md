# GitHub Actions

Workflows run on hosted Ubuntu runners. The pipeline installs JDK 17, restores Gradle caches, executes tests/lint, builds APK artifacts and optionally signs release builds when keystore secrets are present.

Required optional release secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Runtime n8n and AI credentials are not GitHub Actions secrets; they are configured by the user inside the app and stored securely.
