# OpenCode Runtime

## Overview

The OpenCode runtime runs a local AI coding assistant server on the device, accessible at `http://127.0.0.1:8765`.

## Configuration

| Property   | Value                                |
|------------|--------------------------------------|
| Port       | 8765                                 |
| Endpoint   | `http://127.0.0.1:8765`             |
| Health     | `GET http://127.0.0.1:8765/healthz` |
| Config dir | `local-runtime/home/opencode/`       |
| Data dir   | `local-runtime/data/opencode/`       |

## Terminal bridge

OpenCode exposes a terminal bridge that allows the app to execute commands through the runtime. The terminal integration uses polling-based output capture — the app periodically reads stdout/stderr from the running process rather than streaming.

## GUI state

The OpenCode GUI state (conversation history, active sessions) is maintained in-memory by the runtime process. The app communicates via HTTP API to read and mutate state.

## Packaged payload

OpenCode is packaged as a pre-built binary payload under `assets/runtime/opencode/`. The `manifest.json` declares `"packaged": false` by default — set to `true` once binaries are added.
