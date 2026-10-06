#!/usr/bin/env bash

set -euo pipefail

find env -type f -name "*.example" | while read -r example; do

    target="${example%.example}"

    if [[ -f "$target" ]]; then

        echo "[WARN] $target already exists. Skipping."

        continue

    fi

    cp "$example" "$target"

    echo "[INFO] Created $target"

done