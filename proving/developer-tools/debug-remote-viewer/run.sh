#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
CLASSPATH="target/classes"
CLASSPATH="$CLASSPATH:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"
echo "==> Running Debug Remote Viewer (connecting to localhost:9876)"
java --enable-preview --enable-native-access=ALL-UNNAMED -cp "$CLASSPATH" \
    -XstartOnFirstThread \
    org.dynamisengine.games.debugremote.Main ${1:-localhost} ${2:-9876}
