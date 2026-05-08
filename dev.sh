#!/usr/bin/env bash
#
#
#   ./dev.sh run               # build + start server (Implementation.App)
#   ./dev.sh run --skip-build
#   ./dev.sh test              # run TestRunner with -ea
#   ./dev.sh seed              # fill DB if empty
#   ./dev.sh seed --reset      # delete app.db, sync + seed
#   ./dev.sh build             # compile only
#   ./dev.sh build --clean
#   ./dev.sh kill              # stop whatever listens on $PORT (default 8080)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

COMMAND="${1:-run}"
shift || true

SKIP_BUILD=false
CLEAN=false
MAIN_CLASS="${MAIN_CLASS:-Implementation.App}"
JAR_PATH="${JAR_PATH:-Autumn/lib/sqlite-jdbc-3.51.3.0.jar}"
JAVA_RELEASE="${JAVA_RELEASE:-25}"
RESET=false
PORT="${PORT:-8080}"
FORCE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build|-SkipBuild) SKIP_BUILD=true ;;
    --clean|-Clean)          CLEAN=true ;;
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

ensure_jar() {
  if [[ ! -f "$JAR_PATH" ]]; then
    echo "SQLite JDBC jar not found at '$JAR_PATH'." >&2
    exit 1
  fi
}

build() {
  ensure_jar
  if [[ "$CLEAN" == true ]] && [[ -d out ]]; then
    rm -rf out
  fi
  if ! find . \( -path ./out -o -path ./.git \) -prune -o -name '*.java' -type f -print -quit | grep -q .; then
    echo "No Java source files found." >&2
    exit 1
  fi
  mkdir -p out
  echo "Compiling Java sources with --release $JAVA_RELEASE ..."
  find . \( -path ./out -o -path ./.git \) -prune -o -name '*.java' -type f -print0 \
    | xargs -0 javac --release "$JAVA_RELEASE" -cp "$JAR_PATH" -d out
  echo "Build successful. Classes are in ./out"
}

classpath() {
  ensure_jar
  echo "out:${JAR_PATH}"
}

cmd_kill() {
  local pids
  pids="$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    echo "No listening process on port $PORT."
    return 0
  fi
  local pid
  for pid in $pids; do
    [[ -z "$pid" ]] && continue
    if [[ "$FORCE" == true ]]; then
      kill -9 "$pid" && echo "Stopped PID $pid on port $PORT (SIGKILL)." || echo "Failed to kill PID $pid" >&2
    else
      kill "$pid" && echo "Stopped PID $pid on port $PORT." || echo "Failed to kill PID $pid (try --force)" >&2
    fi
  done
}

case "$COMMAND" in
  kill)
    cmd_kill
    ;;
  build)
    build
    ;;
  run|test|seed)
    if [[ "$SKIP_BUILD" == false ]]; then
      build
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
    ;;
  *)
    echo "Usage: $0 {build|run|test|seed|kill} [options]" >&2
    exit 2
    ;;
esac
