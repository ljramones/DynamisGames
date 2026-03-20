#!/usr/bin/env bash
set -e
echo "==> Building window-input"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
