#!/usr/bin/env bash
# deploy-frontend-prod.sh — Deploy the frontend to the production EC2 box in one go.
#
# Assumes your changes are already committed AND pushed to origin/master —
# this script does not commit or push anything itself.
#
# What it does:
#   1. Pulls latest master on the server (source only, no build there)
#   2. Builds the frontend locally (production box is a memory-constrained
#      t3.micro; building there once already crashed it — see git history)
#   3. Backs up the server's old dist/ and transfers a clean new one
#   4. Syncs it into /var/www/library-frontend (served directly by Nginx —
#      see /etc/nginx/sites-enabled/targetzone.co.in)
#   5. Verifies the live site is now serving the freshly built JS bundle
#
# Usage:  ./scripts/deploy-frontend-prod.sh

set -euo pipefail

SSH_KEY="$HOME/.ssh/my-ec2-key.pem"
SSH_HOST="ubuntu@13.50.206.73"
REMOTE_REPO="/home/ubuntu/library-system"
REMOTE_WEBROOT="/var/www/library-frontend"
SITE_HOST="targetzone.co.in"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"

G='\033[0;32m' Y='\033[1;33m' B='\033[0;34m' R='\033[0;31m' BLD='\033[1m' NC='\033[0m'
log()  { echo -e "${G}  ✓  ${NC}$*"; }
info() { echo -e "${B}  ·  ${NC}$*"; }
err()  { echo -e "${R}  ✗  ${NC}$*" >&2; exit 1; }
step() { echo -e "\n${BLD}${B}━━━  $*  ━━━${NC}"; }

ssh_cmd() { ssh -i "$SSH_KEY" -o ConnectTimeout=15 "$SSH_HOST" "$@"; }

[[ -f "$SSH_KEY" ]] || err "SSH key not found at $SSH_KEY"
[[ -d "$FRONTEND_DIR" ]] || err "frontend/ not found at $FRONTEND_DIR"

step "1/5 — Checking server is reachable"
ssh_cmd "echo ok" >/dev/null || err "Could not reach $SSH_HOST"
log "Server reachable."

step "2/5 — Pulling latest master on the server"
ssh_cmd "cd $REMOTE_REPO && git pull origin master"
log "Server source up to date."

step "3/5 — Building frontend locally"
( cd "$FRONTEND_DIR" && npx vite build --mode production )
BUNDLE=$(ls "$FRONTEND_DIR/dist/assets" | grep -E '^index-.*\.js$')
log "Built $BUNDLE"

step "4/5 — Transferring dist/ and syncing into $REMOTE_WEBROOT"
ssh_cmd "mv $REMOTE_REPO/frontend/dist $REMOTE_REPO/frontend/dist.bak.\$(date +%Y%m%d%H%M%S) 2>/dev/null || true"
scp -i "$SSH_KEY" -r "$FRONTEND_DIR/dist" "$SSH_HOST:$REMOTE_REPO/frontend/"
ssh_cmd "sudo rsync -a --delete $REMOTE_REPO/frontend/dist/ $REMOTE_WEBROOT/ && sudo chown -R www-data:www-data $REMOTE_WEBROOT"
log "Synced into $REMOTE_WEBROOT."

step "5/5 — Verifying"
HTTP_CODE=$(ssh_cmd "curl -sk -o /dev/null -w '%{http_code}' -H 'Host: $SITE_HOST' https://localhost/")
LIVE_BUNDLE=$(ssh_cmd "curl -sk -H 'Host: $SITE_HOST' https://localhost/" | grep -o 'index-[A-Za-z0-9]*\.js' | head -1)

if [[ "$HTTP_CODE" != "200" ]]; then
    err "Site returned HTTP $HTTP_CODE, expected 200."
fi
if [[ "$LIVE_BUNDLE" != "$BUNDLE" ]]; then
    err "Live site is serving $LIVE_BUNDLE, expected $BUNDLE."
fi

log "Deployed and verified — production is serving $LIVE_BUNDLE"
