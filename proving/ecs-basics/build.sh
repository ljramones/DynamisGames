#!/usr/bin/env bash
set -e
echo "==> Building ecs-basics"
cd "$(dirname "$0")"
mvn clean package -q
echo "==> Build complete"
