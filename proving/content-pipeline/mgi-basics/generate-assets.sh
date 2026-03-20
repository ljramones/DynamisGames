#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
CLASSPATH="target/classes"
CLASSPATH="$CLASSPATH:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"
echo "==> Generating MGI test assets"
java --enable-preview --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector -cp "$CLASSPATH" \
    org.dynamisengine.games.mgi.MgiAssetGenerator
