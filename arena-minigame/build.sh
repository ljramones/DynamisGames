#!/usr/bin/env bash
set -e
echo "==> Building arena-minigame"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
