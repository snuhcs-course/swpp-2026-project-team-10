# AI-generated with Claude Code, 2026-10-14, reviewed by Jaewan Park
# shellcheck shell=bash
# Shortcuts for operating the Pix server on a course server (server/README.md, Deploy). It is not run but loaded
# into your shell, from the checkout that deploys update, so every account gets the same commands:
#
#   echo '[ -f /opt/pix/repo/server/deploy/pix.sh ] && . /opt/pix/repo/server/deploy/pix.sh' >> ~/.bashrc
#
# Then `pix help` lists the commands. Most of them use sudo, because only root reaches Docker and /opt/pix/.env.
# Keep the commands in step with deploy.sh.

pix() {
    local deploy=/opt/pix/repo/server/deploy/deploy.sh state=/opt/pix/state copy
    case "${1:-}" in
        status | deploy | restart | rollback)
            # sudo drops the environment, so the one setting deploy.sh reads from it is passed on by name.
            if [ -n "${MAX_WAIT_SECONDS:-}" ]; then
                sudo MAX_WAIT_SECONDS="$MAX_WAIT_SECONDS" "$deploy" "$@"
            else
                sudo "$deploy" "$@"
            fi
            ;;
        logs)
            copy=$(sed -n 's/^ACTIVE=//p' "$state/deploy.state" 2> /dev/null)
            if [ -z "$copy" ]; then
                echo "pix: nothing is deployed yet" >&2
                return 1
            fi
            sudo docker logs -f --tail "${2:-100}" "pix-app-$copy-1"
            ;;
        nginx) sudo docker logs -f --tail "${2:-100}" pix-nginx-1 ;;
        redis) sudo docker exec -it pix-redis-1 redis-cli ;;
        journal) sudo journalctl -u pix-deploy -n "${2:-50}" --no-pager ;;
        build) less +G "$state/last-build.log" ;;
        timer) systemctl list-timers pix-deploy.timer ;;
        env) sudoedit /opt/pix/.env && echo "Apply it with: pix restart" ;;
        health)
            curl -sS -m 5 -D - -o /dev/null http://127.0.0.1/health | grep -i -E '^HTTP|x-pix-copy'
            echo "version: $(curl -sS -m 5 http://127.0.0.1/version)"
            ;;
        help | -h | --help)
            cat << 'EOF'
pix: shortcuts for the Pix server on this machine (server/README.md, Deploy)

  pix status         what is deployed, which copy serves, open rooms, the containers
  pix deploy [ref]   deploy the branch now, or a given branch or commit
  pix rollback       switch back to the image that served before the last deploy
  pix restart        start the other copy from the same image, e.g. after pix env
  pix logs [n]       follow the serving app's log from its last n lines (100)
  pix nginx [n]      follow nginx's access log from its last n lines (100)
  pix journal [n]    the last n lines the timer's deploys printed (50)
  pix build          the last build log, opened at its end
  pix timer          when the deploy timer last ran and runs next
  pix env            edit /opt/pix/.env, then run pix restart to apply it
  pix health         /health and /version through nginx, and which copy answered
  pix redis          redis-cli in the session store; read only (SCARD sessions,
                     SMEMBERS workers): FLUSHALL or DEL would drop live rooms
  pix help           this list

MAX_WAIT_SECONDS=0 pix deploy switches without waiting for open rooms.
Ctrl-C stops pix logs and pix nginx. One deployment serves everyone on this
server: tell the team before a rollback, and never edit files in /opt/pix/repo.
EOF
            ;;
        "")
            echo "usage: pix status | deploy [ref] | restart | rollback | logs [n] | nginx [n] | journal [n] | build | timer | env | health | redis | help"
            echo "Run 'pix help' for what each one does."
            ;;
        *)
            echo "pix: unknown command '$1'. Run 'pix help' for the list." >&2
            return 2
            ;;
    esac
}
