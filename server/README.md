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

## Verify

Check the server's lint, formatting, and tests. From `server/`:

```sh
uv run ruff check .
uv run ruff format --check .
uv run pytest
```

## Continuous integration

Server CI has two independent workflows: [server-lint](../.github/workflows/server-lint.yml) (Ruff lint and formatting) and [server-test](../.github/workflows/server-test.yml) (pytest). Each runs on pull requests, pushes to `dev` and `main`, merge queues, and manual dispatch, using Python from `.python-version` and dependencies from `uv.lock`. Their check names match their workflow names. New commits to a PR cancel its older runs.
