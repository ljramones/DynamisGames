#!/usr/bin/env bash
set -e
echo "==> Building debug-history-timeline"
cd "$(dirname "$0")"
mvn clean package -q -Dgpg.skip=true
echo "==> Build complete"
