#!/usr/bin/env python3
"""Generate `assets/runtime/manifest.json` from what was actually built.

The manifest is the contract between the payload build and the app: versions,
entry points, endpoints and digests. This script only ever reports what exists on
disk, so `packaged` can never be claimed for a payload that was not built — the
app's honesty gate relies on that.
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent
ASSETS = REPO / "android/app/src/main/assets/runtime"
JNI_LIBS = REPO / "android/app/src/main/jniLibs"
BUILD = REPO / ".runtime-build"
MANIFEST = ASSETS / "manifest.json"

# Pins: the single source of truth for the build scripts. They must match the
# constants in RuntimePins.kt, which scripts/verify-runtime.sh enforces.
NODE_VERSION = "24.9.0"
N8N_VERSION = "2.38.7"
OPENCODE_VERSION = "1.18.30"
SCHEMA_VERSION = 2


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_meta(name: str) -> dict | None:
    path = BUILD / name / "meta.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text())
    except Exception:
        return None


def node_section() -> dict:
    abis: dict[str, dict] = {}
    for abi in ("arm64-v8a", "armeabi-v7a", "x86_64", "x86"):
        binary = JNI_LIBS / abi / "libnode.so"
        if binary.is_file():
            abis[abi] = {"sha256": sha256(binary), "sizeBytes": binary.stat().st_size}
    packaged = bool(abis)
    return {
        "version": NODE_VERSION,
        "distOs": "android",
        "shared": False,
        "library": "libnode.so",
        "flavor": "android-executable",
        "extraLibs": ["libc++_shared.so"],
        "source": f"https://github.com/nodejs/node/releases/tag/v{NODE_VERSION}",
        "packaged": packaged,
        "abis": abis,
    }


def n8n_section() -> dict:
    meta = load_meta("n8n")
    archive = ASSETS / "n8n" / "payload.zip"
    packaged = bool(meta and meta.get("packaged") and archive.is_file())
    payload = {"kind": "asset", "path": "runtime/n8n/payload.zip", "sha256": "", "sizeBytes": 0}
    if packaged:
        payload["sha256"] = sha256(archive)
        payload["sizeBytes"] = archive.stat().st_size
    return {
        "enabled": True,
        "packaged": packaged,
        "version": N8N_VERSION,
        "entry": "n8n/bin/n8n",
        "args": ["start"],
        "port": 5678,
        "healthPath": "/healthz",
        "guiPath": None,
        "memoryMb": 512,
        "integrity": (meta or {}).get("npmIntegrity", ""),
        "source": f"https://registry.npmjs.org/n8n/-/n8n-{N8N_VERSION}.tgz",
        "license": "SEE LICENSE IN LICENSE.md (n8n sustainable-use licence — review before distributing a payload)",
        "payload": payload,
        "env": {
            "N8N_DIAGNOSTICS_ENABLED": "false",
            "N8N_TEMPLATES_ENABLED": "false",
            "N8N_VERSION_NOTIFICATIONS_ENABLED": "false",
            "EXTERNAL_FRONTEND_HOOKS_URLS": "",
        },
    }


def opencode_section() -> dict:
    meta = load_meta("opencode")
    archive = ASSETS / "opencode" / "payload.zip"
    packaged = bool(meta and meta.get("packaged") and archive.is_file())
    payload = {"kind": "asset", "path": "runtime/opencode/payload.zip", "sha256": "", "sizeBytes": 0}
    if packaged:
        payload["sha256"] = sha256(archive)
        payload["sizeBytes"] = archive.stat().st_size
    return {
        "enabled": True,
        "packaged": packaged,
        "version": OPENCODE_VERSION,
        "entry": (meta or {}).get("entry", "opencode/server.js"),
        "args": ["serve", "--port", "{port}", "--hostname", "127.0.0.1"],
        "port": 8765,
        "healthPath": "/global/health",
        "guiPath": None,
        "memoryMb": 448,
        "integrity": "",
        "source": (
            "https://github.com/anomalyco/opencode "
            f"(v{OPENCODE_VERSION}); npm opencode-ai {OPENCODE_VERSION} ships Bun platform binaries only"
        ),
        "license": "MIT",
        "payload": payload,
        "env": {"OPENCODE_DISABLE_AUTOUPDATE": "true"},
        "unavailableReason": (meta or {}).get("unavailableReason", ""),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest-version", default=None)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    node = node_section()
    n8n = n8n_section()
    opencode = opencode_section()

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "manifestVersion": args.manifest_version
        or datetime.datetime.now(datetime.timezone.utc).strftime("%Y.%m.%d-%H%M"),
        "builtAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "notes": [
            "Generated by scripts/generate-runtime-manifest.py — do not edit by hand.",
            "packaged=false means the payload is NOT in this APK build: the app reports NOT INSTALLED and never falls back to a remote server.",
            "n8n and OpenCode are JavaScript payloads executed by the embedded Node engine (jniLibs/<abi>/libnode.so).",
        ],
        "node": node,
        "runtimes": {"n8n": n8n, "opencode": opencode},
    }

    text = json.dumps(manifest, indent=2) + "\n"
    if args.dry_run:
        sys.stdout.write(text)
        return 0

    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(text)

    summary = {
        "node": f"packaged={node['packaged']} abis={','.join(node['abis']) or 'none'}",
        "n8n": f"packaged={n8n['packaged']} digest={n8n['payload']['sha256'][:12] or '-'}",
        "opencode": f"packaged={opencode['packaged']}"
        + (f" reason={opencode['unavailableReason']}" if not opencode["packaged"] else ""),
    }
    print("manifest written to", MANIFEST.relative_to(REPO))
    for key, value in summary.items():
        print(f"  {key}: {value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
