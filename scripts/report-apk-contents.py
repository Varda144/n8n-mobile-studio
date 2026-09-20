#!/usr/bin/env python3
"""Report what a built APK actually contains.

The acceptance criterion for this project is that a runtime is only called
"embedded" once its payload is packaged in the APK. This script reads the APK the
way the device will: it lists the native libraries, the bundled payload archives
and the runtime manifest, and prints the result as workflow annotations, which are
readable (`gh run view`) even when the raw job log is not reachable.

Exit code is 0 when the APK is consistent with its own manifest:
  * every runtime the manifest marks `packaged: true` has its archive in the APK;
  * the Node engine the manifest declares is present for each declared ABI;
  * anything the manifest marks `packaged: false` is absent (no silent payload).

Usage: report-apk-contents.py <path to apk> [--require n8n,opencode]
"""

from __future__ import annotations

import json
import pathlib
import sys
import zipfile

MIB = 1024 * 1024


def announce(level: str, message: str) -> None:
    print(f"::notice::{message}" if level == "notice" else f"::{level}::{message}")


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    require: list[str] = []
    if "--require" in sys.argv:
        require = [r for r in sys.argv[sys.argv.index("--require") + 1].split(",") if r]
    if not args:
        print("usage: report-apk-contents.py <apk> [--require node,n8n]", file=sys.stderr)
        return 2

    apk = pathlib.Path(args[0])
    if not apk.is_file():
        print(f"FAIL no APK at {apk}", file=sys.stderr)
        return 1

    problems: list[str] = []
    with zipfile.ZipFile(apk) as bundle:
        names = bundle.namelist()
        sizes = {info.filename: info.file_size for info in bundle.infolist()}
        engines = {n: sizes[n] for n in names if n.startswith("lib/") and n.endswith("libnode.so")}
        extra_libs = {n: sizes[n] for n in names if n.startswith("lib/") and n.endswith("libc++_shared.so")}
        payloads = {n: sizes[n] for n in names if n.startswith("assets/runtime/") and n.endswith(".zip")}
        manifest_name = "assets/runtime/manifest.json"
        manifest = None
        if manifest_name in names:
            manifest = json.loads(bundle.read(manifest_name).decode("utf-8"))

    announce("notice", f"APK {apk.name}: {apk.stat().st_size / MIB:.1f} MiB, {len(names)} entries")
    for name, size in sorted(engines.items()):
        announce("notice", f"engine {name}: {size / MIB:.1f} MiB")
    for name, size in sorted(extra_libs.items()):
        announce("notice", f"C++ runtime {name}: {size / MIB:.1f} MiB")
    for name, size in sorted(payloads.items()):
        announce("notice", f"payload {name}: {size / MIB:.1f} MiB")

    if manifest is None:
        # An APK without a manifest embeds nothing; that is only acceptable when
        # nothing was required.
        message = "APK carries no assets/runtime/manifest.json — it embeds no runtimes"
        (announce("error", message), problems.append(message))[0]
        manifest = {"node": {}, "runtimes": {}}

    node = manifest.get("node") or {}
    announce(
        "notice",
        f"manifest: node packaged={node.get('packaged')} abis={','.join(sorted(node.get('abis') or {})) or 'none'}",
    )
    for component, entry in sorted((manifest.get("runtimes") or {}).items()):
        announce("notice", f"manifest: {component} packaged={entry.get('packaged')} version={entry.get('version')}")

    # 1. Claims must be backed by archives.
    for component, entry in (manifest.get("runtimes") or {}).items():
        expected = f"assets/runtime/{component}/payload.zip"
        if entry.get("packaged") and expected not in payloads:
            problem = f"{component} is marked packaged but {expected} is not in the APK"
            announce("error", problem)
            problems.append(problem)

    # 2. The engine must be there for every ABI the manifest claims.
    if node.get("packaged"):
        for abi in (node.get("abis") or {}):
            engine = f"lib/{abi}/{node.get('library', 'libnode.so')}"
            if engine not in names:
                problem = f"node is marked packaged for {abi} but {engine} is not in the APK"
                announce("error", problem)
                problems.append(problem)
            for extra in node.get("extraLibs") or []:
                if f"lib/{abi}/{extra}" not in names:
                    problem = f"node/{abi} needs {extra} but lib/{abi}/{extra} is not in the APK"
                    announce("error", problem)
                    problems.append(problem)

    # 3. What the build was asked to embed must really be embedded.
    for component in require:
        if component == "node":
            if not engines:
                problem = "the build required the Node engine, but the APK contains none"
                announce("error", problem)
                problems.append(problem)
        else:
            if f"assets/runtime/{component}/payload.zip" not in payloads:
                problem = f"the build required {component}, but its payload archive is not in the APK"
                announce("error", problem)
                problems.append(problem)

    if problems:
        print(f"FAIL {len(problems)} problem(s) with the built APK", file=sys.stderr)
        return 1

    announce("notice", "APK contents match the manifest it carries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
