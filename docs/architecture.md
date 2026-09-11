# Architecture

The app uses a native Android structure: Compose UI → feature state/use cases → repositories → Room/DataStore and remote n8n API. Secrets are isolated behind `SecureStorage`, which uses Android Keystore.

## Modules by responsibility

`ui/` contains Compose screens, navigation and theme. `domain/` contains serializable domain models. `data/` contains persistence and network adapters. `security/` contains secret storage and risk policy. `ai/` and `mcp/` expose provider-neutral integration contracts. `work/` contains WorkManager jobs.

The visual workflow editor uses a generic graph model instead of hard-coding only known nodes, so future n8n node metadata can be added without replacing the canvas model.
