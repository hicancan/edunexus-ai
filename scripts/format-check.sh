#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Checking web formatting..."
(
  cd "$repo_root/apps/web"
  npm run format:check
)

echo "Checking api formatting..."
(
  cd "$repo_root/apps/api"
  mvn spotless:check
)

echo "Checking ai-service formatting..."
(
  cd "$repo_root"
  uv run --project apps/ai-service --group dev ruff check apps/ai-service
  uv run --project apps/ai-service --group dev ruff format --check apps/ai-service
)
