#!/usr/bin/env bash
# AI-generated with Claude Code, 2026-10-13, reviewed by Jaewan Park
# One-time setup of a course server for the Pix server (server/README.md, Deploy). It installs no packages and
# creates no users. Run it as root from the checkout, with the branch this server follows:
#
#   sudo git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git /opt/pix/repo
#   sudo /opt/pix/repo/server/deploy/setup.sh dev     # Server B; use "main" on Server A
#
# Running it again is safe: it keeps an existing .env and only rewrites the branch and the systemd units.
set -euo pipefail

PIX_HOME=/opt/pix
REPO=$PIX_HOME/repo

die() { echo "ERROR: $*" >&2; exit 1; }

BRANCH=${1:-}
[ -n "$BRANCH" ] || die "usage: setup.sh <branch>   (dev on Server B, main on Server A)"
[ "$(id -u)" = 0 ] || die "run this with sudo"
[ -f "$REPO/server/deploy/deploy.sh" ] || die "clone the repository to $REPO first (see the top of this file)"

for tool in git curl docker; do
    command -v "$tool" > /dev/null 2>&1 || die "$tool is not installed"
done
docker compose version > /dev/null 2>&1 || die "Docker Compose is missing: sudo apt install -y docker-compose-v2"
docker buildx version > /dev/null 2>&1 || die "Docker Buildx is missing: sudo apt install -y docker-buildx"

mkdir -p "$PIX_HOME/state"
printf 'BRANCH=%s\n' "$BRANCH" > "$PIX_HOME/deploy.conf"

if [ ! -f "$PIX_HOME/.env" ]; then
    install -m 600 "$REPO/server/.env.example" "$PIX_HOME/.env"
    NEW_ENV=yes
fi

install -m 644 "$REPO/server/deploy/pix-deploy.service" "$REPO/server/deploy/pix-deploy.timer" /etc/systemd/system/
systemctl daemon-reload

echo "This server follows the branch: $BRANCH"
echo
echo "Next:"
if [ "${NEW_ENV:-no}" = yes ]; then
    echo "  1. Put the OpenRouter key in $PIX_HOME/.env:   sudoedit $PIX_HOME/.env"
else
    echo "  1. $PIX_HOME/.env already exists and was kept."
fi
echo "  2. Deploy once by hand:   sudo $REPO/server/deploy/deploy.sh deploy"
echo "  3. Turn on automatic deploys:   sudo systemctl enable --now pix-deploy.timer"
