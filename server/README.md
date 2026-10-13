# Pix server

FastAPI backend for Pix. The server's responsibilities include session signaling, pose generation, and planned APIs for friend invitations and other features as the project develops.

The current implementation provides iteration 1 signaling (anonymous room-code sessions and WebRTC SDP/ICE relay between one photographer and one subject) and the pose generation REST API, which turns a scene photo into pose candidates through an image-editing model on [OpenRouter](https://openrouter.ai/). Video, guide images, guide state, and remote zoom go directly between the phones over WebRTC. Database support and account and friend APIs are not implemented yet.

The wire contract comes from [Design Documentation §2.5.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation) and Android's [SignalMessage.kt](../android/app/src/main/java/com/lastpenguin/pix/session/signaling/SignalMessage.kt). Room behavior follows [FR-8 and FR-6](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications). The REST contract comes from Design Documentation §2.5.3 and Android's [PixApi.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/PixApi.kt) and [ApiModels.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/ApiModels.kt); pose generation follows §2.6.3 and FR-4.

## Setup

Install [uv](https://docs.astral.sh/uv/getting-started/installation/), then run these commands from the repository root:

```sh
cd server
uv sync --locked
```

Optional settings are listed in `.env.example`. Copy it to `.env` in `server/` to override defaults, or export `PIX_` environment variables. The default waiting-room TTL is 600 seconds, empty-session TTL is 60 seconds, and cleanup runs every second. Expiry is also checked during message handling. If changing `PIX_MAX_MESSAGE_BYTES`, keep Uvicorn's `--ws-max-size` consistent.

Pose generation needs `OPENROUTER_API_KEY`, an OpenRouter key with credits, in `.env` or the environment. Without it the server still runs, and `POST /poses` answers `UPSTREAM_ERROR`.

## Run on the test Wi-Fi

From `server/`, start the server:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Use **one worker and one server instance**: room membership is process-local and is lost when the server restarts. For development, `--reload` can replace `--workers 1`; a reload ends existing sessions.

Put the laptop and both phones on the same Wi-Fi. Connect Android to `ws://<laptop-Wi-Fi-IP>:8000/ws` for signaling and `http://<laptop-Wi-Fi-IP>:8000/api/v1` for the REST API (a phone's `localhost` refers to that phone). The network must allow device-to-device traffic and inbound TCP port 8000 on the laptop. Iteration 1 uses the debug-build HTTP/WS exception in Design §2.8. `iceServers` is always `[]`; STUN/TURN and cellular connectivity belong to later iterations.

Uvicorn's WebSocket ping/pong detects dead connections; no application heartbeat message is required.

`GET /health` returns `{"status":"ok"}`.

## WebSocket protocol

Send JSON **text** frames to `/ws` on a persistent connection. A socket belongs to at most one room. Messages and field casing match the Android contract.

| Client message | Result |
| --- | --- |
| `{"type":"room.create"}` | Sender gets `{"type":"room.created","code":"482915","sessionId":"s_…","iceServers":[]}`. |
| `{"type":"room.join","code":"482915"}` | Subject gets `{"type":"session.joined","sessionId":"s_…","role":"SUBJECT","iceServers":[]}`; photographer gets `{"type":"peer.joined"}`. |
| `{"type":"signal","sdp":{"type":"offer","sdp":"v=0\r\n…"}}` | Forwarded unchanged to the subject. Only the photographer sends offers; only the subject sends answers. |
| `{"type":"signal","candidate":{"sdpMid":"0","sdpMLineIndex":0,"candidate":"candidate:…"}}` | Forwarded unchanged to the other phone. `sdpMid` may be null; an empty candidate marks the end of candidates. |
| `{"type":"leave"}` | Other phone gets `{"type":"peer.left","reason":"PEER_LEFT"}`. There is no acknowledgment to the sender. |

A `signal` has exactly one non-null `sdp` or `candidate`; the unused field may be omitted or null, as in Android serialization. Unexpected fields are rejected. Neither display names nor media are exchanged through this hub.

Room codes are random six-digit strings, including possible leading zeroes, and unique among active/recently expired codes. An unjoined room expires after ten minutes; its photographer receives an `EXPIRED` error and can create a new room on the same socket. Once joined, a session does not expire at that deadline.

An explicit photographer `leave` cancels the room immediately and releases both sockets. A subject `leave` releases that subject and notifies the photographer; the room remains available for a manual join while the photographer is still connected. On an unexpected socket disconnect, the remaining peer receives `peer.left` with reason `CONNECTION_LOST`. A room without a photographer cannot be joined. Sessions are removed 60 seconds after their last socket disconnects (or earlier if an unjoined code expires). Iteration 1 has no photographer resume protocol; after losing that socket, create a new room.

Errors have the form `{"type":"error","code":"NOT_FOUND","message":"No active room with this code"}`.

| Code | Meaning |
| --- | --- |
| `NOT_FOUND` | Unknown/cancelled room, or photographer disconnected. |
| `FULL` | A subject already occupies the room, or no code could be allocated. |
| `EXPIRED` | Room expired. Codes are remembered for another ten minutes, then report `NOT_FOUND`. |
| `UNKNOWN_TYPE` | Unrecognized message type. |
| `INVALID_MESSAGE` | Malformed JSON, binary frame, invalid fields, or invalid signaling payload. |
| `INVALID_STATE` | Already in a room, signaling before joining, or SDP from the wrong role. |
| `PEER_UNAVAILABLE` | No connected counterpart to receive a signal. |

Errors leave the socket usable. Text messages larger than 64 KiB close the socket with code 1009. Slow consumers have a bounded outgoing queue; queue overflow or a five-second send timeout disconnects that socket with code 1013. Request bodies, room codes, and signaling payloads are not logged by the app.

## Testing the photographer's phone without a second phone

`tools/fake_subject.py` is a subject phone made of Python. It joins a room code, answers the WebRTC offer with [aiortc](https://github.com/aiortc/aiortc), exchanges `hello` on the data channels, counts the video frames it receives, receives the photographer's guide image (reassembled from its chunks and checked against the CRC; `--save-guide guide.webp` keeps it, `--have-guide <id>` reports an image it already has), prints every `guide.state`, optionally sends `camera.zoom.set`, and prints the echoed `camera.state` with its delay. It is a development tool and not part of the server; its two dependencies are not in `pyproject.toml`.

```sh
pip install websockets aiortc
python tools/fake_subject.py 482915 --seconds 20 --zoom 2.0
```

`tools/fake_photographer.py` is the other side: it creates a room and prints the code; once a phone joins it sends a test-pattern video, the two data channels, `hello`, `camera.capabilities`, the guide image from `--guide` (a WebP cutout with alpha, at most 720 px tall) with its `guide.state`, then moves and resizes the guide for `--seconds` as a drag would, `--clear` removes it at the end, and every `camera.zoom.set` is echoed as `camera.state`. Use it to test Subject view with one phone or an emulator: `python tools/fake_photographer.py --guide cutout.webp`, then *Shoot together › Join with a code instead* on the phone (it needs `pillow` as well).

Start the server, open Pix on one phone or emulator (an emulator reaches the laptop at `http://10.0.2.2:8000/`), tap *Shoot together*, and pass the code. The phone shows "Live · fake-subject connected", and the script reports the first video frame, a frame count, and the `camera.state` that answers the zoom request.

## REST API

The base URL is `http://<laptop-Wi-Fi-IP>:8000/api/v1`.

| Endpoint | Result |
| --- | --- |
| `GET /pose-templates` | `[{"id":"hands_on_hips","label":"Hands on hips"}, …]` with the four templates `hands_on_hips`, `wave`, `walking`, and `arms_crossed`. The pose descriptions stay on the server. |
| `POST /poses` | One candidate for one template: `{"templateId":"wave","image":"<Base64 JPEG>","elapsedMs":9961}`. The app sends one request per template in parallel. |

`POST /poses` takes `multipart/form-data` with three parts: `image` (a JPEG with a long side of at most 1024 px and no EXIF), `templateId`, and `seed` (an integer). The whole request may be at most 1 MiB; a photo prepared as Design §2.6.3 describes is a few hundred KB.

```sh
curl -F "image=@scene.jpg" -F templateId=wave -F seed=1829304756 http://localhost:8000/api/v1/poses
```

The server wraps the template's pose description in the prompt in [templates.py](src/pix_server/poses/templates.py) and sends it with the photo to OpenRouter's Image API. `PIX_POSE_MODEL` chooses the model; it defaults to `openai/gpt-image-2.5-flare@low` and takes any OpenRouter image model id, with `@low`, `@medium`, or `@high` appended for a model that has quality tiers. The seed is reduced to 0…2³¹−1, the range a model such as Seedream accepts; a model without seed support, like the default one, ignores it. A candidate that arrives in another format is converted to JPEG. The server waits at most `PIX_POSE_UPSTREAM_TIMEOUT_SECONDS` (30) for the image service.

When the phone cancels a request by closing its connection (Design §2.6.3), the server stops its call to the image service within a quarter of a second, and OpenRouter does not bill the unfinished image. A cancelled request still counts toward the rate limit and leaves no line in the access log. A phone that loses its network without closing the connection is not noticed; the 30-second timeout ends that call.

Errors have the form `{"error":{"code":"UNKNOWN_TEMPLATE","message":"No pose template with this id"}}`.

| Status and code | Meaning |
| --- | --- |
| `400 INVALID_IMAGE` | Not a readable JPEG, a long side over 1024 px, or EXIF metadata. |
| `400 UNKNOWN_TEMPLATE` | No template has that `templateId`. |
| `400 INVALID_REQUEST` | A part is missing or `seed` is not an integer. It replaces FastAPI's default 422, which this API uses for `REJECTED`. |
| `413` | The request is larger than 1 MiB. It is refused before the upload is read, so it carries Starlette's own body (`Content Too Large`) and not the error shape above. |
| `422 REJECTED` | The image service declined the photo or pose (a content filter or a model refusal). |
| `429 RATE_LIMITED` | One client address sent more than `PIX_POSE_RATE_LIMIT` (20) requests to the image service within `PIX_POSE_RATE_WINDOW_SECONDS` (3600). Requests refused before that point are not counted. |
| `502 UPSTREAM_ERROR` | The image service failed, could not be reached, or gave an unreadable answer, or `OPENROUTER_API_KEY` is not set. The server log gives OpenRouter's status code, for example 402 when the credits run out, and never the text of its answer. |
| `504 UPSTREAM_TIMEOUT` | The image service did not answer in time. |

The rate limit is counted per client IP address in memory, so it resets when the server restarts; 20 requests are five sets of four poses. The scene photo and the candidate exist only in memory for the duration of the request and are never written to disk or logged (FR-4.9, NFR-13). The 1 MiB request limit is what keeps an upload in memory: Starlette would write a larger one to a temporary file. OpenRouter bills each generated image.

## Deploy

The server runs on the two course servers (Design §2.8, Hosting and Deployment). Each is reachable only through the course's HTTP proxy on port 80, so there is no HTTPS or WSS, and the two cannot reach each other.

| Server | Address | Branch | Use |
| --- | --- | --- | --- |
| A (`ssh swpp-a`) | `http://swpp26t10.147-46-242-169.sslip.io` | `main` | Production; release builds of the app use it. |
| B (`ssh swpp-b`) | `http://swpp26t10.147-46-242-174.sslip.io` | `dev` | Staging; set `pix.serverUrl` to it in a debug build. |

[`deploy/docker-compose.yml`](deploy/docker-compose.yml) runs nginx on port 80 and two copies of the app, `app-blue` and `app-green`, built from the [`Dockerfile`](Dockerfile). One copy serves and the other is stopped. nginx forwards `/api/v1/`, `/ws`, and `/health` to the serving copy, answers `/version` with the commit that is live, and answers `404` for everything else, so FastAPI's `/docs` and the app's `/internal` routes are not public. A new public route must be added to [`deploy/nginx/nginx.conf`](deploy/nginx/nginx.conf).

### How a deploy happens

GitHub cannot push to the servers: SSH goes through a campus-only relay with personal keys, and a self-hosted runner needs repository admin rights and is advised against on a public repository. So each server pulls. A systemd timer runs [`deploy/deploy.sh auto`](deploy/deploy.sh) every minute as root:

1. It fetches the server's branch. If the commit is already recorded, it stops.
2. If the commit does not change `server/`, it records the commit and restarts nothing.
3. It builds the image. pytest runs as a build stage, so a commit whose tests fail stops here.
4. It starts the image as the idle copy and waits for the container's health check.
5. It waits until the serving copy has no open room, for at most 10 minutes. A room counts until its photographer leaves, or until 60 s after its last socket dropped. The two copies cannot share a room until the Redis session store is built (Design AD-18). After 10 minutes it goes on anyway: sessions that are already live continue, because the video goes phone to phone, and rooms still waiting for a subject end.
6. It points nginx at the new copy and reloads it, checks `/health` through nginx, and stops the old copy with 40 s for requests in flight.
7. It records the commit, which `/version` then shows, and deletes images other than the two copies'.

A failure at any step leaves the serving copy as it was, and the timer does not try that `server/` tree again; fix the branch, or run `deploy.sh deploy` to retry. On GitHub, the [server-image](../.github/workflows/server-image.yml) workflow builds the image and checks the routes through nginx on every pull request, and the [server-deploy](../.github/workflows/server-deploy.yml) workflow waits after a merge until `/version` shows the commit, so a deploy that failed is a red mark on the commit.

### Set up a server once

The server needs Docker with the Compose and Buildx plugins (`sudo apt install -y docker.io docker-compose-v2 docker-buildx`). Then, on the server:

```sh
sudo git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git /opt/pix/repo
sudo /opt/pix/repo/server/deploy/setup.sh dev        # "main" on Server A
sudoedit /opt/pix/.env                               # put the OpenRouter key in
sudo /opt/pix/repo/server/deploy/deploy.sh deploy    # the first deploy, by hand
sudo systemctl enable --now pix-deploy.timer         # then automatic deploys
```

[`setup.sh`](deploy/setup.sh) installs no packages and creates no users. It writes the branch to `/opt/pix/deploy.conf`, creates `/opt/pix/.env` from `.env.example` (readable by root only), and installs the systemd units. Secrets exist only in `/opt/pix/.env`; `/opt/pix/state/` holds what `deploy.sh` writes. To try a branch before it is merged, pass it to the first deploy: `deploy.sh deploy feat/2-deploy`.

### Operate it

Run these on the server with `sudo`:

| Command | What it does |
| --- | --- |
| `/opt/pix/repo/server/deploy/deploy.sh status` | The branch, the recorded commit, which copy serves, the open rooms, and the containers. |
| `… deploy.sh deploy [ref]` | Deploys a branch or a commit now; the default is the server's branch. It also retries a commit the timer gave up on. |
| `… deploy.sh rollback` | Switches back to the image that served before the last deploy, without waiting for rooms. The timer does not deploy the rolled-back code again until the branch changes `server/`. |
| `… deploy.sh restart` | Starts the other copy from the same image, for example after editing `/opt/pix/.env`. |
| `journalctl -u pix-deploy -n 50` | What the timer's deploys printed. `/opt/pix/state/last-build.log` has the last build in full. |
| `docker logs -n 100 pix-app-blue-1` | The app's log; `pix-app-green-1` for the other copy and `pix-nginx-1` for the access log. Logs rotate at 10 MB, five files each. |

Set `MAX_WAIT_SECONDS=0` in front of a `deploy.sh` command to switch without waiting for rooms.

### What the course's proxy does

Measured on Server B on 2026-10-13; nginx and the app are configured for it:

- **WebSockets pass and stay open.** One with pings stayed open for 300 s and an idle one for 200 s, although the course cuts a plain HTTP response after 120 s. Uvicorn's ping every 20 s keeps nginx's 75 s read timeout from closing a quiet socket.
- **The phone's address is the last `X-Forwarded-For` value.** The proxy adds it and passes on whatever the client sent before it. nginx takes the last value only and replaces the header on the way to the app, which Uvicorn trusts (`--forwarded-allow-ips "*"`) because nothing but nginx can reach its port. The rate limit therefore counts per phone address; phones behind one carrier or Wi-Fi gateway still share an address, so limit per user once accounts exist.
- **A cancelled request does not reach the server.** The proxy keeps its connection open after the phone closes its side, so a pose request the user cancelled is still generated and billed. The rate limit bounds the cost.
- **Uploads stay small and in memory.** nginx refuses a request over 1 MiB with its own 413 and buffers a smaller one in memory, and it never writes a pose answer to a temporary file (FR-4.9, NFR-13). What the course's proxy does with an upload is outside our control.
- **Nothing is encrypted in transit.** A scene photo and the session token travel as plain HTTP between the phone and the server (NFR-14).

Docker Hub limits anonymous pulls per address, and the course servers share theirs. If a build fails with `toomanyrequests`, wait and run `deploy.sh deploy`; the base images are cached after the first pull.

## Lint, format, and test

Run these from `server/` before you push. On every pull request, the [server-lint](../.github/workflows/server-lint.yml) workflow runs the two checks and the [server-test](../.github/workflows/server-test.yml) workflow runs the tests.

| Command | What it does |
| --- | --- |
| `uv run ruff format .` | Formats Python files with Ruff. |
| `uv run ruff format --check .` | Fails if a file is not formatted. |
| `uv run ruff check .` | Ruff lint. Add `--fix` to apply the fixes Ruff can make, such as import order. |
| `uv run pytest` | Tests in `tests/`. They run in one process, without a running server or a phone. |

- **Rules.** Ruff rules are in `ruff.toml`: a 120-character line limit and the lint rule sets `E4`, `E7`, `E9`, `F`, and `I`.
- **Suppressing.** For one line, use `# noqa: <rule>` with a comment that says why. To turn off a rule for some files or for the whole server, add it to `per-file-ignores` or `ignore` in `ruff.toml`.
- **Tests.** Put tests in `tests/test_<module>.py`. Add tests in the pull request that writes the code. The `client`, `settings`, and `clock` fixtures in `tests/conftest.py` give an app whose clock the test moves, so expiry is tested without waiting. `client` also replaces OpenRouter with the fake `image_api`, which records requests and returns what the test sets, so tests never call the real service or need a key; `make_image` builds test photos. `async def` tests need no marker, and a test fails after 15 seconds.

## Compare image-editing models

[`scripts/compare_image_edit_apis.py`](scripts/compare_image_edit_apis.py) sends scene photos to image-editing models through [OpenRouter](https://openrouter.ai/) and reports latency, cost, and the resulting images side by side. It is not part of the server, and it does not import `pix_server`.

Set `OPENROUTER_API_KEY` in your shell to an OpenRouter key with credits (the script reads the environment only, not `.env`), then run it from `server/` with one or more photos:

```sh
export OPENROUTER_API_KEY=<your key>
uv run scripts/compare_image_edit_apis.py photo1.jpg photo2.jpg
```

With no options, each photo goes to four models with all four pose templates: 16 images per photo, which takes about a minute and cost about $0.50 in October 2026. Each photo is first prepared the way the app sends it (upright, long side 1024 px, JPEG quality 85, no EXIF), and the poses for one model are requested in parallel, like the app's four `POST /poses` calls.

The terminal prints one line per image, then a summary table, the total cost, and the report path. Results go to `scripts/out/<timestamp>/`:

| File | Contents |
| --- | --- |
| `report.html` | The summary table, then one table per photo: models as rows, the original and each pose as columns. Red marks a failed request or one slower than the server's 30-second upstream timeout. Click an image to see it at full size. |
| `results.json` | Every request's latency, cost, parameters, and error, plus the prompt and poses used. |
| `<photo>/<model>/<pose>.*` | The returned images, next to the prepared `scene.jpg`. |

| Option | What it does |
| --- | --- |
| `--models ID ...` | Models to compare. Add `@low` or `@medium` to a model that has quality tiers, for example `openai/gpt-image-2@low`. |
| `--list-models` | Lists every model that can edit an image, with current prices. It needs no key or photo and costs nothing. |
| `--poses NAME ...` | A subset of `hands_on_hips`, `wave`, `walking`, and `arms_crossed`. |
| `--pose-text "..."` | A free-text pose in Korean or English (FR-4.8) instead of the templates. Repeat it for several poses. |
| `--prompt "..."` | Another prompt to try. It must contain `{pose}`, which is replaced with the pose description. |
| `--concurrency N` | Runs N photo-and-model combinations at once. The default, 1, matches one user generating. |
| `--out DIR` | The output folder, instead of the timestamped one. |
| `--timeout S` | Gives up on a request after S seconds. The default is 60. |

```sh
# Smoke test: one image from one model
uv run scripts/compare_image_edit_apis.py photo.jpg --models bytedance-seed/seedream-5-0-flash --poses wave

# Many photos, two models, several combinations at once
uv run scripts/compare_image_edit_apis.py photos/*.jpg --models google/gemini-3.1-flash-image bytedance-seed/seedream-5-0-flash --concurrency 4

# A free-text pose
uv run scripts/compare_image_edit_apis.py photo.jpg --pose-text "벽에 기대서 한 손은 주머니에"
```

- **Cost.** A run requests photos × models × poses images. Only returned images are billed; failed and timed-out requests are not. If lines show `Insufficient credits`, the OpenRouter account is empty.
- **Photos.** Use JPEG, PNG, or WebP with the whole person in the frame; HEIC does not open. Portrait 3:4 matches the app's frame.
- **Privacy.** Photos are sent to OpenRouter and the model's provider. `scripts/out/` holds the photos and results and is git-ignored; do not commit them.
- **Defaults.** The default models, prompt, and pose descriptions are `DEFAULT_MODELS`, `PROMPT`, and `POSES` at the top of the script.
