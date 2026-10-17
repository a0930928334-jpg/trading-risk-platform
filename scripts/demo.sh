#!/usr/bin/env bash
# End-to-end demo: register -> log in -> deposit -> place a marketable buy order -> see the trades.
#
# Requires the application running with the dev profile (so the simulated market maker provides liquidity):
#   docker compose up -d postgres
#   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#
# Usage: scripts/demo.sh [base-url]        (default http://localhost:8080)
# Needs only bash, curl and sed (works in Git Bash on Windows).
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
EMAIL="demo-$(date +%s)-$RANDOM@example.com"
PASSWORD="Password123!"
JSON="Content-Type: application/json"

step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

# call METHOD PATH [BODY] -> prints the response body, fails on HTTP >= 400
call() {
  local method="$1" path="$2" body="${3:-}" response status
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE_URL$path" -H "$JSON")
  [[ -n "${TOKEN:-}" ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ -n "$body" ]] && args+=(-d "$body")
  response="$(curl "${args[@]}")"
  status="${response##*$'\n'}"
  response="${response%$'\n'*}"
  echo "$response"
  if (( status >= 400 )); then
    echo "HTTP $status from $method $path" >&2
    exit 1
  fi
}

field() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" | head -n 1; }

step "Health"
call GET /actuator/health | cut -c1-80

step "Register $EMAIL"
call POST /api/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Demo User\"}"

step "Log in"
TOKEN="$(call POST /api/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | field accessToken)"
echo "access token: ${TOKEN:0:24}..."

step "Deposit 50,000 USD"
call POST /api/account/deposits '{"amount": 50000}' >/dev/null
call GET /api/account

step "AAPL order book (market maker quotes)"
call GET "/api/instruments/AAPL/book?depth=3"

step "Buy 150 AAPL at up to 192.00 (crosses the best asks)"
ORDER="$(call POST /api/orders '{"symbol":"AAPL","side":"BUY","price":192.00,"quantity":150}')"
echo "$ORDER"
ORDER_ID="$(echo "$ORDER" | field id)"

step "Trades for order $ORDER_ID"
call GET "/api/trades?size=5"

step "Positions and cash after the trade"
call GET /api/positions
call GET /api/account

step "A resting order, then cancel it (its reservation is released)"
RESTING_ID="$(call POST /api/orders '{"symbol":"MSFT","side":"BUY","price":400.00,"quantity":5}' | field id)"
call DELETE "/api/orders/$RESTING_ID"

step "A price 30% above the reference is rejected by the price-band rule (stored as REJECTED)"
call POST /api/orders '{"symbol":"NVDA","side":"BUY","price":156.00,"quantity":1}'

step "Cash ledger (newest first)"
call GET "/api/account/ledger?size=10"

printf '\nDone. Swagger UI: %s/swagger-ui/index.html\n' "$BASE_URL"
