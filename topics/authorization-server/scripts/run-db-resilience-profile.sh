#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RESULT_DIR="$ROOT_DIR/build/nonfunctional"
FORM_FILE="$RESULT_DIR/client-credentials.form"
APP_PID=""
mkdir -p "$RESULT_DIR"
tr -d '\r\n' <"$ROOT_DIR/scripts/client-credentials.form" >"$FORM_FILE"

cleanup() {
  docker compose up -d --wait >/dev/null 2>&1 || true
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID"
    wait "$APP_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

token_status() {
  curl -sS --max-time 10 -o "$RESULT_DIR/last-token-response.json" -w '%{http_code}' \
    -u load-client:load-client-secret -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-binary "@$FORM_FILE" http://127.0.0.1:9099/oauth2/token
}

cd "$ROOT_DIR"
docker compose up -d --wait
"$ROOT_DIR/../../gradlew" -p "$ROOT_DIR/../.." :topics:authorization-server:bootJar
java -jar build/libs/sas-demo-1.0.0.jar --spring.datasource.hikari.connection-timeout=2000 \
  >"$RESULT_DIR/db-resilience-app.log" 2>&1 &
APP_PID="$!"

for _ in {1..120}; do
  if curl -fsS http://127.0.0.1:9099/.well-known/oauth-authorization-server >/dev/null; then break; fi
  sleep 0.25
done
baseline="$(token_status)"
[[ "$baseline" == "200" ]]

docker compose stop db >/dev/null
SECONDS=0
set +e
outage="$(token_status)"
curl_exit="$?"
set -e
outage_seconds="$SECONDS"
if [[ "$curl_exit" -eq 0 ]] && [[ "$outage" == "200" ]]; then
  echo "token issuance unexpectedly succeeded during DB outage" >&2
  exit 1
fi

SECONDS=0
docker compose up -d --wait >/dev/null
recovered="000"
for _ in {1..80}; do
  set +e
  recovered="$(token_status)"
  set -e
  if [[ "$recovered" == "200" ]]; then break; fi
  sleep 0.25
done
recovery_seconds="$SECONDS"
[[ "$recovered" == "200" ]]

{
  printf 'baseline_status\t%s\n' "$baseline"
  printf 'outage_status\t%s\n' "$outage"
  printf 'outage_curl_exit\t%s\n' "$curl_exit"
  printf 'outage_response_seconds\t%s\n' "$outage_seconds"
  printf 'recovered_status\t%s\n' "$recovered"
  printf 'recovery_seconds_including_db_start\t%s\n' "$recovery_seconds"
} >"$RESULT_DIR/db-resilience.tsv"

echo "Raw result: $RESULT_DIR/db-resilience.tsv"
