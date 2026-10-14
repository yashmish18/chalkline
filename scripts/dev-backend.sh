#!/usr/bin/env bash
# Runs the backend for development and keeps it current — no manual restarts:
#   * a change under backend/src/main is recompiled and DevTools restarts the app (~2 s);
#   * a change to backend/pom.xml (new dependency) triggers a full restart, which DevTools can't do.
# Stop with Ctrl+C.
#
#   scripts/dev-backend.sh                 # live database (iet_dashboard), dev profile
#   SPRING_DATASOURCE_URL=... scripts/dev-backend.sh
set -euo pipefail
cd "$(dirname "$0")/../backend"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

MARK=target/.pom-stamp
APP=""

start() {
  mvn -q dependency:resolve >/dev/null 2>&1 || true   # fetch any new dependency (needs network once)
  mvn -q -o compile
  touch "$MARK"
  mvn -o spring-boot:run &
  APP=$!
}

stop() {
  if [ -n "$APP" ]; then
    pkill -P "$APP" 2>/dev/null || true
    kill "$APP" 2>/dev/null || true
    wait "$APP" 2>/dev/null || true
  fi
}

trap 'stop; exit 0' INT TERM
start
echo "Watching backend/src/main and pom.xml for changes…"

while true; do
  sleep 2
  if [ pom.xml -nt "$MARK" ]; then
    echo "pom.xml changed — full restart"
    stop
    start
    continue
  fi
  if ! kill -0 "$APP" 2>/dev/null; then
    echo "Backend exited — restarting in 5 s (fix the error shown above if it keeps happening)"
    sleep 5
    start
    continue
  fi
  if [ -n "$(find src/main -type f -newer target/classes -print -quit 2>/dev/null)" ]; then
    echo "Change detected — recompiling"
    if mvn -q -o compile; then
      echo "Compiled; DevTools is restarting the app"
    else
      echo "Compile failed — fix the error and save again (the app keeps running the last good build)"
    fi
    touch target/classes
  fi
done
