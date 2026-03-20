#!/usr/bin/env bash
set -e
echo "==> Building ui-basics"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
