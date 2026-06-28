#!/usr/bin/env bash
set -euo pipefail

# ─── Colours ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[1;33m'
CYN='\033[0;36m'; BLU='\033[0;34m'; MAG='\033[0;35m'
WHT='\033[1;37m'; RST='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE="$ROOT_DIR/docker-compose.infra.yml"

log()  { echo -e "${WHT}[run]${RST} $*"; }
ok()   { echo -e "${GRN}[ok]${RST}  $*"; }
err()  { echo -e "${RED}[err]${RST} $*" >&2; }
warn() { echo -e "${YLW}[warn]${RST} $*"; }

# ─── Env ─────────────────────────────────────────────────────────────────────
export DB_URL="postgres://library_user:library_pass@localhost:5432/library_db?sslmode=disable"
export REDIS_HOST=localhost
export REDIS_PORT=6379
export JWT_SECRET=${JWT_SECRET:-your-jwt-secret-here}
export DEV_MODE=true
export APITXT_AUTH_KEY=${APITXT_AUTH_KEY:-}
export ADMIN_PHONES=9071356842,8132978111
export PAYMENT_GATEWAY=CASHFREE
#export CASHFREE_APP_ID=<your-prod-cashfree-app-id>
#export CASHFREE_SECRET_KEY=<your-prod-cashfree-secret>
#export CASHFREE_ENV=production

export CASHFREE_APP_ID=${CASHFREE_APP_ID:-}
export CASHFREE_SECRET_KEY=${CASHFREE_SECRET_KEY:-}
export CASHFREE_ENV=${CASHFREE_ENV:-sandbox}

export META_WHATSAPP_TOKEN=${META_WHATSAPP_TOKEN:-}
export META_WHATSAPP_PHONE_NUMBER_ID=${META_WHATSAPP_PHONE_NUMBER_ID:-}
export META_WHATSAPP_API_VERSION=v21.0
export META_WHATSAPP_TEMPLATE_NAME=otpvm
export META_NOTIFICATION_TEMPLATE_NAME=tznallh
export META_WHATSAPP_LANGUAGE=en
export ADMIN_WHATSAPP=9071356842,8132978111
export NOTIFICATION_SERVICE_URL=http://localhost:8085
export USER_SERVICE_URL=http://localhost:8082
export UPLOAD_DIR="$SCRIPT_DIR/uploads"

mkdir -p "$UPLOAD_DIR/photos" "$UPLOAD_DIR/aadhaar" "$UPLOAD_DIR/gallery"

# ─── Infra ────────────────────────────────────────────────────────────────────
log "Starting Postgres + Redis..."
docker compose -f "$COMPOSE" up -d postgres redis

log "Waiting for Postgres..."
until docker exec library-postgres pg_isready -U library_user -d library_db -q 2>/dev/null; do
    printf '.'; sleep 1
done
echo
ok "Postgres ready"

log "Applying migrations..."
docker exec -i library-postgres psql -U library_user -d library_db \
  < "$SCRIPT_DIR/migrate.sql" > /dev/null 2>&1 && ok "Migrations applied" || warn "Migration error (check migrate.sql)"

log "Waiting for Redis..."
until docker exec library-redis redis-cli ping 2>/dev/null | grep -q PONG; do
    printf '.'; sleep 1
done
echo
ok "Redis ready"

# ─── Build all services ──────────────────────────────────────────────────────
log "Building services..."
for svc in notification-service auth-service user-service membership-service seat-service admin-service api-gateway; do
    go build -o "$SCRIPT_DIR/$svc/.bin" "$SCRIPT_DIR/$svc/" \
        && ok "Built $svc" \
        || { err "Failed to build $svc"; exit 1; }
done

# ─── Service launcher ────────────────────────────────────────────────────────
PIDS=()

# Usage: start_svc <colour> <name> <dir>
start_svc() {
    local colour="$1" name="$2" dir="$3"
    mkdir -p "$SCRIPT_DIR/.logs"

    "$SCRIPT_DIR/$dir/.bin" 2>&1 \
        | sed "s/^/$(printf "${colour}[${name}]${RST} ")/" &
    PIDS+=($!)
    ok "Started ${name} (pid ${PIDS[-1]})"
}

# ─── Cleanup on exit ─────────────────────────────────────────────────────────
cleanup() {
    echo
    log "Shutting down services..."
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
    ok "All services stopped. Infra (Postgres/Redis) still running."
    log "Stop infra: docker compose -f $COMPOSE down"
}
trap cleanup EXIT INT TERM

# ─── Start services (notification first, gateway last) ───────────────────────
echo
log "Starting Go services..."
start_svc "$MAG" "notif"      "notification-service"
start_svc "$CYN" "auth"       "auth-service"
start_svc "$BLU" "user"       "user-service"
start_svc "$GRN" "membership" "membership-service"
start_svc "$YLW" "seat"       "seat-service"
start_svc "$RED" "admin"      "admin-service"

# Brief pause so upstream services bind their ports before gateway starts
sleep 2

start_svc "$WHT" "gateway"    "api-gateway"

echo
echo -e "${GRN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}"
echo -e "${WHT}  Go backend running.  API gateway → http://localhost:8080${RST}"
echo -e "${WHT}  DEV_MODE=true  →  OTP is always 123456${RST}"
echo -e "${WHT}  Admin phones   →  9071356842 / 8132978111${RST}"
echo -e "${GRN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}"
echo -e "  ${CYN}Frontend:${RST}  cd frontend && npm run dev"
echo -e "  ${CYN}Stop:${RST}      Ctrl+C"
echo

# Keep script alive — wait for any child to exit unexpectedly
wait -n 2>/dev/null || wait
