#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Formatting web..."
(
  cd "$repo_root/apps/web"
  npm run format
)

echo "Formatting api..."
(
  cd "$repo_root/apps/api"
  mvn spotless:apply
)

echo "Formatting ai-service..."
(
  cd "$repo_root"
  uv run --project apps/ai-service --group dev ruff check --fix apps/ai-service
  uv run --project apps/ai-service --group dev ruff format apps/ai-service
)
