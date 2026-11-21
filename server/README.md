# Pix server

The Pix server is a FastAPI backend with three parts:

- **Session signaling:** room codes, the WebRTC SDP/ICE relay between one photographer and one subject, and STUN/TURN credentials from Cloudflare.
- **The pose generation REST API:** candidates from an image-editing model on [OpenRouter](https://openrouter.ai/), and poses and tips from a vision model.
- **Accounts and friends.**

Video, guide images, guide state, and remote control go directly between the phones over WebRTC. Rooms, room codes, presence, and the rate limits are in a Redis session store, so several worker processes can run ([Session store](#session-store)). A database holds the accounts, friend requests, the friend list, and invitations ([Database](#database)). Invitations and friend requests reach the friend's phone as push notifications through Firebase Cloud Messaging (FCM).

This README tells how to set up, run, test, deploy, and operate the server, and how to run its tools. The [Design Documentation][design] (2.5–2.8) gives the design. Room behavior follows FR-6 and FR-8 of the [Requirements and Specifications][rs].

## Setup

Install [uv](https://docs.astral.sh/uv/getting-started/installation/). Then run these commands from the repository root:

```sh
cd server
uv sync --locked
```

`.env.example` lists the optional settings. To change a default, copy the file to `.env` in `server/`, or export `PIX_` environment variables. If you change `PIX_MAX_MESSAGE_BYTES`, set Uvicorn's `--ws-max-size` to match.

Some features need a value in `.env` or in the environment:

- **Pose generation** needs `OPENROUTER_API_KEY`, an OpenRouter key with credits. Without it, the server still runs. `POST /poses` and `POST /pose-tips` answer `UPSTREAM_ERROR`, and `POST /pose-suggestions` answers the default poses. OpenRouter bills each generated image, so set a spending limit on the key in OpenRouter's dashboard.
- **Google sign-in** needs `PIX_GOOGLE_CLIENT_ID`, the web client ID that the app passes to Google sign-in. Without it, the Google endpoints answer `503 UNAVAILABLE`. Sign-up with a Pix ID and a password still works.
- **Sessions across networks** need `PIX_TURN_KEY_ID` and `PIX_TURN_API_TOKEN`, the TURN key of Cloudflare Realtime. With the key, each phone gets Cloudflare's STUN and TURN servers with its own credentials. The credentials are valid for `PIX_TURN_TTL_SECONDS` (4 h). Without the key, each phone gets Cloudflare's STUN server only. The same happens when Cloudflare fails or takes longer than `PIX_TURN_TIMEOUT_SECONDS` (3 s). The server logs only Cloudflare's status, never the token or a credential.
- **Push notifications** need `PIX_FCM_CREDENTIALS_FILE` ([Push notifications](#push-notifications)).

## Run it locally

From `server/`, start the server:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Started this way without `PIX_REDIS_URL`, the server keeps its rooms inside the process. So use **one worker**. A restart loses the rooms. Without `PIX_DATABASE_URL`, the accounts are in `pix.db`, a SQLite file in `server/` that stays after a restart. To start again with no accounts, delete the file. For development, you can use `--reload` instead of `--workers 1`, but a reload ends the open sessions.

To run several workers, or to keep rooms after a restart, give the server a Redis ([Session store](#session-store)). Several workers also need one `PIX_JWT_SECRET`. Without it, each process makes its own token key at start, so the workers refuse each other's access tokens. A restart also ends the access tokens, and the app refreshes them.

To connect a debug build to the laptop, set `pix.serverUrl` (`android/README.md`, *Server address*):

- From a phone on the same Wi-Fi, use `http://<laptop-IP>:8000/`. The Wi-Fi must allow device-to-device traffic, and the laptop must accept inbound TCP on port 8000.
- From an emulator, use `http://10.0.2.2:8000/`.

Without a TURN key, each phone gets Cloudflare's STUN server only, which is enough on one Wi-Fi ([Design 2.6.7][d267]). Across networks, give the server the TURN key ([Setup](#setup)), or use the AWS dev server. Debug builds use it by default.

Uvicorn's WebSocket ping and pong find dead connections. The protocol needs no heartbeat message of its own.

`GET /health` returns `{"status":"ok"}`. While the session store does not answer, it returns `503` with `{"status":"unavailable"}`.

## WebSocket protocol

Phones send JSON text frames to `/ws` on a persistent connection. [Design 2.5.2][d252] gives the messages, the error codes, and the rules for rooms, invitations' rooms, and rejoin tokens. Android's [SignalMessage.kt](../android/app/src/main/java/com/lastpenguin/pix/session/signaling/SignalMessage.kt) implements the phone's side. A room code is a string of six random digits, and it can start with zeroes. The server does not log request bodies, room codes, or signaling payloads.

The server closes a socket with these codes:

- **1009:** a text message larger than 64 KiB.
- **1013:** the socket's outgoing queue (`PIX_OUTBOUND_QUEUE_SIZE`, 128 messages) is full, or a send takes longer than `PIX_SEND_TIMEOUT_SECONDS` (5). This disconnects a slow client. The server also closes all its sockets with 1013 when the session store fails or restarts ([Design 2.7][d27]). The phone can connect again later.
- **1001:** the server stops.

## Testing the photographer's phone without a second phone

`tools/fake_subject.py` is a subject phone made of Python. It is a development tool and not part of the server. Its two dependencies are not in `pyproject.toml`. The script does these steps:

- It joins a room code and answers the WebRTC offer with [aiortc](https://github.com/aiortc/aiortc).
- It exchanges `hello` on the data channels and counts the video frames that it receives.
- It receives the photographer's guide image, reassembles it from its chunks, and checks it against the CRC. `--save-guide guide.png` keeps the image, as the `mask-png` that the app sends. `--have-guide <id>` reports an image that it already has.
- It prints every `guide.state`.
- On request, it sends a zoom (`--zoom`, as `camera.zoom.set`), a guide move (`--move cx,cy,height`: three steps, then the final value), a brightness index (`--exposure`), and a flash mode (`--flash`).
- It prints every echo, such as the `camera.state` that answers a zoom, with the delay since the request.

```sh
pip install websockets aiortc
python tools/fake_subject.py 482915 --seconds 20 --zoom 2.0
python tools/fake_subject.py 482915 --move 0.3,0.5,0.9 --exposure 2 --flash AUTO
```

To test the photographer's phone:

1. Start the server.
2. Open Pix on one phone or emulator. An emulator reaches the laptop at `http://10.0.2.2:8000/`.
3. Tap *Shoot together › I'll take the photo › Show a code*.
4. Give the code to `tools/fake_subject.py`, as in the commands above.

The phone shows its live light, and TalkBack reads "Live · fake-subject connected". The script reports the first video frame, a frame count, and the `camera.state` that answers the zoom request.

`tools/fake_photographer.py` is the other side. It also needs `pillow`. It creates a room and prints the code. When a phone joins, the script does these steps:

1. It sends a test-pattern video, the two data channels, `hello`, and `camera.capabilities`.
2. It sends the guide image from `--guide` as the app sends it, with its `guide.state`. The image is a `mask-png` of protocol 3: the alpha of a cutout, or a grayscale mask as it is, fitted within 1280 × 720 px.
3. For `--seconds`, it moves and resizes the guide as a drag would.
4. With `--clear`, it clears the guide at the end.

The script answers every zoom, brightness, and flash request with `camera.state`. It applies a `guide.move` to its guide and echoes `guide.state` with `by: SUBJECT`. Its own guide animation then stops.

To test Subject view with one phone or an emulator:

1. Run `python tools/fake_photographer.py --guide person.png`.
2. On the phone, tap *Shoot together › I'll be in the photo*.
3. Enter the code that the script printed, and tap *Connect*.

## REST API

On a laptop, the base URL is `http://<laptop-Wi-Fi-IP>:8000/api/v1`. On a server, it is `http://<host>/api/v1`. [Design 2.5.3][d253] gives every endpoint, its body, and its errors. Android's [PixApi.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/PixApi.kt) and [ApiModels.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/ApiModels.kt) implement the phone's side. An error has the form `{"error":{"code":"UNKNOWN_TEMPLATE","message":"No pose template with this id"}}`, but a `413` for a request over 1 MiB has a plain body.

The pose endpoints need no sign-in and take `multipart/form-data`. Each photo must be a JPEG with a long side of at most 1024 px and no EXIF. To try the endpoints without the app:

```sh
curl -F "image=@scene.jpg" http://localhost:8000/api/v1/pose-suggestions
curl -F "image=@scene.jpg" -F "description=벽에 기대서 카메라 보며 살짝 웃기" -F seed=1829304756 http://localhost:8000/api/v1/poses
curl -F "scene=@scene.jpg" -F "candidate=@candidate.jpg" -F "pose=Walk slowly past the railing" -F language=ko http://localhost:8000/api/v1/pose-tips
```

[Design 2.6.3][d263] gives the models and the prompts, and [Design 2.8][d28] (*Privacy*) gives the rate limits. Each model and each limit is a setting in `.env.example`. `PIX_POSE_MODEL` takes any OpenRouter image model id, with `@low`, `@medium`, or `@high` for a model with quality tiers. The prompts and the default poses are in [prompts.toml](src/pix_server/poses/prompts.toml), and the server does not start when this file is broken.

For a `502 UPSTREAM_ERROR`, the server log gives OpenRouter's status code, for example 402 when the credits run out. A pose or tip request that the phone cancels still counts toward the rate limit and leaves no line in the access log. When a phone loses its network without closing the connection, the server does not notice, and the timeout ends the call.

## Accounts and friends

The account and friend endpoints are in [`src/pix_server/accounts/`](src/pix_server/accounts/). [Design 2.5.3][d253] gives the endpoints, the bodies, the error codes, and the session tokens. [Design 2.8][d28] (*Accounts*) gives the password hashing and the token settings. Most of these endpoints need `Authorization: Bearer <access token>`.

`DELETE /me` deletes the caller's account with its sign-ins, devices, friendships, friend requests, and invitations, and frees its Pix ID ([Design 2.5.3][d253]). The app's *Account* sheet calls it after asking.

## Privacy policy

The privacy policy is [`src/pix_server/privacy/policy.ko.md`](src/pix_server/privacy/policy.ko.md) and [`policy.en.md`](src/pix_server/privacy/policy.en.md). The server renders both when it starts and serves them at `/privacy`, in Korean, the version that applies, and at `/privacy/en`. The store links to `https://swpp26t10.15-134-147-114.sslip.io/privacy`, and the app shows the same page in *Settings › Privacy* and on its sign-up screens. The wiki's *Privacy Policy* pages copy the two files: change all four together, and `tests/test_privacy.py` fails until the copies match.

## Invitations and push

The invitation endpoints are in [`src/pix_server/invitations/`](src/pix_server/invitations/). [Design 2.6.6][d266] gives the flow, and [Design 2.5.3][d253] gives the bodies and the errors. The invite link, `http://<host>/i/<id>`, opens a small page whose button passes the invitation to Pix.

### Push notifications

The server signs in to FCM as a service account of the app's Firebase project. It uses the account's key file, a secret that never goes into the repository. The setup has these steps:

1. In the Google Cloud console of the Firebase project, create the service account with only the role *Firebase Cloud Messaging API Admin*.
2. Add a JSON key to the account.
3. Locally, keep the key file outside the repository, and set `PIX_FCM_CREDENTIALS_FILE` to its path in `server/.env`.
4. On a server, copy the file with `scp`. Install it where Compose mounts it, readable only by the container's user (uid 10001). Then restart the server:

   ```sh
   sudo install -d -m 700 -o 10001 -g 10001 /opt/pix/secrets
   sudo install -m 400 -o 10001 -g 10001 fcm-sender.json /opt/pix/secrets/fcm-sender.json && rm fcm-sender.json
   sudo /opt/pix/repo/server/deploy/deploy.sh restart
   ```

Without the file, the server logs a warning at start and sends no pushes. Invitations still work through their link.

## Session store

[Design 2.7][d27] (*Session store*) gives the Redis keys, the Lua scripts, and the behavior when Redis fails. The code is in [`src/pix_server/store/`](src/pix_server/store/).

| `PIX_REDIS_URL` | Where the rooms are |
| --- | --- |
| Not set | Inside the server process, in [fakeredis](https://github.com/cunla/fakeredis-py), which runs the same scripts. This is for one worker on a laptop, with nothing to install. fakeredis is a development dependency, so the deployed image refuses to start without `PIX_REDIS_URL`. |
| `redis://<host>:6379/0` | In that Redis. On the servers, [`deploy/docker-compose.yml`](deploy/docker-compose.yml) sets it to its own `redis` service. To try several workers locally, `docker run --rm -p 6379:6379 redis:8-alpine` starts a Redis. |

A Redis restart loses the open rooms. The workers then continue with new rooms.

## Database

[Design 2.7][d27] gives the schema of the database. The server uses it through SQLAlchemy. The models are in [`db/models.py`](src/pix_server/db/models.py), and the Alembic migrations are in [`db/migrations/`](src/pix_server/db/migrations/).

| `PIX_DATABASE_URL` | Where the accounts are |
| --- | --- |
| Not set | `sqlite+aiosqlite:///pix.db`, a file in the working directory. For one worker on a laptop. |
| `postgresql+asyncpg://<user>:<password>@<host>:5432/<db>` | In that PostgreSQL. On the servers, the Compose file sets it to its own `postgres` service. |

Every worker brings the schema to the newest revision at start, before it takes requests. During a deploy, the old copy keeps serving on the new schema, so a revision must not break the release before it ([Design 2.7][d27]). `test_database.py` fails while the migrations and the models differ. To add a revision, do these steps from `server/`:

1. Change the models.
2. Run `uv run alembic upgrade head`.
3. Run `uv run alembic revision --autogenerate -m "what changed"`. Both commands use `PIX_DATABASE_URL`, or `pix.db`.
4. Read the generated file in `db/migrations/versions/`, and tidy it.

## Deploy

Pix has one server for `main` and one for `dev`. Each runs in two versions with the same setup: on a course server and on AWS ([Design 2.8][d28], *Hosting* and *Deployment*). Each course server is reachable only through the course's HTTP proxy on port 80, and only from the campus network. So the course servers have no HTTPS or WSS, and they cannot reach each other. The AWS servers stay on permanently, answer from every network, cellular data included, and serve HTTPS ([HTTPS](#https)). So the app's builds use the AWS servers.

| Branch | Course server | AWS server (`ap-southeast-2`) | Builds |
| --- | --- | --- | --- |
| `main` | Server A (`ssh swpp-a`), `http://swpp26t10.147-46-242-169.sslip.io` | AWS main, `https://swpp26t10.15-134-147-114.sslip.io` | Release builds use AWS main. |
| `dev` | Server B (`ssh swpp-b`), `http://swpp26t10.147-46-242-174.sslip.io` | AWS dev, `https://swpp26t10.3-105-185-251.sslip.io` | Debug builds use AWS dev by default. |

To use a course server, set `pix.serverUrl` in a debug build.

[`deploy/docker-compose.yml`](deploy/docker-compose.yml) runs these services:

- **nginx** on port 80, and on the AWS servers also on port 443 for HTTPS ([HTTPS](#https)). It forwards `/api/v1/`, `/ws`, `/health`, `/i/` (the invite link's page), and `/privacy` (the privacy policy) to the serving copy. It answers `/version` with the commit that is live, Let's Encrypt's check from `/opt/pix/acme`, and `404` for everything else. So FastAPI's `/docs` and the server's `/internal` routes are not public.
- **`app-blue` and `app-green`**, two copies of the server, built from the [`Dockerfile`](Dockerfile). One copy serves, and the other does not run. Each copy runs two workers.
- **Redis**, which both copies use. Its port is not published, and it writes nothing to disk.
- **PostgreSQL**, which both copies use. Its port is not published either. Its files are in `/opt/pix/postgres` on the server's own disk, and nothing makes a backup of them yet.

[Changing the deploy files](#changing-the-deploy-files) is about the secrets, that directory, and the routes.

### How a deploy happens

GitHub cannot push to the course servers. SSH goes through a campus-only relay with personal keys. A self-hosted runner needs repository admin rights, and GitHub advises against one on a public repository. So each course server pulls. GitHub deploys the AWS servers instead ([The AWS servers](#the-aws-servers)).

On a course server, a systemd timer runs [`deploy/deploy.sh auto`](deploy/deploy.sh) every minute as root. The script does these steps:

1. It runs `git fetch` for the server's branch. If the commit is already recorded, it stops.
2. If the commit does not change `server/`, it records the commit and restarts nothing.
3. It builds the image. pytest runs as a build stage, so a commit whose tests fail stops here.
4. It starts the image as the idle copy and waits for the container's health check, which also needs Redis. PostgreSQL must be healthy before the copy starts, and the copy migrates it at start. The first copy starts Redis and PostgreSQL, and later deploys leave them running.
5. It waits until the serving copy has no open room, for at most 10 minutes. A room counts until its photographer leaves, or until 60 s after its last socket dropped. [Design 2.8][d28] (*Deployment*) says why the script waits. After 10 minutes, the script continues anyway. Live sessions continue, because the video goes phone to phone, and rooms that still wait for a subject end.
6. It switches nginx to the new copy, reloads nginx, and checks `/health` through nginx. Then it stops the old copy and gives requests in flight 70 s. Uvicorn's graceful shutdown is 65 s, longer than a pose request may take.
7. It records the commit, which `/version` then shows, and deletes images other than the two copies' images.

The timer cannot deploy **a change that `deploy.sh` and the Compose file need together** the first time. An example is a new variable that `deploy.sh` must pass to Compose. The timer runs the `deploy.sh` of the commit that is already deployed, and the new Compose file refuses to start without the variable. That try fails safely, and the timer does not try it again. Deploy once by hand with the new script from the branch's newest commit (`main` on Server A):

```sh
sudo git -C /opt/pix/repo fetch -q origin dev
sudo git -C /opt/pix/repo show FETCH_HEAD:server/deploy/deploy.sh | sudo tee /tmp/pix-deploy.sh > /dev/null
sudo bash /tmp/pix-deploy.sh deploy
```

The PR that makes such a change says so.

A failure at any step leaves the serving copy as it was, and the timer does not try that `server/` tree again. To try again, fix the branch, or run `deploy.sh deploy`. On every pull request, the [server-image](../.github/workflows/server-image.yml) workflow builds the image and checks the routes through nginx. After a merge, the [server-deploy](../.github/workflows/server-deploy.yml) workflow waits until `/version` shows the commit, so a failed deploy shows as a red mark on the commit. This workflow is now disabled on GitHub, so it does not run.

### Changing the deploy files

- Keep `deploy.sh` runnable by bash 3.2, so you can try it on macOS with `PIX_HOME` set to a scratch directory. Keep everything in it inside functions, because a deploy checks out another version of the file while it runs.
- Operators source [`deploy/pix.sh`](deploy/pix.sh) and do not run it ([Operate it](#operate-it)). When a `deploy.sh` command or a container name changes, change it in `pix.sh` too.
- nginx forwards only `/api/v1/`, `/ws`, `/health`, `/i/` (the invite link's page), and `/privacy` (the privacy policy). Add a new public route to [`deploy/nginx/nginx.conf`](deploy/nginx/nginx.conf), but never add `/internal/*` to it. `deploy.sh` reads `/internal/stats` inside the container, to wait for open rooms before it switches copies. Its one server block serves both ports, so a route needs nothing more for HTTPS.
- HTTPS is [`docker-compose.https.yml`](deploy/docker-compose.https.yml) and [`deploy/nginx/https/`](deploy/nginx/https/). `deploy.sh` adds the Compose file only on a server with a certificate, so a course server never publishes port 443. Anything else that runs Compose on an AWS server passes both files, or nginx loses port 443.
- nginx's `pix` log format and the server's `HideQuery` filter omit query strings from the access logs. But nginx's error log prints the whole request line of a failed request. So nothing personal, such as a Pix ID, goes in a path or a query, and the lookup is `POST /users/lookup`.
- `deploy.sh` makes two secrets once and passes them to Compose. One is PostgreSQL's password (`/opt/pix/db-password`, `PIX_DB_PASSWORD`). PostgreSQL keeps the password that it got when it first created its files, so never generate that file again. The other is the access-token key (`/opt/pix/jwt-secret`, `PIX_JWT_SECRET`).
- PostgreSQL's files are bind-mounted from `/opt/pix/postgres` (`PIX_POSTGRES_DIR`), not kept in a Docker volume. So `down -v` and volume prunes cannot delete accounts. Never delete that directory. Anything that runs this Compose file sets `PIX_DB_PASSWORD`, `PIX_JWT_SECRET`, and `PIX_POSTGRES_DIR`, the [server-image](../.github/workflows/server-image.yml) workflow included.
- The `test` stage of the [`Dockerfile`](Dockerfile) copies `.env.example` for `tests/test_config.py`, which keeps it in step with `config.py`.

### Set up a server once

The server needs git, curl, and Docker with the Compose and Buildx plugins (`sudo apt install -y docker.io docker-compose-v2 docker-buildx`). Then, on the server:

```sh
sudo git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git /opt/pix/repo
sudo /opt/pix/repo/server/deploy/setup.sh dev        # the server's branch: main or dev
sudoedit /opt/pix/.env                               # fill in the names left empty
sudo /opt/pix/repo/server/deploy/deploy.sh deploy    # the first deploy, by hand
sudo systemctl enable --now pix-deploy.timer         # automatic deploys, on a course server only
```

[`setup.sh`](deploy/setup.sh) installs no packages and creates no users. It does these steps:

- It sets the branch in `/opt/pix/deploy.conf` and keeps any other setting there.
- It creates `/opt/pix/.env` from `.env.example`, readable by root only. It keeps an `.env` that exists.
- It creates `/opt/pix/state/`, and `/opt/pix/secrets/` for the FCM key file ([Push notifications](#push-notifications)).
- It installs the systemd units.

The API keys and tokens are only in `/opt/pix/.env`. The other secrets are the two files that `deploy.sh` makes and the FCM key file. `/opt/pix/state/` holds what `deploy.sh` writes. To try a branch before you merge it, give it to the first deploy: `deploy.sh deploy feat/2-deploy`.

### The AWS servers

Two AWS servers stay on permanently, one for each branch:

| Server | Instance | Elastic IP | Branch |
| --- | --- | --- | --- |
| AWS main | `i-0fc0336d74f394646` | `15.134.147.114` | `main` |
| AWS dev | `i-07f7e5dd7cc4cc9cc` | `3.105.185.251` | `dev` |

Each one's setup is the same as a course server's, with `setup.sh main` or `setup.sh dev`, but without the timer. GitHub can reach them, so the [server-deploy-aws](../.github/workflows/server-deploy-aws.yml) workflow deploys a branch's server after each push to `main` or `dev`:

1. The workflow has AWS Systems Manager run the `PixDeploy` document on the branch's server.
2. `PixDeploy` waits for any deploy that already runs there, and then runs `deploy.sh deploy`.
3. The workflow shows in the run what `deploy.sh` printed.
4. The workflow checks `/version` at the public address.

A failed deploy shows as a red mark on the commit. *Re-run jobs*, or *Run workflow* on `main` or `dev`, tries again, even for a `server/` tree that failed before.

The workflow signs in with the access key of an IAM user. That user may run only `PixDeploy`, and only on these two instances. `deploy.sh deploy` takes the branch from the server's own `deploy.conf`, so a leaked key can do no more than deploy each server's branch again. The key is a secret of the repository's `aws` environment, which only `main` and `dev` may use. The security group needs ports 80 and 443 open, plus SSH for operators.

Do this setup once in the AWS console, in the servers' region (`ap-southeast-2`):

1. **Let Systems Manager reach each server.** Create an IAM role for EC2 with the managed policy `AmazonSSMManagedInstanceCore`. Attach the role to each instance (*EC2 › Instances › Actions › Security › Modify IAM role*). Ubuntu and Amazon Linux images include the SSM agent. Within a few minutes, the instance shows as online in *Systems Manager › Fleet Manager*. If it does not, on Ubuntu, run `sudo snap restart amazon-ssm-agent`.
2. **Create the `PixDeploy` document** with this content. Use *Systems Manager › Documents › Create document › Command or Session*, type *Command*, format YAML. When a change to `deploy.sh` touches its command or its lock, change the document too:

   ```yaml
   schemaVersion: "2.2"
   description: Deploys the newest commit of the Pix server's branch with deploy.sh (server/README.md, The AWS server).
   mainSteps:
     - action: aws:runShellScript
       name: deploy
       inputs:
         timeoutSeconds: "2400"
         runCommand:
           - 'flock -w 900 /opt/pix/deploy.lock true || { echo "another deploy held the lock for 15 minutes" >&2; exit 1; }'
           - /opt/pix/repo/server/deploy/deploy.sh deploy
   ```

3. **Create the IAM user that the workflow signs in as**, `pix-github-deploy`, without console access. Give it this inline policy. The policy lets the user run `PixDeploy` on the two instances and read the output:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": "ssm:SendCommand",
         "Resource": [
           "arn:aws:ec2:ap-southeast-2:<account-id>:instance/<main-instance-id>",
           "arn:aws:ec2:ap-southeast-2:<account-id>:instance/<dev-instance-id>",
           "arn:aws:ssm:ap-southeast-2:<account-id>:document/PixDeploy"
         ]
       },
       { "Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*" }
     ]
   }
   ```

   Then create an access key for the user (*Security credentials › Create access key*, for an application running outside AWS).
4. **Store the key in GitHub.** This step needs a repository admin. In *Settings › Environments*, create the environment `aws`. Limit its deployment branches to `main` and `dev`. Add the secrets `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Then delete every other copy of the key.
5. **Give the workflow the instance IDs and addresses.** Put them in the workflow's `env`, by branch (`AWS_INSTANCE_ID`, `SERVER_URL`). They are not secrets.
6. **Disable the timer on each server** with `sudo systemctl disable --now pix-deploy.timer`. Then only the workflow deploys, and its log shows each build. If the timer stays on, deploys still work. But the run often shows only that the commit was already live.
7. **Set up HTTPS** on each server ([HTTPS](#https)).
8. **Send the logs to CloudWatch** ([Logs in CloudWatch](#logs-in-cloudwatch)).

To replace the key:

1. Create a second key.
2. Update the two secrets.
3. Delete the old key.

If you delete an AWS instance, also remove it from the IAM user's policy. Delete the IAM user with the last instance.

### HTTPS

The AWS servers serve HTTPS on port 443 with a certificate from [Let's Encrypt](https://letsencrypt.org/). The certificate names a hostname that [sslip.io](https://sslip.io/) resolves to the server's Elastic IP: `swpp26t10.15-134-147-114.sslip.io` resolves to `15.134.147.114`. Port 80 stays open, because Let's Encrypt checks the hostname over it.

- Every server's nginx serves Let's Encrypt's check, `/.well-known/acme-challenge/`, from `/opt/pix/acme`. The folder is empty on the course servers.
- Once `/etc/letsencrypt/live/pix/fullchain.pem` exists, `deploy.sh` adds [`docker-compose.https.yml`](deploy/docker-compose.https.yml) to each Compose command. That file publishes port 443 and mounts [`nginx/https/`](deploy/nginx/https/) and `/etc/letsencrypt`, so nginx also listens on 443 with the certificate. `deploy.sh status` shows `https: on` once the running nginx publishes port 443.
- On a server with the certificate, `deploy.sh` refuses to deploy or roll back to a commit without `docker-compose.https.yml`, which would close port 443. The commit that serves keeps serving.
- certbot's systemd timer renews the certificate before it expires. Its deploy hook, `deploy.sh reload`, then has nginx read the certificate again.
- nginx tells the app that a request came over HTTPS (`X-Forwarded-Proto`), so the app's invite links start with `https://`.

Set it up once on each AWS server, after a deploy of a commit that has these files:

1. In the security group, allow inbound TCP on port 443 from anywhere.
2. On the server, set `host` to the server's own hostname, then run the rest:

   ```sh
   host=swpp26t10.15-134-147-114.sslip.io   # AWS main; swpp26t10.3-105-185-251.sslip.io on AWS dev
   sudo apt install -y certbot
   sudo certbot certonly --webroot -w /opt/pix/acme --cert-name pix -d "$host" \
     --deploy-hook '/opt/pix/repo/server/deploy/deploy.sh reload' --register-unsafely-without-email --agree-tos
   sudo /opt/pix/repo/server/deploy/deploy.sh restart
   ```

   `restart` recreates nginx with port 443. To try the certbot line first against Let's Encrypt's test service, add `--dry-run`.
3. Check it from your computer: `curl https://<hostname>/health` answers `{"status":"ok"}`. On the server, `sudo certbot renew --dry-run` checks the renewal.

sslip.io is not on the Public Suffix List, so all sslip.io names share Let's Encrypt's limit of 50 new certificates in 7 days. If certbot is refused for that limit, try again later. A renewal of the same name is exempt.

### Logs in CloudWatch

The AWS servers also send the logs of the app's copies and of nginx to [CloudWatch Logs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/), in the servers' region. So the logs outlive a deploy, which recreates a copy's container and deletes its local log, and you can read and search them without SSH.

- Each server has one log group, `/pix/main` or `/pix/dev`, which keeps 30 days. nginx's access lines hold the phones' addresses, so keep that limit short.
- Each container writes to a stream named after it (`pix-app-blue-1`, `pix-app-green-1`, `pix-nginx-1`), which goes on after a deploy recreates the container.
- Docker keeps a local copy, so `pix logs` and `docker logs` still work. Redis and PostgreSQL keep their local log only: a change of their log driver would restart them, and a restart of Redis ends every room.
- `deploy.sh` adds [`docker-compose.cloudwatch.yml`](deploy/docker-compose.cloudwatch.yml) when `/opt/pix/deploy.conf` sets `CLOUDWATCH_GROUP` and `CLOUDWATCH_REGION`. Before each deploy, restart, or rollback, it checks that the server can make a log stream there, with a container that writes one line to the `deploy-check` stream. When that fails, it warns and keeps the logs on the server for that run. Docker reports a failed upload of a line only in its own journal (`journalctl -u docker`), so the check cannot prove that lines arrive: step 3 does. `deploy.sh status` says where nginx's log goes.
- An alarm emails you when nginx answers 5 or more requests with a 5xx status in 5 minutes. It cannot see a server that does not answer at all, which writes no log. No check from outside covers that.
- The server's own lines start with their level and logger, such as `WARNING pix_server.poses.generator: …`. Uvicorn's start with `INFO:` or `ERROR:`.

Set it up once:

1. **Make the log groups and the alarm.** Open CloudShell in the AWS console, in `ap-southeast-2`, and run this with the email addresses that get the alarm. Each address confirms its subscription from the email AWS sends, within three days.

   ```sh
   export AWS_REGION=ap-southeast-2
   topic=$(aws sns create-topic --name pix-alerts --query TopicArn --output text)
   for email in <address 1> <address 2>; do
     aws sns subscribe --topic-arn "$topic" --protocol email --notification-endpoint "$email"
   done
   for env in main dev; do
     aws logs create-log-group --log-group-name /pix/$env
     aws logs put-retention-policy --log-group-name /pix/$env --retention-in-days 30
     aws logs put-metric-filter --log-group-name /pix/$env --filter-name http-5xx \
       --filter-pattern '[ip, dash, user, time, request = *HTTP*, status = 5*, bytes, referer, agent]' \
       --metric-transformations metricName=Http5xx,metricNamespace=Pix/$env,metricValue=1,defaultValue=0
     aws cloudwatch put-metric-alarm --alarm-name pix-$env-5xx --namespace Pix/$env --metric-name Http5xx \
       --statistic Sum --period 300 --evaluation-periods 1 --threshold 5 \
       --comparison-operator GreaterThanOrEqualToThreshold --treat-missing-data notBreaching --alarm-actions "$topic"
   done
   ```

   To add an address later, run the `topic=` line, which returns the topic that exists, and the `subscribe` line. `aws sns list-subscriptions-by-topic --topic-arn "$topic"` lists the addresses. Every alarm email has a link that unsubscribes its address.

   To check the filter against one of nginx's lines:

   ```sh
   aws logs test-metric-filter --filter-pattern '[ip, dash, user, time, request = *HTTP*, status = 5*, bytes, referer, agent]' \
     --log-event-messages '203.0.113.7 - - [21/Nov/2026:10:00:00 +0000] "GET /health HTTP/1.1" 502 157 "-" "curl/8.5"'
   ```

2. **Let the servers write there.** In *IAM › Roles*, open the role attached to the instances (*The AWS servers*, step 1). Choose *Add permissions › Create inline policy*, paste this JSON with your account ID, and name the policy `pix-cloudwatch-logs`:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": ["logs:CreateLogStream", "logs:PutLogEvents"],
         "Resource": "arn:aws:logs:ap-southeast-2:<account-id>:log-group:/pix/*"
       }
     ]
   }
   ```

3. **Turn it on, on each server**, once it runs a commit with these files. Use `/pix/dev` on AWS dev. `restart` recreates nginx, which drops the open sockets for a moment; phones in a session take their place again.

   ```sh
   echo 'CLOUDWATCH_GROUP=/pix/main' | sudo tee -a /opt/pix/deploy.conf
   echo 'CLOUDWATCH_REGION=ap-southeast-2' | sudo tee -a /opt/pix/deploy.conf
   sudo /opt/pix/repo/server/deploy/deploy.sh restart
   sudo /opt/pix/repo/server/deploy/deploy.sh status    # logs: CloudWatch, log group /pix/main, and this server
   ```

   Then open *CloudWatch › Log groups › /pix/main* and the `pix-nginx-1` stream: after any request, its line is there, which shows that the server can write to the group.

To read the logs, open *CloudWatch › Log groups › /pix/main*, or search with *Logs Insights*:

```text
fields @timestamp, @logStream, @message
| filter @message like /ERROR|WARNING|Traceback/
| sort @timestamp desc
```

### Settings

[`.env.example`](.env.example) lists every setting, and a test keeps it in step with [`config.py`](src/pix_server/config.py). A name with no value, such as `OPENROUTER_API_KEY=`, `PIX_GOOGLE_CLIENT_ID=`, or `PIX_TURN_API_TOKEN=`, needs a value on every server. Every other name is in a comment. Its line shows the server's default, or says what sets the value. Uncomment a name in `/opt/pix/.env` only to give a server another value. Then a default that changes in the code still reaches the server.

Each deploy and each `deploy.sh restart` compare `/opt/pix/.env` with the `.env.example` of the commit that they start. They never change either file. They warn about two kinds of names:

- A name that the example leaves empty and that `.env` has no value for.
- A name that `.env` sets and that the example does not list. The name is probably misspelled, because the server ignores names that it does not know.

`pix status` shows the result, and an operator sees it in one line at login. The server also logs at start which features are off. To set a value, run `pix env`, then `pix restart`. On a server set up before 2026-10-15, `/opt/pix/.env` has every default uncommented, copied from the old example. Those lines pin the defaults of that time, so delete the lines that you did not mean to change.

### Operate it

[`deploy/pix.sh`](deploy/pix.sh) gives each account on a server a `pix` command for the operations below. Load it once from your `~/.bashrc`. Load it from the checkout that deploys update, so everyone gets the same version:

```sh
echo '[ -f /opt/pix/repo/server/deploy/pix.sh ] && . /opt/pix/repo/server/deploy/pix.sh' >> ~/.bashrc
```

`pix help` lists the commands: `pix status`, `pix deploy [ref]`, `pix rollback`, `pix restart`, `pix logs` for the serving copy, `pix nginx`, `pix journal`, `pix build`, `pix timer`, `pix env`, `pix health`, `pix redis`, and `pix db`. `pix status` also lists the settings that `/opt/pix/.env` lacks ([Settings](#settings)). The commands need an account that can use `sudo`. One deployment serves everyone on a server, so tell the team before a rollback. Never edit files in `/opt/pix/repo`, where each deploy checks out its commit.

Without the shortcuts, run these commands on the server with `sudo`:

| Command | What it does |
| --- | --- |
| `/opt/pix/repo/server/deploy/deploy.sh status` | The branch, the recorded commit, which copy serves, whether HTTPS is on, where the logs go, the open rooms, and the containers. |
| `… deploy.sh deploy [ref]` | Deploys a branch or a commit now. The default is the server's branch. It also deploys a commit that the timer stopped trying. |
| `… deploy.sh rollback` | Switches back to the image that served before the last deploy, without a wait for rooms. The timer does not deploy the rolled-back code again until the branch changes `server/`. |
| `… deploy.sh reload` | Has nginx read its configuration and the certificate again, after it checks them. certbot's deploy hook runs it after a renewal ([HTTPS](#https)). |
| `… deploy.sh restart` | Starts the other copy from the same image, for example after you edit `/opt/pix/.env`. |
| `journalctl -u pix-deploy -n 50` | What the timer's deploys printed. `/opt/pix/state/last-build.log` has the last build in full. |
| `docker logs -n 100 pix-app-blue-1` | The server's log. Use `pix-app-green-1` for the other copy, `pix-nginx-1` for the access log, `pix-redis-1` for the session store, and `pix-postgres-1` for the database. Logs rotate at 10 MB, five files each. On the AWS servers, CloudWatch keeps the app's and nginx's logs too ([Logs in CloudWatch](#logs-in-cloudwatch)). |

A deploy waits at most `MAX_WAIT_SECONDS` (600) for open rooms. To switch without a wait for one run, put `MAX_WAIT_SECONDS=0` in front of a `deploy.sh` command (after `sudo`) or in front of `pix deploy`. To change the limit for every deploy on a server, the timer's included, put `MAX_WAIT_SECONDS=<seconds>` in `/opt/pix/deploy.conf`. The timer's next run uses it. A value in front of a command still wins for that run.

Docker Hub limits anonymous pulls per address, and the course servers share one address. If a build fails with `toomanyrequests`, wait, then run `deploy.sh deploy`. After the first pull, the server keeps the base images in its cache.

### What the course's proxy does

[Design 2.8][d28] (*Hosting*) gives what we measured on Server B on 2026-10-13. Two results set how nginx and the server work:

- **Long-lived WebSockets.** The course cuts a plain HTTP response after 120 s, but WebSockets stay open. Uvicorn pings every 20 s, so nginx's 75 s read timeout does not close a quiet socket.
- **The phone's address.** nginx takes only the last `X-Forwarded-For` value, which the proxy adds, and replaces the header on the way to the server. Uvicorn trusts the header (`--forwarded-allow-ips "*"`), because only nginx can reach its port. So the rate limit counts per phone address. Phones behind one carrier or Wi-Fi gateway still share an address.

## Lint, format, and test

Run these commands from `server/` before you push. On every pull request, the [server-lint](../.github/workflows/server-lint.yml) workflow runs the two checks, and the [server-test](../.github/workflows/server-test.yml) workflow runs the tests.

| Command | What it does |
| --- | --- |
| `uv run ruff format .` | Formats Python files with Ruff. |
| `uv run ruff format --check .` | Fails if a file is not formatted. |
| `uv run ruff check .` | Ruff lint. Add `--fix` to apply the fixes that Ruff can make, such as import order. |
| `uv run pytest` | Tests in `tests/`. They run in one process, without a running server, a Redis, a database server, or a phone. |
| `uv run pytest --cov=pix_server --cov-branch --cov-report=term-missing --cov-report=html` | The same tests with line and branch coverage. It prints a summary in the terminal and writes `htmlcov/index.html`. The omit list is in `pyproject.toml`. The targets are in the [Testing Documentation][testing] (2.1.2–2.1.4). |
| `PIX_TEST_REDIS_URL=redis://localhost:6379/0 PIX_TEST_DATABASE_URL=postgresql+asyncpg://pix:pix@localhost:5432/pix uv run pytest` | The same tests on a real Redis and PostgreSQL, which they empty first (Redis's database 0, PostgreSQL's `public` schema). Either variable works alone. CI runs both ways. |

- **Rules.** Ruff rules are in `ruff.toml`: a 120-character line limit and the lint rule sets `E4`, `E7`, `E9`, `F`, and `I`.
- **Suppressing.** For one line, use `# noqa: <rule>` with a comment that says why. To disable a rule for some files or for the whole server, add it to `per-file-ignores` or `ignore` in `ruff.toml`.
- **Tests.** Put tests in `tests/test_<module>.py`. Add tests in the pull request that writes the code. `async def` tests need no marker, and a test fails after 15 seconds.
- **Fixtures.** `tests/conftest.py` gives a server whose clock the test moves (`client`, `settings`, `clock`), fakes of OpenRouter, Cloudflare, Google, FCM, and Redis, and a new SQLite file for each test. So tests need no key. `make_image` builds test photos. [Testing 2.1.8][t218] lists each fixture and how a test controls it.

## AI scripts

The scripts in [`scripts/`](scripts/) try the models and the prompts of the server, and they run the AI module tests. They are not part of the server, and they do not import `pix_server`. Run them from `server/`:

- **Key.** A script reads `OPENROUTER_API_KEY` from the environment. `uv run --env-file .env` passes the key in `server/.env`. You can also export the key in your shell.
- **Cost.** Each model call costs money on the OpenRouter key, so try a small run first.
- **Privacy.** The scripts send the photos to OpenRouter and to the model's provider. The results go to `scripts/out/`, which git ignores. Do not commit them.
- **Prompts.** The scripts read the server's [prompts.toml](src/pix_server/poses/prompts.toml), so a run tries what the server sends.
- **Options.** The docstring at the top of each script says what it does and what it writes. `--help` lists the options, such as another prompt to try.

### Compare image-editing models

[`compare_image_edit_apis.py`](scripts/compare_image_edit_apis.py) sends scene photos to image-editing models. Its `report.html` shows the latency, the cost, and the images side by side. With no options, each photo goes to the four models of `DEFAULT_MODELS` with the four default poses. That is 16 images per photo, about $0.50 in October 2026. `--list-models` lists the models and their prices, and costs nothing. Use JPEG, PNG, or WebP photos with the whole person in the frame. HEIC does not open.

```sh
uv run scripts/compare_image_edit_apis.py photo1.jpg photo2.jpg

# Smoke test: one image from one model
uv run scripts/compare_image_edit_apis.py photo.jpg --models bytedance-seed/seedream-5-0-flash --poses wave

# Many photos, two models, several combinations at once
uv run scripts/compare_image_edit_apis.py photos/*.jpg --models google/gemini-3.1-flash-image bytedance-seed/seedream-5-0-flash --concurrency 4

# A free-text pose
uv run scripts/compare_image_edit_apis.py photo.jpg --pose-text "벽에 기대서 한 손은 주머니에"
```

### Compare suggestion models

[`compare_suggestion_models.py`](scripts/compare_suggestion_models.py) sends scene photos with the server's suggestion prompt to vision models. With `--description`, it sends the variation prompt for that description. For each model, it reports the latency, the cost, and how many answers the server could use. 240 calls cost about $0.05 in October 2026.

```sh
uv run scripts/compare_suggestion_models.py photo1.jpg photo2.jpg                          # ten models, two rounds
uv run scripts/compare_suggestion_models.py photo.jpg --models google/gemini-3.1-flash-lite z-ai/glm-5.3-flash --rounds 3
uv run scripts/compare_suggestion_models.py photo.jpg --models google/gemini-3.1-flash-lite --description "점프하기"
```

### Compare filter models

[`compare_filter_models.py`](scripts/compare_filter_models.py) sends the labeled descriptions of [`filter_cases.toml`](scripts/filter_cases.toml), in English and Korean, to decision models with the two questions of the description filter. For each model and language, it reports the harmless descriptions that the model refuses and the attacks that it catches at each cutoff.

```sh
uv run --env-file .env scripts/compare_filter_models.py                                   # seven models, two rounds
uv run --env-file .env scripts/compare_filter_models.py --models typesafe/jev-1.14 --rounds 1
uv run --env-file .env scripts/compare_filter_models.py --cases my_cases.toml --label teammates
```

### Try the person check

[`try_person_check.py`](scripts/try_person_check.py) sends labeled scene photos to a decision model with the question of the person check (FR-4.13). Give the scenes with a person after `--with-person`, and the scenes without one after `--without-person`, as files or folders. At `--cutoff`, it prints how many scenes with a person the check would refuse, and how many scenes without one it would let through.

```sh
uv run --env-file .env scripts/try_person_check.py --with-person people/ --without-person empty/
uv run --env-file .env scripts/try_person_check.py --with-person people/ --without-person empty/ --rounds 2 --cutoff 0.4
uv run --env-file .env scripts/try_person_check.py --without-person empty/ --prompts my_prompts.toml --label wording2
```

### Try pose tips

[`try_pose_tips.py`](scripts/try_pose_tips.py) tries the tip prompt on scene photos, with candidates that it makes once. So you can change the prompt and run it again without paying for new images. Like the server, it asks about the scene and the candidate in two requests and writes the tip's sentences from the two answers; `--prompt-file` replaces `tip_prompt`, which both requests get.

```sh
uv run --env-file .env scripts/try_pose_tips.py candidates photos/*.jpg      # one candidate per scene, made once
uv run --env-file .env scripts/try_pose_tips.py set photo.jpg --name k01     # four candidates for one scene, to pick one
uv run --env-file .env scripts/try_pose_tips.py tips --rounds 2              # a tip for every pair, twice
uv run --env-file .env scripts/try_pose_tips.py tips --language ko --label ko # the tips in Korean, as for a phone in Korean
uv run --env-file .env scripts/try_pose_tips.py tips --prompt-file my_prompt.txt --only k01-4 --label try2
```

### Evaluate the AI modules

[`eval_poses.py`](scripts/eval_poses.py) runs the AI module tests on the frozen set in [`eval/`](eval/). [Testing 3.1][t31] gives the method, the rules, and the thresholds. `judge` reads `ANTHROPIC_API_KEY` and sends the photos to Anthropic.

A run needs a server with high enough limits for 100 sets, 400 candidates, and 100 tips. Start one locally, with the key in `server/.env` and a database of its own. Then run the steps in this order:

```sh
mkdir -p scripts/out/eval && PIX_POSE_RATE_LIMIT=5000 PIX_POSE_GLOBAL_LIMIT=5000 PIX_SUGGESTION_RATE_LIMIT=5000 PIX_SUGGESTION_GLOBAL_LIMIT=5000 \
  PIX_TIP_RATE_LIMIT=5000 PIX_TIP_GLOBAL_LIMIT=5000 PIX_DATABASE_URL=sqlite+aiosqlite:///scripts/out/eval/pix.db \
  uv run --env-file .env uvicorn pix_server.main:app --port 8765 --workers 1     # in its own terminal
uv run scripts/eval_poses.py generate --run pilot --limit 10                   # a pilot: the first 10 cases
uv run --env-file .env scripts/eval_poses.py judge pilot --now                 # direct calls, for results in minutes
uv run scripts/eval_poses.py generate --run run1                               # a full run; an interrupted one continues
uv run --env-file .env scripts/eval_poses.py judge run1                        # the Batch API; waits for the results
uv run scripts/eval_poses.py report run1 run2 run3                             # each run, then all together
```

`judge` sends only what has no verdict with the current rubric, so a changed rubric judges a whole run again. It judges the poses and the suggestions; the tips are checked by us, below. `report` writes `summary.txt`, `summary.json`, and `report.html`.

After a change to the tip's prompt or words, ask a run's tips again for the same candidates, without paying for new images. We check the tips ourselves, not the judge (Testing 3.1): `sample --kind tip` writes `label-tips.html`, each of us answers every sentence in a browser and downloads the labels, and `agree --kind tip` counts the tips that match their pose into `tip-check.txt` and `tip-check.json`, where `report` reads the tips' metric. A reading of the run's other tips through Claude Code (Testing 3.1) goes to `tip-check-rest.json` in the same shape, and `report` gives it on a line of its own. The labels must be downloaded from the page of the current sample: `agree` refuses those of another run or of the tips before a `retip`, and the page keeps a labeler's answers apart for each sample.

```sh
uv run scripts/eval_poses.py retip run1                                        # keeps the tips before in each set
uv run scripts/eval_poses.py retip run1 --failed                               # only the sets whose tip request failed
uv run scripts/eval_poses.py sample run1 --kind tip --size 50                  # label-tips.html with 50 of the run's tips
uv run scripts/eval_poses.py agree run1 --kind tip --labels labels-tips-*.json # from our downloaded answers
```

To compare the situation prompts with the default ones (Testing 3.1, *Situations*), run the situation cases with their own situations and with `default`, then judge and report both. `--situation` gives every case but the selfies that situation:

```sh
uv run scripts/eval_poses.py generate --cases eval/situation_cases.json --run situ-own
uv run scripts/eval_poses.py generate --cases eval/situation_cases.json --run situ-default --situation default
uv run scripts/eval_poses.py generate --cases eval/situation_cases.json --run situ-x-landmark --situation landmark --only m1 h1 --modes suggested
```

To check the judge (Testing 3.1, *Judge validation*):

1. `sample` picks 60 candidates of a judged run into `label.html`: half that the judge failed, and half that it passed.
2. Each labeler opens `label.html` in a browser and answers every rule without the judge's verdicts. Then the labeler downloads the labels.
3. Keep the labels in `eval/labels/` as `<run>-<name>.json`.
4. `judge --repeat 3` judges the same 60 candidates twice more.
5. `agree` compares the labels with each other and with the judge, and counts the verdicts that changed across the three judgments.

```sh
uv run scripts/eval_poses.py sample run1 --size 60
uv run --env-file .env scripts/eval_poses.py judge run1 --repeat 3
uv run scripts/eval_poses.py agree run2 --labels eval/labels/run2-*.json      # the labels are kept in eval/labels/
```

To try a changed rubric, `judge --sample` judges only those 60 candidates. `sample --by-rubric HASH` splits a new sample by an earlier rubric's verdicts, to check the changed rubric on the cases that the old rubric failed.

The filter and the person check have their own held-out sets: `eval/filter_cases.toml`, and the photos of `eval/person/with/` and `eval/person/without/` with their labels in `eval/person/labels.json`. The phone's person check runs on a device with the app's `PersonCheckEval` (`android/app/src/androidTest/`):

1. Install the app and its test APK.
2. Push the photos to `/data/local/tmp`.
3. Start the test with `adb shell am instrument`.
4. Pull `phone.json`.

Do not use Gradle's `connectedDebugAndroidTest`, because it would uninstall the app, and the file with it. `eval_poses.py person` puts both checks together, as the app runs them.

```sh
(cd ../android && ./gradlew :app:installDebug :app:installDebugAndroidTest)          # one device connected
adb push eval/person/. /data/local/tmp/pix-eval-person/
adb shell am instrument -w -e class com.lastpenguin.pix.generation.PersonCheckEval com.lastpenguin.pix.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.lastpenguin.pix/files/phone.json scripts/out/person/phone.json
uv run --env-file .env scripts/compare_filter_models.py --cases eval/filter_cases.toml --models typesafe/jev-1.14 --rounds 1 --label heldout
uv run --env-file .env scripts/try_person_check.py --with-person eval/person/with --without-person eval/person/without --label heldout
uv run scripts/eval_poses.py person --phone scripts/out/person/phone.json --jev scripts/out/person/<timestamp>-heldout/results.json
```

A full run's images cost an estimated $5 through OpenRouter. Its judgments cost about $7.50 with the Batch API, an estimate from the 10-case pilot of 2026-11-05. In that pilot, with direct calls, the judge cost about $0.030 for a pose, $0.018 for a tip, and $0.008 for a set of suggestions. A batch costs half that.

[design]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation
[d252]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#252-signaling-protocol-phone--server-websocket
[d253]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#253-rest-api-phone--server
[d263]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#263-pose-generation
[d266]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#266-invitations-and-push-iteration-3
[d267]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#267-reconnection-and-sessions-across-networks-iteration-3
[d27]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#27-database
[d28]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#28-implementation-decisions
[rs]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications
[testing]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation
[t218]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#218-writing-a-test-the-fakes-and-the-pitfalls
[t31]: https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#31-plan
