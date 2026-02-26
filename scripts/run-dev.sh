#!/usr/bin/env bash
set -e

if ! command -v conda >/dev/null 2>&1; then
  echo "conda not found. Please install Miniforge/Conda and create env 'edunexus-ai'."
  exit 1
fi

echo "[1/4] Start infra"
docker compose up -d

echo "[2/4] Start AI service"
(
  cd "$(dirname "$0")/../apps/ai-service" &&
  conda run -n edunexus-ai uv sync --project . --python 3.12 &&
  conda run -n edunexus-ai uv run --project . --python 3.12 uvicorn ai_service.app:app --host 0.0.0.0 --port 8000
) &

echo "[3/4] Start API service"
(cd "$(dirname "$0")/../apps/api" && mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=0.0.0.0 --server.port=8080") &

echo "[4/4] Start web service"
(cd "$(dirname "$0")/../apps/web" && npm install && npm run dev -- --host 0.0.0.0 --port 5173) &

wait
