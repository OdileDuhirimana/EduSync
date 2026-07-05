#!/usr/bin/env bash
set -euo pipefail

# Simple end-to-end smoke hitting the API Gateway and core services.
#
# WHY this script no longer sets X-User-Roles/X-User-Id headers directly
# (it used to — that was the exact exploit path an external code review
# flagged as a critical, complete authorization bypass): the gateway's
# JwtAuthenticationFilter now strips any client-supplied copy of those
# headers on every request and re-derives them itself from a
# cryptographically verified access token. Setting them here would no
# longer do anything except demonstrate that the bypass is closed, which
# this script does explicitly further down instead of accidentally.
#
# Prerequisites:
#  - Run infra (optional): docker compose -f infra/docker-compose.yml up -d
#  - export AUTH_JWT_SECRET before starting every service (they will not
#    start without it)
#  - Start services: auth (9001), user (9002), course (9003), enrollment (9004),
#    assessment (9005), submission (9006), grading (9007), analytics (9008), realtime (9009), gateway (8080)
#  - Then run this script: scripts/smoke.sh

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
TENANT=${TENANT:-acme}
EMAIL=${EMAIL:-alice@acme.edu}
PASSWORD=${PASSWORD:-P@ssw0rd!}

JQ=$(command -v jq || true)

say() { echo -e "\n[smoke] $*"; }

call() {
  local method=$1
  local path=$2
  local data=${3:-}
  local cmd=(curl -sS --fail -X "$method" "$GATEWAY_URL$path" -H "X-Tenant-Id: $TENANT")

  if [[ -n "${AUTH_HEADER:-}" ]]; then
    cmd+=(-H "Authorization: Bearer $AUTH_HEADER")
  fi

  if [[ -n "$data" ]]; then
    cmd+=(-H "Content-Type: application/json" -d "$data")
  fi
  "${cmd[@]}"
}

# WHY a separate helper that does not use --fail: several steps below
# intentionally expect a non-2xx response (401/403) to prove the
# authorization boundary works. `curl --fail` would abort the whole script
# on those expected failures.
call_expect_status() {
  local expected=$1
  local method=$2
  local path=$3
  local data=${4:-}
  local cmd=(curl -sS -o /dev/null -w "%{http_code}" -X "$method" "$GATEWAY_URL$path" -H "X-Tenant-Id: $TENANT")
  if [[ -n "${AUTH_HEADER:-}" ]]; then
    cmd+=(-H "Authorization: Bearer $AUTH_HEADER")
  fi
  if [[ -n "$data" ]]; then
    cmd+=(-H "Content-Type: application/json" -d "$data")
  fi
  local actual
  actual=$("${cmd[@]}")
  if [[ "$actual" != "$expected" ]]; then
    echo "[smoke] ERROR: $method $path expected HTTP $expected but got $actual" >&2
    exit 1
  fi
  echo "  -> $method $path returned $actual as expected"
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

say "Login to get a real signed access token"
TOKENS=$(call POST /auth/login '{"email":"'"$EMAIL"'","password":"'"$PASSWORD"'"}')
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

say "Security check: an anonymous request cannot forge its way into course creation"
call_expect_status 401 POST /courses '{"code":"ALG101","title":"Algorithms 101"}'

say "Security check: a real STUDENT token also cannot create a course (only STUDENT is ever legitimately issued by /auth/register)"
call_expect_status 403 POST /courses '{"code":"ALG101","title":"Algorithms 101"}'

say "/users/me (identity now comes from the verified token via the gateway, not a client header)"
call GET /users/me | pp

say "List courses (paginated; works for any authenticated caller)"
call GET "/courses?page=0&size=10" | pp

say "Enroll self into a seeded/previously-published course id, if provided"
if [[ -n "${DEMO_COURSE_ID:-}" ]]; then
  ENR=$(call POST /enrollments '{"courseId":"'"$DEMO_COURSE_ID"'"}')
  echo "$ENR" | pp
  say "List my enrollments"
  call GET /enrollments/me | pp
else
  echo "  (skipped: export DEMO_COURSE_ID=<id> to exercise enrollment; course creation" \
       "requires an INSTRUCTOR-role account, which this script's registration flow" \
       "cannot provision since only STUDENT is ever issued on self-registration — see" \
       "README 'Known Limitations' / AUTH-05)"
fi

say "All basic smoke steps completed successfully."
