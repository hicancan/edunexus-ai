#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080}"
AI_BASE="${AI_BASE:-http://127.0.0.1:8000}"

echo "[1/7] API health"
curl -fsS "${API_BASE}/actuator/health" >/dev/null

echo "[2/7] AI health"
curl -fsS "${AI_BASE}/health" >/dev/null

echo "[3/7] Student login"
STUDENT_LOGIN=$(curl -fsS -X POST "${API_BASE}/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"student01","password":"12345678"}')
STUDENT_TOKEN=$(python -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])" <<<"$STUDENT_LOGIN")

echo "[4/7] Student fetch sessions"
curl -fsS "${API_BASE}/api/v1/student/chat/sessions?page=1&size=5" -H "Authorization: Bearer ${STUDENT_TOKEN}" >/dev/null

echo "[5/7] Teacher login"
TEACHER_LOGIN=$(curl -fsS -X POST "${API_BASE}/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"teacher01","password":"12345678"}')
TEACHER_TOKEN=$(python -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])" <<<"$TEACHER_LOGIN")

echo "[6/7] Teacher analytics"
curl -fsS "${API_BASE}/api/v1/teacher/students/00000000-0000-0000-0000-000000000003/analytics" -H "Authorization: Bearer ${TEACHER_TOKEN}" >/dev/null

echo "[7/7] AI internal auth guard"
CODE=$(curl -sS -o /dev/null -w "%{http_code}" "${AI_BASE}/internal/v1/ping")
if [[ "$CODE" != "401" ]]; then
  echo "Expected 401 for missing X-Service-Token, got ${CODE}"
  exit 1
fi

echo "Acceptance smoke passed"
