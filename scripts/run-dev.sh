#!/usr/bin/env bash
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

load_env_file() {
  local env_file="$1"
  if [ ! -f "${env_file}" ]; then
    return 0
  fi

  while IFS= read -r line || [ -n "${line}" ]; do
    case "${line}" in
      ''|\#*) continue ;;
    esac
    local key="${line%%=*}"
    local value="${line#*=}"
    if [ -z "${key}" ]; then
      continue
    fi
    if [ -z "${!key+x}" ]; then
      export "${key}=${value}"
    fi
  done < "${env_file}"
}

should_use_compose_value() {
  local current="$1"
  shift
  if [ -z "${current}" ]; then
    return 0
  fi
  local lowered
  lowered="$(printf '%s' "${current}" | tr '[:upper:]' '[:lower:]')"
  for prefix in "$@"; do
    local normalized
    normalized="$(printf '%s' "${prefix}" | tr '[:upper:]' '[:lower:]')"
    case "${lowered}" in
      "${normalized}"*) return 0 ;;
    esac
  done
  return 1
}

normalize_database_url() {
  local current="$1"
  if [ -z "${current}" ]; then
    return 0
  fi
  case "${current}" in
    jdbc:postgresql://*)
      printf '%s\n' "${current}"
      return 0
      ;;
    postgres://*|postgresql://*)
      python - "${current}" "${POSTGRES_DB:-edunexus}" <<'PY'
from urllib.parse import urlparse
import sys

raw = sys.argv[1]
default_db = sys.argv[2]
parsed = urlparse(raw)
db = parsed.path.lstrip("/") or default_db
port = parsed.port or 5432
print(f"jdbc:postgresql://{parsed.hostname}:{port}/{db}")
PY
      return 0
      ;;
    *)
      printf '%s\n' "${current}"
      return 0
      ;;
  esac
}

normalize_legacy_ollama_model() {
  case "$1" in
    qwen3:4b) printf 'qwen3.5:4b\n' ;;
    qwen3:8b) printf 'qwen3.5:9b\n' ;;
    *) printf '%s\n' "$1" ;;
  esac
}

compose_host_port() {
  local service="$1"
  local container_port="$2"
  local raw
  raw="$(cd "${PROJECT_ROOT}" && docker compose port "${service}" "${container_port}" 2>/dev/null | head -n 1)"
  if [ -z "${raw}" ]; then
    return 1
  fi
  printf '%s\n' "${raw##*:}"
}

load_env_file "${PROJECT_ROOT}/.env"

if [ -n "${DATABASE_URL:-}" ]; then
  export DATABASE_URL="$(normalize_database_url "${DATABASE_URL}")"
fi
for model_var in OLLAMA_MODEL OLLAMA_RAG_MODEL OLLAMA_STRUCTURED_MODEL OLLAMA_LESSON_PLAN_MODEL; do
  current_value="${!model_var:-}"
  if [ -n "${current_value}" ]; then
    export "${model_var}=$(normalize_legacy_ollama_model "${current_value}")"
  fi
done

if ! command -v uv >/dev/null 2>&1; then
  echo "uv not found. Please install uv (curl -LsSf https://astral.sh/uv/install.sh | sh)"
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
(cd "${PROJECT_ROOT}" && docker compose up -d)

if postgres_port="$(compose_host_port postgres 5432)"; then
  if should_use_compose_value "${DATABASE_URL:-}" \
    "jdbc:postgresql://127.0.0.1:5432/" \
    "jdbc:postgresql://localhost:5432/" \
    "postgresql://127.0.0.1:5432/" \
    "postgresql://localhost:5432/"; then
    export POSTGRES_HOST="127.0.0.1"
    export POSTGRES_PORT="${postgres_port}"
    export DATABASE_URL="jdbc:postgresql://127.0.0.1:${postgres_port}/${POSTGRES_DB:-edunexus}"
  fi
fi

if redis_port="$(compose_host_port redis 6379)"; then
  if should_use_compose_value "${REDIS_URL:-}" "redis://127.0.0.1:6379/" "redis://localhost:6379/"; then
    export REDIS_HOST="127.0.0.1"
    export REDIS_PORT="${redis_port}"
    export REDIS_URL="redis://127.0.0.1:${redis_port}/0"
  fi
fi

if qdrant_http_port="$(compose_host_port qdrant 6333)"; then
  if should_use_compose_value "${QDRANT_URL:-}" "http://127.0.0.1:6333" "http://localhost:6333"; then
    export QDRANT_HOST="127.0.0.1"
    export QDRANT_PORT="${qdrant_http_port}"
    export QDRANT_URL="http://127.0.0.1:${qdrant_http_port}"
  fi
fi

if minio_port="$(compose_host_port minio 9000)"; then
  if should_use_compose_value "${S3_ENDPOINT:-}" "http://127.0.0.1:9000" "http://localhost:9000"; then
    export S3_ENDPOINT="http://127.0.0.1:${minio_port}"
  fi
fi

export APP_PORT="${API_PORT}"
export APP_RUNTIME_STRATEGY="${APP_RUNTIME_STRATEGY:-云边端协同}"
export AI_SERVICE_PORT="${AI_PORT}"
export WEB_PORT="${WEB_PORT}"
export API_BASE_URL="http://127.0.0.1:${API_PORT}"
export VITE_API_BASE_URL="http://127.0.0.1:${API_PORT}"
export AI_SERVICE_GRPC_HOST="127.0.0.1"
export AI_SERVICE_GRPC_PORT="50051"
export APP_GRPC_SERVER_PORT="${APP_GRPC_SERVER_PORT:-9090}"
export JAVA_GRPC_URL="127.0.0.1:${APP_GRPC_SERVER_PORT}"

echo "[2/4] Start AI service"
if is_port_in_use "${AI_PORT}"; then
  echo "AI service skipped: port ${AI_PORT} already in use"
else
  (
    cd "${PROJECT_ROOT}/apps/ai-service" &&
    UV_LINK_MODE=copy uv run --python 3.12 uvicorn ai_service.app:app --host "${HOST_BIND}" --port "${AI_PORT}"
  ) &
fi

echo "[3/4] Start API service"
if is_port_in_use "${API_PORT}"; then
  echo "API service skipped: port ${API_PORT} already in use"
else
  (cd "${PROJECT_ROOT}/apps/api" && mvn spring-boot:run "-Dspring-boot.run.arguments=--server.address=${HOST_BIND} --server.port=${API_PORT}") &
fi

echo "[4/4] Start web service"
if is_port_in_use "${WEB_PORT}"; then
  echo "Web service skipped: port ${WEB_PORT} already in use"
else
  (cd "${PROJECT_ROOT}/apps/web" && npm install && npm run dev -- --host "${HOST_BIND}" --port "${WEB_PORT}") &
fi

wait
