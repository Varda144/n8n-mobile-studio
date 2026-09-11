# Security

- API keys are encrypted with AES-GCM using a key generated in Android Keystore.
- Keys are not written to logs.
- Do not commit `.jks`, `.keystore`, passwords, tokens or API keys.
- Release signing is CI-only through GitHub Secrets.
- `RiskLevel` categorizes read, low-risk write, high-risk write and destructive operations.
- AI/MCP generated actions must pass user approval before high-risk or destructive execution.
- Network failures should not be presented as successful remote writes.
