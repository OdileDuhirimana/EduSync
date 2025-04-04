#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TENANT="${TENANT:-acme}"

echo "[seed] Using gateway: $GATEWAY_URL, tenant: $TENANT"

echo "[seed] Register demo user alice@acme.edu"
curl -sS -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!","firstName":"Alice","lastName":"Ngabo"}' | jq . || true

echo "[seed] Login to get tokens"
curl -sS -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!"}' | jq . || true

echo "[seed] Done (stub). Extend this script per project.md §14 once services are implemented."
