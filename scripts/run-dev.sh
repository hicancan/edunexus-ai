#!/usr/bin/env bash
set -e

if ! command -v conda >/dev/null 2>&1; then
  echo "conda not found. Please install Miniforge/Conda and create env 'edunexus-ai'."
  exit 1
fi

HOST_BIND="${APP_HOST:-0.0.0.0}"
API_PORT="${APP_PORT:-8080}"
AI_PORT="${AI_SERVICE_PORT:-8000}"
WEB_PORT="${WEB_PORT:-5173}"

is_port_in_use() {
  local port="$1"
  python - "$port" <<'PY'
import socket
import sys

port = int(sys.argv[1])
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    s.bind(("0.0.0.0", port))
except OSError:
    sys.exit(0)
finally:
    s.close()
sys.exit(1)
PY
}

echo "[1/4] Start infra"
docker compose up -d

echo "[2/4] Start AI service"
if is_port_in_use "${AI_PORT}"; then
  echo "AI service skipped: port ${AI_PORT} already in use"
else
  (
    cd "$(dirname "$0")/../apps/ai-service" &&
    if [ ! -f ".venv/pyvenv.cfg" ]; then
      rm -rf .venv
      conda run --no-capture-output -n edunexus-ai uv venv --python 3.12 .venv
    fi &&
    UV_LINK_MODE=copy conda run --no-capture-output -n edunexus-ai uv sync --project . &&
    conda run --no-capture-output -n edunexus-ai uv run --project . uvicorn ai_service.app:app --host "${HOST_BIND}" --port "${AI_PORT}"
  ) &
fi

echo "[3/4] Start API service"
if is_port_in_use "${API_PORT}"; then
  echo "API service skipped: port ${API_PORT} already in use"
else
  (cd "$(dirname "$0")/../apps/api" && mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=${HOST_BIND} --server.port=${API_PORT}") &
fi

echo "[4/4] Start web service"
if is_port_in_use "${WEB_PORT}"; then
  echo "Web service skipped: port ${WEB_PORT} already in use"
else
  (cd "$(dirname "$0")/../apps/web" && npm install && npm run dev -- --host "${HOST_BIND}" --port "${WEB_PORT}") &
fi

wait
