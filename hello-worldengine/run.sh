#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

CLASSPATH="target/classes"
CLASSPATH="$CLASSPATH:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"

echo "==> Running Hello WorldEngine"
java --enable-preview -cp "$CLASSPATH" org.dynamisengine.games.hello.Main
