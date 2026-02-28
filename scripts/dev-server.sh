#!/bin/bash
# Run JVM server with manual watch mode
# This is more reliable than bloop's built-in --watch
# Requires: sudo apt install inotify-tools

set -e

cd "$(dirname "$0")/.."

WATCH_DIRS="jvm/src/main/scala shared/src/main/scala"

cleanup() {
  echo ""
  echo "Shutting down..."
  kill $SERVER_PID 2>/dev/null || true
  exit 0
}

trap cleanup SIGINT SIGTERM

start_server() {
  echo ""
  echo "=========================================="
  echo "Compiling and starting server..."
  echo "=========================================="

  if bloop compile jvm; then
    # Start server in background
    bloop run jvm &
    SERVER_PID=$!

    echo ""
    echo "Server running (PID: $SERVER_PID)"
    echo "Watching for changes... (Ctrl+C to stop)"
  else
    echo "Compilation failed, waiting for changes..."
    SERVER_PID=""
  fi
}

# Initial start
start_server

# Watch for changes
while true; do
  inotifywait -r -q -e modify,create,delete \
    --include '.*\.scala$' \
    $WATCH_DIRS

  echo ""
  echo "Change detected, restarting..."

  # Kill previous server if running
  if [ -n "$SERVER_PID" ]; then
    kill $SERVER_PID 2>/dev/null || true
    wait $SERVER_PID 2>/dev/null || true
    sleep 0.3
  fi

  start_server
done

