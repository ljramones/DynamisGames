#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if [ -z "$1" ]; then
    echo "Usage: ./run.sh <session.ndjson>"
    exit 1
fi
CLASSPATH="target/classes"
CLASSPATH="$CLASSPATH:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"
echo "==> Running Debug Session Player: $1"
java --enable-preview --enable-native-access=ALL-UNNAMED -cp "$CLASSPATH" \
    -XstartOnFirstThread \
    org.dynamisengine.games.debugplayer.Main "$1"
