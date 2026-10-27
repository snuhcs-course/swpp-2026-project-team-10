# Pix server

FastAPI backend for Pix. The server's responsibilities include session signaling, pose generation, accounts and friends, and planned APIs for friend invitations and other features as the project develops.

The current implementation provides iteration 1 signaling (anonymous room-code sessions and WebRTC SDP/ICE relay between one photographer and one subject) and the pose generation REST API, which turns a scene photo into pose candidates through an image-editing model on [OpenRouter](https://openrouter.ai/), with poses that a vision model suggests for the scene when the user describes none. Video, guide images, guide state, and remote zoom go directly between the phones over WebRTC. Rooms, room codes, presence, and the pose rate limit are kept in a Redis session store, so the server can run several worker processes ([Session store](#session-store)). Accounts (sign-up with a Pix ID and a password, or Google), exact Pix ID lookup, friend requests, and the friend list with online status are kept in a database ([Accounts and friends](#accounts-and-friends), [Database](#database)). Invitations are not implemented yet.

The wire contract comes from [Design Documentation §2.5.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation) and Android's [SignalMessage.kt](../android/app/src/main/java/com/lastpenguin/pix/session/signaling/SignalMessage.kt). Room behavior follows [FR-8 and FR-6](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications). The REST contract comes from Design Documentation §2.5.3 and Android's [PixApi.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/PixApi.kt) and [ApiModels.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/ApiModels.kt); pose generation follows §2.6.3 and FR-4.

## Setup

Install [uv](https://docs.astral.sh/uv/getting-started/installation/), then run these commands from the repository root:

```sh
cd server
uv sync --locked
```

Optional settings are listed in `.env.example`. Copy it to `.env` in `server/` to override defaults, or export `PIX_` environment variables. The default waiting-room TTL is 600 seconds, empty-session TTL is 60 seconds, and cleanup runs every second. Expiry is also checked during message handling. If changing `PIX_MAX_MESSAGE_BYTES`, keep Uvicorn's `--ws-max-size` consistent.

Pose generation needs `OPENROUTER_API_KEY`, an OpenRouter key with credits, in `.env` or the environment. Without it the server still runs, `POST /poses` answers `UPSTREAM_ERROR`, and `POST /pose-suggestions` answers the default poses. Google sign-in needs `PIX_GOOGLE_CLIENT_ID`, the web client ID that the app passes to Google sign-in; without it the Google endpoints answer `503 UNAVAILABLE`, and sign-up with a Pix ID and a password still works. Sessions across networks need `PIX_TURN_KEY_ID` and `PIX_TURN_API_TOKEN`, the TURN key of Cloudflare Realtime: with them each phone gets Cloudflare's STUN and TURN servers with credentials of its own, valid for `PIX_TURN_TTL_SECONDS` (4 h); without them, or when Cloudflare fails or takes longer than `PIX_TURN_TIMEOUT_SECONDS` (3 s), each phone gets Cloudflare's STUN server only, which is enough on one Wi-Fi (Design 2.6.7). The server logs only Cloudflare's status, never the token or a credential.

## Run on the test Wi-Fi

From `server/`, start the server:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Started this way, without `PIX_REDIS_URL`, the server keeps its rooms inside the process, so use **one worker**; a restart loses the rooms. Without `PIX_DATABASE_URL` the accounts are in `pix.db`, a SQLite file in `server/` that survives restarts; delete it to start over. For development, `--reload` can replace `--workers 1`; a reload ends existing sessions. To run several workers, or to keep rooms across a restart, give it a Redis ([Session store](#session-store)).

Put the laptop and both phones on the same Wi-Fi. Connect Android to `ws://<laptop-Wi-Fi-IP>:8000/ws` for signaling and `http://<laptop-Wi-Fi-IP>:8000/api/v1` for the REST API (a phone's `localhost` refers to that phone). The network must allow device-to-device traffic and inbound TCP port 8000 on the laptop. Iteration 1 uses the debug-build HTTP/WS exception in Design §2.8. `iceServers` is always `[]`; STUN/TURN and cellular connectivity belong to later iterations.

Uvicorn's WebSocket ping/pong detects dead connections; no application heartbeat message is required.

`GET /health` returns `{"status":"ok"}`, or `503` with `{"status":"unavailable"}` while the session store does not answer.

## WebSocket protocol

Send JSON **text** frames to `/ws` on a persistent connection. A socket belongs to at most one room. Messages and field casing match the Android contract. A signed-in phone sends `Authorization: Bearer <access token>` with the upgrade, which shows its user as online to their friends while the socket is open; without a token, or with an unknown or expired one, the socket works the same but shows nobody online.

| Client message | Result |
| --- | --- |
| `{"type":"room.create"}` | Sender gets `{"type":"room.created","code":"482915","sessionId":"s_…","iceServers":[…]}`, its own STUN and TURN entries (Design 2.5.2). |
| `{"type":"room.join","code":"482915"}` | Subject gets `{"type":"session.joined","sessionId":"s_…","role":"SUBJECT","iceServers":[…]}`, asked for only once the join succeeded; photographer gets `{"type":"peer.joined"}`. |
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
| `RATE_LIMITED` | The client's address sent `PIX_JOIN_FAILURE_LIMIT` (100) wrong codes, unknown or expired, within `PIX_JOIN_FAILURE_WINDOW_SECONDS` (600). Every join from it is refused, the right code included, until the oldest leaves the window, so codes cannot be guessed. The count is per address, in the session store. |
| `UNKNOWN_TYPE` | Unrecognized message type. |
| `INVALID_MESSAGE` | Malformed JSON, binary frame, invalid fields, or invalid signaling payload. |
| `INVALID_STATE` | Already in a room, signaling before joining, or SDP from the wrong role. |
| `PEER_UNAVAILABLE` | No connected counterpart to receive a signal. |

Errors leave the socket usable. Text messages larger than 64 KiB close the socket with code 1009. Slow consumers have a bounded outgoing queue; queue overflow or a five-second send timeout disconnects that socket with code 1013. The server also closes its sockets with 1013 when the session store fails or restarts ([Session store](#session-store)); the phone can connect again later. Request bodies, room codes, and signaling payloads are not logged by the app.

## Testing the photographer's phone without a second phone

`tools/fake_subject.py` is a subject phone made of Python. It joins a room code, answers the WebRTC offer with [aiortc](https://github.com/aiortc/aiortc), exchanges `hello` on the data channels, counts the video frames it receives, receives the photographer's guide image (reassembled from its chunks and checked against the CRC; `--save-guide guide.webp` keeps it, `--have-guide <id>` reports an image it already has), prints every `guide.state`, optionally sends `camera.zoom.set`, and prints the echoed `camera.state` with its delay. It is a development tool and not part of the server; its two dependencies are not in `pyproject.toml`. It can also send a guide move (`--move cx,cy,height`: three steps, then the final value), a brightness index (`--exposure`), and a flash mode (`--flash`), and prints every echo with the delay since the request (Iter2-P17).

```sh
pip install websockets aiortc
python tools/fake_subject.py 482915 --seconds 20 --zoom 2.0
python tools/fake_subject.py 482915 --move 0.3,0.5,0.9 --exposure 2 --flash AUTO
```

`tools/fake_photographer.py` is the other side: it creates a room and prints the code; once a phone joins it sends a test-pattern video, the two data channels, `hello`, `camera.capabilities`, the guide image from `--guide` (a WebP cutout with alpha, at most 720 px tall) with its `guide.state`, then moves and resizes the guide for `--seconds` as a drag would, `--clear` removes it at the end, and every `camera.zoom.set` is echoed as `camera.state`. Use it to test Subject view with one phone or an emulator: `python tools/fake_photographer.py --guide cutout.webp`, then *Shoot together › Join with a code instead* on the phone (it needs `pillow` as well). It answers brightness and flash requests with `camera.state`, and applies a `guide.move` to its guide, echoing `guide.state` with `by: SUBJECT` and stopping its own guide animation (Iter2-P17).

Start the server, open Pix on one phone or emulator (an emulator reaches the laptop at `http://10.0.2.2:8000/`), tap *Shoot together*, and pass the code. The phone shows "Live · fake-subject connected", and the script reports the first video frame, a frame count, and the `camera.state` that answers the zoom request.

## REST API

The base URL is `http://<laptop-Wi-Fi-IP>:8000/api/v1`.

| Endpoint | Result |
| --- | --- |
| `POST /pose-suggestions` | Four poses for the scene in English, `{"poses":["Lean your back against the door, cross one ankle, and look at the camera.", …]}`, which the app then sends as four descriptions; with a `description`, that description followed by three variations of it. Asked once per set. |
| `POST /poses` | One candidate for a pose description: `{"templateId":null,"image":"<Base64 JPEG>","elapsedMs":9961}`. The app sends four requests in parallel, one per pose: the user's description and its three variations, or the suggested poses. A `templateId` instead of a description answers with that id. |
| `POST /pose-tips` | How to hold the phone for the candidate the user chose, `{"text":"Zoom in or take a step closer, hold the phone at your chest and keep it level - the body keeps its proportions.","framing":"closer","hold":"chest","height":"keep","angle":"level"}`. The app asks once, when the user takes a candidate, and shows `text` on Camera. |
| `GET /pose-templates` | `[{"id":"hands_on_hips","label":"Hands on hips"}, …]`: the four default poses `hands_on_hips`, `wave`, `walking`, and `arms_crossed`, for apps from before Iteration 2, which send their ids to `POST /poses`. The current app does not call it. |

`POST /pose-suggestions` takes `multipart/form-data` with `image`, the same photo as `POST /poses`, and an optional `description`, checked as for `POST /poses`. `POST /poses` takes `image` (a JPEG with a long side of at most 1024 px and no EXIF), `seed` (an integer), and either `description`, the pose in Korean or English, or `templateId`. The description is trimmed, each run of whitespace becomes one space, and it may then be 1–100 characters; an empty `description` counts as none. The whole request may be at most 1 MiB; a photo prepared as Design §2.6.3 describes is a few hundred KB. `POST /pose-tips` takes `scene`, the photo sent to `POST /poses`, `candidate`, the chosen candidate within the same limits (the app sends it with a long side of 768 px), and `pose`, the pose it was made with, folded and limited like a description.

```sh
curl -F "image=@scene.jpg" http://localhost:8000/api/v1/pose-suggestions
curl -F "image=@scene.jpg" -F "description=벽에 기대서 카메라 보며 살짝 웃기" http://localhost:8000/api/v1/pose-suggestions
curl -F "image=@scene.jpg" -F "description=벽에 기대서 카메라 보며 살짝 웃기" -F seed=1829304756 http://localhost:8000/api/v1/poses
curl -F "scene=@scene.jpg" -F "candidate=@candidate.jpg" -F "pose=Walk slowly past the railing" http://localhost:8000/api/v1/pose-tips
```

The server puts every pose, a description, a suggested pose, or a template's default pose, into one prompt and sends it with the photo to OpenRouter's Image API. The prompt sets the pose and the expression, lets the model frame the person closer or from a slightly different height but never wider than the photo, and keeps the person, the clothing, and the place whatever the pose asks. For suggestions the server sends the photo with a second prompt to a vision model through OpenRouter's chat completions, and keeps the first four different poses of 1–100 characters from its answer; the default poses fill the rest, or all four when the model fails, is unreachable, or does not answer within `PIX_SUGGESTION_TIMEOUT_SECONDS` (8), so that endpoint never fails because of the model. With a description the same model gets a third prompt and writes three variations of it, which keep what the description says and change what it leaves open, because four requests with one text give nearly the same image; the server leaves out the description if the model repeats it, and the description fills in for any variation it cannot use. `PIX_SUGGESTION_MODEL` chooses the vision model; it defaults to `google/gemini-3.1-flash-lite`, and `z-ai/glm-5.3-flash` is the tested alternative (Design §2.6.3). The three prompts and the default poses (id, label, and the pose sent to the model) are in [prompts.toml](src/pix_server/poses/prompts.toml), which explains its rules at the top; the server reads it when it starts and refuses to start if it is wrong, and both scripts below read the same file. `PIX_POSE_MODEL` chooses the model; it defaults to `openai/gpt-image-2.5-flare@low` and takes any OpenRouter image model id, with `@low`, `@medium`, or `@high` appended for a model that has quality tiers. The seed is reduced to 0…2³¹−1, the range a model such as Seedream accepts; a model without seed support, like the default one, ignores it. A candidate that arrives in another format is converted to JPEG. The server waits at most `PIX_POSE_UPSTREAM_TIMEOUT_SECONDS` (60) for the image service, as long as the app waits for a whole set.

For a tip the server sends the scene, then the candidate, with `tip_prompt` from the same file to `PIX_TIP_MODEL` (default `google/gemini-3.1-flash-lite`) at `PIX_TIP_REASONING` (`low`). The model answers in a fixed JSON schema: the shot and the pose it sees, where the phone was for the scene and where it should be for this pose (by the photographer's posture: crouching, waist, chest, eye level, or above the head), the angle, the framing, and one sentence that says them in words; the prompt holds photographers' rules of thumb for the height and the angle. The server compares the two heights for `height` (`raise`, `lower`, or `keep`). A sentence that is empty, longer than 160 characters, or holds a number (in digits, or in words such as "two steps" or "one step"), a unit, or a degree is not used, because it is hard to follow while shooting; then, and when the model fails or does not answer within `PIX_TIP_TIMEOUT_SECONDS` (10), the endpoint fails and the app shows no tip. The answer took 2–3.5 s on 2026-10-25.

When the phone cancels a pose or tip request by closing its connection (Design §2.6.3), the server stops its call to OpenRouter within a quarter of a second, and OpenRouter does not bill the unfinished image. The app drops a tip request when the user takes another candidate before the tip arrives. A cancelled request still counts toward the rate limit and leaves no line in the access log. A phone that loses its network without closing the connection is not noticed; the timeout (60 s for a pose, 10 s for a tip) ends that call.

Errors have the form `{"error":{"code":"UNKNOWN_TEMPLATE","message":"No pose template with this id"}}`.

| Status and code | Meaning |
| --- | --- |
| `400 INVALID_IMAGE` | Not a readable JPEG, a long side over 1024 px, or EXIF metadata; for `POST /pose-tips`, the scene or the candidate. |
| `400 UNKNOWN_TEMPLATE` | No template has that `templateId`. |
| `400 INVALID_REQUEST` | A part is missing, `seed` is not an integer, both or neither of `templateId` and `description` were sent, or the description is blank or longer than 100 characters; for `POST /pose-tips`, a part is missing, or the pose is blank or longer than 100 characters. It replaces FastAPI's default 422, which this API uses for `REJECTED`. |
| `413` | The request is larger than 1 MiB. It is refused before the upload is read, so it carries Starlette's own body (`Content Too Large`) and not the error shape above. |
| `422 REJECTED` | The image service declined the photo or pose (a content filter or a model refusal). It does not say which, so the message is the same with a description; the app asks for another description or another photo (US-5). For `POST /pose-tips`, the tip model declined the photos. |
| `429 RATE_LIMITED` | One client address sent more than `PIX_POSE_RATE_LIMIT` (20) requests to the image service within `PIX_POSE_RATE_WINDOW_SECONDS` (3600), or all clients together more than `PIX_POSE_GLOBAL_LIMIT` (500) within `PIX_POSE_GLOBAL_WINDOW_SECONDS` (86400); the message says which. `POST /pose-suggestions` is counted apart, in windows of the same lengths, with `PIX_SUGGESTION_RATE_LIMIT` (10) and `PIX_SUGGESTION_GLOBAL_LIMIT` (500), and `POST /pose-tips` with `PIX_TIP_RATE_LIMIT` (20) and `PIX_TIP_GLOBAL_LIMIT` (1000). Requests refused before that point are not counted, and a request refused by one window is counted in neither. |
| `503 UNAVAILABLE` | The session store, which counts the rate limit, does not answer. Nothing is sent to the image service. |
| `502 UPSTREAM_ERROR` | The image service failed, could not be reached, or gave an unreadable answer, or `OPENROUTER_API_KEY` is not set; for `POST /pose-tips`, also an answer that cannot be used. The server log gives OpenRouter's status code, for example 402 when the credits run out, and never the text of its answer. |
| `504 UPSTREAM_TIMEOUT` | The image service did not answer in time. |

The rate limits are counted in the session store, so every worker counts against the same windows; 20 requests are five sets of four poses, and the global 500 bound what the image service can cost in a day however many addresses send requests. Set a spending limit on the OpenRouter key as well, in OpenRouter's dashboard, as the last bound. The scene photo, the description, the suggested poses, the candidate, and the tip exist only in memory for the duration of the request and are never written to disk or logged (FR-4.9, NFR-13); for suggestions and variations the log says only how many of the model's poses were usable, and for a tip only that it could not be used. The 1 MiB request limit is what keeps an upload in memory: Starlette would write a larger one to a temporary file. OpenRouter bills each generated image.

## Accounts and friends

The endpoints, request and response bodies, and error codes are in Design §2.5.3; the code is in [`src/pix_server/accounts/`](src/pix_server/accounts/). Every endpoint below except the four sign-ins, `/auth/check-pix-id`, and `/auth/refresh` needs `Authorization: Bearer <access token>` and answers `401 UNAUTHENTICATED` without it, or with an expired one. Pose generation and room codes need no sign-in (FR-5.1).

| Endpoint | Result |
| --- | --- |
| `POST /auth/signup` | `{"pixId","password","displayName"}` → `201 {"accessToken","refreshToken","expiresIn","user"}`. A Pix ID is 4–20 of `a–z 0–9 . _`; a password is 8–64 characters with a letter and a digit, and not the Pix ID; `PIX_ID_INVALID` and `PASSWORD_INVALID` name the broken rule. |
| `POST /auth/check-pix-id` | `{"pixId"}` → `{"available": true}` or `false`, for *Check* on Sign up and the Pix ID setup; `400 PIX_ID_INVALID` names the broken rule. Sign-up still refuses a Pix ID taken in the meantime. |
| `POST /auth/login` | `{"pixId","password"}` → `200`, as above, or `401 INVALID_CREDENTIALS` for a wrong password and an unknown Pix ID alike. |
| `POST /auth/google` | `{"idToken"}` → `200`, as above, or `404 NOT_REGISTERED` when no account uses that Google account; the app then asks whether the person has a Pix account. |
| `POST /auth/refresh` | `{"refreshToken"}` → `200 {"accessToken","refreshToken","expiresIn"}`; the old refresh token stops working, and one used twice ends the sign-in (`401 INVALID_REFRESH_TOKEN`). |
| `POST /auth/google/signup` | `{"idToken"}` → `201`, a new account without a Pix ID, named after the Google account. |
| `PUT /me/google` | `{"idToken"}` → the account, which now signs in with Google too (`409 GOOGLE_IN_USE`, `409 GOOGLE_ALREADY_ADDED`). |
| `PUT /me/password` | `{"password"}` → the account of a Google sign-up, which now signs in with its Pix ID and this password too (`403 PIX_ID_REQUIRED` before it has a Pix ID, `409 PASSWORD_ALREADY_SET`). Changing a password is not offered, and there is no email or password reset. |
| `DELETE /auth/session` | Ends this phone's sign-in: its refresh token stops working and its device token is deleted. Its access token works until it expires, at most 15 minutes later. |
| `GET /me`, `PUT /me` | The account, with `hasPassword` and `hasGoogle`; `PUT` sets `pixId`, `displayName`, or both. A password account changes its Pix ID only with its current `password`, which the new Pix ID must not equal (`401 INVALID_CREDENTIALS`, `400 PIX_ID_INVALID`). |
| `POST /devices` | `{"fcmToken","platform":"android"}` → `204`. |
| `POST /users/lookup` | `{"pixId"}` → `{"user","relation","requestId"}` for an exact Pix ID, or `404 NOT_FOUND`. |
| `GET /friend-requests`, `POST /friend-requests`, `POST /friend-requests/{id}/accept`, `…/decline`, `DELETE /friend-requests/{id}` | Received and sent requests; send (`201`, or `200` with the new friend when the other user had already asked); accept; decline; withdraw. |
| `GET /friends`, `DELETE /friends/{userId}` | `{"friends":[{"id","pixId","displayName","online","inSession"}]}`; remove a friend. |

```sh
curl -H 'Content-Type: application/json' -d '{"pixId":"dongje.p","password":"sunset2026","displayName":"Dongje"}' http://localhost:8000/api/v1/auth/signup
curl -H "Authorization: Bearer eyJhbGciOi…" -H 'Content-Type: application/json' -d '{"pixId":"junhyeong.h"}' http://localhost:8000/api/v1/users/lookup
```

- **Passwords** are hashed with argon2id on a worker thread, and only the hash is stored. They travel over plain HTTP to the course servers, which have no TLS (Design AD-11). Login attempts are not limited for now.
- **Google ID tokens** are checked against Google's published keys, which the server fetches from Google and keeps for as long as Google allows, and against `PIX_GOOGLE_CLIENT_ID`. Only Google's subject id is stored.
- **Tokens.** A sign-in gives an access token and a refresh token. The access token is a JWT signed with HS256 and `PIX_JWT_SECRET`, with the claims `iss` (`pix`), `sub` (the user), `sid` (the sign-in), `iat`, and `exp`, `PIX_ACCESS_TOKEN_MINUTES` (15) later; it is checked without the database and not stored. The refresh token is random, stored only as a SHA-256 hash in `sessions`, and lasts `PIX_SESSION_TTL_DAYS` (30) from its last use; each refresh replaces it, and an old one that comes back ends the sign-in. Without `PIX_JWT_SECRET` each process makes its own at start, so access tokens end on a restart (the app refreshes) and several workers would not accept each other's; on the course servers `deploy.sh` makes one in `/opt/pix/jwt-secret`.
- **Database down.** An account request that cannot reach the database answers `503 UNAVAILABLE`; pose generation and rooms do not use it.
- **Friend endpoints** answer `403 PIX_ID_REQUIRED` until the caller has a Pix ID, and a user without one cannot be found. Pushes for friend requests come with invitations (Iter2-P15).
- **Logs.** No URL carries a Pix ID, because nginx's error log prints a failed request's whole request line; the lookup takes it in the body. Neither the app nor nginx logs a request's query string, and nothing logs a password, a token, or a request body.
- **Password hashing** runs two at a time per worker; each argon2id hash holds 64 MiB, so a burst of sign-ins waits instead of filling memory.

## Session store

Room state is in Redis and not in the server process (Design §2.7, AD-18): sessions, room codes, recently expired codes, each socket's connection record, and the pose rate limit. The code is in [`src/pix_server/store/`](src/pix_server/store/).

- **Workers.** Each server process is one worker. A room created through one worker can be joined through another, and a signal for a socket on another worker travels over Redis pub/sub. The image runs two workers.
- **Transitions.** Create, join, leave, disconnect, and expiry each run as one Lua script inside Redis ([`store/scripts.py`](src/pix_server/store/scripts.py)), so two workers cannot seat two subjects in one room. The caller passes the time in, which is why the tests can move a clock.
- **Housekeeping.** Every `PIX_CLEANUP_INTERVAL_SECONDS` each worker renews a heartbeat that lasts `PIX_WORKER_TTL_SECONDS` (15), and the one worker that gets a lock for that interval expires rooms and removes empty sessions. A worker whose heartbeat ran out, for example after a crash, loses its sockets in the store, and the other phones get `peer.left` with `CONNECTION_LOST`.
- **A worker that stops** gives up its sockets at once. Its sessions stay in the store for the usual 60 s, which is what a phone that reconnects will come back to once the app can rejoin (Design §2.6.7, #56).
- **Redis down or restarted.** When a worker's call to Redis fails, or it finds that it is no longer registered there (Redis came back empty, or the other workers took it for dead), messages for its sockets may have been lost. It closes all of them with 1013 and registers again: a live session goes on phone to phone, and a waiting room ends on the phone with an error instead of keeping a code that no longer works. Until Redis answers, `/health` answers `503`, new sockets are closed with 1013, and pose requests answer `503 UNAVAILABLE`. A restart loses the open rooms; the workers carry on with new ones.

| `PIX_REDIS_URL` | Where the rooms are |
| --- | --- |
| Not set | Inside the server process, in [fakeredis](https://github.com/cunla/fakeredis-py), which runs the same scripts. This is for one worker on a laptop: nothing to install. fakeredis is a development dependency, so the deployed image does not have it and refuses to start without `PIX_REDIS_URL`. |
| `redis://<host>:6379/0` | In that Redis. On the course servers [`deploy/docker-compose.yml`](deploy/docker-compose.yml) sets it to its own `redis` service. Locally, `docker run --rm -p 6379:6379 redis:8-alpine` gives one for trying several workers. |

## Database

Accounts, sessions (refresh tokens), devices, friend requests, and friendships are in a relational database through SQLAlchemy (Design §2.7). The models are in [`db/models.py`](src/pix_server/db/models.py) and the Alembic migrations in [`db/migrations/`](src/pix_server/db/migrations/).

| `PIX_DATABASE_URL` | Where the accounts are |
| --- | --- |
| Not set | `sqlite+aiosqlite:///pix.db`, a file in the working directory. For one worker on a laptop. |
| `postgresql+asyncpg://<user>:<password>@<host>:5432/<db>` | In that PostgreSQL. On the course servers the Compose file sets it to its own `postgres` service. |

- **Migrations run at start.** Every worker brings the schema to the newest revision before it takes requests. On PostgreSQL an advisory lock makes one worker migrate while the others wait, so the two workers of each copy can start together. During a deploy the old copy keeps serving on the new schema, so a revision must not break the code before it: add a column before code uses it, and drop it a release after code stops using it.
- **A new revision.** Change the models, then from `server/` run `uv run alembic upgrade head` and `uv run alembic revision --autogenerate -m "what changed"` (both use `PIX_DATABASE_URL`, or `pix.db`), read the generated file in `db/migrations/versions/`, and tidy it. `test_database.py` fails while the migrations and the models differ.

## Deploy

The server runs on the two course servers (Design §2.8, Hosting and Deployment). Each is reachable only through the course's HTTP proxy on port 80, so there is no HTTPS or WSS, and the two cannot reach each other.

| Server | Address | Branch | Use |
| --- | --- | --- | --- |
| A (`ssh swpp-a`) | `http://swpp26t10.147-46-242-169.sslip.io` | `main` | Production; release builds of the app use it. |
| B (`ssh swpp-b`) | `http://swpp26t10.147-46-242-174.sslip.io` | `dev` | Staging; set `pix.serverUrl` to it in a debug build. |

[`deploy/docker-compose.yml`](deploy/docker-compose.yml) runs nginx on port 80, Redis, PostgreSQL, and two copies of the app, `app-blue` and `app-green`, built from the [`Dockerfile`](Dockerfile). One copy serves and the other is stopped; each copy runs two workers, and both copies use the same Redis, whose port is not published and which writes nothing to disk, and the same PostgreSQL, whose port is not published either and whose files are in `/opt/pix/postgres` on the server's own disk. That is a directory mounted into the container, not a Docker volume, so the accounts survive a crash or restart of PostgreSQL, a deploy, a reboot, and even `docker compose down -v`; deleting the directory deletes them, and nothing backs it up yet. Its password is made by `deploy.sh` on the first deploy and kept in `/opt/pix/db-password`, readable by root only; PostgreSQL takes it when it first fills `/opt/pix/postgres`, so do not change or delete that file. `deploy.sh` makes the key that signs access tokens the same way, in `/opt/pix/jwt-secret`. nginx forwards `/api/v1/`, `/ws`, and `/health` to the serving copy, answers `/version` with the commit that is live, and answers `404` for everything else, so FastAPI's `/docs` and the app's `/internal` routes are not public. A new public route must be added to [`deploy/nginx/nginx.conf`](deploy/nginx/nginx.conf).

### How a deploy happens

GitHub cannot push to the servers: SSH goes through a campus-only relay with personal keys, and a self-hosted runner needs repository admin rights and is advised against on a public repository. So each server pulls. A systemd timer runs [`deploy/deploy.sh auto`](deploy/deploy.sh) every minute as root:

1. It fetches the server's branch. If the commit is already recorded, it stops.
2. If the commit does not change `server/`, it records the commit and restarts nothing.
3. It builds the image. pytest runs as a build stage, so a commit whose tests fail stops here.
4. It starts the image as the idle copy and waits for the container's health check, which also needs Redis; PostgreSQL has to be healthy before the copy starts, and the copy migrates it as it starts. Redis and PostgreSQL are started with the first copy and are left running by later deploys.
5. It waits until the serving copy has no open room, for at most 10 minutes. A room counts until its photographer leaves, or until 60 s after its last socket dropped. The two copies share their rooms through Redis, but stopping a copy closes its sockets, and the app cannot come back to its room until it can rejoin (Design §2.6.7, #56); until then the wait stays. After 10 minutes it goes on anyway: sessions that are already live continue, because the video goes phone to phone, and rooms still waiting for a subject end.
6. It points nginx at the new copy and reloads it, checks `/health` through nginx, and stops the old copy with 70 s for requests in flight (Uvicorn's graceful shutdown is 65 s, longer than a pose request may take).
7. It records the commit, which `/version` then shows, and deletes images other than the two copies'.

**A change that `deploy.sh` and the Compose file need together**, such as a new variable that `deploy.sh` must pass to Compose, cannot be deployed by the timer the first time: the timer runs the `deploy.sh` of the commit already deployed, and the new Compose file refuses to start without the variable. That try fails safely and is not retried. Deploy once by hand with the new script, taken from the fetched commit (`main` on Server A):

```sh
sudo git -C /opt/pix/repo fetch -q origin dev
sudo git -C /opt/pix/repo show FETCH_HEAD:server/deploy/deploy.sh | sudo tee /tmp/pix-deploy.sh > /dev/null
sudo bash /tmp/pix-deploy.sh deploy
```

The PR that makes such a change says so. The first was #50, which added PostgreSQL and the secrets `deploy.sh` makes.

A failure at any step leaves the serving copy as it was, and the timer does not try that `server/` tree again; fix the branch, or run `deploy.sh deploy` to retry. On GitHub, the [server-image](../.github/workflows/server-image.yml) workflow builds the image and checks the routes through nginx on every pull request, and the [server-deploy](../.github/workflows/server-deploy.yml) workflow waits after a merge until `/version` shows the commit, so a deploy that failed is a red mark on the commit.

### Set up a server once

The server needs Docker with the Compose and Buildx plugins (`sudo apt install -y docker.io docker-compose-v2 docker-buildx`). Then, on the server:

```sh
sudo git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git /opt/pix/repo
sudo /opt/pix/repo/server/deploy/setup.sh dev        # "main" on Server A
sudoedit /opt/pix/.env                               # fill in the names left empty
sudo /opt/pix/repo/server/deploy/deploy.sh deploy    # the first deploy, by hand
sudo systemctl enable --now pix-deploy.timer         # then automatic deploys
```

[`setup.sh`](deploy/setup.sh) installs no packages and creates no users. It sets the branch in `/opt/pix/deploy.conf`, keeping any other setting there, creates `/opt/pix/.env` from `.env.example` (readable by root only), and installs the systemd units. Secrets exist only in `/opt/pix/.env`; `/opt/pix/state/` holds what `deploy.sh` writes. To try a branch before it is merged, pass it to the first deploy: `deploy.sh deploy feat/2-deploy`.

### Settings

[`.env.example`](.env.example) lists every setting, and a test keeps it in step with [`config.py`](src/pix_server/config.py). A name with no value, such as `OPENROUTER_API_KEY=`, `PIX_GOOGLE_CLIENT_ID=`, or `PIX_TURN_API_TOKEN=`, must be set on every server; every other name is commented and shows the default the app already has, or says where it is set. Uncomment a name in `/opt/pix/.env` only to give a server another value, so a default changed in the code still reaches it.

Each deploy and `deploy.sh restart` compare `/opt/pix/.env` with the `.env.example` of the commit being started, and never change either file. They warn about a name the example leaves empty that `.env` has no value for, and about a name `.env` sets that the example does not list, which is probably misspelled, because the app ignores names it does not know. `pix status` shows the result, an operator sees it in one line on logging in, and the app logs which features are off at start. Fill in a value with `pix env`, then `pix restart`. A server set up before 2026-10-15 has every default uncommented in `/opt/pix/.env`, copied from the old example; those lines pin today's values, so delete the ones you did not mean to change.

### Operate it

[`deploy/pix.sh`](deploy/pix.sh) gives each account on a server a `pix` command for the operations below. Load it once from your `~/.bashrc`, from the checkout that deploys update, so everyone gets the same version:

```sh
echo '[ -f /opt/pix/repo/server/deploy/pix.sh ] && . /opt/pix/repo/server/deploy/pix.sh' >> ~/.bashrc
```

`pix help` lists the commands: `pix status`, `pix deploy [ref]`, `pix rollback`, `pix restart`, `pix logs` for the serving copy, `pix nginx`, `pix journal`, `pix build`, `pix timer`, `pix env`, `pix health`, `pix redis`, and `pix db`; `pix status` also lists the settings `/opt/pix/.env` lacks ([Settings](#settings)). They need an account that can use `sudo`. One deployment serves everyone on a server, so tell the team before a rollback, and never edit files in `/opt/pix/repo`, where each deploy checks out its commit.

Without the shortcuts, run these on the server with `sudo`:

| Command | What it does |
| --- | --- |
| `/opt/pix/repo/server/deploy/deploy.sh status` | The branch, the recorded commit, which copy serves, the open rooms, and the containers. |
| `… deploy.sh deploy [ref]` | Deploys a branch or a commit now; the default is the server's branch. It also retries a commit the timer gave up on. |
| `… deploy.sh rollback` | Switches back to the image that served before the last deploy, without waiting for rooms. The timer does not deploy the rolled-back code again until the branch changes `server/`. |
| `… deploy.sh restart` | Starts the other copy from the same image, for example after editing `/opt/pix/.env`. |
| `journalctl -u pix-deploy -n 50` | What the timer's deploys printed. `/opt/pix/state/last-build.log` has the last build in full. |
| `docker logs -n 100 pix-app-blue-1` | The app's log; `pix-app-green-1` for the other copy, `pix-nginx-1` for the access log, `pix-redis-1` for the session store, and `pix-postgres-1` for the database. Logs rotate at 10 MB, five files each. |

A deploy waits at most `MAX_WAIT_SECONDS` (600) for open rooms. Set `MAX_WAIT_SECONDS=0` in front of a `deploy.sh` command (after `sudo`), or in front of `pix deploy`, to switch without waiting for that one run. To change it for every deploy on a server, the timer's included, put `MAX_WAIT_SECONDS=<seconds>` in `/opt/pix/deploy.conf`; the timer's next run uses it, and a value given in front of a command still wins for that run.

### What the course's proxy does

Measured on Server B on 2026-10-13; nginx and the app are configured for it:

- **WebSockets pass and stay open.** One with pings stayed open for 300 s and an idle one for 200 s, although the course cuts a plain HTTP response after 120 s. Uvicorn's ping every 20 s keeps nginx's 75 s read timeout from closing a quiet socket.
- **The phone's address is the last `X-Forwarded-For` value.** The proxy adds it and passes on whatever the client sent before it. nginx takes the last value only and replaces the header on the way to the app, which Uvicorn trusts (`--forwarded-allow-ips "*"`) because nothing but nginx can reach its port. The rate limit therefore counts per phone address; phones behind one carrier or Wi-Fi gateway still share an address, so limit per user once accounts exist.
- **A cancelled request does not reach the server.** The proxy keeps its connection open after the phone closes its side, so a pose request the user cancelled is still generated and billed. The rate limit bounds the cost.
- **Uploads stay small and in memory.** nginx refuses a request over 1 MiB with its own 413 and buffers a smaller one in memory, and it never writes a pose answer to a temporary file (FR-4.9, NFR-13). What the course's proxy does with an upload is outside our control.
- **Nothing is encrypted in transit.** A scene photo, a password at sign-in, and the access and refresh tokens travel as plain HTTP between the phone and the server (NFR-14).

Docker Hub limits anonymous pulls per address, and the course servers share theirs. If a build fails with `toomanyrequests`, wait and run `deploy.sh deploy`; the base images are cached after the first pull.

## Lint, format, and test

Run these from `server/` before you push. On every pull request, the [server-lint](../.github/workflows/server-lint.yml) workflow runs the two checks and the [server-test](../.github/workflows/server-test.yml) workflow runs the tests.

| Command | What it does |
| --- | --- |
| `uv run ruff format .` | Formats Python files with Ruff. |
| `uv run ruff format --check .` | Fails if a file is not formatted. |
| `uv run ruff check .` | Ruff lint. Add `--fix` to apply the fixes Ruff can make, such as import order. |
| `uv run pytest` | Tests in `tests/`. They run in one process, without a running server, a Redis, a database server, or a phone. |
| `uv run pytest --cov=pix_server --cov-branch --cov-report=term-missing --cov-report=html` | The same tests with line and branch coverage: a summary in the terminal and `htmlcov/index.html`, with the omit list in `pyproject.toml` and the targets in the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation) (2.1.2–2.1.4). |
| `PIX_TEST_REDIS_URL=redis://localhost:6379/0 PIX_TEST_DATABASE_URL=postgresql+asyncpg://pix:pix@localhost:5432/pix uv run pytest` | The same tests on a real Redis and PostgreSQL, which they empty first (Redis's database 0, PostgreSQL's `public` schema). Either variable works alone. CI runs both ways. |

- **Rules.** Ruff rules are in `ruff.toml`: a 120-character line limit and the lint rule sets `E4`, `E7`, `E9`, `F`, and `I`.
- **Suppressing.** For one line, use `# noqa: <rule>` with a comment that says why. To turn off a rule for some files or for the whole server, add it to `per-file-ignores` or `ignore` in `ruff.toml`.
- **Tests.** Put tests in `tests/test_<module>.py`. Add tests in the pull request that writes the code. The `client`, `settings`, and `clock` fixtures in `tests/conftest.py` give an app whose clock the test moves, so expiry is tested without waiting. `client` also replaces OpenRouter with the fake `image_api`, which records requests and returns what the test sets, so tests never call the real service or need a key; `make_image` builds test photos. The session store in tests is an in-process Redis (fakeredis) that runs the real Lua scripts; `make_registry` starts registries that share it, each standing for one worker, and `outage` switches the store off and on. Each test gets a new SQLite file as its database (`database_url`), which the app migrates as it starts; `google` replaces Google's token check (a token `google:<subject>:<name>` is valid), `client` hashes passwords with argon2id at its lowest cost, and `sign_up` makes an account and returns its headers. `async def` tests need no marker, and a test fails after 15 seconds.

## Compare image-editing models

[`scripts/compare_image_edit_apis.py`](scripts/compare_image_edit_apis.py) sends scene photos to image-editing models through [OpenRouter](https://openrouter.ai/) and reports latency, cost, and the resulting images side by side. It is not part of the server, and it does not import `pix_server`.

Set `OPENROUTER_API_KEY` in your shell to an OpenRouter key with credits (the script reads the environment only, not `.env`), then run it from `server/` with one or more photos:

```sh
export OPENROUTER_API_KEY=<your key>
uv run scripts/compare_image_edit_apis.py photo1.jpg photo2.jpg
```

With no options, each photo goes to four models with all four default poses: 16 images per photo, which takes about a minute and cost about $0.50 in October 2026. Each photo is first prepared the way the app sends it (upright, long side 1024 px, JPEG quality 85, no EXIF), and the poses for one model are requested in parallel, like the app's four `POST /poses` calls.

The terminal prints one line per image, then a summary table, the total cost, and the report path. Results go to `scripts/out/<timestamp>/`:

| File | Contents |
| --- | --- |
| `report.html` | The summary table, then one table per photo: models as rows, the original and each pose as columns. Red marks a failed request or one slower than the server's 60-second upstream timeout. Click an image to see it at full size. |
| `results.json` | Every request's latency, cost, parameters, and error, plus the prompt and poses used. |
| `<photo>/<model>/<pose>.*` | The returned images, next to the prepared `scene.jpg`. |

| Option | What it does |
| --- | --- |
| `--models ID ...` | Models to compare. Add `@low` or `@medium` to a model that has quality tiers, for example `openai/gpt-image-2@low`. |
| `--list-models` | Lists every model that can edit an image, with current prices. It needs no key or photo and costs nothing. |
| `--poses NAME ...` | A subset of `hands_on_hips`, `wave`, `walking`, and `arms_crossed`. |
| `--pose-text "..."` | A free-text pose in Korean or English (FR-4.8) instead of the default poses. Repeat it for several poses, or four times for one set of a description. |
| `--prompt "..."` | Another pose prompt to try. It must contain `{pose}`, which is replaced with the pose. |
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
- **Defaults.** The default models are `DEFAULT_MODELS` at the top of the script. The pose prompt and the default poses come from the server's [prompts.toml](src/pix_server/poses/prompts.toml), so a run tries what the server sends; `--prompt` tries another wording without editing it.

## Compare suggestion models

[`scripts/compare_suggestion_models.py`](scripts/compare_suggestion_models.py) sends scene photos with the server's suggestion prompt to vision models through OpenRouter's chat completions, each photo to every model at once, and reports for each model how many answers the server could use (four different poses of at most 100 characters), the latency, and the cost. With `--description`, it sends the server's variation prompt for that description instead, and an answer is usable with three variations besides the description. It is not part of the server and does not import `pix_server`. Run it like the script above:

```sh
uv run scripts/compare_suggestion_models.py photo1.jpg photo2.jpg                          # ten models, two rounds
uv run scripts/compare_suggestion_models.py photo.jpg --models google/gemini-3.1-flash-lite z-ai/glm-5.3-flash --rounds 3
uv run scripts/compare_suggestion_models.py photo.jpg --models google/gemini-3.1-flash-lite --description "점프하기"
```

`--rounds N` calls each model N times per photo, to see the spread of latency and of the poses; `--prompt` tries another suggestion or variation prompt (`{description}` stands for the description); `--reasoning` sets the reasoning effort, by default `minimal` like the server, and a model that refuses `none` is retried with `minimal`. `report.html` shows each photo with every model's poses per round, and `results.json` keeps every answer. A call costs a fraction of a cent; 240 calls cost about $0.05 in October 2026.

## Try pose tips

[`scripts/try_pose_tips.py`](scripts/try_pose_tips.py) tries the tip prompt on scene photos with candidates made once, so the prompt can be changed and run again without paying for new images. It reads `OPENROUTER_API_KEY` from the environment; `uv run --env-file .env` passes the one in `server/.env`.

```sh
uv run --env-file .env scripts/try_pose_tips.py candidates photos/*.jpg      # one candidate per scene, made once
uv run --env-file .env scripts/try_pose_tips.py set photo.jpg --name k01     # four candidates for one scene, to pick one
uv run --env-file .env scripts/try_pose_tips.py tips --rounds 2              # a tip for every pair, twice
uv run --env-file .env scripts/try_pose_tips.py tips --prompt-file my_prompt.txt --only k01-4 --label try2
```

`candidates` and `set` prepare each scene like the app and ask for candidates the way the server does, with its pose and suggestion prompts, model, and 3:4 ratio, and keep each scene and candidate in `scripts/out/tips/pairs/<name>/`; a pair that exists is never made again. `tips` sends every pair with the server's tip prompt, schema, and reasoning effort, or with `--prompt-file` and `--reasoning`, and writes `results.json`, `report.html`, and contact sheets (`sheet-<n>.png`: the scene, the candidate, the decisions, and the sentence) to `scripts/out/tips/runs/<timestamp>-<label>/`, with the prompt it used. It prints the latency, the reasoning tokens, the sentences with a number or without an angle, and, with `--rounds`, how many pairs got the same decisions every time. `scripts/out/` is git-ignored; the photos go to OpenRouter as in the other scripts.
