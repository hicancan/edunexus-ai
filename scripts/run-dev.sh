#!/usr/bin/env bash
set -e

echo "[1/4] Start infra"
docker compose up -d

echo "[2/4] Start AI service"
(cd "$(dirname "$0")/../apps/ai-service" && pip install -r requirements.txt && python -m uvicorn main:app --host 0.0.0.0 --port 8000) &

echo "[3/4] Start API service"
(cd "$(dirname "$0")/../apps/api" && mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=0.0.0.0 --server.port=8080") &

echo "[4/4] Start web service"
(cd "$(dirname "$0")/../apps/web" && npm install && npm run dev -- --host 0.0.0.0 --port 5173) &

wait
