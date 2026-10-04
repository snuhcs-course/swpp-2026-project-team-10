# Pix server

FastAPI backend for Pix. The server's responsibilities include session signaling and planned APIs for friend invitations, pose generation, and other features as the project develops.

The current implementation provides iteration 1 signaling: anonymous room-code sessions and WebRTC SDP/ICE relay between one photographer and one subject. Video, guide images, guide state, and remote zoom go directly between the phones over WebRTC. Database support, account and friend APIs, and pose generation are not implemented yet.

The wire contract comes from [Design Documentation §2.5.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation) and Android's [SignalMessage.kt](../android/app/src/main/java/com/lastpenguin/pix/session/signaling/SignalMessage.kt). Room behavior follows [FR-8 and FR-6](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications).

## Setup

Install [uv](https://docs.astral.sh/uv/getting-started/installation/), then run these commands from the repository root:

```sh
cd server
uv sync --locked
```

Optional settings are listed in `.env.example`. Copy it to `.env` in `server/` to override defaults, or export `PIX_` environment variables. The default waiting-room TTL is 600 seconds, empty-session TTL is 60 seconds, and cleanup runs every second. Expiry is also checked during message handling. If changing `PIX_MAX_MESSAGE_BYTES`, keep Uvicorn's `--ws-max-size` consistent.

## Run on the test Wi-Fi

From `server/`, start the server:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Use **one worker and one server instance**: room membership is process-local and is lost when the server restarts. For development, `--reload` can replace `--workers 1`; a reload ends existing sessions.

Put the laptop and both phones on the same Wi-Fi. Connect Android to `ws://<laptop-Wi-Fi-IP>:8000/ws` (a phone's `localhost` refers to that phone). The network must allow device-to-device traffic and inbound TCP port 8000 on the laptop. Iteration 1 uses the debug-build HTTP/WS exception in Design §2.8. `iceServers` is always `[]`; STUN/TURN and cellular connectivity belong to later iterations.

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
- **Tests.** Put tests in `tests/test_<module>.py`. Add tests in the pull request that writes the code. The `client`, `settings`, and `clock` fixtures in `tests/conftest.py` give an app whose clock the test moves, so expiry is tested without waiting. `async def` tests need no marker, and a test fails after 15 seconds.

## Compare image-editing models

[`scripts/compare_image_edit_apis.py`](scripts/compare_image_edit_apis.py) sends scene photos to image-editing models through [OpenRouter](https://openrouter.ai/) and reports latency, cost, and the resulting images side by side. It is not part of the server: it does not import `pix_server`, and it declares its own dependencies inline, so `uv run` fetches them on the first run without touching `pyproject.toml` or `uv.lock`.

Set `OPENROUTER_API_KEY` in your shell to an OpenRouter key with credits (the script reads the environment only, not `.env`), then run it from `server/` with one or more photos:

```sh
export OPENROUTER_API_KEY=<your key>
uv run scripts/compare_image_edit_apis.py photo1.jpg photo2.jpg
```

With no options, each photo goes to four models with all four pose templates: 16 images per photo, which takes about a minute and cost about $0.50 in October 2026. Each photo is first prepared the way the app sends it (upright, long side 1024 px, JPEG quality 85, no EXIF), and the poses for one model are requested in parallel, like the app's four `POST /poses` calls.

The terminal prints one line per image, then a summary table, the total cost, and the report path. Results go to `scripts/out/<timestamp>/`:

| File | Contents |
| --- | --- |
| `report.html` | The summary table, then one table per photo: models as rows, the original and each pose as columns. Red marks a failed request or one slower than the server's 25-second upstream timeout (Design §2.8). Click an image to see it at full size. |
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
