#!/usr/bin/env bash
set -e
echo "==> Building debug-session-queries"
cd "$(dirname "$0")"
mvn clean package -q -Dgpg.skip=true
echo "==> Build complete"
