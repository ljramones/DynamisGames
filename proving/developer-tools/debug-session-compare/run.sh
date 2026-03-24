#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: ./run.sh <baseline.ndjson> <regression.ndjson>"
    exit 1
fi
CLASSPATH="target/classes"
CLASSPATH="$CLASSPATH:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"
echo "==> Running Debug Session Compare"
echo "    Left:  $1"
echo "    Right: $2"
java --enable-preview --enable-native-access=ALL-UNNAMED -cp "$CLASSPATH" \
    -XstartOnFirstThread \
    org.dynamisengine.games.debugcompare.Main "$1" "$2"
