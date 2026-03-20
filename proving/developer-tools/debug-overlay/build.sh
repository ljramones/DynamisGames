#!/usr/bin/env bash
set -e
echo "==> Building debug-overlay"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
