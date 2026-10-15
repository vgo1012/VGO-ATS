#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/deployment-scope.sh"

assert_scope() {
  local expected="$1"
  shift
  local actual
  if (($# > 0)); then
    actual="$(printf '%s\0' "$@" | bash "$script")"
  else
    actual="$(bash "$script" </dev/null)"
  fi
  [[ "$actual" == "$expected" ]] || {
    echo "expected $expected, got $actual for paths: $*" >&2
    exit 1
  }
}

assert_scope web ui/src/App.css 'ui/file with space.css'
assert_scope web ui/src/old.css
assert_scope full ui/src/App.css deploy/deploy.sh
assert_scope full ui/src/App.css docs/README.md
assert_scope full
echo 'deployment scope cases passed'
