#!/usr/bin/env bash
set -Eeuo pipefail

release_id="${1:-}"
if [[ ! "$release_id" =~ ^[0-9a-f]{7,40}$ ]]; then
  echo "release id must be a Git commit SHA" >&2
  exit 2
fi
scope="${2:-full}"
if [[ "$scope" != full && "$scope" != web ]]; then
  echo "deployment scope must be full or web" >&2
  exit 2
fi

release_root="$(dirname "$(dirname "$(realpath -e "${BASH_SOURCE[0]}")")")"
releases_root=/opt/vgo/releases
shared_root=/opt/vgo/shared
env_file="$shared_root/.env"
secret_file="$shared_root/application-prod-secrets.yml"
current_link=/opt/vgo/current
deploy_lock="$shared_root/deploy.lock"

exec 9>"$deploy_lock"
echo "waiting for deployment lock: $release_id"
if ! flock -w 3600 9; then
  echo "timed out waiting for deployment lock: $release_id" >&2
  exit 75
fi
echo "deployment lock acquired: $release_id"

for required in "$env_file" "$secret_file"; do
  if [[ ! -f "$required" ]]; then
    echo "missing required production file: $required" >&2
    exit 2
  fi
done

export RELEASE_ID="$release_id"
previous_root="$(readlink -f "$current_link" 2>/dev/null || true)"
web_assets_volume=vgo_web_build_assets
edge_dir="$shared_root/edge"
active_slot_file="$shared_root/active-web-slot"
active_slot=""
previous_backend_tag="$(docker inspect -f '{{.Config.Image}}' vgo-backend-1 2>/dev/null || true)"
previous_backend_tag="${previous_backend_tag#vgo-backend:}"
backend_changed=false
legacy_stopped=false
edge_started=false
edge_backup=""
edge_config_changed=false
socket_switched=false

active_web_slot_from_socket() {
  local socket_target
  socket_target="$(docker exec vgo-edge-1 readlink /run/web-sockets/active.sock)" || return
  case "$socket_target" in
    blue.sock|green.sock) printf '%s\n' "${socket_target%.sock}" ;;
    *) return 1 ;;
  esac
}

if docker inspect vgo-edge-1 >/dev/null 2>&1; then
  active_slot="$(active_web_slot_from_socket)" || { echo "invalid active Web socket" >&2; exit 1; }
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "vgo-web-$active_slot-1" 2>/dev/null || true)" == healthy ]] || {
    echo "active Web slot is not healthy" >&2; exit 1;
  }
  [[ "$(docker inspect -f '{{.State.Running}}' vgo-edge-1 2>/dev/null || true)" == true ]] || {
    echo "public edge is not running" >&2; exit 1;
  }
  if [[ -f "$active_slot_file" && "$(cat "$active_slot_file")" != "$active_slot" ]]; then
    echo "warning: recovering active Web slot from edge socket" >&2
  fi
elif [[ -f "$active_slot_file" ]]; then
  echo "active Web state exists without an edge container" >&2
  exit 1
fi

target_slot=blue
[[ "$active_slot" == blue ]] && target_slot=green
WEB_BLUE_RELEASE_ID="$release_id"
WEB_GREEN_RELEASE_ID="$release_id"
if [[ -n "$active_slot" ]]; then
  active_image="$(docker inspect -f '{{.Config.Image}}' "vgo-web-$active_slot-1")"
  [[ "$active_image" =~ ^vgo-web:([0-9a-f]{7,40})$ ]] || { echo "invalid active Web image" >&2; exit 1; }
  if [[ "$active_slot" == blue ]]; then WEB_BLUE_RELEASE_ID="${BASH_REMATCH[1]}";
  else WEB_GREEN_RELEASE_ID="${BASH_REMATCH[1]}"; fi
fi
export WEB_BLUE_RELEASE_ID WEB_GREEN_RELEASE_ID
compose=(docker compose --project-name vgo --env-file "$env_file" -f "$release_root/deploy/docker-compose.prod.yml")

prime_web_assets() {
  local image="$1"
  docker run --rm --network none --read-only \
    --mount "type=volume,source=$web_assets_volume,target=/retained" \
    --entrypoint sh "$image" -ec \
    'test -d /usr/share/nginx/html/_build && cp -R /usr/share/nginx/html/_build/. /retained/'
}

wait_for_health() {
  local attempts=60
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --fail --silent --show-error --max-time 10 \
      --resolve www.vgo.example.org:443:127.0.0.1 \
      https://www.vgo.example.org/healthz >/dev/null; then
      return 0
    fi
    sleep 5
  done
  return 1
}

rollback() {
  echo "rolling back Web release $release_id" >&2
  if [[ "$socket_switched" == true && -n "$active_slot" ]]; then
    switch_active_socket "$active_slot" || return
  fi
  if [[ -n "$edge_backup" ]]; then
    for file in default.conf security-headers.inc; do
      cp "$edge_backup/$file" "$edge_dir/$file.restore" || return
      mv -f "$edge_dir/$file.restore" "$edge_dir/$file" || return
    done
    docker exec vgo-edge-1 nginx -t || return
    docker exec vgo-edge-1 nginx -s reload || return
  fi
  if [[ "$edge_started" == true ]]; then
    "${compose[@]}" stop edge || true
    docker rm vgo-edge-1 >/dev/null 2>&1 || true
  fi
  if [[ "$legacy_stopped" == true ]]; then
    if [[ -n "$previous_root" && -f "$previous_root/deploy/docker-compose.prod.yml" ]]; then
      RELEASE_ID="$(basename "$previous_root")" docker compose --project-name vgo \
        --env-file "$env_file" -f "$previous_root/deploy/docker-compose.prod.yml" \
        up -d --no-deps web || return
    fi
  fi
  if [[ "$backend_changed" == true && "$previous_backend_tag" =~ ^[0-9a-f]{7,40}$ \
      && -n "$previous_root" && -f "$previous_root/deploy/docker-compose.prod.yml" ]]; then
    RELEASE_ID="$previous_backend_tag" docker compose --project-name vgo \
      --env-file "$env_file" -f "$previous_root/deploy/docker-compose.prod.yml" \
      up -d --wait --wait-timeout 300 backend || return
  fi
  [[ -z "$previous_root" ]] || wait_for_health
}

install_edge_config() {
  local backup_candidate
  install -d -m 755 "$edge_dir" || return
  if [[ -n "$active_slot" ]] &&
    cmp -s "$release_root/deploy/nginx.conf" "$edge_dir/default.conf" &&
    cmp -s "$release_root/deploy/security-headers.conf" "$edge_dir/security-headers.inc"; then
    return 0
  fi
  edge_config_changed=true
  if [[ -n "$active_slot" ]]; then
    backup_candidate="$(mktemp -d "$shared_root/edge-backup.XXXXXX")" || return
    if ! cp -a "$edge_dir/." "$backup_candidate/"; then
      [[ "$backup_candidate" == "$shared_root"/edge-backup.* ]] && rm -rf -- "$backup_candidate"
      return 1
    fi
    edge_backup="$backup_candidate"
  fi
  install -m 644 "$release_root/deploy/nginx.conf" "$edge_dir/default.conf.new" || return
  mv -f "$edge_dir/default.conf.new" "$edge_dir/default.conf" || return
  install -m 644 "$release_root/deploy/security-headers.conf" "$edge_dir/security-headers.inc.new" || return
  mv -f "$edge_dir/security-headers.inc.new" "$edge_dir/security-headers.inc"
}

switch_active_socket() {
  local slot="$1"
  docker exec "vgo-web-$slot-1" sh -ec '
    cd /run/web-sockets
    rm -f active.new
    ln -s "$1.sock" active.new
    mv -f active.new active.sock
    test "$(readlink active.sock)" = "$1.sock"
  ' sh "$slot"
}

wait_for_static_index() {
  local expected actual attempt
  expected="$(docker exec "vgo-web-$target_slot-1" sha256sum /usr/share/nginx/html/index.html | awk '{print $1}')"
  for ((attempt = 1; attempt <= 30; attempt++)); do
    if actual="$(curl --fail --silent --show-error --max-time 10 \
      --resolve www.vgo.example.org:443:127.0.0.1 https://www.vgo.example.org/ \
      | sha256sum | awk '{print $1}')" && [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "public homepage does not match the new Web slot" >&2
  return 1
}

retain_release_count=5

cleanup_old_releases() {
  local previous_id=""
  local candidate candidate_path resolved image
  local retained=0
  local -a release_candidates=()
  local -A protected_releases=()

  protected_releases["$release_id"]=1
  if [[ -n "$previous_root" ]]; then
    previous_id="$(basename "$previous_root")"
    if [[ "$previous_id" =~ ^[0-9a-f]{7,40}$ ]]; then
      protected_releases["$previous_id"]=1
    fi
  fi

  mapfile -t release_candidates < <(
    find "$releases_root" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %f\n' \
      | sort -nr \
      | awk '{print $2}'
  )

  for candidate in "${release_candidates[@]}"; do
    [[ "$candidate" =~ ^[0-9a-f]{7,40}$ ]] || continue
    if ((retained < retain_release_count)); then
      protected_releases["$candidate"]=1
      ((retained += 1))
      continue
    fi
    [[ -n "${protected_releases[$candidate]:-}" ]] && continue

    candidate_path="$releases_root/$candidate"
    if ! resolved="$(realpath -e -- "$candidate_path")"; then
      echo "warning: cannot resolve old release: $candidate_path" >&2
      continue
    fi
    if [[ ! -d "$resolved" || "$(dirname "$resolved")" != "$releases_root" \
      || "$(basename "$resolved")" != "$candidate" ]]; then
      echo "warning: refusing to remove unexpected release path: $resolved" >&2
      continue
    fi

    for image in "vgo-backend:$candidate" "vgo-web:$candidate"; do
      if docker image inspect "$image" >/dev/null 2>&1 && ! docker image rm "$image"; then
        echo "warning: cannot remove old application image: $image" >&2
      fi
    done
    rm -rf -- "$resolved"
    echo "removed old release: $candidate"
  done

  docker image prune --force
  docker builder prune --all --force --max-used-space 8GB
}

if [[ "$scope" == web ]]; then
  if [[ ! -f "$previous_root/deploy/docker-compose.prod.yml" ]] ||
    [[ "$(docker inspect -f '{{.State.Health.Status}}' vgo-backend-1 2>/dev/null || true)" != healthy ]]; then
    echo "web-only deployment requires a healthy existing backend" >&2
    exit 1
  fi
  "${compose[@]}" build --pull "web-$target_slot"
else
  "${compose[@]}" build --pull backend "web-$target_slot"
fi

while IFS= read -r image_tag; do
  if [[ "$image_tag" =~ ^[0-9a-f]{7,40}$ && "$image_tag" != "$release_id" ]]; then
    prime_web_assets "vgo-web:$image_tag"
  fi
done < <(docker image ls vgo-web --format '{{.Tag}}')
prime_web_assets "vgo-web:$release_id"

if [[ "$scope" == full ]]; then
  backend_changed=true
  if ! "${compose[@]}" up -d --wait --wait-timeout 300 backend; then
    rollback || true
    exit 1
  fi
fi
if ! "${compose[@]}" up -d --no-deps --wait --wait-timeout 180 "web-$target_slot"; then
  rollback || true
  exit 1
fi

if ! install_edge_config; then
  rollback || true
  exit 1
fi
if [[ -n "$active_slot" ]]; then
  if [[ "$edge_config_changed" == true ]] &&
    { ! docker exec vgo-edge-1 nginx -t || ! docker exec vgo-edge-1 nginx -s reload; }; then
    rollback || true
    exit 1
  fi
else
  if ! switch_active_socket "$target_slot"; then rollback || true; exit 1; fi
  if docker inspect vgo-web-1 >/dev/null 2>&1; then
    legacy_stopped=true
    if ! docker stop vgo-web-1; then rollback || true; exit 1; fi
  fi
  edge_started=true
  if ! "${compose[@]}" up -d --no-deps --wait --wait-timeout 180 edge; then
    rollback || true
    exit 1
  fi
fi

if [[ -n "$active_slot" ]]; then
  socket_switched=true
  if ! switch_active_socket "$target_slot"; then rollback || true; exit 1; fi
fi

if ! wait_for_health || ! wait_for_static_index; then
  "${compose[@]}" ps >&2 || true
  "${compose[@]}" logs --tail=200 backend edge "web-$target_slot" >&2 || true
  rollback || true
  exit 1
fi

if ! (install -d -m 700 /opt/vgo/shared/backups/mysql-hourly &&
  install -m 755 "$release_root/deploy/vgo-mysql-backup" /usr/local/sbin/vgo-mysql-backup &&
  install -m 644 "$release_root/deploy/vgo-mysql-backup.service" /etc/systemd/system/vgo-mysql-backup.service &&
  install -m 644 "$release_root/deploy/vgo-mysql-backup.timer" /etc/systemd/system/vgo-mysql-backup.timer &&
  systemctl daemon-reload && systemctl start vgo-mysql-backup.service &&
  systemctl enable --now vgo-mysql-backup.timer); then
  rollback || true
  exit 1
fi

if ! (install -m 755 "$release_root/deploy/vgo-health-check" /usr/local/sbin/vgo-health-check &&
  install -m 644 "$release_root/deploy/vgo-health-check.service" /etc/systemd/system/vgo-health-check.service &&
  install -m 644 "$release_root/deploy/vgo-health-check.timer" /etc/systemd/system/vgo-health-check.timer &&
  systemctl daemon-reload && systemctl enable --now vgo-health-check.timer); then
  rollback || true
  exit 1
fi

if ! printf '%s\n' "$target_slot" > "${active_slot_file}.new" ||
  ! mv -f "${active_slot_file}.new" "$active_slot_file"; then
  rollback || true
  exit 1
fi
temporary_link="${current_link}.new"
if ! ln -sfn "$release_root" "$temporary_link" || ! mv -Tf "$temporary_link" "$current_link"; then
  if [[ -n "$active_slot" ]]; then
    printf '%s\n' "$active_slot" > "${active_slot_file}.restore"
    mv -f "${active_slot_file}.restore" "$active_slot_file"
  else
    rm -f "$active_slot_file"
  fi
  rollback || true
  exit 1
fi
if [[ -n "$edge_backup" ]]; then
  [[ "$edge_backup" == "$shared_root"/edge-backup.* ]] && rm -rf -- "$edge_backup"
fi
if [[ "$legacy_stopped" == true ]]; then docker rm vgo-web-1 >/dev/null || true; fi
if ! cleanup_old_releases; then
  echo "warning: deployment is healthy, but release cleanup failed" >&2
fi
echo "deployment healthy: $release_id"
