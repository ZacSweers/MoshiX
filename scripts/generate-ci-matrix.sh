#!/bin/bash

# Copyright (C) 2026 Zac Sweers
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

ALIASES_FILE="compiler-version-aliases.txt"

echo "Reading Kotlin compiler versions from $ALIASES_FILE..."

versions=$(grep -v '^[[:space:]]*#' "$ALIASES_FILE" | grep -v '^[[:space:]]*$')

if [[ -z "$versions" ]]; then
  echo "No Kotlin compiler versions found in $ALIASES_FILE" >&2
  exit 1
fi

rows=""
first=true
for kotlin_version in $versions; do
  for ksp_enabled in true false; do
    echo "  - Kotlin $kotlin_version / KSP $ksp_enabled"

    row="{\"kotlin\":\"$kotlin_version\",\"ksp_enabled\":$ksp_enabled}"
    if [[ "$first" == true ]]; then
      rows="$row"
      first=false
    else
      rows="$rows,$row"
    fi
  done
done

matrix_json="{\"include\":[$rows]}"

echo ""
echo "Generated matrix JSON:"
echo "$matrix_json"

if command -v jq >/dev/null 2>&1; then
  echo ""
  echo "Pretty-printed matrix:"
  echo "$matrix_json" | jq .
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "matrix=$matrix_json" >> "$GITHUB_OUTPUT"
fi
