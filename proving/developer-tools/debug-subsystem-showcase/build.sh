#!/usr/bin/env bash
set -e
echo "==> Building debug-subsystem-showcase"
cd "$(dirname "$0")"
mvn clean package -q -Dgpg.skip=true
echo "==> Build complete"
