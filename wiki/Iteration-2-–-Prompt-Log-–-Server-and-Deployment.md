Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

## Server deployment plan (#46)

- [Jaewan, 10-13] "I need to deploy our server on the course's provided servers (one for main, one for dev) (issue #46). I have a rough plan as below, can you see if this good, and tell me how I should do this? The servers are reachable in this mac by `ssh swpp-a` and `ssh swpp-b` but don't go into there on your own and make any changes there (install code run, etc.). […]"
  > Goal: continuous, zero-downtime deployment of our FastAPI server to the SWPP course servers using Docker + nginx + GitHub Actions.
  >
  > - Servers: Server A and Server B, both Ubuntu 24.04. Only port 80 is reachable from outside; no HTTPS, no IPv6. We SSH in through a relay with personal keys, which must NOT go into GitHub, so GitHub can't SSH in to deploy.
  > - Branch -> server: main -> Server A (production), dev -> Server B (staging). The two servers can't talk to each other, so each one runs independently with its own DB.
  > - CI on GitHub's hosted runners: run pytest, build the Docker image, push it to GHCR tagged with the commit SHA (ghcr.io/<owner>/<repo>:<sha>, lowercase).
  > - CD on a self-hosted GitHub Actions runner on each server, labeled swpp-a / swpp-b. It runs as a dedicated `deploy` user (in the docker group) and connects out to GitHub, so no inbound SSH is needed. The deploy job copies the repo's deploy/ folder into /opt/swpp (owned by `deploy`) and runs deploy.sh <image>.
  > - Server setup: install Docker with `apt install docker.io docker-compose-v2`. Do NOT install nginx on the host. nginx runs as a container bound to 0.0.0.0:80.
  > - Blue-green with docker compose: one nginx service, plus two copies of the app, app-blue and app-green. nginx proxies to whichever copy is named in an include file (nginx/upstream/active.conf). nginx looks up the containers through Docker's DNS (resolver 127.0.0.11) at request time, so it starts even when one copy is stopped.
  > - deploy.sh steps:
  >   1. Start the new image in the idle copy.
  >   2. Wait for it to report healthy (GET /health, with a Docker healthcheck).
  >   3. Rewrite active.conf to point at it, then run `nginx -t` and `nginx -s reload` (graceful, so requests in progress finish).
  >   4. Check through nginx that the new copy is answering (an X-Served-By header).
  >   5. Wait ~10s for old requests to drain, then stop the old copy. Keep it on disk so `deploy.sh rollback` is instant.
  >   6. If anything fails, live traffic is untouched (or switched back).
  >   It also needs `status` and `rollback` subcommands, and prunes this app's old images.
  > - Server limits to respect:
  >   - nproc shows 38 CPUs but we only get 8, so set uvicorn workers explicitly (WEB_CONCURRENCY).
  >   - 16 GiB RAM: both copies run briefly during a deploy.
  >   - 40 GiB disk: rotate Docker logs.
  >   - Requests over 120s get cut off with a 504.
  >   - Secrets are never committed.
- [Jaewan, 10-13] "do the first check by ssh-ing into swpp-b server. ask me before anything you need to install there"
- [Jaewan, 10-13] "i ran it"
- [Jaewan, 10-13] "I think we can just drop https or wss for this course project, just use without certificates. what about TURN? we can't use this in the provided servers?"
- [Jaewan, 10-13] "Okay let's use Cloudflare for TURN. And just one more check, is there no other way to use https/wss on these servers? Or should I try more?"
- [Jaewan, 10-13] "okay. now the websocket tests are over, now give the plan?"
- [Jaewan, 10-13] "we can't auto deploy on merge by github actions? and i don't think we are allowed to create new users. we may have to use just one member's user. and we can't do zero downtime deploy? that doesn't kill the live rooms?"
- [Jaewan, 10-13] "what if we do use redis?"
- [Jaewan, 10-13] "i think we should consider what happens when we scale up (using multiple workers; though we won't operate at scale for this course project) so it seems good to re-design with redis. let's update the design. then how should we do the deployment now? i installed docker-buildx on both servers, what should we do now"
- [Jaewan, 10-13] "there seems to be updates to the remote `dev` branch. can you merge them into this branch? and re-apply your fixes over them. but don't git add or commit your fixes"
- [Jaewan, 10-13] "now write the deployment files here"
- [Jaewan, 10-13] "what does android connect to? what if i want to connect to server B?"
- [Jaewan, 10-13] "is the dockerfile reasonably well-written? i don't want build too long or the image too large unnecessarily"
- [Jaewan, 10-13] "regarding redis use, how are we planning to store the room info there? and one more question, what about image generation tasks running during server switches? what if we use redis for this too?"
- [Jaewan, 10-13] "thanks. commit and push the current changes"
- [Jaewan, 10-14] "now that the branch is pushed to gh, what should i do at server b now?"
- [Jaewan, 10-14] "what do you mean about the invite link? we need to fix the design if we can't use HTTPS?"
- [Jaewan, 10-14] "how would the 'custom scheme' look like?"

## Redis setup (#73)

- [Jaewan, 10-14] "okay now add the redis container settings and implement the relevant code changes in this branch"
- [Jaewan, 10-14] "so what is the current behavior when switching servers? and what is to be changed on #56?"
- [Jaewan, 10-14] "commit and push this fix"
- [Jaewan, 10-14] "just remove the rejoin part from the issue"
- [Jaewan, 10-14] "the image build workflow failed"
- [Jaewan, 10-14] "commit and push"
- [Jaewan, 10-14] "there are updates in dev that change the design document. merge and solve conflicts, then commit and push"
- [Jaewan, 10-14] "before updating the testing document, first i have a review comment. can you read and fix what's wrong?"
- [Jaewan, 10-14] "commit and push this"
- [Jaewan, 10-14] "can you explain what was wrong and what you fixed?"
- [Jaewan, 10-14] "update the testing document now"
- [Jaewan, 10-14] "commit and push"

## Server shortcuts

- [Jaewan, 10-14] "i think i should register some aliases to the server, any recommendations?"
- [Jaewan, 10-14] "add a help option there"
- [Jaewan, 10-14] "save the shortcut as a script in the repo"
- [Jaewan, 10-14] "what does MAX_WAIT_SECONDS=0 do and what is the default? do we need it?"
- [Jaewan, 10-14] "what happens to the timer we set initially? this uses 600? if we change to 0 then will that be updated automatically?"
- [Jaewan, 10-14] "fix both of those in this branch"
- [Jaewan, 10-14] "commit and push"
- [Jaewan, 10-16] "when i ssh into the server, i get the error `-bash: /opt/pix/state/env-check: Permission denied pix: /opt/pix/.env needs attention (). Run pix env, then pix restart.`. what's wrong?"
