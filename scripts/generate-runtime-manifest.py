#!/usr/bin/env python3
"""Generate android/app/src/main/assets/runtime/manifest.json from build outputs.

The manifest is the contract between the payload build and the app: the app reads
it to learn *what* it may start (entry point, arguments, port, health path, env)
and refuses to claim anything the APK does not actually contain.

Design rules this script enforces:

* `packaged` is derived, never declared by hand. A runtime is `packaged` only if
  its archive exists under `assets/runtime/<id>/`, its sha256 and size match the
  recorded metadata, and the declared entry point is really inside the archive.
* Pins are cross-checked against `RuntimePins.kt`. If the payload was built for
  Node 24.9.0 but the app pins another version, this fails instead of producing an
  APK whose manifest disagrees with the code that reads it.
* A missing payload produces `packaged: false` plus a reason in `notes`, which is
  what the UI shows as NOT INSTALLED / NOT PACKAGED.

Run standalone (`--dry-run` prints without writing) or via
scripts/prepare-runtime.sh.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import sys
import zipfile

SCHEMA_VERSION = 2

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
APP_SRC = REPO_ROOT / "android/app/src/main"
ASSETS_RUNTIME = APP_SRC / "assets/runtime"
JNI_DIR = APP_SRC / "jniLibs"
BUILD_DIR = REPO_ROOT / ".runtime-build"
KOTLIN = APP_SRC / "kotlin/com/n8n/mobile/studio"
PINS_FILE = KOTLIN / "runtime/RuntimePins.kt"
N8N_VERSION_FILE = KOTLIN / "runtime/n8n/N8nVersion.kt"
OPENCODE_VERSION_FILE = KOTLIN / "runtime/opencode/OpenCodeVersion.kt"
APP_CONSTANTS_FILE = KOTLIN / "core/AppConstants.kt"

COMPONENTS = ("n8n", "opencode")


def fail(message: str) -> "NoReturn":  # type: ignore[valid-type]
    print(f"FAIL {message}", file=sys.stderr)
    if "GITHUB_ACTIONS" in __import__("os").environ:
        print(f"::error::{message}")
    raise SystemExit(1)


def sha256_of(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_pins() -> dict[str, str]:
    text = PINS_FILE.read_text(encoding="utf-8")
    pins: dict[str, str] = {}
    for name in (
        "SCHEMA_VERSION",
        "NODE_VERSION",
        "N8N_VERSION",
        "OPENCODE_VERSION",
        "PAYLOAD_ARCHIVE",
        "NODE_CORE_LIB",
        "NODE_BUILD_FLAVOR",
        "SCHEMA_",
    ):
        match = re.search(rf"const val {name}\s*=\s*\"([^\"]*)\"", text)
        if match:
            pins[name] = match.group(1)
    match = re.search(r"const val SCHEMA_VERSION\s*=\s*(\d+)", text)
    if match:
        pins["SCHEMA_VERSION"] = match.group(1)
    return pins


def kotlin_string(text: str, name: str) -> str:
    match = re.search(rf'const val {name}\s*=\s*"([^"]*)"', text)
    if not match:
        fail(f"cannot read {name} from the Kotlin sources — the manifest contract must not be guessed")
    return match.group(1)


def kotlin_int(text: str, name: str) -> int:
    match = re.search(rf'const val {name}\s*=\s*(\d+)', text)
    if not match:
        fail(f"cannot read {name} from the Kotlin sources")
    return int(match.group(1))


def read_contract() -> dict[str, dict]:
    """Endpoint/launch contract, read from the Kotlin code that implements it.

    A runtime that is not packaged *still* has a declared contract (port, health
    path, entry point, arguments): it is what the app shows and what the payload
    build must honour. Deriving it from the Kotlin sources — instead of repeating
    literals here — means a manifest can never promise a port the app does not
    probe.
    """
    n8n = N8N_VERSION_FILE.read_text(encoding="utf-8")
    opencode = OPENCODE_VERSION_FILE.read_text(encoding="utf-8")
    constants = APP_CONSTANTS_FILE.read_text(encoding="utf-8")

    n8n_port = kotlin_int(constants, "N8N_PORT")
    opencode_port = kotlin_int(constants, "OPENCODE_PORT")
    n8n_entry = kotlin_string(n8n, "ENTRY")
    opencode_entry = kotlin_string(opencode, "ENTRY")
    if kotlin_int(n8n, "DEFAULT_PORT") != n8n_port:
        fail("N8nVersion.DEFAULT_PORT and AppConstants.N8N_PORT disagree")
    if kotlin_int(opencode, "DEFAULT_PORT") != opencode_port:
        fail("OpenCodeVersion.DEFAULT_PORT and AppConstants.OPENCODE_PORT disagree")

    serve_args_match = re.search(r"SERVE_ARGS[^=]*=\s*listOf\(([^)]*)\)", opencode, re.S)
    if not serve_args_match:
        fail("cannot read OpenCodeVersion.SERVE_ARGS")
    serve_args = [argument for argument in re.findall(r'"([^"]*)"', serve_args_match.group(1))]

    return {
        "n8n": {
            "entry": n8n_entry,
            "args": ["start"],
            "port": n8n_port,
            "healthPath": kotlin_string(constants, "N8N_HEALTH_PATH"),
            "guiPath": "/",
            "memoryMb": 640,
        },
        "opencode": {
            "entry": opencode_entry,
            "args": serve_args,
            "port": opencode_port,
            "healthPath": kotlin_string(constants, "OPENCODE_HEALTH_PATH"),
            "guiPath": "/",
            "memoryMb": 448,
        },
    }


def extra_libs_from_pins(pins: dict[str, str]) -> list[str]:
    text = PINS_FILE.read_text(encoding="utf-8")
    match = re.search(r"NODE_EXTRA_LIBS[^=]*=\s*listOf\(([^)]*)\)", text, re.S)
    if not match:
        fail("cannot read RuntimePins.NODE_EXTRA_LIBS")
    return re.findall(r'"([^"]+)"', match.group(1))


def load_meta(component: str) -> dict | None:
    path = BUILD_DIR / component / "meta.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        fail(f".runtime-build/{component}/meta.json is not valid JSON: {error}")


def verify_archive(component: str, payload: dict) -> tuple[pathlib.Path, int]:
    """Return (archive, size) after proving it matches the recorded digest."""
    rel = payload.get("path", "")
    if not rel:
        fail(f"{component}: payload metadata has no path")
    archive = ASSETS_RUNTIME.parent / rel
    if not archive.is_file():
        fail(f"{component}: payload archive missing at {archive.relative_to(REPO_ROOT)}")
    actual_size = archive.stat().st_size
    if payload.get("sizeBytes") and payload["sizeBytes"] != actual_size:
        fail(
            f"{component}: archive size mismatch "
            f"(metadata {payload['sizeBytes']} bytes, file {actual_size} bytes)"
        )
    actual_digest = sha256_of(archive)
    if payload.get("sha256") != actual_digest:
        fail(
            f"{component}: archive digest mismatch "
            f"(metadata {payload.get('sha256', '<none>')[:16]}…, file {actual_digest[:16]}…)"
        )
    return archive, actual_size


def entry_in_archive(archive: pathlib.Path, entry: str) -> bool:
    with zipfile.ZipFile(archive) as zf:
        names = set(zf.namelist())
    return entry in names


def node_section(pins: dict[str, str], notes: list[str]) -> dict:
    """Node engine facts, taken from what scripts/build-node-android.sh produced."""
    meta_path = BUILD_DIR / "node" / "node.json"
    section: dict = {
        "version": pins.get("NODE_VERSION", ""),
        "distOs": "android",
        "shared": False,
        # Pinned identity of the engine the app expects; the ABI digests below are
        # what prove it was really built.
        "library": pins.get("NODE_CORE_LIB", "libnode.so"),
        "flavor": pins.get("NODE_BUILD_FLAVOR", "android-executable"),
        "extraLibs": extra_libs_from_pins(pins),
        "source": f"https://github.com/nodejs/node/releases/tag/v{pins.get('NODE_VERSION', '')}",
        "packaged": False,
        "abis": {},
    }
    if not meta_path.is_file():
        notes.append(
            "Node engine: not built in this checkout — run scripts/build-node-android.sh "
            "(the app reports NOT_INSTALLED for both runtimes until it is)."
        )
        return section

    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    if meta.get("version") != pins.get("NODE_VERSION"):
        fail(
            "node pin mismatch: RuntimePins.kt pins "
            f"{pins.get('NODE_VERSION')} but .runtime-build/node/node.json is {meta.get('version')}"
        )
    section["extraLibs"] = meta.get("extraLibs", [])
    section["apiLevel"] = meta.get("apiLevel")
    for key in ("library", "flavor", "distOs"):
        pin_key = {"library": "NODE_CORE_LIB", "flavor": "NODE_BUILD_FLAVOR", "distOs": "NODE_DIST_OS"}[key]
        if meta.get(key) and pins.get(pin_key) and meta[key] != pins[pin_key]:
            fail(
                f"node: payload build produced {key}={meta[key]!r} but RuntimePins.{pin_key} is "
                f"{pins[pin_key]!r}"
            )

    abis: dict[str, dict] = {}
    for abi, recorded in (meta.get("abis") or {}).items():
        lib = JNI_DIR / abi / pins.get("NODE_CORE_LIB", "libnode.so")
        if not lib.is_file():
            notes.append(f"Node engine: {abi} digest recorded but jniLibs/{abi}/libnode.so is missing.")
            continue
        actual_digest = sha256_of(lib)
        actual_size = lib.stat().st_size
        if recorded.get("sha256") not in ("", actual_digest):
            fail(f"node/{abi}: jniLibs digest does not match .runtime-build/node/node.json")
        abis[abi] = {"sha256": actual_digest, "sizeBytes": actual_size}
        # The C++ runtime that the engine links against must ship next to it.
        for extra in section["extraLibs"]:
            if not (JNI_DIR / abi / extra).is_file():
                notes.append(f"Node engine: {abi} needs {extra} but jniLibs/{abi}/{extra} is absent.")
    section["abis"] = abis
    section["packaged"] = bool(abis)
    if not abis:
        notes.append("Node engine: no ABI has both an engine and its shared libraries.")
    return section


def runtime_section(component: str, pins: dict[str, str], contract: dict, notes: list[str]) -> dict:
    meta = load_meta(component)
    declared = contract[component]
    section: dict = {
        "enabled": True,
        "packaged": False,
        "version": pins.get("N8N_VERSION" if component == "n8n" else "OPENCODE_VERSION", ""),
        "entry": declared["entry"],
        "args": declared["args"],
        "port": declared["port"],
        "healthPath": declared["healthPath"],
        "guiPath": declared["guiPath"],
        "memoryMb": declared["memoryMb"],
        "integrity": "",
        "source": "",
        "license": "",
        "payload": None,
        "env": {},
    }
    if meta is None:
        notes.append(
            f"{component}: no payload build metadata (.runtime-build/{component}/meta.json) — "
            "run scripts/prepare-runtime.sh to build it."
        )
        return section

    for key in ("integrity", "source", "license", "env", "memoryMb"):
        if key in meta and meta[key] not in (None, "", [], {}):
            section[key] = meta[key]
    # The payload build must agree with the Kotlin contract, or the manifest would
    # describe a runtime the app cannot reach.
    for key in ("entry", "args", "port", "healthPath", "guiPath"):
        if key in meta and meta[key] not in (None, "", [], {}):
            if meta[key] != declared[key]:
                fail(
                    f"{component}: payload build declares {key}={meta[key]!r} but the app expects "
                    f"{declared[key]!r}"
                )

    pinned = pins.get("N8N_VERSION" if component == "n8n" else "OPENCODE_VERSION")
    if meta.get("version") != pinned:
        fail(
            f"{component}: pin mismatch — RuntimePins.kt pins {pinned} but the payload build "
            f"produced {meta.get('version')}"
        )

    if not meta.get("packaged", False):
        reason = meta.get("unavailableReason", "payload build reported it as unavailable")
        section["unavailableReason"] = reason
        notes.append(f"{component}: NOT packaged — {reason}")
        return section

    payload = meta.get("payload") or {}
    archive, size = verify_archive(component, payload)
    entry = section["entry"]
    if not entry:
        fail(f"{component}: packaged payload without an entry point")
    if not entry_in_archive(archive, entry):
        fail(f"{component}: entry '{entry}' is not inside {archive.name}")
    section["payload"] = {
        "kind": payload.get("kind", "asset"),
        "path": payload.get("path", ""),
        "sha256": payload.get("sha256", ""),
        "sizeBytes": size,
    }
    section["packaged"] = True
    notes.append(f"{component}: packaged {section['version']} ({size // (1024 * 1024)} MiB, entry {entry}).")
    return section


def build_manifest(pins: dict[str, str]) -> dict:
    notes: list[str] = []
    contract = read_contract()
    node = node_section(pins, notes)
    runtimes = {
        component: runtime_section(component, pins, contract, notes) for component in COMPONENTS
    }

    # The engine is what makes a runtime launchable at all: without it, a payload
    # archive must not be advertised as packaged.
    if runtimes["n8n"].get("packaged") and not node["packaged"]:
        runtimes["n8n"]["packaged"] = False
        notes.append("n8n: payload present but the Node engine is missing for every ABI.")
    if runtimes["opencode"].get("packaged") and not node["packaged"]:
        runtimes["opencode"]["packaged"] = False
        notes.append("opencode: payload present but the Node engine is missing for every ABI.")

    now = dt.datetime.now(dt.timezone.utc)
    return {
        "schemaVersion": int(pins.get("SCHEMA_VERSION", SCHEMA_VERSION)),
        "manifestVersion": now.strftime("%Y.%m.%d-%H%M%S"),
        "builtAt": now.isoformat(timespec="seconds"),
        "node": node,
        "runtimes": runtimes,
        "notes": notes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="print the manifest instead of writing it")
    parser.add_argument("--out", default=str(ASSETS_RUNTIME / "manifest.json"))
    args = parser.parse_args()

    pins = read_pins()
    if int(pins.get("SCHEMA_VERSION", "0")) != SCHEMA_VERSION:
        fail(
            f"RuntimePins.SCHEMA_VERSION is {pins.get('SCHEMA_VERSION')} but this generator "
            f"writes schema {SCHEMA_VERSION}"
        )
    if pins.get("PAYLOAD_ARCHIVE") != "payload.zip":
        fail(f"unexpected payload archive name in RuntimePins.kt: {pins.get('PAYLOAD_ARCHIVE')}")

    manifest = build_manifest(pins)
    text = json.dumps(manifest, indent=2) + "\n"

    if args.dry_run:
        print(text)
        return 0

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")

    packaged = [c for c, s in manifest["runtimes"].items() if s["packaged"]]
    print(f"wrote {out.relative_to(REPO_ROOT)}")
    print(f"node engine packaged: {manifest['node']['packaged']} (ABIs: {', '.join(manifest['node']['abis']) or 'none'})")
    print(f"runtimes packaged: {', '.join(packaged) if packaged else 'none'}")
    for note in manifest["notes"]:
        print(f"note: {note}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
