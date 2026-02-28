#!/bin/bash
# Full dev rebuild: link JS, copy to resources, compile and run JVM server
# Usage: ./scripts/dev-server.sh

set -e

cd "$(dirname "$0")/.."

echo "=========================================="
echo "Building JS..."
echo "=========================================="
bloop link js

echo ""
echo "Copying JS to resources..."
cp .bloop/js/js-js/main.js jvm/src/main/resources/static/js/main.js

echo ""
echo "=========================================="
echo "Compiling and running JVM server..."
echo "=========================================="
bloop run jvm

