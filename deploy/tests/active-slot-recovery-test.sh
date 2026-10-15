#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/deploy.sh"
function_body="$(sed -n '/^active_web_slot_from_socket() {/,/^}/p' "$script")"
[[ -n "$function_body" ]] || { echo 'missing active slot reader' >&2; exit 1; }
eval "$function_body"

docker() {
  [[ "$*" == 'exec vgo-edge-1 readlink /run/web-sockets/active.sock' ]] || return 2
  [[ "${mock_failure:-false}" == false ]] || return 1
  printf '%s\n' "$mock_target"
}

for mock_target in blue.sock green.sock; do
  [[ "$(active_web_slot_from_socket)" == "${mock_target%.sock}" ]]
done
mock_target=other.sock
if active_web_slot_from_socket >/dev/null; then exit 1; fi
mock_failure=true
if active_web_slot_from_socket >/dev/null; then exit 1; fi
echo 'active slot reader follows the edge socket and rejects invalid state'
