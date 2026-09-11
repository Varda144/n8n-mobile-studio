#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MANIFEST="$REPO_ROOT/android/app/src/main/assets/runtime/manifest.json"

echo "==> Verify runtime manifest"

if [ ! -f "$MANIFEST" ]; then
    echo "ERROR: manifest.json not found at $MANIFEST" >&2
    exit 1
fi

if command -v python3 >/dev/null 2>&1; then
    python3 -c "
import json
m = json.load(open('$MANIFEST'))
print(f'Schema version: {m.get(\"schemaVersion\", \"?\")}')
print(f'Built at:       {m.get(\"builtAt\", \"?\")}')
print()
for name, r in m.get('runtimes', {}).items():
    enabled  = r.get('enabled', False)
    packaged = r.get('packaged', False)
    port     = r.get('port', '?')
    payload  = r.get('payload', '?')
    print(f'  [{name}]')
    print(f'    enabled:  {enabled}')
    print(f'    packaged: {packaged}')
    print(f'    port:     {port}')
    print(f'    payload:  {payload}')
    print()
"
else
    echo "(python3 unavailable — using grep fallback)"
    echo ""
    for key in enabled packaged port; do
        echo "$key values:"
        grep -o "\"$key\":[^,}]*" "$MANIFEST" || true
        echo ""
    done
fi

echo "==> Verification complete."
