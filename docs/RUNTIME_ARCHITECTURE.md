# Runtime Architecture

N8N Mobile Studio embeds runtime processes (n8n and OpenCode) as local, app-owned processes. No remote proxying or URL faking is used.

## Filesystem layout

The app provisions a `local-runtime` directory on the device's internal storage:

```
local-runtime/
├── bin/          # Executable binaries for each runtime
├── home/         # Runtime home directories (config, data)
├── projects/     # User workspace directories
├── logs/         # Per-runtime log files
├── data/         # Persistent state (SQLite, JSON, etc.)
├── tmp/          # Ephemeral temp files
```

Each runtime gets its own subtree under these root folders, namespaced by runtime identifier (`n8n`, `opencode`).

## Embedded runtime contract

The file `assets/runtime/manifest.json` declares which runtimes are bundled and their configuration:

- **schemaVersion**: manifest format version
- **builtAt**: build timestamp
- **runtimes**: map of runtime name to `{ enabled, packaged, payload, port }`

A runtime with `"packaged": true` has its binary payload bundled in the APK under `assets/runtime/<name>/`. When `packaged: false`, the payload directory exists as a placeholder and the actual binaries must be supplied by the user or a packaging script.

## Foreground service

`LocalRuntimeService` is a foreground service (`dataSync` type) that manages the lifecycle of runtime processes:

1. **Start**: Launches runtime binaries, binds to their ports, begins health monitoring.
2. **Running**: Keeps the process alive via the foreground notification. Responds to health probe callbacks.
3. **Stop**: Gracefully terminates runtime processes, cleans up temp files, releases resources.

The service uses `LockManager` to serialize start/stop operations, preventing race conditions when multiple components request runtime state changes simultaneously.

## Health probes

Each runtime exposes an HTTP health endpoint on its configured port:

- **n8n**: `GET http://127.0.0.1:5678/healthz`
- **OpenCode**: `GET http://127.0.0.1:8765/healthz`

The service polls these endpoints at a configurable interval. A failure threshold triggers a runtime restart.

## Important

Runtimes are **not** faked via remote URLs or reverse proxies. All processes run locally on the device and bind to `127.0.0.1`.
