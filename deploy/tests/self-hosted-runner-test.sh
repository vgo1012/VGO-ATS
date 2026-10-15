#!/usr/bin/env bash
set -euo pipefail

workflow="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.github/workflows/deploy.yml"
test_dir="$(mktemp -d "${TMPDIR:-/tmp}/vgo-runner-test.XXXXXX")"
trap 'status=$?; if ((status != 0)) && [[ -f "$test_dir/missing-baseline.log" ]]; then cat "$test_dir/missing-baseline.log" >&2; fi; rm -rf -- "$test_dir"' EXIT

step_script() {
  awk -v name="$1" '
    { sub(/\r$/, "") }
    $0 == "      - name: " name { found = 1; next }
    found && $0 == "        run: |" { script = 1; next }
    script && /^          / { print substr($0, 11); next }
    script && /^[[:space:]]*$/ { print ""; next }
    script { exit }
  ' "$workflow"
}

step_script '配置 SSH' > "$test_dir/setup.sh"
step_script '清理发布凭据与源码包' > "$test_dir/cleanup.sh"
[[ -s "$test_dir/setup.sh" && -s "$test_dir/cleanup.sh" ]]
bash -n "$test_dir/setup.sh" "$test_dir/cleanup.sh"

[[ "$(grep -Fc 'runs-on: [self-hosted, Linux, vgo-deploy]' "$workflow")" == 2 ]]
grep -Fq "if: github.ref == 'refs/heads/main' && github.event_name == 'workflow_dispatch' && inputs.mode == 'deploy'" "$workflow"
grep -Fq "if: github.event_name == 'workflow_dispatch' && inputs.mode == 'monitor'" "$workflow"
grep -Fq 'persist-credentials: false' "$workflow"
grep -Fq 'UserKnownHostsFile=$DEPLOY_SSH_DIR/known_hosts' "$workflow"
grep -Fq 'StrictHostKeyChecking=yes' "$workflow"
grep -A1 -F '      - name: 清理发布凭据与源码包' "$workflow" | grep -Fq 'if: always()'

# 直接运行工作流的发布包选择逻辑，验证已获取和缺失的旧发布基线。
step_script '上传并发布' | awk '
  $0 == "deployment_mode=full" { found = 1 }
  found { print }
  found && /^echo "发布范围：/ { exit }
' > "$test_dir/select-package.sh"
[[ -s "$test_dir/select-package.sh" ]] || exit 1
bash -n "$test_dir/select-package.sh"

mkdir -p "$test_dir/source/deploy" "$test_dir/source/ui"
cp "$(dirname "$workflow")/../../deploy/deployment-scope.sh" "$test_dir/source/deploy/"
printf 'old\n' > "$test_dir/source/ui/version.txt"
printf 'obsolete\n' > "$test_dir/source/ui/obsolete file.txt"
git -C "$test_dir/source" init -q
git -C "$test_dir/source" config user.name 'deployment-test'
git -C "$test_dir/source" config user.email 'deployment-test@example.invalid'
git -C "$test_dir/source" add .
git -C "$test_dir/source" commit -qm 'initial release'
missing_release="$(git -C "$test_dir/source" rev-parse HEAD)"
for ((commit=1; commit<50; commit++)); do
  git -C "$test_dir/source" commit -q --allow-empty -m "release $commit"
done
available_release="$(git -C "$test_dir/source" rev-parse HEAD)"
printf 'new\n' > "$test_dir/source/ui/version.txt"
rm "$test_dir/source/ui/obsolete file.txt"
git -C "$test_dir/source" add .
git -C "$test_dir/source" commit -qm 'update frontend'
git clone -q --depth=50 "file://$test_dir/source" "$test_dir/shallow"
[[ "$(git -C "$test_dir/shallow" rev-parse --is-shallow-repository)" == true ]] || exit 1
mkdir -p "$test_dir/packages"
(
  set -euo pipefail
  cd "$test_dir/shallow"
  RUNNER_TEMP="$test_dir/packages"
  GITHUB_SHA="$(git rev-parse HEAD)"
  archive="vgo-${GITHUB_SHA}.tar.gz"
  previous_release="$missing_release"
  [[ "$(git cat-file -t "$previous_release" 2>/dev/null || true)" == '' ]] || exit 1
  source "$test_dir/select-package.sh" 2> "$test_dir/missing-baseline.log"
  # Bash 3.2 不会因 [[ ]] 失败触发 set -e，断言必须显式退出。
  [[ "$deployment_mode" == full && "$scope" == full ]] || exit 1
  [[ "$archive" == "vgo-${GITHUB_SHA}.tar.gz" ]] || exit 1

  previous_release="$available_release"
  source "$test_dir/select-package.sh"
  [[ "$deployment_mode" == delta && "$scope" == web ]] || exit 1
  [[ "$(tar -xOf "$RUNNER_TEMP/$archive" ./ui/version.txt)" == new ]] || exit 1
  [[ "$(tar -xOf "$RUNNER_TEMP/$archive" ./.deploy-deleted)" == 'ui/obsolete file.txt' ]] || exit 1
)
echo 'shallow checkout falls back for missing baselines and packages available baselines'

export RUNNER_TEMP="$test_dir/temp" GITHUB_ENV="$test_dir/env"
test_home="$test_dir/home"
export GITHUB_SHA=0123456789abcdef0123456789abcdef01234567
export SSH_PRIVATE_KEY='disposable-test-key' SSH_KNOWN_HOSTS='disposable-test-host-key'
mkdir -p "$RUNNER_TEMP" "$test_home/.ssh"
printf 'existing-user-host-key\n' > "$test_home/.ssh/known_hosts"
cp "$test_home/.ssh/known_hosts" "$test_dir/original-hosts"

# 缺少凭据时应在创建任何密钥文件之前失败。
if SSH_PRIVATE_KEY='' bash "$test_dir/setup.sh" 2>/dev/null; then
  echo 'setup accepted an empty SSH key' >&2
  exit 1
fi
[[ ! -e "$GITHUB_ENV" ]]

bash "$test_dir/setup.sh"
export DEPLOY_SSH_DIR="$(sed -n 's/^DEPLOY_SSH_DIR=//p' "$GITHUB_ENV")"
[[ "$DEPLOY_SSH_DIR" == "$RUNNER_TEMP"/vgo-ssh.* ]]
[[ "$(cat "$DEPLOY_SSH_DIR/id_vgo")" == "$SSH_PRIVATE_KEY" ]]
[[ "$(cat "$DEPLOY_SSH_DIR/known_hosts")" == "$SSH_KNOWN_HOSTS" ]]
cmp "$test_home/.ssh/known_hosts" "$test_dir/original-hosts"
[[ ! -e "$test_home/.ssh/id_vgo" ]]
case "$(uname -s)" in
  Linux*)
    [[ "$(stat -c %a "$DEPLOY_SSH_DIR")" == 700 ]]
    [[ "$(stat -c %a "$DEPLOY_SSH_DIR/id_vgo")" == 600 ]]
    [[ "$(stat -c %a "$DEPLOY_SSH_DIR/known_hosts")" == 600 ]]
    ;;
  *) echo 'POSIX permission assertions require the Linux runner' ;;
esac

touch "$RUNNER_TEMP/vgo-${GITHUB_SHA}.tar.gz" "$RUNNER_TEMP/vgo-delta-${GITHUB_SHA}.tar.gz"
bash "$test_dir/cleanup.sh"
[[ ! -e "$DEPLOY_SSH_DIR" ]]
[[ ! -e "$RUNNER_TEMP/vgo-${GITHUB_SHA}.tar.gz" ]]
[[ ! -e "$RUNNER_TEMP/vgo-delta-${GITHUB_SHA}.tar.gz" ]]
cmp "$test_home/.ssh/known_hosts" "$test_dir/original-hosts"

# checkout 或验证阶段失败时，SSH 步骤尚未执行，清理仍须成功。
DEPLOY_SSH_DIR='' bash "$test_dir/cleanup.sh"
echo 'self-hosted routing, branch guard and temporary SSH lifecycle cases passed'
