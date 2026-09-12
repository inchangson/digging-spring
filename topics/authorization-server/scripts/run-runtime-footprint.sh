#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
: "${BO_AUTH_DIR:?Set BO_AUTH_DIR to the bo-auth checkout}"
: "${BO_AUTH_JAR:?Set BO_AUTH_JAR to the built bo-auth executable JAR}"
: "${BO_AUTH_ENV_FILE:?Set BO_AUTH_ENV_FILE to the local environment file}"
: "${BO_AUTH_READY_URL:?Set BO_AUTH_READY_URL to a non-sensitive readiness URL}"
SAMPLE_DELAY_SECONDS="${SAMPLE_DELAY_SECONDS:-20}"
RESULT_DIR="$ROOT_DIR/build/nonfunctional"
SAS_JAR="$ROOT_DIR/build/libs/sas-demo-1.0.0.jar"
BO_JAR="$BO_AUTH_JAR"
SAS_PID=""
BO_PID=""
mkdir -p "$RESULT_DIR"

cleanup() {
  for pid in "$SAS_PID" "$BO_PID"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid"
      wait "$pid" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

wait_ready() {
  local pid="$1"
  local url="$2"
  local log="$3"
  for _ in {1..120}; do
    if curl -fsS "$url" >/dev/null; then return; fi
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "process exited before readiness; see $log" >&2
      exit 1
    fi
    sleep 0.25
  done
  echo "readiness timed out; see $log" >&2
  exit 1
}

measure() {
  local service="$1"
  local pid="$2"
  local jar="$3"
  local log="$4"
  local startup rss threads bytes entries
  startup="$(sed -nE 's/.*Started .* in ([0-9.]+) seconds.*/\1/p' "$log" | tail -1)"
  rss="$(ps -o rss= -p "$pid" | tr -d ' ')"
  threads="$(jcmd "$pid" Thread.print | awk '/^"/{count++} END{print count+0}')"
  bytes="$(stat -f '%z' "$jar")"
  entries="$(jar tf "$jar" | awk 'END{print NR}')"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$service" "$startup" "$rss" "$threads" "$bytes" "$entries"
}

cd "$ROOT_DIR"
docker compose up -d --wait
"$ROOT_DIR/../../gradlew" -p "$ROOT_DIR/../.." :topics:authorization-server:bootJar
test -f "$BO_JAR"
test -f "$BO_AUTH_ENV_FILE"

java -jar "$SAS_JAR" --demo.policy-cache=false >"$RESULT_DIR/sas-demo.log" 2>&1 &
SAS_PID="$!"
wait_ready "$SAS_PID" http://127.0.0.1:9099/.well-known/oauth-authorization-server "$RESULT_DIR/sas-demo.log"
sleep "$SAMPLE_DELAY_SECONDS"
{
  printf 'service\tstartup_seconds\trss_kib\tthreads\tjar_bytes\tjar_entries\n'
  measure sas-demo "$SAS_PID" "$SAS_JAR" "$RESULT_DIR/sas-demo.log"
} >"$RESULT_DIR/runtime-footprint.tsv"
kill "$SAS_PID"
wait "$SAS_PID" 2>/dev/null || true
SAS_PID=""

(
  cd "$BO_AUTH_DIR"
  set -a
  source "$BO_AUTH_ENV_FILE"
  set +a
  exec java -jar "$BO_JAR" --spring.profiles.active=local --server.port=18010
) >"$RESULT_DIR/bo-auth.log" 2>&1 &
BO_PID="$!"
wait_ready "$BO_PID" "$BO_AUTH_READY_URL" "$RESULT_DIR/bo-auth.log"
sleep "$SAMPLE_DELAY_SECONDS"
measure bo-auth "$BO_PID" "$BO_JAR" "$RESULT_DIR/bo-auth.log" >>"$RESULT_DIR/runtime-footprint.tsv"

echo "Raw result: $RESULT_DIR/runtime-footprint.tsv"
