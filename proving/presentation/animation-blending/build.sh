#!/usr/bin/env bash
set -e
echo "==> Building animation-blending"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
