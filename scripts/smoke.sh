#!/usr/bin/env bash
set -euo pipefail

# Simple end-to-end smoke hitting the API Gateway and core services.
# Prerequisites:
#  - Run infra (optional): docker compose -f infra/docker-compose.yml up -d
#  - Start services: auth (9001), user (9002), course (9003), enrollment (9004),
#    assessment (9005), submission (9006), grading (9007), analytics (9008), realtime (9009), gateway (8080)
#  - Then run this script: scripts/smoke.sh

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
TENANT=${TENANT:-acme}
EMAIL=${EMAIL:-alice@acme.edu}
PASSWORD=${PASSWORD:-P@ssw0rd!}
USER_ID=${USER_ID:-u-1}

JQ=$(command -v jq || true)

say() { echo -e "\n[smoke] $*"; }

call() {
  local method=$1
  local path=$2
  local data=${3:-}
  if [[ -n "$data" ]]; then
    curl -sS --fail -X "$method" "$GATEWAY_URL$path" \
      -H "Content-Type: application/json" \
      -H "X-Tenant-Id: $TENANT" \
      ${AUTH_HEADER:+-H "Authorization: Bearer $AUTH_HEADER"} \
      ${EXTRA_HEADERS:+$EXTRA_HEADERS} \
      -d "$data"
  else
    curl -sS --fail -X "$method" "$GATEWAY_URL$path" \
      -H "X-Tenant-Id: $TENANT" \
      ${AUTH_HEADER:+-H "Authorization: Bearer $AUTH_HEADER"} \
      ${EXTRA_HEADERS:+$EXTRA_HEADERS}
  fi
}

pp() {
  if [[ -n "$JQ" ]]; then "$JQ" .; else cat; fi
}

say "Gateway health"
call GET /actuator/health | pp

say "Service health checks via gateway"
for p in /auth/health /users/health /courses/health /enrollments/health /assessments/health /submissions/health /grading/health /analytics/health; do
  echo -n "  -> $p: "
  call GET "$p" >/dev/null && echo OK
done

say "Register user (idempotent)"
call POST /auth/register '{"email":"'"$EMAIL"'","password":"'"$PASSWORD"'","firstName":"Alice","lastName":"Ngabo"}' | pp || true

say "Login to get tokens"
TOKENS=$(call POST /auth/login '{"email":"'"$EMAIL"'","password":"'"$PASSWORD""}')
echo "$TOKENS" | pp
if [[ -n "$JQ" ]]; then
  ACCESS=$(echo "$TOKENS" | jq -r .accessToken)
else
  ACCESS=$(echo "$TOKENS" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
fi
if [[ -z "${ACCESS:-}" ]]; then
  echo "[smoke] ERROR: could not parse accessToken from login response" >&2
  exit 1
fi
AUTH_HEADER="$ACCESS"

say "/users/me (via headers stub)"
EXTRA_HEADERS='-H "X-User-Id: '"$USER_ID"'" -H "X-User-Email: '"$EMAIL"'"'
call GET /users/me | pp
EXTRA_HEADERS=""

say "Create course (requires X-User-Roles: INSTRUCTOR)"
EXTRA_HEADERS='-H "X-User-Roles: INSTRUCTOR"'
CREATE=$(call POST /courses '{"code":"ALG101","title":"Algorithms 101"}')
echo "$CREATE" | pp
if [[ -n "$JQ" ]]; then
  COURSE_ID=$(echo "$CREATE" | jq -r .id)
else
  COURSE_ID=$(echo "$CREATE" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
fi
if [[ -z "${COURSE_ID:-}" ]]; then
  echo "[smoke] ERROR: could not parse course id" >&2
  exit 1
fi

say "Publish course"
call POST "/courses/$COURSE_ID/publish" | pp

say "Enroll self into the course"
EXTRA_HEADERS='-H "X-User-Id: '"$USER_ID"'"'
ENR=$(call POST /enrollments '{"courseId":"'"$COURSE_ID"'"}')
echo "$ENR" | pp

say "List my enrollments"
call GET /enrollments/me | pp
EXTRA_HEADERS=""

say "All basic smoke steps completed successfully."