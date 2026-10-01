# Pix signaling server

Iteration 1's FastAPI signaling hub. It creates anonymous room-code sessions and relays WebRTC SDP and ICE messages between one photographer and one subject. Video, guide images, guide state, and remote zoom go directly between the phones over WebRTC. The server has no database, accounts, image-generation endpoints, or media storage.

The wire contract comes from [Design Documentation §2.5.2](../wiki/Design-Documentation.md) and Android's [SignalMessage.kt](../android/app/src/main/java/com/lastpenguin/pix/session/signaling/SignalMessage.kt). Room behavior follows [FR-8 and FR-6](../wiki/Requirements-and-Specifications.md).

## Run on the test Wi-Fi

Install [uv](https://docs.astral.sh/uv/getting-started/installation/), then from `server/`:

```sh
uv sync --locked
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Use **one worker and one server instance**: room membership is process-local and is lost when the server restarts. For development, `--reload` can replace `--workers 1`; a reload ends existing sessions.

Put the laptop and both phones on the same Wi-Fi. Connect Android to `ws://<laptop-Wi-Fi-IP>:8000/ws` (a phone's `localhost` refers to that phone). The network must allow device-to-device traffic and inbound TCP port 8000 on the laptop. Iteration 1 uses the debug-build HTTP/WS exception in Design §2.8. `iceServers` is always `[]`; STUN/TURN and cellular connectivity belong to later iterations. `GET /health` returns `{"status":"ok"}`.

Optional settings are listed in `.env.example`. Copy it to `.env` in this directory to override defaults, or export `PIX_` environment variables. The default waiting-room TTL is 600 seconds, empty-session TTL is 60 seconds, and cleanup runs every second. Expiry is also checked during message handling. If changing `PIX_MAX_MESSAGE_BYTES`, keep Uvicorn's `--ws-max-size` consistent. Uvicorn's WebSocket ping/pong detects dead connections; no application heartbeat message is required.

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

## Verify

From `server/`:

```sh
uv run ruff check .
uv run ruff format --check .
uv run pytest
```

With the server running, exercise the NFR-18 capacity target using real sockets:

```sh
uv run python scripts/load_signaling.py --sessions 50
```

The script holds 50 two-phone sessions open simultaneously, checks independent SDP/ICE exchanges, then leaves each session. It checks the signaling server; camera/video latency and phone-to-phone WebRTC connectivity need the Android apps on the test devices.

## Continuous integration

Server CI has two independent workflows: [server-lint](../.github/workflows/server-lint.yml) (Ruff lint and formatting) and [server-test](../.github/workflows/server-test.yml) (pytest). Each runs on pull requests, pushes to `dev` and `main`, merge queues, and manual dispatch, using Python from `.python-version` and dependencies from `uv.lock`. Their check names match their workflow names. New commits to a PR cancel its older runs.

To enforce these checks, configure a GitHub branch ruleset for `dev` and `main`: require a pull request, require both `server-lint` and `server-test` to pass, and require branches to be up to date before merging. Enable the rules after the workflows have run successfully on GitHub so their checks are available to select. Keep required-check names aligned with these workflows when they are renamed or consolidated. Adding workflow files alone does not configure merge protection. See [GitHub's ruleset documentation](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository).

The workflows deliberately have no PR path filters: GitHub can leave a required check pending when an entire workflow is skipped, blocking an Android-only or documentation-only PR. The current checks are inexpensive enough to run for every PR. If they become costly, use change detection inside each workflow while retaining an always-reported required check. See [GitHub's guidance on skipped required checks](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).
