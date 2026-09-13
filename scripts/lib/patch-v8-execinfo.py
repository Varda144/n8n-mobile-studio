#!/usr/bin/env python3
"""Patch V8 so it does not use <execinfo.h> on Android.

V8 decides whether backtrace()/backtrace_symbols() are available by looking for a
glibc-like libc:

    #if V8_LIBC_GLIBC || V8_LIBC_BSD || V8_LIBC_UCLIBC || V8_OS_SOLARIS
    #define HAVE_EXECINFO_H 1
    #endif

Bionic is not one of those, but it does ship an <execinfo.h> that declares none of
those functions, so any build that reaches this branch fails deep in
`stack_trace_posix.cc` with three "use of undeclared identifier" errors and no
hint about the cause. The patch makes the branch explicitly exclude Android, which
is what V8 does for other platforms without execinfo.

Idempotent: running it twice changes nothing, and an unexpected source layout is
reported instead of silently patched.
"""

from __future__ import annotations

import pathlib
import sys

ORIGINAL = "#if V8_LIBC_GLIBC || V8_LIBC_BSD || V8_LIBC_UCLIBC || V8_OS_SOLARIS"
PATCHED = """// android-node: Bionic ships <execinfo.h> without backtrace_symbols(), so V8
// must use its pointer-only fallback there.
#if (V8_LIBC_GLIBC || V8_LIBC_BSD || V8_LIBC_UCLIBC || V8_OS_SOLARIS) && \\
    !defined(__ANDROID__)"""


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-v8-execinfo.py <path to stack_trace_posix.cc>", file=sys.stderr)
        return 2
    path = pathlib.Path(sys.argv[1])
    if not path.is_file():
        print(f"FAIL {path} not found", file=sys.stderr)
        return 1

    text = path.read_text(encoding="utf-8")
    if PATCHED.splitlines()[2] in text:
        print("already patched")
        return 0
    if ORIGINAL not in text:
        print(f"FAIL {path}: expected V8 execinfo guard not found (source moved?)", file=sys.stderr)
        return 1

    path.write_text(text.replace(ORIGINAL, PATCHED, 1), encoding="utf-8")
    print(f"patched {path.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
