#!/usr/bin/env bash
set -e
echo "==> Building interaction-rendered"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
