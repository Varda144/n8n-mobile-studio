# Installing the APK

The APK is built by CI; a phone cannot compile it. Every push builds one and
uploads it as the `n8n-mobile-studio-debug` artifact.

## Get the build

1. Open the repository's **Actions** tab on the phone's browser.
2. Pick the latest **Build Debug APK** run whose branch is
   `arena/01a09062-n8n-mobile-studio` (open the run through the branch filter or
   from the commit's checks).
3. Download the artifact `n8n-mobile-studio-debug` (a `.zip` containing
   `app-debug.apk`), unzip it, and tap the APK to install. Android will ask to
   allow installs from the browser.

Termux, a desktop and an Android SDK are not needed to install — only to build.

## What to expect on first launch

The APK contains the Node engine (`lib/arm64-v8a/libnode.so`) and, when the
payload build ran, `assets/runtime/n8n/payload.zip`. On the first start the app's
foreground service:

1. extracts the bundled payload into its private storage and verifies its digest
   (this takes a little while for a payload of a few hundred megabytes);
2. writes the `node` shim and the pid preload into `local-runtime/bin`;
3. starts the runtime with `libnode.so n8n/bin/n8n start`;
4. only reports **RUNNING** once the process answers on `http://127.0.0.1:5678`.

n8n creates its SQLite database on that first run, which can take minutes on a
phone; the app keeps waiting while the process is alive and says so on the tile.

## Where things are

Everything the runtime writes lives in the app's private storage:

```
/data/data/com.n8n.mobile.studio.debug/files/local-runtime/
  bin/         node shim + pid preload
  payloads/    installed runtime payloads (digest-verified)
  n8n-home/    n8n's own folder (database, config)
  logs/        process output, rotated at 4 MiB
  projects/    OpenCode's project root
```

`LOCAL → LOGS` shows the same log lines the runtime produced, and
`LOCAL → SETTINGS` reports whether the engine is really present and runnable
(`node --version` probe, ABI, storage).

## Verifying that the runtimes are really embedded

On a machine with the APK:

```bash
unzip -l app-debug.apk | grep -E 'libnode|payload.zip|manifest.json'
unzip -p app-debug.apk assets/runtime/manifest.json | head -40
```

A payload-enabled build lists `lib/arm64-v8a/libnode.so`,
`assets/runtime/n8n/payload.zip` and a manifest whose `packaged` flags are `true`
for exactly what is inside. Debug builds made before the payloads were built say
`packaged: false`, and the app then reports NOT_INSTALLED instead of pretending.
