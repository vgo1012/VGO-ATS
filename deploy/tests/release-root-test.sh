#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/deploy.sh"
root_line="$(grep -m1 '^release_root=' "$script")"
test_dir="$(mktemp -d "${TMPDIR:-/tmp}/vgo-release-root.XXXXXX")"
cleanup() {
  local resolved parent
  resolved="$(realpath -e "$test_dir")"
  parent="$(realpath -e "$(dirname "$test_dir")")"
  if [[ "$(dirname "$resolved")" == "$parent" && "$(basename "$resolved")" == vgo-release-root.* ]]; then
    rm -rf -- "$resolved"
  fi
}
trap cleanup EXIT

mkdir -p "$test_dir/releases/version/deploy"
printf '#!/usr/bin/env bash\n%s\nprintf "%%s\\n" "$release_root"\n' "$root_line" \
  > "$test_dir/releases/version/deploy/probe.sh"
ln -s "$test_dir/releases/version" "$test_dir/current"
if [[ ! -L "$test_dir/current" ]]; then
  echo 'release root symlink check skipped: native symlinks unavailable'
  exit 0
fi
actual="$(bash "$test_dir/current/deploy/probe.sh")"
expected="$(cd "$test_dir/releases/version" && pwd -P)"
[[ "$actual" == "$expected" ]] || {
  echo "release root resolved to $actual, expected $expected" >&2
  exit 1
}
echo 'release root resolves physical path'
