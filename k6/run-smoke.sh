#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080/api/v1}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

echo "Running k6 smoke against ${BASE_URL}" >&2
k6 run -e BASE_URL="$BASE_URL" "$SCRIPT_DIR/smoke.js"
