#!/bin/bash
# Compile and link JS using bloop, then copy to the correct directory
# Also updates a marker Scala file to trigger bloop's JVM watch mode restart

set -e

MARKER_FILE="jvm/src/main/scala/server/JsChanged.scala"

bloop link js

cp .bloop/js/js-js/main.js jvm/src/main/resources/static/js/main.js

# Update timestamp in the Scala file to trigger recompilation
TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
sed -i "s|// Last JS build:.*|// Last JS build: $TIMESTAMP|" "$MARKER_FILE"
sed -i "s|val timestamp: String = \".*\"|val timestamp: String = \"$TIMESTAMP\"|" "$MARKER_FILE"

echo "✓ Linked JS copied to jvm/src/main/resources/static/js/"

