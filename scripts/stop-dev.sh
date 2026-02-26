#!/usr/bin/env bash
set -e

API_PORT="${APP_PORT:-8080}"
AI_PORT="${AI_SERVICE_PORT:-8000}"
WEB_PORT="${WEB_PORT:-5173}"

KEEP_INFRA="${KEEP_INFRA:-0}"
DOWN_INFRA="${DOWN_INFRA:-0}"
FORCE_PORT_KILL="${FORCE_PORT_KILL:-0}"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

find_pid_by_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"${port}" -sTCP:LISTEN | head -n 1
    return 0
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -ltnp "sport = :${port}" 2>/dev/null | awk -F'pid=' 'NR>1 {split($2,a,","); print a[1]; exit}'
    return 0
  fi

  if command -v netstat >/dev/null 2>&1; then
    netstat -ltnp 2>/dev/null | awk -v p=":${port}" '$4 ~ p {split($7,a,"/"); print a[1]; exit}'
    return 0
  fi

  return 1
}

should_stop_pid() {
  local pid="$1"
  shift
  local cmd
  cmd="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
  if [ -z "${cmd}" ]; then
    return 1
  fi

  local lowered
  lowered="$(printf '%s' "${cmd}" | tr '[:upper:]' '[:lower:]')"
  for marker in "$@"; do
    local m
    m="$(printf '%s' "${marker}" | tr '[:upper:]' '[:lower:]')"
    if printf '%s' "${lowered}" | grep -q "${m}"; then
      return 0
    fi
  done

  if [ "${FORCE_PORT_KILL}" = "1" ]; then
    return 0
  fi
  return 1
}

stop_by_port() {
  local service_name="$1"
  local port="$2"
  shift 2
  local markers=("$@")

  local pid
  pid="$(find_pid_by_port "${port}" | tr -d '[:space:]' || true)"
  if [ -z "${pid}" ]; then
    echo "${service_name}: port ${port} is already free"
    return 0
  fi

  if ! should_stop_pid "${pid}" "${markers[@]}"; then
    echo "${service_name}: port ${port} occupied by non-project pid=${pid}; skipped"
    return 0
  fi

  if kill "${pid}" >/dev/null 2>&1; then
    sleep 1
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" >/dev/null 2>&1 || true
    fi
    echo "${service_name}: stopped pid=${pid} on port ${port}"
  else
    echo "${service_name}: failed to stop pid=${pid} on port ${port}"
  fi
}

echo "[1/4] Stop AI service"
stop_by_port "AI service" "${AI_PORT}" "ai_service.app:app" "uvicorn"

echo "[2/4] Stop API service"
stop_by_port "API service" "${API_PORT}" "com.edunexus.api.apiapplication" "spring-boot:run"

echo "[3/4] Stop web service"
stop_by_port "Web service" "${WEB_PORT}" "vite" "apps/web"

echo "[4/4] Stop infrastructure"
if [ "${KEEP_INFRA}" = "1" ]; then
  echo "Infrastructure kept running (KEEP_INFRA=1)"
else
  if [ "${DOWN_INFRA}" = "1" ]; then
    (cd "${PROJECT_ROOT}" && docker compose down)
  else
    (cd "${PROJECT_ROOT}" && docker compose stop)
  fi
fi

echo "Stop script completed"
