# n8n API integration

The app isolates n8n requests in `N8nApi`. Current implemented foundation calls use the public API-key header `X-N8N-API-KEY` and `/api/v1` endpoints for workflow and execution access.

The connected n8n instance remains the source of truth. Features that depend on optional edition/version capabilities should be capability-detected and disabled gracefully rather than guessed.

The official n8n documentation should be checked before adding or changing endpoint paths. The n8n documentation describes its API reference and notes that API availability can vary by configuration/edition. The app therefore keeps endpoint construction centralized.
