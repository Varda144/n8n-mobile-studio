# N8N Mobile Studio — Implementation Plan

## Repository audit
The repository was a small Flutter prototype with a Flutter-wired Android Gradle module, a custom visual editor, engine/model files, and a single Flutter APK workflow. The existing Flutter code is retained as historical/reference material while the active build is migrated to native Kotlin/Compose. The previous credential store used XOR-style obfuscation, which is not acceptable for production; the native project uses Android Keystore-backed AES-GCM instead.

## Target architecture
Native Kotlin + Jetpack Compose, MVVM/Clean Architecture, Room for cached domain data, DataStore for preferences, Android Keystore-backed encrypted secrets, OkHttp for n8n transport, WorkManager for synchronization, and an extensible n8n workflow graph model.

## Implementation phases
1. Foundation and CI.
2. Room/DataStore and secure storage.
3. n8n API and multi-instance management.
4. Dashboard and synchronization.
5. Workflow CRUD/search/import/export.
6. Visual workflow editor.
7. Executions/webhooks/templates.
8. AI provider and workflow tooling.
9. MCP/risk permissions/notifications/background work.
10. Tests/lint/security/release hardening.

## Verification
The initial migration establishes the native build, secure instance entry flow, remote workflow loading, Room schema, DataStore persistence, provider-neutral AI/MCP interfaces, editor canvas foundation and CI structure. Later phases must replace placeholder states with real API-backed behavior and tests before claiming feature completion.
