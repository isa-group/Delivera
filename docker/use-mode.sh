#!/usr/bin/env bash

set -euo pipefail

MODE="${1:-dev}"

SOURCE="env/.env.compose.${MODE}"
TARGET=".env"

if [[ ! -f "$SOURCE" ]]; then
    bash generate-compose-env.sh "$MODE"

fi

cp "$SOURCE" "$TARGET"

echo "[INFO] Activated compose environment: $MODE"