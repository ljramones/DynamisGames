#!/usr/bin/env bash
set -e
echo "==> Building debug-remote-viewer"
cd "$(dirname "$0")"
mvn clean package -q -Dgpg.skip=true
echo "==> Build complete"
