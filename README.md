# N8N Mobile Studio

N8N Mobile Studio is a native Kotlin/Jetpack Compose Android client for managing remote n8n instances from a phone. The project is designed around a phone-only development workflow: Android builds run in GitHub Actions rather than requiring Android Studio on the development device.

## Current foundation

- Native Kotlin + Jetpack Compose + Material 3
- Clean-ish feature/data/domain separation
- Multi-instance storage with secure API-key storage
- OkHttp n8n API abstraction
- Room database schema for cached instances/workflows/executions
- DataStore preferences for instance metadata
- Risk policy and MCP/AI abstractions
- Mobile workflow-editor foundation
- WorkManager background-sync foundation
- GitHub Actions CI/CD layout

## Build in GitHub Actions

Push to `main` or run the workflows manually from the Actions tab. The debug workflow produces an APK artifact. Release signing uses GitHub Secrets only when configured.

The supported development workflow does not require an Android SDK or Android Studio on the phone.

## Runtime security

API keys are stored using an AES-GCM key generated in the Android Keystore. Secrets are never intended for source control or CI logs. Destructive and high-risk actions are modeled through `RiskLevel` and must be confirmed by the UI before implementation of the action path.

## n8n compatibility

API calls are isolated in `data/api/N8nApi.kt` so endpoint/version capability handling can evolve without coupling the UI to transport details. The implementation is intentionally conservative where an n8n capability is not guaranteed across editions or versions.

## Development from Android/Termux

Edit source on the phone and push to GitHub. No local Android SDK is required for the supported build workflow.

## Roadmap

See `docs/IMPLEMENTATION_PLAN.md` and the feature documentation under `docs/`. The project is being migrated from the repository's original Flutter prototype to the requested native Kotlin architecture.
