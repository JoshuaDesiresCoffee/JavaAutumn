#!/usr/bin/env bash
#
# Same behaviour as dev.ps1 (Mac / Linux).
#
#   ./dev.sh run
#   ./dev.sh run --skip-build
#   ./dev.sh test
#   ./dev.sh seed
#   ./dev.sh seed --reset
#   ./dev.sh kill
#   ./dev.sh kill --port=3000 --force
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

COMMAND="${1:-run}"
shift || true

SKIP_BUILD=false
MAIN_CLASS="${MAIN_CLASS:-Implementation.App}"
JAR_PATH="${JAR_PATH:-Autumn/lib/sqlite-jdbc-3.51.3.0.jar}"
JAVA_RELEASE="${JAVA_RELEASE:-25}"
RESET=false
PORT="${PORT:-8080}"
FORCE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build|-SkipBuild) SKIP_BUILD=true ;;
    --main=*)                MAIN_CLASS="${1#*=}" ;;
    --jar=*)                 JAR_PATH="${1#*=}" ;;
    --release=*)             JAVA_RELEASE="${1#*=}" ;;
    --reset|-Reset)          RESET=true ;;
    --port=*)                PORT="${1#*=}" ;;
    --force|-Force)          FORCE=true ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

classpath() {
  if [[ ! -f "$JAR_PATH" ]]; then
    echo "SQLite JDBC jar not found at '$JAR_PATH'." >&2
    exit 1
  fi
  echo "out:${JAR_PATH}"
}

cmd_kill() {
  local pids
  pids="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    echo "No listening process on port $PORT."
    exit 0
  fi
  # lsof may print multiple PIDs (newline-separated)
  local pid
  for pid in $pids; do
    [[ -z "$pid" ]] && continue
    if [[ "$FORCE" == true ]]; then
      kill -9 "$pid" && echo "Stopped PID $pid on port $PORT (SIGKILL)." || echo "Failed to kill PID $pid" >&2
    else
      kill "$pid" && echo "Stopped PID $pid on port $PORT." || echo "Failed to kill PID $pid (try --force)" >&2
    fi
  done
  exit 0
}

if [[ "$COMMAND" == "kill" ]]; then
  cmd_kill
fi

case "$COMMAND" in
  run|test|seed) ;;
  *)
    echo "Usage: $0 {run|test|seed|kill} [options]" >&2
    exit 2
    ;;
esac

if [[ "$SKIP_BUILD" == false ]]; then
  bash "$ROOT/build.sh" --release="$JAVA_RELEASE" --jar="$JAR_PATH"
fi

CP="$(classpath)"

case "$COMMAND" in
  run)
    echo "Starting $MAIN_CLASS ..."
    exec java -cp "$CP" "$MAIN_CLASS"
    ;;
  test)
    echo "Running TestRunner (-ea) ..."
    exec java -ea -cp "$CP" Implementation.tests.TestRunner
    ;;
  seed)
    ARGS=(Implementation.SeedDatabase)
    if [[ "$RESET" == true ]]; then
      ARGS+=(--reset)
      echo "Reset DB + seed ..."
    else
      echo "Seed if empty ..."
    fi
    exec java -cp "$CP" "${ARGS[@]}"
    ;;
esac
