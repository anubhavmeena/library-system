#!/usr/bin/env bash
# deploy-prod.sh — Deploy rust-backend and/or frontend to the production
# Lightsail box in one go.
#
# Assumes your changes are already committed AND pushed to origin/master —
# this script does not commit or push anything itself.
#
# Usage:
#   ./scripts/deploy-prod.sh                 # deploy both backend and frontend
#   ./scripts/deploy-prod.sh --backend-only
#   ./scripts/deploy-prod.sh --frontend-only
#
# Backend:
#   1. Runs `cargo test` locally — refuses to deploy if anything fails
#   2. Cross-compiles a static musl binary locally (x86_64-unknown-linux-musl)
#      — never build on the server itself: it's a memory-constrained 1GB Lightsail box
#      and a `cargo build --release` there has crashed/rebooted the instance
#      before. Cross-compiling locally avoids that entirely.
#   3. Backs up the server's current binary, transfers the new one, verifies
#      the transfer with a checksum, restarts library-backend.service
#
# Frontend:
#   1. Builds the frontend locally (same memory-constrained-box reasoning as above —
#      `vite build` directly on the server has also crashed it once)
#   2. Syncs dist/ to the server with `rsync --checksum` (not scp) — vite
#      resets mtimes on every build even for byte-identical files, so a
#      plain mtime-based sync would re-upload the whole (multi-MB) gallery
#      every time; checksum comparison actually skips unchanged files while
#      still catching genuinely new ones (content-hashed JS/CSS bundles).
#      Backs up the live webroot (cheap hardlink copy) before syncing into
#      /var/www/library-frontend (served directly by Nginx — see
#      /etc/nginx/sites-enabled/targetzone.co.in), fixes ownership
#   3. Verifies the live site is serving the freshly built JS bundle
#
# Both: pulls latest master on the server first (source only, never built
# there) so migrations/config on disk match what's actually running.
#
# SSH transport: prod moved 2026-09-05 off the old EC2 box (which had gone
# Cloudflare-Tunnel-only for SSH) to a new AWS Lightsail instance with a
# static public IP — direct `ssh ubuntu@<IP>` works, no cloudflared/tunnel
# needed. The old box is stopped (kept as a rollback target, not live) so
# targetzone.co.in's DNS no longer points at it either.

set -euo pipefail

SSH_KEY="$HOME/.ssh/my-ec2-key.pem"
SSH_HOST="ubuntu@52.66.154.11"
REMOTE_REPO="/home/ubuntu/library-system"
REMOTE_WEBROOT="/var/www/library-frontend"
SITE_HOST="targetzone.co.in"
MUSL_TARGET="x86_64-unknown-linux-musl"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
BACKEND_DIR="$REPO_ROOT/rust-backend"

DO_BACKEND=1
DO_FRONTEND=1
for arg in "$@"; do
    case "$arg" in
        --backend-only)  DO_FRONTEND=0 ;;
        --frontend-only) DO_BACKEND=0 ;;
        *) echo "Unknown argument: $arg (expected --backend-only or --frontend-only)" >&2; exit 1 ;;
    esac
done

G='\033[0;32m' Y='\033[1;33m' B='\033[0;34m' R='\033[0;31m' BLD='\033[1m' NC='\033[0m'
log()  { echo -e "${G}  ✓  ${NC}$*"; }
info() { echo -e "${B}  ·  ${NC}$*"; }
err()  { echo -e "${R}  ✗  ${NC}$*" >&2; exit 1; }
step() { echo -e "\n${BLD}${B}━━━  $*  ━━━${NC}"; }

SSH_OPTS=(-i "$SSH_KEY" -o ConnectTimeout=15)
ssh_cmd() { ssh "${SSH_OPTS[@]}" "$SSH_HOST" "$@"; }

[[ -f "$SSH_KEY" ]] || err "SSH key not found at $SSH_KEY"
if (( DO_FRONTEND )); then [[ -d "$FRONTEND_DIR" ]] || err "frontend/ not found at $FRONTEND_DIR"; fi
if (( DO_BACKEND )); then [[ -d "$BACKEND_DIR" ]] || err "rust-backend/ not found at $BACKEND_DIR"; fi

step "Checking server is reachable"
ssh_cmd "echo ok" >/dev/null || err "Could not reach $SSH_HOST"
log "Server reachable."

step "Pulling latest master on the server"
ssh_cmd "cd $REMOTE_REPO && git pull origin master"
log "Server source up to date."

if (( DO_BACKEND )); then
    step "Backend — running tests"
    ( cd "$BACKEND_DIR" && cargo test )
    log "All tests passed."

    step "Backend — cross-compiling musl binary locally"
    if ! rustup target list --installed | grep -q "$MUSL_TARGET"; then
        info "Installing rustup target $MUSL_TARGET..."
        rustup target add "$MUSL_TARGET"
    fi
    if ! command -v x86_64-linux-musl-gcc &>/dev/null; then
        err "x86_64-linux-musl-gcc not found — install a musl C toolchain first (e.g. 'sudo pacman -S musl' on Arch, 'sudo apt install musl-tools' on Debian/Ubuntu)."
    fi
    ( cd "$BACKEND_DIR" && cargo build --release --target "$MUSL_TARGET" )
    LOCAL_BIN="$BACKEND_DIR/target/$MUSL_TARGET/release/library-backend"
    [[ -f "$LOCAL_BIN" ]] || err "Build did not produce $LOCAL_BIN"
    LOCAL_SHA=$(sha256sum "$LOCAL_BIN" | awk '{print $1}')
    log "Built library-backend (sha256 ${LOCAL_SHA:0:12}...)"

    step "Backend — transferring and restarting"
    ssh_cmd "cp $REMOTE_REPO/rust-backend/target/release/library-backend $REMOTE_REPO/rust-backend/target/release/library-backend.bak.\$(date +%Y%m%d%H%M%S) 2>/dev/null || true"
    scp -i "$SSH_KEY" "$LOCAL_BIN" "$SSH_HOST:$REMOTE_REPO/rust-backend/target/release/library-backend.new"
    REMOTE_SHA=$(ssh_cmd "sha256sum $REMOTE_REPO/rust-backend/target/release/library-backend.new" | awk '{print $1}')
    [[ "$LOCAL_SHA" == "$REMOTE_SHA" ]] || err "Checksum mismatch after transfer (local $LOCAL_SHA, remote $REMOTE_SHA)"
    ssh_cmd "mv $REMOTE_REPO/rust-backend/target/release/library-backend.new $REMOTE_REPO/rust-backend/target/release/library-backend && sudo systemctl restart library-backend.service"
    log "Transferred (checksum verified) and restarted library-backend.service."

    step "Backend — verifying"
    sleep 2
    BACKEND_ACTIVE=$(ssh_cmd "systemctl is-active library-backend.service" || true)
    BACKEND_HTTP=$(ssh_cmd "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/plans")
    [[ "$BACKEND_ACTIVE" == "active" ]] || err "library-backend.service is not active (status: $BACKEND_ACTIVE)"
    [[ "$BACKEND_HTTP" == "200" ]] || err "Backend health check returned HTTP $BACKEND_HTTP, expected 200"
    log "Backend deployed and verified — service active, /api/plans returns 200."
fi

if (( DO_FRONTEND )); then
    step "Frontend — building locally"
    ( cd "$FRONTEND_DIR" && npx vite build --mode production )
    BUNDLE=$(ls "$FRONTEND_DIR/dist/assets" | grep -E '^index-.*\.js$')
    log "Built $BUNDLE"

    step "Frontend — syncing dist/ to the server (checksum-based, skips unchanged files like gallery photos)"
    rsync -az --checksum --delete -e "ssh -i $SSH_KEY -o ConnectTimeout=15" \
        "$FRONTEND_DIR/dist/" "$SSH_HOST:$REMOTE_REPO/frontend/dist/"
    log "Synced dist/ to server."

    step "Frontend — backing up live webroot and syncing into $REMOTE_WEBROOT"
    ssh_cmd "sudo cp -al $REMOTE_WEBROOT ${REMOTE_WEBROOT}.bak.\$(date +%Y%m%d%H%M%S) 2>/dev/null || true"
    ssh_cmd "sudo rsync -a --delete $REMOTE_REPO/frontend/dist/ $REMOTE_WEBROOT/ && sudo chown -R www-data:www-data $REMOTE_WEBROOT"
    log "Synced into $REMOTE_WEBROOT."

    step "Frontend — verifying"
    HTTP_CODE=$(ssh_cmd "curl -sk -o /dev/null -w '%{http_code}' -H 'Host: $SITE_HOST' https://localhost/")
    LIVE_BUNDLE=$(ssh_cmd "curl -sk -H 'Host: $SITE_HOST' https://localhost/" | grep -o 'index-[A-Za-z0-9]*\.js' | head -1)
    [[ "$HTTP_CODE" == "200" ]] || err "Site returned HTTP $HTTP_CODE, expected 200."
    [[ "$LIVE_BUNDLE" == "$BUNDLE" ]] || err "Live site is serving $LIVE_BUNDLE, expected $BUNDLE."
    log "Frontend deployed and verified — production is serving $LIVE_BUNDLE"
fi

echo -e "\n${BLD}${G}Deploy complete.${NC}"
