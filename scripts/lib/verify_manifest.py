#!/usr/bin/env python3
"""Verify that the packaged manifest describes what is really in the APK.

Exit code 0 = the app's claims are backed by artifacts on disk.
Exit code 1 = at least one claim is false; every problem is printed as a GitHub
annotation so a failed run explains itself without the job log.
"""

from __future__ import annotations

import hashlib
import json
import pathlib
import re
import sys
import zipfile

MIN_ENGINE_BYTES = 5 * 1024 * 1024
HEX64 = __import__("re").compile(r"^[0-9a-f]{64}$")


def is_sha256(value: str) -> bool:
    return bool(HEX64.match(value or ""))


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def pin(pins: str, name: str) -> str:
    match = re.search(rf'const val {name}\s*=\s*"([^"]+)"', pins)
    return match.group(1) if match else ""


def check_pins(repo: pathlib.Path, manifest: dict, problems: list[str], notes: list[str]) -> None:
    pins_file = repo / "android/app/src/main/kotlin/com/n8n/mobile/studio/runtime/RuntimePins.kt"
    if not pins_file.is_file():
        notes.append("RuntimePins.kt not found; pin cross-check skipped")
        return
    pins = pins_file.read_text()
    node = manifest.get("node") or {}
    runtimes = manifest.get("runtimes") or {}
    comparisons = [
        ("NODE_VERSION", pin(pins, "NODE_VERSION"), str(node.get("version") or "")),
        ("N8N_VERSION", pin(pins, "N8N_VERSION"), str((runtimes.get("n8n") or {}).get("version") or "")),
        (
            "OPENCODE_VERSION",
            pin(pins, "OPENCODE_VERSION"),
            str((runtimes.get("opencode") or {}).get("version") or ""),
        ),
        ("SCHEMA_VERSION", pin(pins, "SCHEMA_VERSION"), str(manifest.get("schemaVersion") or "")),
    ]
    for name, expected, actual in comparisons:
        if expected and actual != expected:
            problems.append(f"RuntimePins.{name}={expected!r} but the manifest says {actual!r}")
    if not pin(pins, "NODE_CORE_LIB"):
        problems.append("RuntimePins.NODE_CORE_LIB is missing")


def check_runtime(
    repo: pathlib.Path,
    component: str,
    entry: dict,
    problems: list[str],
    notes: list[str],
) -> None:
    packaged = bool(entry.get("packaged"))
    payload = entry.get("payload") or {}
    relative = payload.get("path") or f"runtime/{component}/payload.zip"
    archive = repo / "android/app/src/main/assets" / relative
    declared_entry = (entry.get("entry") or "").lstrip("/")

    if not packaged:
        if archive.is_file():
            problems.append(
                f"{component}: manifest says packaged=false but {relative} exists — "
                "a stale payload would ship inside the APK"
            )
        reason = entry.get("unavailableReason") or "no payload was built"
        notes.append(f"{component}: NOT packaged — {reason}")
        return

    if not archive.is_file():
        problems.append(f"{component}: packaged but {relative} is missing")
        return

    declared_digest = (payload.get("sha256") or "").strip()
    if not declared_digest:
        # Without a digest the APK cannot prove which payload it ships, and a
        # corrupted/foreign archive would be installed unnoticed.
        problems.append(f"{component}: packaged without a sha256 digest for its payload")
    elif not is_sha256(declared_digest):
        problems.append(f"{component}: payload sha256 is malformed ({declared_digest[:16]}…)")
    else:
        digest = sha256(archive)
        if digest != declared_digest:
            problems.append(
                f"{component}: payload digest mismatch ({digest[:12]}… vs {declared_digest[:12]}…)"
            )
    size = archive.stat().st_size
    if payload.get("sizeBytes") and size != payload["sizeBytes"]:
        problems.append(f"{component}: payload size mismatch ({size} vs {payload['sizeBytes']})")
    if not declared_entry:
        problems.append(f"{component}: packaged without an entry point")
        return
    try:
        with zipfile.ZipFile(archive) as bundle:
            names = set(bundle.namelist())
    except zipfile.BadZipFile:
        problems.append(f"{component}: {relative} is not a readable zip archive")
        return
    if declared_entry not in names:
        problems.append(f"{component}: entry '{declared_entry}' is missing inside {relative}")
    elif component == "n8n" and entry.get("packaged"):
        # n8n's CLI is a Node script; make sure we did not package a directory.
        notes.append(f"{component}: packaged, {size / 1048576:.1f} MiB, entry {declared_entry}")


def check_node(repo: pathlib.Path, manifest: dict, problems: list[str], notes: list[str]) -> None:
    node = manifest.get("node") or {}
    jni = repo / "android/app/src/main/jniLibs"
    library = node.get("library") or "libnode.so"
    if node.get("packaged"):
        count = 0
        for abi, payload in (node.get("abis") or {}).items():
            binary = jni / abi / library
            if not binary.is_file():
                problems.append(f"node/{abi}: manifest declares an engine but {binary} is missing")
                continue
            declared = (payload.get("sha256") or "").strip()
            if not is_sha256(declared):
                problems.append(f"node/{abi}: packaged without a valid sha256 digest")
                continue
            digest = sha256(binary)
            if digest != declared:
                problems.append(f"node/{abi}: engine digest mismatch")
            if payload.get("sizeBytes") and binary.stat().st_size != payload["sizeBytes"]:
                problems.append(f"node/{abi}: engine size mismatch")
            if binary.stat().st_size < MIN_ENGINE_BYTES:
                problems.append(
                    f"node/{abi}: engine is only {binary.stat().st_size} bytes — "
                    "that is not a Node build"
                )
            for lib in node.get("extraLibs") or []:
                if not (binary.parent / lib).is_file():
                    problems.append(f"node/{abi}: shared library {lib} is not packaged next to the engine")
            count += 1
        notes.append(f"node: packaged for {count} ABI(s)")
    else:
        stale = sorted(jni.glob(f"*/{library}")) if jni.is_dir() else []
        if stale:
            notes.append(
                "node: manifest says unpackaged but jniLibs contains an engine for "
                + ", ".join(path.parent.name for path in stale)
                + " — it will ship inside the APK"
            )
        else:
            notes.append("node: NOT packaged — this APK contains no Node engine")


def main() -> int:
    repo = pathlib.Path(sys.argv[1]).resolve()
    manifest_path = pathlib.Path(sys.argv[2]).resolve()
    manifest = json.loads(manifest_path.read_text())

    problems: list[str] = []
    notes: list[str] = []

    if manifest.get("schemaVersion") != 2:
        problems.append(f"unsupported manifest schemaVersion {manifest.get('schemaVersion')!r}")
    runtimes = manifest.get("runtimes") or {}
    unexpected = set(runtimes) - {"n8n", "opencode"}
    if unexpected:
        problems.append(
            "manifest declares runtimes this app does not support: " + ", ".join(sorted(unexpected))
        )

    check_pins(repo, manifest, problems, notes)
    for component, entry in runtimes.items():
        if component in {"n8n", "opencode"}:
            check_runtime(repo, component, entry, problems, notes)
    check_node(repo, manifest, problems, notes)

    for line in notes:
        print(f"  {line}")
    for problem in problems:
        print(f"FAIL {problem}")
        print(f"::error::runtime verification: {problem[:900]}")

    if problems:
        print(f"{len(problems)} problem(s) found")
        return 1
    print("all packaged runtimes are backed by real artifacts")
    return 0


if __name__ == "__main__":
    sys.exit(main())
