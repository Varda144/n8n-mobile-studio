# Development

1. Edit on Android/Termux or any normal development environment.
2. Push a branch or commit.
3. Let GitHub Actions run tests, lint and APK builds.
4. Download the debug artifact from the workflow run.

Do not depend on an Android SDK installed on the phone. Keep changes small and testable; do not introduce heavy native dependencies without a clear requirement.
