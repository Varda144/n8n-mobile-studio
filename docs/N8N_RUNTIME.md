# n8n Runtime

## Overview

The n8n runtime runs a local n8n instance on the device, accessible at `http://127.0.0.1:5678`.

## Configuration

| Property   | Value                              |
|------------|------------------------------------|
| Port       | 5678                               |
| Endpoint   | `http://127.0.0.1:5678`           |
| Health     | `GET http://127.0.0.1:5678/healthz` |
| Config dir | `local-runtime/home/n8n/`          |
| Data dir   | `local-runtime/data/n8n/`          |

## Architecture classes

- **Config** — Loads and manages n8n configuration (credentials, settings, environment).
- **Storage** — Handles n8n's local data persistence (SQLite database, file storage).
- **Process** — Manages the n8n Node.js process lifecycle (start, stop, restart).
- **Health** — Polls the health endpoint and reports runtime status.
- **Version** — Reads and reports the bundled n8n version.

## GUI rule

The n8n web UI must be rendered in an in-app WebView or launched in an external browser. It is **not** embedded as native Compose components.

## External browser launch

When the user taps "Open in browser", the app sends an `ACTION_VIEW` intent with the n8n URL. The system opens the default browser to `http://127.0.0.1:5678`.
