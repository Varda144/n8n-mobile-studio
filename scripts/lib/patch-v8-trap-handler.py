#!/usr/bin/env python3
"""Disable V8's WebAssembly trap handler in a Node source tree, for Android.

Every Android build of Node needs this. The trap handler is a JIT/Wasm feature that
assumes a signal-handling contract Android's runtime does not offer, and V8 refuses
to enable it there — "It would require some careful security review before the trap
handler can be enabled on Android".

Upstream ships `android-patches/trap-handler.h.patch` for exactly this, and Node's
`android-configure patch` applies it. That patch was written in 2022 and no longer
applies to V8 13.x: the header gained Loong64 and RISC-V clauses and the arm64
simulator clause changed, so `patch -f` fails, `android-configure` still prints
"Tried to patch", and the build proceeds with the trap handler *enabled* for the
arm64-simulator-on-x64 host toolchain. The failure surfaces much later and far away,
as a link of the host libraries:

    handler-outside.cc: undefined reference to `trap_handler::RegisterDefaultTrapHandler()'
    api.cc:             undefined reference to `trap_handler::TryHandleSignal(int, siginfo_t*, void*)'
    simulator-arm64.cc: undefined reference to `v8_internal_simulator_ProbeMemory'

Each symbol is defined in a translation unit behind the same guard that the
unpatched header turns on and off inconsistently, which is why the failure looks
like a linker problem and is really a configuration one.

This script performs the same edit as upstream's patch, but it locates the block by
structure rather than by context lines, and it verifies the outcome: either the
header ends up with the trap handler unconditionally off, or the build stops here
with a reason. Idempotent — a header that is already disabled is left alone.
"""

from __future__ import annotations

import pathlib
import re
import sys

DEFINE = "#define V8_TRAP_HANDLER_SUPPORTED"
VIA_SIMULATOR = "V8_TRAP_HANDLER_VIA_SIMULATOR"

REPLACEMENT = """// android-node: the WebAssembly trap handler is unsupported on Android, and it
// cannot be built for an arm64 target on an x64 host without linking simulator
// code that only exists when the handler is enabled. Force it off, as the
// platforms V8 does not support here have always done.
#define V8_TRAP_HANDLER_SUPPORTED false"""

_OPENERS = ("#if ", "#if\t", "#ifdef", "#ifndef")
_CLOSER = "#endif"


def _is_directive(line: str, prefixes: tuple[str, ...]) -> bool:
    stripped = line.strip()
    return any(stripped.startswith(prefix) for prefix in prefixes)


def _guard_block(lines: list[str], define_index: int) -> tuple[int, int] | None:
    """The `#if … #endif` block that decides V8_TRAP_HANDLER_SUPPORTED.

    Found structurally: the nearest conditional above the first definition, and its
    matching terminator. Anchoring on the architecture expressions themselves would
    break on the next V8 release, which is how this patch came to exist.
    """
    start = None
    for index in range(define_index, -1, -1):
        if _is_directive(lines[index], _OPENERS):
            start = index
            break
    if start is None:
        return None

    depth = 0
    for index in range(start, len(lines)):
        if _is_directive(lines[index], _OPENERS):
            depth += 1
        elif lines[index].strip().startswith(_CLOSER):
            depth -= 1
            if depth == 0:
                return start, index
    return None


def patch(text: str) -> tuple[str, str]:
    """Returns (new text, outcome) where outcome is 'patched' or 'already disabled'."""
    if not re.search(rf"^{re.escape(DEFINE)}\b", text, flags=re.MULTILINE):
        raise ValueError(f"{DEFINE} not found at all — V8's trap handler source moved")

    enabled = bool(re.search(rf"^{re.escape(DEFINE)} true", text, flags=re.MULTILINE)) or \
        VIA_SIMULATOR in text
    if not enabled:
        return text, "already disabled"

    lines = text.splitlines()
    define_index = next(
        index for index, line in enumerate(lines) if line.strip().startswith(DEFINE)
    )
    block = _guard_block(lines, define_index)
    if block is None:
        raise ValueError("could not find the conditional block around the trap handler define")

    start, end = block
    patched = lines[:start] + REPLACEMENT.splitlines() + lines[end + 1:]
    return "\n".join(patched) + "\n", "patched"


def verify(text: str) -> None:
    """The post-condition the build depends on: the handler cannot be on."""
    if re.search(rf"^{re.escape(DEFINE)} true", text, flags=re.MULTILINE):
        raise ValueError("a `V8_TRAP_HANDLER_SUPPORTED true` branch survived the patch")
    if VIA_SIMULATOR in text:
        raise ValueError(f"{VIA_SIMULATOR} survived the patch")
    if len(re.findall(rf"^{re.escape(DEFINE)} false", text, flags=re.MULTILINE)) != 1:
        raise ValueError("the header does not define exactly one `V8_TRAP_HANDLER_SUPPORTED false`")


# --------------------------------------------------------------------------- tests

_V8_13 = '''#include "include/v8config.h"

namespace v8::internal::trap_handler {

// X64 on Linux, Windows, MacOS, FreeBSD.
#if V8_HOST_ARCH_X64 && V8_TARGET_ARCH_X64 &&                        \\
    ((V8_OS_LINUX && !V8_OS_ANDROID) || V8_OS_WIN || V8_OS_DARWIN || \\
     V8_OS_FREEBSD)
#define V8_TRAP_HANDLER_SUPPORTED true
// Arm64 simulator on x64 on Linux, Mac, or Windows.
#elif V8_TARGET_ARCH_ARM64 && V8_HOST_ARCH_X64 && \\
    (V8_OS_LINUX || V8_OS_DARWIN || V8_OS_WIN)
#define V8_TRAP_HANDLER_VIA_SIMULATOR
#define V8_TRAP_HANDLER_SUPPORTED true
// RISC-V 64 simulator on x64 on Linux.
#elif V8_TARGET_ARCH_RISCV64 && V8_HOST_ARCH_X64 && V8_OS_LINUX
#define V8_TRAP_HANDLER_VIA_SIMULATOR
#define V8_TRAP_HANDLER_SUPPORTED true
// Everything else is unsupported.
#else
#define V8_TRAP_HANDLER_SUPPORTED false
#endif

#if V8_OS_ANDROID && V8_TRAP_HANDLER_SUPPORTED
#error "The V8 trap handler should not be enabled on Android"
#endif
'''

_V8_2022 = '''namespace v8::internal::trap_handler {

// X64 on Linux, Windows, MacOS, FreeBSD.
#if V8_HOST_ARCH_X64 && V8_TARGET_ARCH_X64 && ((V8_OS_LINUX && !V8_OS_ANDROID))
#define V8_TRAP_HANDLER_SUPPORTED true
// Arm64 simulator on x64 on Linux, Mac, or Windows.
#elif V8_TARGET_ARCH_ARM64 && V8_HOST_ARCH_X64 && (V8_OS_LINUX || V8_OS_DARWIN)
#define V8_TRAP_HANDLER_VIA_SIMULATOR
#define V8_TRAP_HANDLER_SUPPORTED true
#else
#define V8_TRAP_HANDLER_SUPPORTED false
#endif

#if V8_OS_ANDROID && V8_TRAP_HANDLER_SUPPORTED
#error "The V8 trap handler should not be enabled on Android"
#endif
'''

_DISABLED = '''namespace v8::internal::trap_handler {

#define V8_TRAP_HANDLER_SUPPORTED false

#if V8_OS_ANDROID && V8_TRAP_HANDLER_SUPPORTED
#error "The V8 trap handler should not be enabled on Android"
#endif
'''


def selftest() -> int:
    failures: list[str] = []

    def check(name: str, condition: bool) -> None:
        print(("  ok   " if condition else "  FAIL ") + name)
        if not condition:
            failures.append(name)

    for label, fixture in (("v8 13.x layout", _V8_13), ("2022 layout", _V8_2022)):
        once, outcome = patch(fixture)
        check(f"{label}: reports it patched the header", outcome == "patched")
        check(f"{label}: no enabled branch remains", "#define V8_TRAP_HANDLER_SUPPORTED true" not in once)
        check(f"{label}: simulator path is gone", VIA_SIMULATOR not in once)
        check(f"{label}: handler ends up off", "#define V8_TRAP_HANDLER_SUPPORTED false" in once)
        check(f"{label}: surrounding code survives", "#if V8_OS_ANDROID && V8_TRAP_HANDLER_SUPPORTED" in once)
        check(f"{label}: error directive survives", '#error "The V8 trap handler should not be enabled on Android"' in once)

        twice, second = patch(once)
        check(f"{label}: second run is a no-op", second == "already disabled" and twice == once)
        try:
            verify(once)
            check(f"{label}: post-condition holds", True)
        except ValueError as error:  # pragma: no cover - failure path
            check(f"{label}: post-condition holds ({error})", False)

    check("an already-disabled header is left untouched", patch(_DISABLED)[0] == _DISABLED)
    check("the 2022 fixture really does enable the handler",
          "#define V8_TRAP_HANDLER_SUPPORTED true" in _V8_2022)

    try:
        patch("// a V8 source tree without the trap handler\n")
        check("an unexpected layout is rejected", False)
    except ValueError:
        check("an unexpected layout is rejected", True)

    try:
        verify(_V8_13)
        check("verification rejects a header that is still enabled", False)
    except ValueError:
        check("verification rejects a header that is still enabled", True)

    print()
    print(f"trap-handler patch self-test: {len(failures)} failed")
    return 1 if failures else 0


def main(argv: list[str]) -> int:
    if argv[1:2] == ["--selftest"]:
        return selftest()
    if len(argv) != 2:
        print("usage: patch-v8-trap-handler.py <path to src/trap-handler/trap-handler.h>", file=sys.stderr)
        return 2

    path = pathlib.Path(argv[1])
    if not path.is_file():
        print(f"FAIL {path} not found", file=sys.stderr)
        return 1

    original = path.read_text(encoding="utf-8")
    try:
        updated, outcome = patch(original)
        verify(updated)
    except ValueError as error:
        print(f"FAIL {path}: {error}", file=sys.stderr)
        return 1

    if outcome == "already disabled":
        print("trap handler already disabled")
        return 0

    path.write_text(updated, encoding="utf-8")
    print(f"disabled the V8 trap handler in {path.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
