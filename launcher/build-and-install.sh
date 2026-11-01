#!/usr/bin/env bash
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
exec "${PYTHON:-python3}" "$REPO/tools/install.py" "$@"
