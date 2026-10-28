#!/usr/bin/env bash
# AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
# Deploys the Pix server on a course server (Design 2.8, Deployment; server/README.md, Deploy). Run as root.
#
#   deploy.sh auto           deploy the configured branch if it has a new commit; the systemd timer runs this
#   deploy.sh deploy [ref]   deploy a branch or a commit now; the default is the configured branch
#   deploy.sh restart        start the other copy from the same image, for example after editing .env
#   deploy.sh rollback       switch back to the image that served before the last deploy
#   deploy.sh status         show what is running
#
# Settings come from /opt/pix/deploy.conf: BRANCH, and optionally HTTP_PORT, MAX_WAIT_SECONDS (600), and PROJECT.
# MAX_WAIT_SECONDS given for one command, as in `sudo MAX_WAIT_SECONDS=0 deploy.sh deploy`, wins over the file.
#
# Two copies of the app, blue and green, sit behind nginx and share their rooms through Redis and their accounts
# through PostgreSQL (Design 2.7). A deploy builds the image, starts it as the idle copy, waits until it is healthy,
# points nginx at it, and stops the other copy. Stopping a copy closes its sockets; the app takes its place again
# (Design 2.6.7, #118), but an older app cannot, so the switch still waits until no room is open.
#
# Everything is inside functions and the last line calls main, so bash has read the whole file before a deploy
# checks out another version of it.
set -euo pipefail

PIX_HOME=${PIX_HOME:-/opt/pix}
REPO=$PIX_HOME/repo
ENV_FILE=$PIX_HOME/.env
DB_PASSWORD_FILE=$PIX_HOME/db-password
JWT_SECRET_FILE=$PIX_HOME/jwt-secret
# The account database's files (Design 2.7). Never delete it: it holds every account.
POSTGRES_DIR=$PIX_HOME/postgres
STATE_DIR=$PIX_HOME/state
STATE_FILE=$STATE_DIR/deploy.state
BUILD_LOG=$STATE_DIR/last-build.log
# Names only, never values: what check_env found wrong with .env, for `status` and the login notice in pix.sh.
ENV_CHECK=$STATE_DIR/env-check
IMAGE=pix-server

log() { echo "[deploy] $*"; }
die() { echo "[deploy] ERROR: $*" >&2; exit 1; }

usage() {
    sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//'
}

# --- state ------------------------------------------------------------------------------------------------------

# ACTIVE is the copy nginx points at. BLUE_COMMIT and GREEN_COMMIT are the commits their images were built from.
# COMMIT is the last commit recorded as live, which /version shows; it is newer than the active copy's commit
# when later commits did not touch server/. FAILED_TREE is a server/ tree that failed or was rolled back, which
# "auto" does not try again.
load_state() {
    ACTIVE="" BLUE_COMMIT="" GREEN_COMMIT="" COMMIT="" FAILED_TREE=""
    # shellcheck disable=SC1090
    if [ -f "$STATE_FILE" ]; then . "$STATE_FILE"; fi
}

save_state() {
    {
        echo "ACTIVE=$ACTIVE"
        echo "BLUE_COMMIT=$BLUE_COMMIT"
        echo "GREEN_COMMIT=$GREEN_COMMIT"
        echo "COMMIT=$COMMIT"
        echo "FAILED_TREE=$FAILED_TREE"
    } > "$STATE_FILE.tmp"
    mv "$STATE_FILE.tmp" "$STATE_FILE"
    printf '%s\n' "$COMMIT" > "$STATE_DIR/version.tmp"
    mv "$STATE_DIR/version.tmp" "$STATE_DIR/version"
}

other() { if [ "$1" = blue ]; then echo green; else echo blue; fi; }
commit_of() { if [ "$1" = blue ]; then echo "$BLUE_COMMIT"; else echo "$GREEN_COMMIT"; fi; }
set_commit() { if [ "$1" = blue ]; then BLUE_COMMIT=$2; else GREEN_COMMIT=$2; fi; }
short() { printf '%s' "$1" | cut -c1-12; }
server_tree() { git -C "$REPO" rev-parse --verify -q "$1:server" || true; }

# --- settings ---------------------------------------------------------------------------------------------------

# Compares .env with the checked-out server/.env.example, without changing either. A name the example lists with no
# value (`KEY=`) needs a value on every server, such as an API key; one with a value is a default the app already
# has, so it is not copied. A name .env sets that the example does not list at all is probably misspelled: the app
# ignores names it does not know. Each finding is logged as a warning and written, as names only, to ENV_CHECK.
check_env() {
    # Its own temporary file, because `status` runs this without the deploy lock.
    local example=$REPO/server/.env.example key missing="" unknown="" tmp
    tmp=$(mktemp "$ENV_CHECK.XXXXXX")
    # mktemp makes the file its owner's alone, but every operator reads it at login (pix.sh); it holds names only.
    chmod 644 "$tmp"
    if [ -f "$example" ] && [ -f "$ENV_FILE" ]; then
        for key in $(sed -n 's/^\([A-Z][A-Z0-9_]*\)=[[:space:]]*$/\1/p' "$example"); do
            if ! grep -q -i -E "^[[:space:]]*(export[[:space:]]+)?$key[[:space:]]*=[[:space:]]*([^[:space:]#\"']|\"[^\"]|'[^'])" "$ENV_FILE"; then
                missing="$missing $key"
            fi
        done
        for key in $(sed -n -E 's/^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=.*/\2/p' "$ENV_FILE"); do
            if ! grep -q -i -E "^(#[[:space:]]*)?$key=" "$example"; then unknown="$unknown $key"; fi
        done
    fi
    if [ -n "$missing" ]; then
        echo "missing:$missing" >> "$tmp"
        log "WARNING: $ENV_FILE has no value for:$missing. server/.env.example says what each is for; set them with pix env, then pix restart"
    fi
    if [ -n "$unknown" ]; then
        echo "unknown:$unknown" >> "$tmp"
        log "WARNING: $ENV_FILE sets names that server/.env.example does not list, so the app ignores them:$unknown. Check their spelling"
    fi
    mv -f "$tmp" "$ENV_CHECK"
}

# --- docker and nginx -------------------------------------------------------------------------------------------

# A random secret in a file, made on the first deploy and readable by root only, so nobody has to put it in .env:
# the account database's password, which PostgreSQL takes when its directory is first filled, and the key that signs
# access tokens, whose change signs everyone out within minutes. Keep both files as they are. They are not in the state directory,
# which nginx reads.
secret() {
    local tmp
    if [ ! -s "$1" ]; then
        # mktemp makes the file readable by root only. A hard link is refused when the name exists, so if two runs
        # make the secret at once (a first deploy and `status`, which takes no lock), the first one's value is kept.
        tmp=$(mktemp "$1.XXXXXX")
        od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$tmp"
        ln "$tmp" "$1" 2> /dev/null || true
        rm -f "$tmp"
    fi
    cat "$1"
}

compose() {
    PIX_BLUE_TAG=$(short "${BLUE_COMMIT:-none}") PIX_GREEN_TAG=$(short "${GREEN_COMMIT:-none}") \
        PIX_ENV_FILE=$ENV_FILE PIX_STATE_DIR=$STATE_DIR PIX_POSTGRES_DIR=$POSTGRES_DIR PIX_HTTP_PORT=$HTTP_PORT \
        PIX_DB_PASSWORD=$(secret "$DB_PASSWORD_FILE") PIX_JWT_SECRET=$(secret "$JWT_SECRET_FILE") \
        docker compose -p "$PROJECT" -f "$REPO/server/deploy/docker-compose.yml" "$@"
}

# Runs a command with its output in the build log, and shows the end of the log only if the command fails.
quietly() {
    if "$@" >> "$BUILD_LOG" 2>&1; then return 0; fi
    tail -n 60 "$BUILD_LOG"
    return 1
}

build_image() {
    : > "$BUILD_LOG"
    log "building $IMAGE:$1 (the tests run first)"
    quietly docker build --target test "$REPO/server" || return 1
    quietly docker build --target runtime -t "$IMAGE:$1" "$REPO/server" || return 1
}

start_copy() {
    if compose up -d --wait --wait-timeout 120 "app-$1" >> "$BUILD_LOG" 2>&1; then return 0; fi
    log "app-$1 did not become healthy; its last lines:"
    compose logs --no-log-prefix --tail 40 "app-$1" 2>&1 || true
    compose stop "app-$1" > /dev/null 2>&1 || true
    return 1
}

point_nginx_at() {
    printf 'set $pix_app "app-%s:8000";\nset $pix_copy "%s";\n' "$1" "$1" > "$STATE_DIR/active.conf.tmp"
    mv "$STATE_DIR/active.conf.tmp" "$STATE_DIR/active.conf"
}

# Tests the configuration first: if nginx refuses it, the running nginx keeps the configuration it has.
reload_nginx() {
    local output
    if ! output=$(compose exec -T nginx nginx -t -q -c /etc/nginx/conf/nginx.conf 2>&1); then
        printf '%s\n' "$output"
        return 1
    fi
    compose exec -T nginx nginx -s reload -c /etc/nginx/conf/nginx.conf >> "$BUILD_LOG" 2>&1 || return 1
}

# The copy that answers /health with 200 through nginx, or nothing. nginx adds the header to its own errors too,
# so the status line is checked as well.
served_copy() {
    curl -sS -m 5 -o /dev/null -D - "http://127.0.0.1:$HTTP_PORT/health" 2> /dev/null | tr -d '\r' \
        | awk 'NR == 1 { ok = ($2 == "200") } tolower($1) == "x-pix-copy:" { copy = $2 } END { if (ok) print copy }'
}

wait_until_served() {
    local attempt
    for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
        if [ "$(served_copy)" = "$1" ]; then return 0; fi
        sleep 1
    done
    return 1
}

# Open rooms on one copy, or nothing if the copy is not running or is too old to say.
open_rooms() {
    compose exec -T "app-$1" python -c 'import json, urllib.request
print(json.load(urllib.request.urlopen("http://127.0.0.1:8000/internal/stats", timeout=3))["sessions"])' 2> /dev/null || true
}

wait_for_no_rooms() {
    local waited=0 rooms
    while :; do
        rooms=$(open_rooms "$1")
        case "$rooms" in
            0) return 0 ;;
            "" | *[!0-9]*)
                log "app-$1 does not report its rooms; not waiting"
                return 0
                ;;
        esac
        if [ "$waited" -ge "$MAX_WAIT_SECONDS" ]; then
            log "$rooms room(s) still open after ${waited}s; switching anyway: live sessions continue, waiting rooms end"
            return 0
        fi
        if [ $((waited % 60)) -eq 0 ]; then
            log "waiting for $rooms open room(s) on app-$1, at most ${MAX_WAIT_SECONDS}s"
        fi
        sleep 5
        waited=$((waited + 5))
    done
}

# Sends traffic to a copy that is already up and healthy, and stops the other one. $2 is "wait" to wait for the
# old copy's rooms first. On failure nginx points at the old copy again and ACTIVE is unchanged.
activate() {
    local new=$1 old was_active=$ACTIVE
    old=$(other "$new")
    if [ -z "$was_active" ] || [ -z "$(compose ps -q --status running nginx 2> /dev/null)" ]; then
        point_nginx_at "$new"
        compose up -d nginx >> "$BUILD_LOG" 2>&1 || return 1
    else
        # Picks up a changed nginx image or mount; does nothing otherwise.
        compose up -d nginx >> "$BUILD_LOG" 2>&1 || return 1
        if [ "$2" = wait ]; then wait_for_no_rooms "$old"; fi
        point_nginx_at "$new"
        if ! reload_nginx; then
            log "nginx refused the configuration; it keeps serving from app-$old"
            point_nginx_at "$old"
            return 1
        fi
    fi
    if ! wait_until_served "$new"; then
        log "nginx does not answer from app-$new"
        if [ -n "$was_active" ]; then
            point_nginx_at "$old"
            reload_nginx || true
        fi
        return 1
    fi
    ACTIVE=$new
    if [ -n "$was_active" ]; then
        log "now serving from app-$new; stopping app-$old (requests in flight get up to 70 s)"
        compose stop "app-$old" >> "$BUILD_LOG" 2>&1 || true
    else
        log "now serving from app-$new"
    fi
}

prune() {
    local image blue green
    blue="$IMAGE:$(short "${BLUE_COMMIT:-none}")"
    green="$IMAGE:$(short "${GREEN_COMMIT:-none}")"
    docker images --format '{{.Repository}}:{{.Tag}}' "$IMAGE" | while read -r image; do
        if [ "$image" != "$blue" ] && [ "$image" != "$green" ]; then
            docker rmi "$image" > /dev/null 2>&1 || true
        fi
    done
    docker image prune -f > /dev/null 2>&1 || true
    docker builder prune -f --filter until=168h > /dev/null 2>&1 || true
}

# --- commands ---------------------------------------------------------------------------------------------------

# Puts the checkout and the idle copy's record back after a failed deploy, and remembers the tree that failed.
give_up() {
    local sha=$1 idle=$2 idle_commit=$3 checkout=$4 reason=$5
    set_commit "$idle" "$idle_commit"
    if [ -n "$checkout" ]; then git -C "$REPO" checkout -q --detach "$checkout" || true; fi
    FAILED_TREE=$(server_tree "$sha")
    save_state
    log "FAILED: $reason. $(short "$sha") is not live${ACTIVE:+; app-$ACTIVE keeps serving}."
}

deploy_commit() {
    local sha=$1 mode=$2 tag tree idle idle_commit checkout=$COMMIT
    tag=$(short "$sha")
    tree=$(server_tree "$sha")

    if ! git -C "$REPO" cat-file -e "$sha:server/Dockerfile" 2> /dev/null \
        || ! git -C "$REPO" cat-file -e "$sha:server/deploy/docker-compose.yml" 2> /dev/null; then
        FAILED_TREE=$tree
        save_state
        log "$tag has no server/Dockerfile or server/deploy/; nothing to deploy"
        return 1
    fi

    if [ -n "$ACTIVE" ] && [ "$tree" = "$(server_tree "$(commit_of "$ACTIVE")")" ] && [ "$(served_copy)" = "$ACTIVE" ]; then
        git -C "$REPO" checkout -q --detach "$sha"
        COMMIT=$sha
        FAILED_TREE=""
        save_state
        log "$tag does not change server/; recorded without a restart"
        return 0
    fi

    idle=$(other "${ACTIVE:-green}")
    idle_commit=$(commit_of "$idle")
    log "deploying $tag to app-$idle"
    git -C "$REPO" checkout -q --detach "$sha"
    check_env
    build_image "$tag" || { give_up "$sha" "$idle" "$idle_commit" "$checkout" "the build or the tests failed"; return 1; }
    set_commit "$idle" "$sha"
    start_copy "$idle" || { give_up "$sha" "$idle" "$idle_commit" "$checkout" "the new copy did not start"; return 1; }
    if ! activate "$idle" "$mode"; then
        compose stop "app-$idle" > /dev/null 2>&1 || true
        give_up "$sha" "$idle" "$idle_commit" "$checkout" "nginx could not switch"
        return 1
    fi
    COMMIT=$sha
    FAILED_TREE=""
    save_state
    prune
    log "done: $tag is live"
}

command_auto() {
    local target tree
    git -C "$REPO" fetch -q origin "$BRANCH" || die "cannot fetch $BRANCH from origin"
    target=$(git -C "$REPO" rev-parse FETCH_HEAD)
    if [ "$target" = "$COMMIT" ]; then return 0; fi
    tree=$(server_tree "$target")
    if [ -n "$FAILED_TREE" ] && [ "$tree" = "$FAILED_TREE" ]; then return 0; fi
    deploy_commit "$target" wait
}

command_deploy() {
    local ref=${1:-$BRANCH} target
    git -C "$REPO" fetch -q origin "$ref" || die "cannot fetch $ref from origin"
    target=$(git -C "$REPO" rev-parse FETCH_HEAD)
    deploy_commit "$target" wait
}

command_restart() {
    local idle idle_commit sha
    [ -n "$ACTIVE" ] || die "nothing is deployed yet"
    idle=$(other "$ACTIVE")
    idle_commit=$(commit_of "$idle")
    sha=$(commit_of "$ACTIVE")
    : > "$BUILD_LOG"
    check_env
    log "restarting $(short "$sha") as app-$idle"
    set_commit "$idle" "$sha"
    if ! start_copy "$idle" || ! activate "$idle" wait; then
        compose stop "app-$idle" > /dev/null 2>&1 || true
        set_commit "$idle" "$idle_commit"
        save_state
        die "the restart failed; app-$ACTIVE keeps serving"
    fi
    save_state
    prune
    log "done: both copies now hold $(short "$sha"), so there is no older image to roll back to"
}

command_rollback() {
    local idle target from
    [ -n "$ACTIVE" ] || die "nothing is deployed yet"
    idle=$(other "$ACTIVE")
    target=$(commit_of "$idle")
    from=$(commit_of "$ACTIVE")
    [ -n "$target" ] || die "there is no earlier image to roll back to"
    [ "$target" != "$from" ] || die "both copies hold $(short "$from"); there is no earlier image to roll back to"
    docker image inspect "$IMAGE:$(short "$target")" > /dev/null 2>&1 || die "the image $IMAGE:$(short "$target") is gone"
    : > "$BUILD_LOG"
    log "rolling back from $(short "$from") to $(short "$target") on app-$idle"
    git -C "$REPO" checkout -q --detach "$target"
    if ! start_copy "$idle" || ! activate "$idle" now; then
        compose stop "app-$idle" > /dev/null 2>&1 || true
        git -C "$REPO" checkout -q --detach "$COMMIT" || true
        die "the rollback failed; app-$ACTIVE keeps serving $(short "$from")"
    fi
    COMMIT=$target
    FAILED_TREE=$(server_tree "$from")
    save_state
    log "done: $(short "$target") is live. $(short "$from") is not deployed again until the branch changes server/, or you run: deploy.sh deploy"
}

command_status() {
    local idle rooms
    echo "branch:        $BRANCH"
    if [ -z "$ACTIVE" ]; then
        echo "nothing is deployed yet"
        return 0
    fi
    idle=$(other "$ACTIVE")
    rooms=$(open_rooms "$ACTIVE")
    echo "recorded:      ${COMMIT:-none} (shown at /version)"
    echo "serving:       app-$ACTIVE, built from $(short "$(commit_of "$ACTIVE")")"
    echo "standby:       app-$idle, built from $(short "$(commit_of "$idle")")"
    echo "through nginx: app-$(served_copy)"
    echo "open rooms:    ${rooms:-unknown}"
    if [ -n "$FAILED_TREE" ]; then echo "not retried:   server/ tree $(short "$FAILED_TREE") failed or was rolled back"; fi
    check_env > /dev/null
    if [ -s "$ENV_CHECK" ]; then sed 's/^/settings:      /' "$ENV_CHECK"; else echo "settings:      every value server/.env.example asks for is set"; fi
    compose ps -a
}

main() {
    local command=${1:-}
    if [ $# -gt 0 ]; then shift; fi
    case "$command" in
        auto | deploy | restart | rollback | status) ;;
        *)
            usage
            exit 2
            ;;
    esac

    [ -d "$REPO/.git" ] || die "$REPO is not a git checkout; see server/README.md, Deploy"
    [ -f "$ENV_FILE" ] || die "$ENV_FILE is missing; see server/README.md, Deploy"
    # Loading deploy.conf would overwrite a wait given for this one command, so that value is kept aside first.
    local wait_for_this_run=${MAX_WAIT_SECONDS:-}
    # shellcheck disable=SC1091
    if [ -f "$PIX_HOME/deploy.conf" ]; then . "$PIX_HOME/deploy.conf"; fi
    BRANCH=${BRANCH:-dev}
    HTTP_PORT=${HTTP_PORT:-80}
    MAX_WAIT_SECONDS=${wait_for_this_run:-${MAX_WAIT_SECONDS:-600}}
    PROJECT=${PROJECT:-pix}
    case "$MAX_WAIT_SECONDS" in
        "" | *[!0-9]*) die "MAX_WAIT_SECONDS must be a whole number of seconds, not '$MAX_WAIT_SECONDS'" ;;
    esac
    mkdir -p "$STATE_DIR" "$POSTGRES_DIR"
    load_state

    if [ "$command" != status ]; then
        # One deploy at a time: the lock is released when this process ends, however it ends.
        exec 9> "$PIX_HOME/deploy.lock"
        if command -v flock > /dev/null 2>&1 && ! flock -n 9; then
            if [ "$command" = auto ]; then exit 0; fi
            die "another deploy is running"
        fi
    fi

    case "$command" in
        auto) command_auto ;;
        deploy) command_deploy "$@" ;;
        restart) command_restart ;;
        rollback) command_rollback ;;
        status) command_status ;;
    esac
}

main "$@"; exit
