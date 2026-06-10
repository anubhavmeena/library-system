#!/usr/bin/env bash
# deploy.sh — Install and run Target Zone Library on a fresh Linux server.
# Tested on: Ubuntu 20.04+, Debian 11+
# Usage:  chmod +x deploy.sh && ./deploy.sh

set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
G='\033[0;32m' Y='\033[1;33m' B='\033[0;34m' R='\033[0;31m'
C='\033[0;36m' BLD='\033[1m' NC='\033[0m'

log()  { echo -e "${G}  ✓  ${NC}$*"; }
info() { echo -e "${B}  ·  ${NC}$*"; }
warn() { echo -e "${Y}  !  ${NC}$*"; }
err()  { echo -e "${R}  ✗  ${NC}$*" >&2; exit 1; }
step() { echo -e "\n${BLD}${C}━━━  $*  ━━━${NC}"; }
ask()  { printf "${Y}  ?  ${NC}%s " "$*"; }

# ── Step 1: Prerequisites ─────────────────────────────────────────────────────
step "Step 1 — Prerequisites"

if ! command -v git &>/dev/null || ! command -v curl &>/dev/null; then
    info "Installing git and curl..."
    sudo apt-get update -qq && sudo apt-get install -y git curl
fi
log "git  $(git --version | awk '{print $3}')"

if ! command -v docker &>/dev/null; then
    info "Docker not found — installing via get.docker.com..."
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$USER"
    sudo systemctl enable --now docker
    warn "Added $USER to the docker group. Log out and back in to use Docker without sudo."
    warn "Using 'sudo docker' for this session."
fi

# Use sudo if the current user cannot reach the Docker socket yet
if docker info &>/dev/null 2>&1; then
    DC_BIN="docker"
else
    DC_BIN="sudo docker"
fi
log "Docker  $($DC_BIN --version | awk '{print $3}' | tr -d ',')"

# Docker Compose v2 plugin preferred; fall back to standalone v1
if $DC_BIN compose version &>/dev/null 2>&1; then
    COMPOSE="$DC_BIN compose"
elif command -v docker-compose &>/dev/null; then
    COMPOSE="docker-compose"
    warn "Using docker-compose v1. Upgrading to v2 is recommended."
else
    info "Installing docker-compose-plugin..."
    sudo apt-get update -qq && sudo apt-get install -y docker-compose-plugin
    COMPOSE="$DC_BIN compose"
fi
log "Compose  $($COMPOSE version | head -1)"

# ── Step 2: Repository ────────────────────────────────────────────────────────
step "Step 2 — Repository"

if [[ ! -f "docker-compose.infra.yml" ]]; then
    warn "docker-compose.infra.yml not found — not in the repo root."
    ask "Git clone URL (blank to abort):"
    read -r CLONE_URL
    [[ -z "$CLONE_URL" ]] && err "Aborted. Re-run this script from the repository root."
    ask "Clone into directory [library-system]:"
    read -r CLONE_DIR
    CLONE_DIR="${CLONE_DIR:-library-system}"
    git clone "$CLONE_URL" "$CLONE_DIR"
    cd "$CLONE_DIR"
fi
log "Repo root: $(pwd)"

# ── Step 3: Environment (.env) ────────────────────────────────────────────────
step "Step 3 — Environment configuration"

# Captured during prompts; used later for the admin DB update.
ADMIN_MOBILE_NEW=""
ADMIN_EMAIL_NEW=""

if [[ -f ".env" ]]; then
    warn ".env already exists — skipping. Delete it to reconfigure from scratch."
else
    JWT_SECRET=$(openssl rand -hex 32)
    log "JWT_SECRET generated (64 hex chars)."

    echo ""
    info "─── Admin account ────────────────────────────────────────────────────────"
    info "The database seed creates: mobile=9999999999 / email=admin@targetzone.co.in"
    info "Enter real credentials to override, or press Enter to keep defaults."
    ask "Admin mobile  [9999999999]:";           read -r ADMIN_MOBILE_NEW
    ask "Admin email   [admin@targetzone.co.in]:"; read -r ADMIN_EMAIL_NEW

    echo ""
    info "─── Twilio — OTP & WhatsApp ──────────────────────────────────────────────"
    info "Leave blank: OTP is hardcoded to '123456', messages are logged only."
    ask "TWILIO_ACCOUNT_SID:";                                  read -r T_SID
    ask "TWILIO_AUTH_TOKEN:";                                   read -r T_TOKEN
    ask "TWILIO_PHONE_NUMBER (e.g. +911234567890):";            read -r T_PHONE
    ask "TWILIO_WHATSAPP_FROM [whatsapp:+14155238886]:";        read -r T_WA
    T_WA="${T_WA:-whatsapp:+14155238886}"

    echo ""
    info "─── SendGrid — email notifications ───────────────────────────────────────"
    info "Leave blank: emails are logged only, not delivered."
    ask "SENDGRID_API_KEY:";                                          read -r SG_KEY
    ask "FROM_EMAIL        [noreply@targetzone.co.in]:";              read -r SG_FROM_EMAIL
    SG_FROM_EMAIL="${SG_FROM_EMAIL:-noreply@targetzone.co.in}"
    ask "FROM_NAME         [Target Zone Library]:";                   read -r SG_FROM_NAME
    SG_FROM_NAME="${SG_FROM_NAME:-Target Zone Library}"
    ask "ADMIN_EMAIL (alert notifications) [admin@targetzone.co.in]:"; read -r SG_ADMIN_EMAIL
    SG_ADMIN_EMAIL="${SG_ADMIN_EMAIL:-admin@targetzone.co.in}"
    ask "ADMIN_WHATSAPP    (e.g. whatsapp:+919876543210) [blank=skip]:"; read -r SG_ADMIN_WA

    echo ""
    info "─── Razorpay — payments ──────────────────────────────────────────────────"
    info "Leave blank: dev_order_* IDs are used and HMAC verification is skipped."
    ask "RAZORPAY_KEY_ID:";     read -r RPY_ID
    ask "RAZORPAY_KEY_SECRET:"; read -r RPY_SECRET

    cat > .env <<ENVEOF
# Target Zone Library — Environment Variables
# Generated by deploy.sh on $(date -u '+%Y-%m-%d %H:%M UTC')

# JWT secret — shared by api-gateway and auth-service (must be identical in both)
JWT_SECRET=${JWT_SECRET}

# Twilio — OTP & WhatsApp (blank = dev mode: OTP is always '123456')
TWILIO_ACCOUNT_SID=${T_SID:-}
TWILIO_AUTH_TOKEN=${T_TOKEN:-}
TWILIO_PHONE_NUMBER=${T_PHONE:-}
TWILIO_WHATSAPP_FROM=${T_WA}

# SendGrid — email notifications (blank = emails logged but not sent)
SENDGRID_API_KEY=${SG_KEY:-}
FROM_EMAIL=${SG_FROM_EMAIL}
FROM_NAME=${SG_FROM_NAME}
ADMIN_EMAIL=${SG_ADMIN_EMAIL}
ADMIN_WHATSAPP=${SG_ADMIN_WA:-}

# Razorpay — payments (blank = dev_order_* IDs generated, HMAC skipped)
RAZORPAY_KEY_ID=${RPY_ID:-}
RAZORPAY_KEY_SECRET=${RPY_SECRET:-}
ENVEOF
    log ".env written."
fi

# ── Step 4: Start infrastructure ──────────────────────────────────────────────
step "Step 4 — Starting infrastructure  (PostgreSQL · Redis · Zookeeper · Kafka)"

$COMPOSE -f docker-compose.infra.yml up -d
log "Infrastructure containers started."

# ── Step 5: Wait for infra health, then seed the database ────────────────────
step "Step 5 — Database initialisation"

wait_healthy() {
    local name="$1" max="${2:-120}" elapsed=0
    info "Waiting for ${name}..."
    until [[ "$(docker inspect --format='{{.State.Health.Status}}' \
               "library-${name}" 2>/dev/null || echo 'pending')" == "healthy" ]]; do
        if (( elapsed >= max )); then
            echo ""
            echo -e "${R}  ✗  ${NC}library-${name} not healthy after ${max}s." >&2
            echo -e "${R}  ✗  ${NC}Logs: docker logs library-${name}" >&2
            exit 1
        fi
        printf "."
        sleep 5
        elapsed=$(( elapsed + 5 ))
    done
    echo ""; log "${name} healthy."
}

wait_healthy "postgres" 120
wait_healthy "redis"     60
wait_healthy "kafka"    180

# PostgreSQL runs docker-entrypoint-initdb.d scripts on its very first start:
#   01_schema.sql — creates all tables (users, memberships, seats, payments, …)
#   02_seed.sql   — inserts admin user, 2 membership plans, 110 seats
# All INSERTs use ON CONFLICT DO NOTHING, so re-running is safe.
info "Schema and seed applied automatically on first PostgreSQL start."

# Optionally override placeholder admin credentials from the seed
if [[ -n "${ADMIN_MOBILE_NEW}" || -n "${ADMIN_EMAIL_NEW}" ]]; then
    info "Updating admin credentials in the database..."
    SET_SQL=""
    [[ -n "$ADMIN_MOBILE_NEW" ]] && SET_SQL="mobile = '${ADMIN_MOBILE_NEW}', "
    [[ -n "$ADMIN_EMAIL_NEW"  ]] && SET_SQL="${SET_SQL}email  = '${ADMIN_EMAIL_NEW}', "
    SET_SQL="${SET_SQL}updated_at = NOW()"
    docker exec library-postgres psql -U library_user -d library_db \
        -c "UPDATE users SET ${SET_SQL} WHERE id = 'a0000000-0000-0000-0000-000000000001';"
    log "Admin credentials updated."
else
    info "Keeping default admin credentials (mobile=9999999999, email=admin@targetzone.co.in)."
fi

# ── Step 6: Build and start application services ──────────────────────────────
step "Step 6 — Building and starting application services"
info "First run compiles 7 Spring Boot services — allow 5–10 minutes."

$COMPOSE -f docker-compose.services.yml up -d --build
log "All application containers started."

# ── Step 7: Wait for application health ───────────────────────────────────────
step "Step 7 — Waiting for application services to become healthy"

wait_app() {
    local name="$1" max="${2:-300}" elapsed=0 status=""
    info "Waiting for ${name}..."
    while (( elapsed < max )); do
        status=$(docker inspect --format='{{.State.Health.Status}}' \
            "library-${name}" 2>/dev/null || echo "pending")
        if [[ "$status" == "healthy" ]]; then
            echo ""; log "${name} healthy."; return 0
        fi
        if [[ "$status" == "unhealthy" ]]; then
            echo ""; warn "${name} reported unhealthy. Check: docker logs library-${name}"; return 1
        fi
        printf "."
        sleep 10
        elapsed=$(( elapsed + 10 ))
    done
    echo ""
    warn "${name} not yet healthy after ${max}s — it may still be starting up."
    warn "Check: docker logs library-${name}"
}

wait_app "api-gateway"          300
wait_app "auth-service"         300
wait_app "user-service"         300
wait_app "membership-service"   300
wait_app "seat-service"         300
wait_app "notification-service" 300
wait_app "admin-service"        300
wait_app "frontend"             120

# ── Step 8: Summary ───────────────────────────────────────────────────────────
SERVER_IP=$(hostname -I | awk '{print $1}')
DISP_MOB="${ADMIN_MOBILE_NEW:-9999999999}"
DISP_EMAIL="${ADMIN_EMAIL_NEW:-admin@targetzone.co.in}"

echo ""
echo -e "${BLD}${G}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLD}${G}║      Target Zone Library — Deployment Complete!      ║${NC}"
echo -e "${BLD}${G}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${BLD}Access points${NC}"
printf "  %-16s  http://%s:3000\n" "Frontend"    "$SERVER_IP"
printf "  %-16s  http://%s:8080\n" "API Gateway" "$SERVER_IP"
printf "  %-16s  http://%s:8090\n" "Kafka UI"    "$SERVER_IP"
echo ""
echo -e "  ${BLD}Admin login${NC}"
printf "  Mobile  %s\n" "$DISP_MOB"
printf "  Email   %s\n" "$DISP_EMAIL"
echo "  OTP     123456  (dev mode)  |  real SMS if Twilio is configured"
echo ""
echo -e "  ${BLD}Useful commands${NC}"
echo "  Tail all logs   $COMPOSE -f docker-compose.services.yml logs -f"
echo "  Restart app     $COMPOSE -f docker-compose.services.yml restart"
echo "  Stop app        $COMPOSE -f docker-compose.services.yml down"
echo "  Stop + infra    $COMPOSE -f docker-compose.services.yml down && $COMPOSE -f docker-compose.infra.yml down"
echo "  Wipe all data   $COMPOSE -f docker-compose.infra.yml down -v   # deletes volumes!"
echo "  Rebuild images  $COMPOSE -f docker-compose.services.yml up -d --build"
echo ""
