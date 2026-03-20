#!/usr/bin/env bash
set -e
echo "==> Building lightengine-presets-demo"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
