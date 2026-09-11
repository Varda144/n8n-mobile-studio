# Runtime payload directory

This directory holds the payloads the app installs into its own sandbox on first
use. It is intentionally empty in the source tree: payloads are produced by
`scripts/prepare-runtime.sh` (invoked by `.github/workflows/runtime-build.yml`)
and are never committed, because they are tens of megabytes of third-party code
that must be pinned and digest-verified at build time.

```
assets/runtime/
  manifest.json          contract between payload build and app (versions, digests, ports, entries)
  n8n/payload.zip        n8n CLI + dependencies, run by the embedded Node
  opencode/payload.zip   OpenCode server bundled for Node (+ shims if required)
```

Payload rules enforced by the app (`RuntimeInstaller`, `RuntimeManifest.validate`):

* the digest in `manifest.json` must match the archive, otherwise the install is
  rejected and the card stays `NOT INSTALLED`;
* every entry declared in the manifest must exist after extraction;
* extraction happens into `payloads/.staging-*` and is renamed into place, so an
  interrupted install can never be launched;
* only the newest two installs per runtime are kept.

Node itself is **not** here: it is compiled for Android and shipped as
`libnode.so` (the engine) plus `libnoderun.so` (a launcher that calls
`node::Start` and writes its pid), because Android only allows an app to execute
code from `nativeLibraryDir`.

See `docs/RUNTIME_ARCHITECTURE.md` for the full contract.
