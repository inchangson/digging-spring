#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

REQUESTS="${REQUESTS:-2000}"
CONCURRENCY="${CONCURRENCY:-32}"
TARGET_DIR="$ROOT_DIR/target/load-profile"
FORM_FILE="$TARGET_DIR/client-credentials.form"
APP_PID=""
mkdir -p "$TARGET_DIR"
tr -d '\r\n' <scripts/client-credentials.form >"$FORM_FILE"

cleanup() {
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID"
    wait "$APP_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

docker compose up -d --wait
mvn -B -q -DskipTests package

start_app() {
  local name="$1"
  local cache="$2"
  java -jar target/sas-demo-1.0.0.jar --demo.policy-cache="$cache" >"$TARGET_DIR/$name-app.log" 2>&1 &
  APP_PID="$!"
  for _ in {1..120}; do
    if curl -fsS http://127.0.0.1:9099/.well-known/oauth-authorization-server >/dev/null; then
      return
    fi
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      echo "application exited before readiness; see $TARGET_DIR/$name-app.log" >&2
      exit 1
    fi
    sleep 0.25
  done
  echo "application readiness timed out" >&2
  exit 1
}

stop_app() {
  kill "$APP_PID"
  wait "$APP_PID" 2>/dev/null || true
  APP_PID=""
}

run_case() {
  local name="$1"
  local cache="$2"
  start_app "$name" "$cache"

  curl -fsS -u load-client:load-client-secret -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-binary "@$FORM_FILE" http://127.0.0.1:9099/oauth2/token >/dev/null
  /usr/sbin/ab -q -n 100 -c 8 -A load-client:load-client-secret \
    -p "$FORM_FILE" -T application/x-www-form-urlencoded \
    http://127.0.0.1:9099/oauth2/token >/dev/null
  docker compose exec -T db psql -U sas_demo -d sas_demo -v ON_ERROR_STOP=1 \
    -c "truncate table oauth2_authorization" \
    -c "select pg_stat_statements_reset()" >/dev/null

  : >"$TARGET_DIR/$name-activity.tsv"
  /usr/sbin/ab -q -n "$REQUESTS" -c "$CONCURRENCY" -A load-client:load-client-secret \
    -p "$FORM_FILE" -T application/x-www-form-urlencoded \
    http://127.0.0.1:9099/oauth2/token >"$TARGET_DIR/$name-ab.txt" &
  local ab_pid="$!"
  while kill -0 "$ab_pid" 2>/dev/null; do
    docker compose exec -T db psql -U sas_demo -d sas_demo -At -F $'\t' -c \
      "select clock_timestamp(),count(*) filter(where state='active'),count(*) filter(where wait_event_type is not null) from pg_stat_activity where datname='sas_demo'" \
      >>"$TARGET_DIR/$name-activity.tsv"
    sleep 0.10
  done
  wait "$ab_pid"
  local non_2xx
  non_2xx="$(awk '/Non-2xx responses:/ {print $3}' "$TARGET_DIR/$name-ab.txt")"
  if [[ -n "$non_2xx" ]] && [[ "$non_2xx" != "0" ]]; then
    echo "$name produced $non_2xx non-2xx responses" >&2
    exit 1
  fi

  docker compose exec -T db psql -U sas_demo -d sas_demo -P pager=off \
    -f /dev/stdin <scripts/profile-statements.sql >"$TARGET_DIR/$name-pg-statements.txt"
  docker compose exec -T db psql -U sas_demo -d sas_demo -At -c \
    "select count(*) from oauth2_authorization" >"$TARGET_DIR/$name-authorization-rows.txt"
  stop_app
}

run_case uncached false
run_case cached true

echo "Raw results: $TARGET_DIR"
