#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

JAVA_RELEASE="${JAVA_RELEASE:-25}"
JAR_PATH="${JAR_PATH:-Autumn/lib/sqlite-jdbc-3.51.3.0.jar}"
CLEAN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean|-Clean) CLEAN=true ;;
    --release=*) JAVA_RELEASE="${1#*=}" ;;
    --jar=*)      JAR_PATH="${1#*=}" ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

if [[ "$CLEAN" == true ]] && [[ -d out ]]; then
  rm -rf out
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "SQLite JDBC jar not found at '$JAR_PATH'." >&2
  exit 1
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
