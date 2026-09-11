# Storage

The app uses three storage mechanisms, each suited to a different data category.

## DataStore Preferences

`DataStore<Preferences>` is used for lightweight, instance-scoped metadata:

- Instance display names and URLs
- Last-selected instance
- UI preferences (theme, layout flags)
- Runtime configuration toggles

Each data category is modeled as a typed wrapper around a `Preferences.Key`.

## Room Database

`AppDatabase` (Room) provides structured, queryable persistence:

| Entity              | Purpose                                  |
|---------------------|------------------------------------------|
| InstanceEntity      | Saved n8n instance configurations         |
| WorkflowEntity      | Cached workflow metadata from instances   |
| ExecutionEntity     | Cached execution history                  |

Each entity has a corresponding `Dao` with suspend-function query methods.

## SecureStorage

`SecureStorage` wraps the Android Keystore for encrypting sensitive data:

- **AndroidKeyStore** — Generates and stores an AES-GCM encryption key. The key never leaves the hardware-backed keystore.
- **KeystoreManager** — Provides the encryption/decryption API over `Cipher` with `GCMParameterSpec`.

Used for: API keys, access tokens, webhook secrets.

## What is never stored in plaintext

- API keys and tokens — always encrypted via `SecureStorage`
- Webhook signing secrets — encrypted via `SecureStorage`
- User credentials — never persisted; held in memory only during active sessions
