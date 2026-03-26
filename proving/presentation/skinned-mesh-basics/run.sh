#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
CLASSPATH="target/classes:target/dependency/*"
# Build classpath including all transitive deps
CLASSPATH="target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)"
echo "==> Running Skinned Mesh Basics"
# Disable MoltenVK validation to prevent abort() on non-fatal errors
export MVK_CONFIG_DEBUG=0
export MVK_DEBUG=0
java --enable-preview --enable-native-access=ALL-UNNAMED -cp "$CLASSPATH" \
    -XstartOnFirstThread \
    -Dorg.lwjgl.vulkan.explicit_init=true \
    org.dynamisengine.games.skinned.SkinnedMeshBasicsApp
