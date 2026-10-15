#!/usr/bin/env bash
set -euo pipefail

scope=web
found=false
while IFS= read -r -d '' path; do
  found=true
  [[ "$path" == ui/* ]] || scope=full
done
[[ "$found" == true ]] || scope=full
printf '%s\n' "$scope"
