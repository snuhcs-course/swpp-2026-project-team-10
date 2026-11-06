# Pix server

The Pix server is a FastAPI backend with three parts:

- **Session signaling:** room codes, the WebRTC SDP/ICE relay between one photographer and one subject, and STUN/TURN credentials from Cloudflare.
- **The pose generation REST API:** candidates from an image-editing model on [OpenRouter](https://openrouter.ai/), and poses and tips from a vision model.
- **Accounts and friends.**

Video, guide images, guide state, and remote control go directly between the phones over WebRTC. Rooms, room codes, presence, and the rate limits are in a Redis session store, so several worker processes can run ([Session store](#session-store)). A database holds the accounts, friend requests, the friend list, and invitations ([Accounts and friends](#accounts-and-friends), [Invitations and push](#invitations-and-push), [Database](#database)). Invitations and friend requests reach the friend's phone as push notifications through Firebase Cloud Messaging (FCM).

The wire contract comes from [Design Documentation §2.5.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation) and Android's [SignalMessage.kt](../android/app/src/main/java/com/lastpenguin/pix/session/signaling/SignalMessage.kt). Room behavior follows [FR-8 and FR-6](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications). The REST contract comes from Design Documentation §2.5.3 and Android's [PixApi.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/PixApi.kt) and [ApiModels.kt](../android/app/src/main/java/com/lastpenguin/pix/core/network/ApiModels.kt). Pose generation follows §2.6.3 and FR-4.

## Setup

Install [uv](https://docs.astral.sh/uv/getting-started/installation/). Then run these commands from the repository root:

```sh
cd server
uv sync --locked
```

`.env.example` lists the optional settings. To change a default, copy the file to `.env` in `server/`, or export `PIX_` environment variables. By default, a waiting room lasts 600 seconds and an empty session lasts 60 seconds. Cleanup runs every second, and the server also checks expiry when it handles a message. If you change `PIX_MAX_MESSAGE_BYTES`, set Uvicorn's `--ws-max-size` to match.

Some features need a value in `.env` or in the environment:

- **Pose generation** needs `OPENROUTER_API_KEY`, an OpenRouter key with credits. Without it, the server still runs. `POST /poses` and `POST /pose-tips` answer `UPSTREAM_ERROR`, and `POST /pose-suggestions` answers the default poses.
- **Google sign-in** needs `PIX_GOOGLE_CLIENT_ID`, the web client ID that the app passes to Google sign-in. Without it, the Google endpoints answer `503 UNAVAILABLE`. Sign-up with a Pix ID and a password still works.
- **Sessions across networks** need `PIX_TURN_KEY_ID` and `PIX_TURN_API_TOKEN`, the TURN key of Cloudflare Realtime. With the key, each phone gets Cloudflare's STUN and TURN servers with its own credentials. The credentials are valid for `PIX_TURN_TTL_SECONDS` (4 h). Without the key, each phone gets Cloudflare's STUN server only. The same happens when Cloudflare fails or takes longer than `PIX_TURN_TIMEOUT_SECONDS` (3 s).
- **Push notifications** need `PIX_FCM_CREDENTIALS_FILE` ([Push notifications](#push-notifications)).

Cloudflare's STUN server alone is enough on one Wi-Fi (Design 2.6.7). The server logs only Cloudflare's status, never the token or a credential.

## Run it locally

From `server/`, start the server:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Started this way without `PIX_REDIS_URL`, the server keeps its rooms inside the process. So use **one worker**. A restart loses the rooms. Without `PIX_DATABASE_URL`, the accounts are in `pix.db`, a SQLite file in `server/` that stays after a restart. To start again with no accounts, delete the file. For development, you can use `--reload` instead of `--workers 1`, but a reload ends the open sessions.

To run several workers, or to keep rooms after a restart, give the server a Redis ([Session store](#session-store)).

To connect a debug build to the laptop, set `pix.serverUrl` (`android/README.md`, *Server address*):

- From a phone on the same Wi-Fi, use `http://<laptop-IP>:8000/`. The Wi-Fi must allow device-to-device traffic, and the laptop must accept inbound TCP on port 8000.
- From an emulator, use `http://10.0.2.2:8000/`.

Without a TURN key, each phone gets Cloudflare's STUN server only, which is enough on one Wi-Fi. Across networks, give the server the TURN key ([Setup](#setup)), or use the AWS server. Debug builds use the AWS server by default.

Uvicorn's WebSocket ping and pong find dead connections. The protocol needs no heartbeat message of its own.

`GET /health` returns `{"status":"ok"}`. While the session store does not answer, it returns `503` with `{"status":"unavailable"}`.

## WebSocket protocol

Send JSON **text** frames to `/ws` on a persistent connection. A socket belongs to at most one room. Messages and field casing match the Android contract.

A signed-in phone sends `Authorization: Bearer <access token>` with the upgrade. While the socket is open, the user's friends see the user as online. A socket without a token, or with an unknown or expired one, works the same but shows nobody online.

| Client message | Result |
| --- | --- |
| `{"type":"room.create"}` | The sender gets `{"type":"room.created","code":"482915","sessionId":"s_…","iceServers":[…],"rejoinToken":"…"}`. `iceServers` holds its own STUN and TURN entries (Design 2.5.2). `rejoinToken` lets a new socket take its place again. |
| `{"type":"room.join","code":"482915"}` | The subject gets `{"type":"session.joined","sessionId":"s_…","role":"SUBJECT","iceServers":[…],"rejoinToken":"…"}`. The server asks for the credentials only after the join succeeds. The photographer gets `{"type":"peer.joined"}`. |
| `{"type":"session.join","sessionId":"s_…","rejoin":true,"rejoinToken":"…"}` | A phone whose socket dropped takes its place again (Design 2.6.7). It gets `{"type":"session.joined","sessionId":"s_…","role":"…","iceServers":[]}` and keeps its first credentials. With `"rejoin":true`, the other phone gets `{"type":"peer.rejoined"}`. With `"rejoin":false`, the phone has no peer connection to keep. When both phones hold their places, the photographer then gets `{"type":"peer.joined"}` and starts a new peer connection. The two phones of an invitation also take their places this way. They send `"rejoin":false` and the tokens from the invitation's endpoints ([Invitations and push](#invitations-and-push)). |
| `{"type":"signal","sdp":{"type":"offer","sdp":"v=0\r\n…"}}` | The server forwards the offer unchanged to the subject. Only the photographer sends offers. Only the subject sends answers. |
| `{"type":"signal","candidate":{"sdpMid":"0","sdpMLineIndex":0,"candidate":"candidate:…"}}` | The server forwards the candidate unchanged to the other phone. `sdpMid` may be null. An empty candidate marks the end of candidates. |
| `{"type":"leave"}` | The other phone gets `{"type":"peer.left","reason":"PEER_LEFT"}`. The sender gets no acknowledgment. |

A `signal` has exactly one non-null `sdp` or `candidate`. The unused field may be absent or null, as in Android serialization. The server rejects unexpected fields. No names and no media go through this hub.

A room code is a random six-digit string, and it can start with zeroes. No two active or recently expired codes are the same. A room that nobody joins expires after ten minutes. Its photographer then receives an `EXPIRED` error and can create a new room on the same socket. After a subject joins, the session does not expire at that deadline.

An invitation's room has no code. It expires with the invitation, 60 s after the photographer sent it. If the friend accepts, the room expires `PIX_INVITATION_JOIN_SECONDS` (15) after the acceptance, if that is later. When the friend declines, the room ends, and the photographer gets a `DECLINED` error.

A `leave` from the photographer cancels the room at once and releases both sockets. A `leave` from the subject releases that subject and tells the photographer. While the photographer stays connected, a subject can join the room again with the code. Nobody can join a room without a photographer.

When a socket disconnects unexpectedly, the other phone receives `peer.left` with reason `CONNECTION_LOST`. The server deletes a session 60 seconds after its last socket disconnects, or earlier if an unjoined code expires. Until then, either phone can take its place again with `session.join` and its token.

Each place has its own rejoin token. The session store keeps only the token's SHA-256. The token decides the place and the role. A subject that joins with the code makes the earlier subject's token useless. A `leave` from a subject ends that subject's own token.

A socket can still hold the place when the phone takes it again. The server treats that socket as the phone's old socket, because it would find a dead socket only after its pings get no answer. The place moves to the new socket at once. The old socket keeps no session, so its later disconnect sends no `peer.left`.

An error has this form: `{"type":"error","code":"NOT_FOUND","message":"No active room with this code"}`.

| Code | Meaning |
| --- | --- |
| `NOT_FOUND` | An unknown or cancelled room, or the photographer disconnected. For `session.join`, the session ended. |
| `FORBIDDEN` | A `session.join` whose token has no place in the session. |
| `FULL` | The room already has a subject, or the server found no free code. |
| `EXPIRED` | The room expired. The server remembers the code for ten more minutes. After that, the code gets `NOT_FOUND`. An invitation's room also sends `EXPIRED` to its photographer when nobody joined in time. |
| `DECLINED` | The friend declined the invitation. The server sends this code to the photographer of the invitation's room. The room is gone, and the socket can start another room. |
| `RATE_LIMITED` | The client's address sent `PIX_JOIN_FAILURE_LIMIT` (100) wrong codes within `PIX_JOIN_FAILURE_WINDOW_SECONDS` (600). A wrong code is unknown or expired. The server then refuses every join from that address, the right code included. The refusals stop when the oldest wrong code leaves the window. So nobody can guess codes. The session store keeps the count per address. |
| `UNKNOWN_TYPE` | The message type is unknown. |
| `INVALID_MESSAGE` | Malformed JSON, a binary frame, invalid fields, or an invalid signaling payload. |
| `INVALID_STATE` | The socket is already in a room, it signals before it joins, or it sends an SDP from the wrong role. For `session.join`, the socket is in another session, or it holds the other place of this session. |
| `PEER_UNAVAILABLE` | No other phone is connected to receive the signal. |

After an error, the socket stays usable. The server closes a socket with these codes:

- **1009:** a text message larger than 64 KiB.
- **1013:** the socket's outgoing queue (`PIX_OUTBOUND_QUEUE_SIZE`, 128 messages) is full, or a send takes longer than `PIX_SEND_TIMEOUT_SECONDS` (5). This disconnects a slow client. The server also closes all its sockets with 1013 when the session store fails or restarts ([Session store](#session-store)). The phone can connect again later.
- **1001:** the server stops.

The server does not log request bodies, room codes, or signaling payloads.

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

The script echoes every `camera.zoom.set` as `camera.state`, and it answers brightness and flash requests with `camera.state`. It applies a `guide.move` to its guide and echoes `guide.state` with `by: SUBJECT`. Its own guide animation then stops.

To test Subject view with one phone or an emulator:

1. Run `python tools/fake_photographer.py --guide person.png`.
2. On the phone, tap *Shoot together › I'll be in the photo*.
3. Enter the code that the script printed, and tap *Connect*.

## REST API

On a laptop, the base URL is `http://<laptop-Wi-Fi-IP>:8000/api/v1`. On a server, it is `http://<host>/api/v1`.

| Endpoint | Result |
| --- | --- |
| `POST /pose-suggestions` | Four poses for the scene, in English: `{"poses":["Lean your back against the door, cross one ankle, and look at the camera.", …]}`. The app then sends them as four descriptions. With a `description`, the answer is that description and three variations of it. The app asks once per set. |
| `POST /poses` | One candidate for a pose description: `{"templateId":null,"image":"<Base64 JPEG>","elapsedMs":9961}`. The candidate has the scene photo's ratio: 3:4, 9:16, or 1:1, whichever is closest to the photo's shape. The app sends four requests in parallel, one for each pose. The poses are the user's description and its three variations, or the suggested poses. A request with a `templateId` instead of a description gets that id in the answer. |
| `POST /pose-tips` | How to hold the phone for the candidate that the user chose: `{"text":"Zoom in or take a step closer, hold the phone at your chest and keep it level - the body keeps its proportions.","framing":"closer","hold":"chest","height":"keep","angle":"level"}`. The app asks once, when the user takes a candidate, and shows `text` on Camera. It never asks for a selfie. |
| `GET /pose-templates` | `[{"id":"hands_on_hips","label":"Hands on hips"}, …]`: the four default poses `hands_on_hips`, `wave`, `walking`, and `arms_crossed`. Apps from before Iteration 2 use it and send these ids to `POST /poses`. The current app does not call it. |

The three `POST` endpoints take `multipart/form-data`:

- **`POST /pose-suggestions`** takes `image`, the same photo as `POST /poses`, and an optional `description`. The server checks the description as for `POST /poses`.
- **`POST /poses`** takes `image`, `seed` (an integer), and either `description` or `templateId`. `image` is a JPEG with a long side of at most 1024 px and no EXIF. `description` is the pose in Korean or English.
- **Both** take an optional `selfie`, `false` when it is absent. `true` marks a selfie scene (FR-1.14), and the server then uses the selfie prompts and default poses. `POST /poses` refuses `selfie` with a `templateId`.
- **`POST /pose-tips`** takes `scene`, `candidate`, `pose`, and an optional `language`. `scene` is the photo sent to `POST /poses`. `candidate` is the chosen candidate, within the same limits, and the app sends it with a long side of 768 px. `pose` is the pose that made the candidate, folded and limited like a description. `language` is `en` or `ko`, the language of the app's screens. The server writes `text` in that language, and in `en` when `language` is absent.

The server trims a description and changes each run of whitespace to one space. The result must then have 1–100 characters. An empty `description` counts as none. A whole request may be at most 1 MiB. A photo prepared as Design §2.6.3 describes is a few hundred KB.

```sh
curl -F "image=@scene.jpg" http://localhost:8000/api/v1/pose-suggestions
curl -F "image=@scene.jpg" -F "description=벽에 기대서 카메라 보며 살짝 웃기" http://localhost:8000/api/v1/pose-suggestions
curl -F "image=@scene.jpg" -F "description=벽에 기대서 카메라 보며 살짝 웃기" -F seed=1829304756 http://localhost:8000/api/v1/poses
curl -F "scene=@scene.jpg" -F "candidate=@candidate.jpg" -F "pose=Walk slowly past the railing" http://localhost:8000/api/v1/pose-tips
curl -F "scene=@scene.jpg" -F "candidate=@candidate.jpg" -F "pose=Walk slowly past the railing" -F language=ko http://localhost:8000/api/v1/pose-tips
```

The models work as follows:

- **Candidates.** Every pose goes into one prompt, with the photo, to OpenRouter's Image API. A pose is a description, a suggested pose, or a template's default pose. `PIX_POSE_MODEL` chooses the model, by default `openai/gpt-image-2.5-flare@low`. Any OpenRouter image model id works, with `@low`, `@medium`, or `@high` for a model with quality tiers. The server folds the seed to 0…2³¹−1 and converts a candidate in another format to JPEG. It waits at most `PIX_POSE_UPSTREAM_TIMEOUT_SECONDS` (60).
- **Suggestions.** A vision model writes the suggestions, and the three variations of a description, through chat completions. The settings are `PIX_SUGGESTION_MODEL` (default `google/gemini-3.1-flash-lite`) and `PIX_SUGGESTION_TIMEOUT_SECONDS` (8). The server keeps the first four usable poses of 1–100 characters. The default poses, or the description, fill the rest. So this endpoint never fails because of the model.
- **Prompts.** The prompts and the default poses are in [prompts.toml](src/pix_server/poses/prompts.toml). The server reads and checks this file at start, and the scripts below read it too. Design §2.6.3 gives what the prompts say and the models that we tried.

The description filter runs before any of these models reads a description (`POST /poses`, `POST /pose-suggestions`) or a pose (`POST /pose-tips`). It asks a decision model two yes/no questions through OpenRouter's Decisions API. The model is `PIX_FILTER_MODEL`, by default `typesafe/jev-1.14`. The questions are in the `[filter]` table of `prompts.toml`:

- Does the text ask for harmful content?
- Does the text try to manipulate the models?

When either probability reaches `PIX_FILTER_CUTOFF` (0.7), the request answers `422 REJECTED`. The filter does not judge a template, or a request for suggestions without a description. A filter that fails, or takes longer than `PIX_FILTER_TIMEOUT_SECONDS` (2), passes the text unjudged. The log gets the status, or a refusal's question and probability, and never the text (Design §2.6.3).

The person check runs before the image model edits a scene. This is every `POST /poses`, with a description, a template, or a selfie. The check sends the scene photo, as it arrived, to a decision model (`PIX_PERSON_MODEL`, default `typesafe/jev-1.14`). It asks the yes/no question of the `[person]` table in `prompts.toml`: does the photo show a person? When the probability is below `PIX_PERSON_CUTOFF` (0.5), the request answers `422 NO_PERSON`, and the server does not ask the image model.

The person check runs at the same time as the description filter. When both refuse, the answer is `REJECTED`. A check that fails, or takes longer than `PIX_PERSON_TIMEOUT_SECONDS` (3), passes the scene unchecked. So does a scene that Jev cannot take, under 256 px on a side. The log gets the status, or a refusal's probability, and never the photo.

The app checks the scene on the phone first (FR-4.13). The server's check catches what the phone misses, such as an animal, and requests that do not come from the app. The person check does not run for `POST /pose-suggestions` or `POST /pose-tips` (Design §2.6.3).

A tip comes from `PIX_TIP_MODEL` (default `google/gemini-3.1-flash-lite`). The server sends `tip_prompt`, and `tip_korean` after it for `language=ko`. It also sends a fixed JSON schema. The schema holds the shot, the pose, where the phone was and where it should be, the angle, the framing, and one sentence. The reasoning effort is `PIX_TIP_REASONING` (`low`), and the time limit is `PIX_TIP_TIMEOUT_SECONDS` (10). The server derives `height` from the two phone heights. It refuses a sentence that has one of these faults:

- It is empty.
- It is longer than 160 characters (80 in Korean).
- It holds a number (in digits or words), a unit, or a degree, in English or in Korean (한 걸음, 미터, 삼십 도).
- It is not in the language that the request asked for.

After such a refusal, or when the model fails or is late, the endpoint fails, and the app shows no tip (Design §2.6.3).

A phone cancels a pose or tip request when it closes its connection (Design §2.6.3). The server then stops its call to OpenRouter within a quarter of a second, and OpenRouter does not bill the unfinished image. The app drops a tip request when the user takes another candidate before the tip arrives. A cancelled request still counts toward the rate limit and leaves no line in the access log. The server does not notice a phone that loses its network without closing the connection. The timeout ends that call: 60 s for a pose, 10 s for a tip.

An error has this form: `{"error":{"code":"UNKNOWN_TEMPLATE","message":"No pose template with this id"}}`.

| Status and code | Meaning |
| --- | --- |
| `400 INVALID_IMAGE` | The image is not a readable JPEG, its long side is over 1024 px, or it has EXIF metadata. For `POST /pose-tips`, this applies to the scene or the candidate. |
| `400 UNKNOWN_TEMPLATE` | No template has that `templateId`. |
| `400 INVALID_REQUEST` | A part is missing, `seed` is not an integer, or the request has both or neither of `templateId` and `description`. Also a description that is blank or longer than 100 characters, or `selfie` with a `templateId`. For `POST /pose-tips`, a part is missing, the pose is blank or longer than 100 characters, or `language` is neither `en` nor `ko`. This code replaces FastAPI's default 422, because this API uses 422 for `REJECTED`. |
| `413` | The request is larger than 1 MiB. The server refuses it before it reads the upload. So the answer has Starlette's own body (`Content Too Large`), not the error shape above. |
| `422 REJECTED` | The description filter refused the description or pose ("This description was refused", "This pose was refused"). Or the image service declined the photo or pose, through a content filter or a model refusal. The image service does not say which of the two it declined, so the message is the same with a description. The app asks for another description or another photo (US-5). For `POST /pose-tips`, the tip model declined the photos. |
| `422 NO_PERSON` | `POST /poses` only. The person check found no person in the scene photo ("The photo shows no person"). The user needs another photo, with the person in it (FR-4.13). |
| `429 RATE_LIMITED` | One client address sent more than `PIX_POSE_RATE_LIMIT` (20) requests to the image service within `PIX_POSE_RATE_WINDOW_SECONDS` (3600). Or all clients together sent more than `PIX_POSE_GLOBAL_LIMIT` (500) within `PIX_POSE_GLOBAL_WINDOW_SECONDS` (86400). The message says which. The server counts `POST /pose-suggestions` separately, in windows of the same lengths, with `PIX_SUGGESTION_RATE_LIMIT` (10) and `PIX_SUGGESTION_GLOBAL_LIMIT` (500). It counts `POST /pose-tips` in the same way, with `PIX_TIP_RATE_LIMIT` (20) and `PIX_TIP_GLOBAL_LIMIT` (1000). The server does not count a request that it refused before this point. A request that one window refuses counts in neither window. |
| `503 UNAVAILABLE` | The session store, which counts the rate limit, does not answer. The server sends nothing to the image service. |
| `502 UPSTREAM_ERROR` | The image service failed, the server could not reach it, or its answer was unreadable. Or `OPENROUTER_API_KEY` is not set. For `POST /pose-tips`, also an answer that the server cannot use. The server log gives OpenRouter's status code, for example 402 when the credits run out. It never logs the text of the answer. |
| `504 UPSTREAM_TIMEOUT` | The image service, or for `POST /pose-tips` the tip model, did not answer in time. |

The session store counts the rate limits, so every worker counts against the same windows. 20 requests are five sets of four poses. The global limit of 500 poses bounds what the image service can cost in a day, for any number of addresses. As the last bound, also set a spending limit on the OpenRouter key, in OpenRouter's dashboard. OpenRouter bills each generated image.

The scene photo, the description, the suggested poses, the candidate, and the tip exist only in memory, for the duration of the request. The server never writes them to disk or logs them (FR-4.9, NFR-13). For suggestions and variations, the log says only how many of the model's poses were usable. For a tip, the log says only that the server could not use it. The 1 MiB request limit keeps an upload in memory, because Starlette would write a larger upload to a temporary file.

## Accounts and friends

Design §2.5.3 gives the endpoints, the request and response bodies, and the error codes. The code is in [`src/pix_server/accounts/`](src/pix_server/accounts/). Most endpoints below need `Authorization: Bearer <access token>`. The exceptions are the four sign-ins, `/auth/check-pix-id`, and `/auth/refresh`. Without the token, or with an expired one, an endpoint answers `401 UNAUTHENTICATED`. Pose generation and room codes need no sign-in (FR-5.1).

| Endpoint | Result |
| --- | --- |
| `POST /auth/signup` | `{"pixId","password","displayName"}` → `201 {"accessToken","refreshToken","expiresIn","user"}`. A Pix ID has 4–20 of `a–z 0–9 . _`. A password has 8–64 characters, with a letter and a digit, and is not the Pix ID. `PIX_ID_INVALID` and `PASSWORD_INVALID` name the broken rule. A taken Pix ID gets `409 PIX_ID_TAKEN`. |
| `POST /auth/check-pix-id` | `{"pixId"}` → `{"available": true}` or `false`. *Check* on Sign up and on the Pix ID setup uses it. `400 PIX_ID_INVALID` names the broken rule. Sign-up still refuses a Pix ID that someone takes in the meantime. |
| `POST /auth/login` | `{"pixId","password"}` → `200`, as above. A wrong password and an unknown Pix ID both get `401 INVALID_CREDENTIALS`. |
| `POST /auth/google` | `{"idToken"}` → `200`, as above, or `404 NOT_REGISTERED` when no account uses that Google account. The app then asks whether the person has a Pix account. |
| `POST /auth/refresh` | `{"refreshToken"}` → `200 {"accessToken","refreshToken","expiresIn"}`. The old refresh token stops working. A refresh token used twice ends the sign-in (`401 INVALID_REFRESH_TOKEN`). |
| `POST /auth/google/signup` | `{"idToken"}` → `201`, a new account without a Pix ID, named after the Google account. |
| `PUT /me/google` | `{"idToken"}` → the account, which can now also sign in with Google (`409 GOOGLE_IN_USE`, `409 GOOGLE_ALREADY_ADDED`). |
| `PUT /me/password` | `{"password"}` → the account of a Google sign-up. The account can now also sign in with its Pix ID and this password. Before the account has a Pix ID, the answer is `403 PIX_ID_REQUIRED`. An account that already has a password gets `409 PASSWORD_ALREADY_SET`. Pix does not let a user change a password, and there is no email or password reset. |
| `DELETE /auth/session` | Ends this phone's sign-in. Its refresh token stops working, and the server deletes its device token. Its access token works until it expires, at most 15 minutes later. |
| `GET /me`, `PUT /me` | The account, with `hasPassword` and `hasGoogle`. `PUT` sets `pixId`, `displayName`, or both. A password account changes its Pix ID only with its current `password`, and the new Pix ID must not be the same as the password (`401 INVALID_CREDENTIALS`, `400 PIX_ID_INVALID`). |
| `POST /devices` | `{"fcmToken","platform":"android"}` → `204`. |
| `POST /users/lookup` | `{"pixId"}` → `{"user","relation","requestId"}` for an exact Pix ID, or `404 NOT_FOUND`. |
| `GET /friend-requests`, `POST /friend-requests`, `POST /friend-requests/{id}/accept`, `…/decline`, `DELETE /friend-requests/{id}` | In this order: list the received and sent requests, send a request, accept, decline, and withdraw. A send answers `201`. When the other user had already asked, it answers `200` with the new friend. |
| `GET /friends`, `DELETE /friends/{userId}` | `GET` answers `{"friends":[{"id","pixId","displayName","online","inSession"}]}`. `DELETE` ends a friendship. |

```sh
curl -H 'Content-Type: application/json' -d '{"pixId":"dongje.p","password":"sunset2026","displayName":"Dongje"}' http://localhost:8000/api/v1/auth/signup
curl -H "Authorization: Bearer eyJhbGciOi…" -H 'Content-Type: application/json' -d '{"pixId":"junhyeong.h"}' http://localhost:8000/api/v1/users/lookup
```

- **Passwords.** The server hashes passwords with argon2id on a worker thread and stores only the hash. Passwords travel over plain HTTP to the servers, which have no TLS (Design AD-11). The server does not limit login attempts for now.
- **Google ID tokens.** The server checks a Google ID token against Google's published keys and against `PIX_GOOGLE_CLIENT_ID`. It gets the keys from Google and keeps them for as long as Google allows. It stores only Google's subject id.
- **Access tokens.** A sign-in gives an access token and a refresh token. The access token is a JWT, signed with HS256 and `PIX_JWT_SECRET`. Its claims are `iss` (`pix`), `sub` (the user), `sid` (the sign-in), `iat`, and `exp`, `PIX_ACCESS_TOKEN_MINUTES` (15) later. The server checks the access token without the database and does not store it.
- **Refresh tokens.** The refresh token is random, and the server stores only its SHA-256 hash, in `sessions`. It lasts `PIX_SESSION_TTL_DAYS` (30) from its last use. Each refresh replaces it. When someone uses an old refresh token again, the server ends the sign-in.
- **The token key.** Without `PIX_JWT_SECRET`, each process makes its own key at start. Then access tokens end on a restart, and the app refreshes them. Several workers would also refuse each other's tokens. On a deployed server, `deploy.sh` makes the key in `/opt/pix/jwt-secret`.
- **Database down.** An account request that cannot reach the database answers `503 UNAVAILABLE`. Pose generation and rooms do not use the database.
- **Friend endpoints** answer `403 PIX_ID_REQUIRED` until the caller has a Pix ID. The lookup cannot find a user without a Pix ID. The server sends a push for a new friend request to the receiver's phones ([Invitations and push](#invitations-and-push)).
- **Logs.** nginx's error log prints the whole request line of a failed request. So no URL carries a Pix ID, and the lookup takes it in the body. Neither the server nor nginx logs a request's query string. Nothing logs a password, a token, or a request body.
- **Password hashing** runs two hashes at a time per worker. Each argon2id hash holds 64 MiB. So a burst of sign-ins waits and does not fill the memory.

## Invitations and push

Design §2.6.6 gives the flow, and §2.5.3 gives the bodies. The code is in [`src/pix_server/invitations/`](src/pix_server/invitations/). Every endpoint except `GET /invitations/{id}` needs a sign-in. To invite, the caller also needs a Pix ID.

| Endpoint | Result |
| --- | --- |
| `POST /invitations` | `{"friendId"}` → `201 {"id","to","status","expiresAt","link","sessionId","rejoinToken","iceServers"}`. The session opens at once, with only the photographer's place. The photographer's phone takes that place with `session.join`. The friend's phones get a push. An invitation to anyone but a friend answers `403 NOT_FRIENDS`, whether or not that user exists. While the friend is in a session, a waiting room included, the answer is `409 BUSY`. |
| `GET /invitations/incoming` | `{"invitations":[{"id","from","status","expiresAt"}]}`: the caller's invitations that are pending and not expired, newest first, at most 20. *See their camera* lists them, so the user can still answer an invitation after the user dismissed its notification (#188). The router declares this route before `/invitations/{id}`. Otherwise that route would read `incoming` as an id. |
| `GET /invitations/{id}` | `{"id","from","status","expiresAt"}`: what the friend's *Invitation* screen shows. It needs no sign-in. `status` is `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`, or, from 60 s on, `EXPIRED`. |
| `POST /invitations/{id}/accept` | Sent by the friend. `200 {"sessionId","rejoinToken","iceServers"}` gives the subject's place, which the friend's phone takes within `PIX_INVITATION_JOIN_SECONDS` (15). The photographer then gets `peer.joined`, as with a room code. |
| `POST /invitations/{id}/decline` | Sent by the friend. The answer is `204`. The room ends, and the photographer gets `DECLINED`. |
| `POST /invitations/{id}/cancel` | Sent by the photographer. The answer is `204`, and the room ends. |

Only the friend can accept or decline, and only the photographer can cancel. An answer from anyone else gets `404 NOT_FOUND`. An answer to an invitation that is already answered or expired gets `410 INVITATION_GONE`. Of two answers at the same moment, only one counts. An accept also gets `410` when the room is gone because the photographer left. That invitation stays pending.

An invitation expires `PIX_INVITATION_TTL_SECONDS` (60) after the photographer sent it, without a database write. When the server creates an invitation, it deletes the invitation rows that are older than a day.

- **Pushes** are FCM data messages at high priority, so the app writes the notification itself. An invitation's push is `{"type":"invitation","invitationId","fromName","expiresAt"}`, and FCM drops it after 60 s. A new friend request's push is `{"type":"friend.request","requestId","fromName"}`, and FCM drops it after a day.
- **Delivery.** The server sends each push after the response, to every phone where the user has a sign-in. So FCM never slows a request. A push that fails is lost, but the invite link still works. The server deletes a device that FCM no longer knows (404). The log shows FCM's status, never a token.
- **The invite link** is `http://<host>/i/<id>`. It opens a small page with a button. The button passes the invitation to Pix through `intent://invite/<id>#Intent;scheme=pix;package=com.lastpenguin.pix;end`. A verified App Link would open Pix directly, but it needs HTTPS. The page reads nothing from the database and names nobody. A path that is not an invitation id answers `404`.

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

Room state is in Redis, not in the server process (Design §2.7, AD-18). Redis holds the sessions, the room codes, the recently expired codes, each socket's connection record, presence, and the rate limits. The rate limits count poses, suggestions, tips, and wrong room codes. The code is in [`src/pix_server/store/`](src/pix_server/store/).

- **Workers.** Each server process is one worker. A phone can join a room through another worker than the one that created it. A signal for a socket on another worker travels over Redis pub/sub. The image runs two workers.
- **Transitions.** Each transition runs as one Lua script inside Redis ([`store/scripts.py`](src/pix_server/store/scripts.py)). The transitions are create, join, rejoin, an invitation's room (open, accept, end), leave, disconnect, and expiry. So two workers cannot seat two subjects in one room. The caller gives the script the time, so the tests can move a clock.
- **Housekeeping.** Every `PIX_CLEANUP_INTERVAL_SECONDS`, each worker renews its heartbeat, which lasts `PIX_WORKER_TTL_SECONDS` (15). The one worker that gets a lock for that interval expires rooms and deletes empty sessions. A worker can lose its heartbeat, for example after a crash. The store then drops that worker's sockets, and the other phones get `peer.left` with `CONNECTION_LOST`.
- **A worker that stops** releases its sockets at once. Its sessions stay in the store for the usual 60 s. In that time, a phone that connects again takes its place again with `session.join` (Design §2.6.7, #118).
- **Redis down or restarted.** A worker's call to Redis can fail. Or the worker can find that it is no longer registered there, because Redis restarted empty or the other workers treated it as dead. In both cases, messages for its sockets may have been lost. The worker closes all its sockets with 1013 and registers again. A live session continues phone to phone, and a waiting room ends on the phone with an error. So no waiting room keeps a code that no longer works.
- **Until Redis answers,** `/health` answers `503`, and the server closes new sockets with 1013. Pose, suggestion, and tip requests answer `503 UNAVAILABLE`. So do the account requests that read presence or change a session: `GET /friends`, accepting a friend request, and sending or answering an invitation. A Redis restart loses the open rooms. The workers continue with new rooms.

| `PIX_REDIS_URL` | Where the rooms are |
| --- | --- |
| Not set | Inside the server process, in [fakeredis](https://github.com/cunla/fakeredis-py), which runs the same scripts. This is for one worker on a laptop, with nothing to install. fakeredis is a development dependency. So the deployed image does not have it, and refuses to start without `PIX_REDIS_URL`. |
| `redis://<host>:6379/0` | In that Redis. On the servers, [`deploy/docker-compose.yml`](deploy/docker-compose.yml) sets it to its own `redis` service. To try several workers locally, `docker run --rm -p 6379:6379 redis:8-alpine` starts a Redis. |

## Database

A relational database holds the accounts, the sessions (refresh tokens), the devices, the friend requests, the friendships, and the invitations. The server uses it through SQLAlchemy (Design §2.7). The models are in [`db/models.py`](src/pix_server/db/models.py), and the Alembic migrations are in [`db/migrations/`](src/pix_server/db/migrations/).

| `PIX_DATABASE_URL` | Where the accounts are |
| --- | --- |
| Not set | `sqlite+aiosqlite:///pix.db`, a file in the working directory. For one worker on a laptop. |
| `postgresql+asyncpg://<user>:<password>@<host>:5432/<db>` | In that PostgreSQL. On the servers, the Compose file sets it to its own `postgres` service. |

- **Migrations run at start.** Every worker brings the schema to the newest revision before it takes requests. On PostgreSQL, an advisory lock lets one worker migrate while the others wait, so the two workers of each copy can start together. During a deploy, the old copy keeps serving on the new schema. So a revision must not break the code before it. Add a column before code uses it, and drop it a release after code stops using it.
- **A new revision.** `test_database.py` fails while the migrations and the models differ. To add a revision, do these steps from `server/`:
  1. Change the models.
  2. Run `uv run alembic upgrade head`.
  3. Run `uv run alembic revision --autogenerate -m "what changed"`. Both commands use `PIX_DATABASE_URL`, or `pix.db`.
  4. Read the generated file in `db/migrations/versions/`, and tidy it.

## Deploy

The server runs on the two course servers and on an AWS server (Design §2.8, *Hosting* and *Deployment*). Each course server is reachable only through the course's HTTP proxy on port 80, and only from the campus network. So the course servers have no HTTPS or WSS, and they cannot reach each other. The AWS server stays on permanently and answers from every network, cellular data included.

| Server | Address | Branch | Use |
| --- | --- | --- | --- |
| A (`ssh swpp-a`) | `http://swpp26t10.147-46-242-169.sslip.io` | `main` | Production. Release builds of the app use it. |
| B (`ssh swpp-b`) | `http://swpp26t10.147-46-242-174.sslip.io` | `dev` | Staging. To use it, set `pix.serverUrl` in a debug build. |
| AWS (`ap-southeast-2`) | `http://3.105.185.251` | `dev` | Debug builds use it by default. Every network reaches it, cellular data included. |

[`deploy/docker-compose.yml`](deploy/docker-compose.yml) runs these services:

- **nginx** on port 80. It forwards `/api/v1/`, `/ws`, `/health`, and `/i/` (the invite link's page) to the serving copy. It answers `/version` with the commit that is live, and `404` for everything else. So FastAPI's `/docs` and the server's `/internal` routes are not public.
- **`app-blue` and `app-green`**, two copies of the server, built from the [`Dockerfile`](Dockerfile). One copy serves, and the other does not run. Each copy runs two workers.
- **Redis**, which both copies use. Its port is not published, and it writes nothing to disk.
- **PostgreSQL**, which both copies use. Its port is not published either. Its files are in `/opt/pix/postgres` on the server's own disk, and nothing makes a backup of them yet.

[Changing the deploy files](#changing-the-deploy-files) is about the secrets, that directory, and the routes.

### How a deploy happens

GitHub cannot push to the course servers. SSH goes through a campus-only relay with personal keys. A self-hosted runner needs repository admin rights, and GitHub advises against one on a public repository. So each course server pulls. GitHub deploys the AWS server instead ([The AWS server](#the-aws-server)).

On a course server, a systemd timer runs [`deploy/deploy.sh auto`](deploy/deploy.sh) every minute as root. The script does these steps:

1. It runs `git fetch` for the server's branch. If the commit is already recorded, it stops.
2. If the commit does not change `server/`, it records the commit and restarts nothing.
3. It builds the image. pytest runs as a build stage, so a commit whose tests fail stops here.
4. It starts the image as the idle copy and waits for the container's health check, which also needs Redis. PostgreSQL must be healthy before the copy starts, and the copy migrates it at start. The first copy starts Redis and PostgreSQL, and later deploys leave them running.
5. It waits until the serving copy has no open room, for at most 10 minutes. A room counts until its photographer leaves, or until 60 s after its last socket dropped. The copies share their rooms through Redis, and the app takes its place again after a socket drop (Design §2.6.7, #118). But stopping a copy also closes the sockets of phones with an older app, which cannot do this, so the wait stays for them. After 10 minutes, the script continues anyway. Live sessions continue, because the video goes phone to phone, and rooms that still wait for a subject end.
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
- nginx forwards only `/api/v1/`, `/ws`, `/health`, and `/i/` (the invite link's page). Add a new public route to [`deploy/nginx/nginx.conf`](deploy/nginx/nginx.conf), but never add `/internal/*` to it. `deploy.sh` reads `/internal/stats` inside the container, to wait for open rooms before it switches copies.
- nginx's `pix` log format and the server's `HideQuery` filter omit query strings from the access logs. But nginx's error log prints the whole request line of a failed request. So nothing personal, such as a Pix ID, goes in a path or a query, and the lookup is `POST /users/lookup`.
- `deploy.sh` makes two secrets once and passes them to Compose. One is PostgreSQL's password (`/opt/pix/db-password`, `PIX_DB_PASSWORD`). PostgreSQL keeps the password that it got when it first created its files, so never generate that file again. The other is the access-token key (`/opt/pix/jwt-secret`, `PIX_JWT_SECRET`).
- PostgreSQL's files are bind-mounted from `/opt/pix/postgres` (`PIX_POSTGRES_DIR`), not kept in a Docker volume. So `down -v` and volume prunes cannot delete accounts. Never delete that directory. Anything that runs this Compose file sets `PIX_DB_PASSWORD`, `PIX_JWT_SECRET`, and `PIX_POSTGRES_DIR`, the [server-image](../.github/workflows/server-image.yml) workflow included.
- The `test` stage of the [`Dockerfile`](Dockerfile) copies `.env.example` for `tests/test_config.py`, which keeps it in step with `config.py`.

### Set up a server once

The server needs git, curl, and Docker with the Compose and Buildx plugins (`sudo apt install -y docker.io docker-compose-v2 docker-buildx`). Then, on the server:

```sh
sudo git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git /opt/pix/repo
sudo /opt/pix/repo/server/deploy/setup.sh dev        # "main" on Server A
sudoedit /opt/pix/.env                               # fill in the names left empty
sudo /opt/pix/repo/server/deploy/deploy.sh deploy    # the first deploy, by hand
sudo systemctl enable --now pix-deploy.timer         # then automatic deploys
```

[`setup.sh`](deploy/setup.sh) installs no packages and creates no users. It does these steps:

- It sets the branch in `/opt/pix/deploy.conf` and keeps any other setting there.
- It creates `/opt/pix/.env` from `.env.example`, readable by root only. It keeps an `.env` that exists.
- It creates `/opt/pix/state/`, and `/opt/pix/secrets/` for the FCM key file ([Push notifications](#push-notifications)).
- It installs the systemd units.

The API keys and tokens are only in `/opt/pix/.env`. The other secrets are the two files that `deploy.sh` makes and the FCM key file. `/opt/pix/state/` holds what `deploy.sh` writes. To try a branch before you merge it, give it to the first deploy: `deploy.sh deploy feat/2-deploy`.

### The AWS server

The AWS server stays on permanently. Its setup is the same as a course server's, with `setup.sh dev`, but without the timer. GitHub can reach it, so the [server-deploy-aws](../.github/workflows/server-deploy-aws.yml) workflow deploys it after each push to `dev`:

1. The workflow has AWS Systems Manager run the `PixDeploy` document on the server.
2. `PixDeploy` waits for any deploy that already runs there, and then runs `deploy.sh deploy`.
3. The workflow shows in the run what `deploy.sh` printed.
4. The workflow checks `/version` at the public address.

A failed deploy shows as a red mark on the commit. *Re-run jobs*, or *Run workflow* on `dev`, tries again, even for a `server/` tree that failed before.

The workflow signs in with the access key of an IAM user. That user may run only `PixDeploy`, and only on this one instance. So a leaked key can do no more than deploy `dev` again. The key is a secret of the repository's `aws` environment, which only `dev` may use. The security group needs only port 80 open, plus SSH for operators.

Do this setup once in the AWS console, in the server's region (`ap-southeast-2`):

1. **Let Systems Manager reach the server.** Create an IAM role for EC2 with the managed policy `AmazonSSMManagedInstanceCore`. Attach the role to the instance (*EC2 › Instances › Actions › Security › Modify IAM role*). Ubuntu and Amazon Linux images include the SSM agent. Within a few minutes, the instance shows as online in *Systems Manager › Fleet Manager*. If it does not, on Ubuntu, run `sudo snap restart amazon-ssm-agent`.
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

3. **Create the IAM user that the workflow signs in as**, `pix-github-deploy`, without console access. Give it this inline policy. The policy lets the user run `PixDeploy` on that one instance and read the output:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": "ssm:SendCommand",
         "Resource": [
           "arn:aws:ec2:ap-southeast-2:<account-id>:instance/<instance-id>",
           "arn:aws:ssm:ap-southeast-2:<account-id>:document/PixDeploy"
         ]
       },
       { "Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*" }
     ]
   }
   ```

   Then create an access key for the user (*Security credentials › Create access key*, for an application running outside AWS).
4. **Store the key in GitHub.** This step needs a repository admin. In *Settings › Environments*, create the environment `aws`. Limit its deployment branches to `dev`. Add the secrets `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Then delete every other copy of the key.
5. **Give the workflow the instance ID.** Put it in the workflow's `env` (`AWS_INSTANCE_ID`). It is not a secret.
6. **Disable the timer on the server** with `sudo systemctl disable --now pix-deploy.timer`. Then only the workflow deploys, and its log shows each build. If the timer stays on, deploys still work. But the run often shows only that the commit was already live.

To replace the key:

1. Create a second key.
2. Update the two secrets.
3. Delete the old key.

If you delete the AWS instance, also delete the IAM user.

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
| `/opt/pix/repo/server/deploy/deploy.sh status` | The branch, the recorded commit, which copy serves, the open rooms, and the containers. |
| `… deploy.sh deploy [ref]` | Deploys a branch or a commit now. The default is the server's branch. It also deploys a commit that the timer stopped trying. |
| `… deploy.sh rollback` | Switches back to the image that served before the last deploy, without a wait for rooms. The timer does not deploy the rolled-back code again until the branch changes `server/`. |
| `… deploy.sh restart` | Starts the other copy from the same image, for example after you edit `/opt/pix/.env`. |
| `journalctl -u pix-deploy -n 50` | What the timer's deploys printed. `/opt/pix/state/last-build.log` has the last build in full. |
| `docker logs -n 100 pix-app-blue-1` | The server's log. Use `pix-app-green-1` for the other copy, `pix-nginx-1` for the access log, `pix-redis-1` for the session store, and `pix-postgres-1` for the database. Logs rotate at 10 MB, five files each. |

A deploy waits at most `MAX_WAIT_SECONDS` (600) for open rooms. To switch without a wait for one run, put `MAX_WAIT_SECONDS=0` in front of a `deploy.sh` command (after `sudo`) or in front of `pix deploy`. To change the limit for every deploy on a server, the timer's included, put `MAX_WAIT_SECONDS=<seconds>` in `/opt/pix/deploy.conf`. The timer's next run uses it. A value in front of a command still wins for that run.

### What the course's proxy does

We measured this on Server B on 2026-10-13, and we configured nginx and the server for it:

- **WebSockets pass and stay open.** A WebSocket with pings stayed open for 300 s, and an idle one for 200 s. But the course cuts a plain HTTP response after 120 s. Uvicorn pings every 20 s, so nginx's 75 s read timeout does not close a quiet socket.
- **The phone's address is the last `X-Forwarded-For` value.** The proxy adds it after any value that the client sent. nginx takes only the last value and replaces the header on the way to the server. Uvicorn trusts the header (`--forwarded-allow-ips "*"`), because only nginx can reach its port. So the rate limit counts per phone address. Phones behind one carrier or Wi-Fi gateway still share an address.
- **A cancelled request does not reach the server.** The proxy keeps its connection open after the phone closes its side. So the image model still makes a pose that the user cancelled, and OpenRouter bills it. The rate limit bounds the cost.
- **Uploads stay small and in memory.** nginx refuses a request over 1 MiB with its own 413, and it buffers a smaller request in memory. It never writes a pose answer to a temporary file (FR-4.9, NFR-13). We cannot control what the course's proxy does with an upload.
- **Nothing is encrypted in transit.** A scene photo, a password at sign-in, and the access and refresh tokens travel as plain HTTP between the phone and the server (NFR-14).

Docker Hub limits anonymous pulls per address, and the course servers share one address. If a build fails with `toomanyrequests`, wait, then run `deploy.sh deploy`. After the first pull, the server keeps the base images in its cache.

## Lint, format, and test

Run these commands from `server/` before you push. On every pull request, the [server-lint](../.github/workflows/server-lint.yml) workflow runs the two checks, and the [server-test](../.github/workflows/server-test.yml) workflow runs the tests.

| Command | What it does |
| --- | --- |
| `uv run ruff format .` | Formats Python files with Ruff. |
| `uv run ruff format --check .` | Fails if a file is not formatted. |
| `uv run ruff check .` | Ruff lint. Add `--fix` to apply the fixes that Ruff can make, such as import order. |
| `uv run pytest` | Tests in `tests/`. They run in one process, without a running server, a Redis, a database server, or a phone. |
| `uv run pytest --cov=pix_server --cov-branch --cov-report=term-missing --cov-report=html` | The same tests with line and branch coverage. It prints a summary in the terminal and writes `htmlcov/index.html`. The omit list is in `pyproject.toml`. The targets are in the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation) (2.1.2–2.1.4). |
| `PIX_TEST_REDIS_URL=redis://localhost:6379/0 PIX_TEST_DATABASE_URL=postgresql+asyncpg://pix:pix@localhost:5432/pix uv run pytest` | The same tests on a real Redis and PostgreSQL, which they empty first (Redis's database 0, PostgreSQL's `public` schema). Either variable works alone. CI runs both ways. |

- **Rules.** Ruff rules are in `ruff.toml`: a 120-character line limit and the lint rule sets `E4`, `E7`, `E9`, `F`, and `I`.
- **Suppressing.** For one line, use `# noqa: <rule>` with a comment that says why. To disable a rule for some files or for the whole server, add it to `per-file-ignores` or `ignore` in `ruff.toml`.
- **Tests.** Put tests in `tests/test_<module>.py`. Add tests in the pull request that writes the code. `async def` tests need no marker, and a test fails after 15 seconds.
- **Fixtures.** The `client`, `settings`, and `clock` fixtures in `tests/conftest.py` give a server whose clock the test moves. So tests check expiry without a wait. `client` also replaces OpenRouter with the fake `image_api`. The fake records requests and returns what the test sets, so tests never call the real service or need a key. `make_image` builds test photos.
- **Session store in tests.** It is an in-process Redis (fakeredis) that runs the real Lua scripts. `make_registry` starts registries that share it, each one for one worker. `outage` stops the store and starts it again.
- **Accounts in tests.** Each test gets a new SQLite file as its database (`database_url`), which the server migrates at start. `google` replaces Google's token check, and a token `google:<subject>:<name>` is valid. `fcm_api` replaces Google's token endpoint and FCM, and records each push, with a service account key made for the tests (`fcm_key`). `client` hashes passwords with argon2id at its lowest cost. `sign_up` makes an account and returns its headers.

## Compare image-editing models

[`scripts/compare_image_edit_apis.py`](scripts/compare_image_edit_apis.py) sends scene photos to image-editing models through [OpenRouter](https://openrouter.ai/). It reports latency, cost, and the resulting images side by side. It is not part of the server, and it does not import `pix_server`.

Set `OPENROUTER_API_KEY` in your shell to an OpenRouter key with credits. The script reads only the environment, not `.env`. Then run it from `server/` with one or more photos:

```sh
export OPENROUTER_API_KEY=<your key>
uv run scripts/compare_image_edit_apis.py photo1.jpg photo2.jpg
```

With no options, each photo goes to four models with all four default poses. That is 16 images per photo. A run takes about a minute, and it cost about $0.50 in October 2026. The script first prepares each photo the way the app sends it: upright, long side 1024 px, JPEG quality 85, no EXIF. It requests the poses for one model in parallel, like the app's four `POST /poses` calls.

The terminal shows one line per image, then a summary table, the total cost, and the report path. The results go to `scripts/out/<timestamp>/`:

| File | Contents |
| --- | --- |
| `report.html` | The summary table, then one table per photo: models as rows, the original and each pose as columns. Red marks a failed request, or one slower than the server's 60-second upstream timeout. Click an image to see it at full size. |
| `results.json` | Every request's latency, cost, parameters, and error, plus the prompt and poses used. |
| `<photo>/<model>/<pose>.*` | The returned images, next to the prepared `scene.jpg`. |

| Option | What it does |
| --- | --- |
| `--models ID ...` | Models to compare. Add `@low` or `@medium` to a model that has quality tiers, for example `openai/gpt-image-2@low`. |
| `--list-models` | Lists every model that can edit an image, with current prices. It needs no key or photo and costs nothing. |
| `--poses NAME ...` | A subset of `hands_on_hips`, `wave`, `walking`, and `arms_crossed`. |
| `--pose-text "..."` | A free-text pose in Korean or English (FR-4.8) instead of the default poses. Repeat it for several poses, or four times for one set of a description. |
| `--prompt "..."` | Another pose prompt to try. It must contain `{pose}`, which the script replaces with the pose. |
| `--concurrency N` | Runs N photo-and-model combinations at once. The default, 1, matches one user who generates poses. |
| `--out DIR` | The output folder, instead of the timestamped one. |
| `--timeout S` | Stops a request after S seconds. The default is 60. |

```sh
# Smoke test: one image from one model
uv run scripts/compare_image_edit_apis.py photo.jpg --models bytedance-seed/seedream-5-0-flash --poses wave

# Many photos, two models, several combinations at once
uv run scripts/compare_image_edit_apis.py photos/*.jpg --models google/gemini-3.1-flash-image bytedance-seed/seedream-5-0-flash --concurrency 4

# A free-text pose
uv run scripts/compare_image_edit_apis.py photo.jpg --pose-text "벽에 기대서 한 손은 주머니에"
```

- **Cost.** A run requests photos × models × poses images. OpenRouter bills only returned images, not failed or timed-out requests. If lines show `Insufficient credits`, the OpenRouter account is empty.
- **Photos.** Use JPEG, PNG, or WebP, with the whole person in the frame. HEIC does not open. The script asks for each candidate in the model's ratio that is closest to the photo's ratio. The app's frames are portrait 3:4, 9:16, and 1:1.
- **Privacy.** The script sends the photos to OpenRouter and to the model's provider. `scripts/out/` holds the photos and results, and git ignores it. Do not commit them.
- **Defaults.** The default models are `DEFAULT_MODELS` at the top of the script. The pose prompt and the default poses come from the server's [prompts.toml](src/pix_server/poses/prompts.toml), so a run tries what the server sends. `--prompt` tries another wording without an edit to that file.

## Compare suggestion models

[`scripts/compare_suggestion_models.py`](scripts/compare_suggestion_models.py) sends scene photos with the server's suggestion prompt to vision models, through OpenRouter's chat completions. It sends each photo to every model at once. For each model, it reports the latency, the cost, and how many answers the server could use. A usable answer has four different poses of at most 100 characters. With `--description`, the script sends the server's variation prompt for that description instead. Then a usable answer has three variations besides the description.

The script is not part of the server and does not import `pix_server`. Run it like the script above:

```sh
uv run scripts/compare_suggestion_models.py photo1.jpg photo2.jpg                          # ten models, two rounds
uv run scripts/compare_suggestion_models.py photo.jpg --models google/gemini-3.1-flash-lite z-ai/glm-5.3-flash --rounds 3
uv run scripts/compare_suggestion_models.py photo.jpg --models google/gemini-3.1-flash-lite --description "점프하기"
```

- `--rounds N` calls each model N times per photo, to see the spread of latency and of the poses.
- `--prompt` tries another suggestion or variation prompt. In it, `{description}` stands for the description.
- `--reasoning` sets the reasoning effort, by default `minimal` like the server. When a model refuses `none`, the script tries it again with `minimal`.

`report.html` shows each photo with every model's poses per round, and `results.json` keeps every answer. A call costs a fraction of a cent, and 240 calls cost about $0.05 in October 2026.

## Compare filter models

[`scripts/compare_filter_models.py`](scripts/compare_filter_models.py) sends labeled descriptions to decision models through OpenRouter's Decisions API. It asks the two yes/no questions of the description filter (Design Documentation 2.6.3). One asks whether the text asks for harmful content. The other asks whether the text tries to manipulate the AI. The questions come from the `[filter]` table of the server's [prompts.toml](src/pix_server/poses/prompts.toml), or of another prompts file with `--prompts`. For each model and language, the script reports how many harmless descriptions the model would refuse at each cutoff. It also reports how many attacks the model would catch.

The descriptions are in [`scripts/filter_cases.toml`](scripts/filter_cases.toml), in English and Korean. The file's header says how to write a case. `--cases` takes another file in the same form. The script is not part of the server and does not import `pix_server`. It reads `OPENROUTER_API_KEY` from the environment.

```sh
uv run --env-file .env scripts/compare_filter_models.py                                   # seven models, two rounds
uv run --env-file .env scripts/compare_filter_models.py --models typesafe/jev-1.14 --rounds 1
uv run --env-file .env scripts/compare_filter_models.py --cases my_cases.toml --label teammates
```

`--cutoffs` sets the cutoffs to count at, by default 0.5, 0.7, and 0.9. `--cutoff` sets the cutoff for the details, by default the filter's 0.7. The details are these:

- the decisions by category
- how often a second round changed a decision
- how often the English and the Korean of a request got the same decision
- every description that each model got wrong

`summary.txt` and `results.json`, with every probability, go to `scripts/out/filter/<timestamp>[-label]/`. The default run, 107 descriptions with seven models in two rounds, cost $0.05 in November 2026.

## Try the person check

[`scripts/try_person_check.py`](scripts/try_person_check.py) sends labeled scene photos to a decision model through OpenRouter's Decisions API (FR-4.13, Design Documentation 2.6.3). It asks the person check's question from the `[person]` table of the server's [prompts.toml](src/pix_server/poses/prompts.toml), or of another prompts file with `--prompts`. Give the scenes with a person after `--with-person`, and the scenes without one after `--without-person`. Each scene is a JPEG or PNG file, or a folder of them. The script prepares each scene like the app's scene photo.

The script prints each scene's probabilities. At `--cutoff` (by default the server's 0.5), it prints how many scenes with a person the check would refuse. It also prints how many scenes without one would pass, with the time and the cost. The script is not part of the server and does not import `pix_server`. It reads `OPENROUTER_API_KEY` from the environment.

```sh
uv run --env-file .env scripts/try_person_check.py --with-person people/ --without-person empty/
uv run --env-file .env scripts/try_person_check.py --with-person people/ --without-person empty/ --rounds 2 --cutoff 0.4
uv run --env-file .env scripts/try_person_check.py --without-person empty/ --prompts my_prompts.toml --label wording2
```

`results.json`, with every probability, goes to `scripts/out/person/<timestamp>[-label]/`. 53 scenes in one round cost $0.005 with Jev 1.14 in November 2026. The photos go to OpenRouter as in the other scripts.

## Try pose tips

[`scripts/try_pose_tips.py`](scripts/try_pose_tips.py) tries the tip prompt on scene photos, with candidates that it makes once. So you can change the prompt and run it again without paying for new images. The script reads `OPENROUTER_API_KEY` from the environment. `uv run --env-file .env` passes the key in `server/.env`.

```sh
uv run --env-file .env scripts/try_pose_tips.py candidates photos/*.jpg      # one candidate per scene, made once
uv run --env-file .env scripts/try_pose_tips.py set photo.jpg --name k01     # four candidates for one scene, to pick one
uv run --env-file .env scripts/try_pose_tips.py tips --rounds 2              # a tip for every pair, twice
uv run --env-file .env scripts/try_pose_tips.py tips --language ko --label ko # the tips in Korean, as for a phone in Korean
uv run --env-file .env scripts/try_pose_tips.py tips --prompt-file my_prompt.txt --only k01-4 --label try2
```

`candidates` and `set` prepare each scene like the app. They ask for candidates the way the server does, with its pose and suggestion prompts, model, and ratio. They keep each scene and candidate in `scripts/out/tips/pairs/<name>/`. The script never makes a pair again when it exists.

`tips` sends every pair with the server's tip prompt, schema, and reasoning effort. `--prompt-file` and `--reasoning` replace the prompt and the effort. For `--language ko`, the script adds the server's `tip_korean` after the prompt. It writes these files to `scripts/out/tips/runs/<timestamp>[-label]/`, with the prompt that it used:

- `results.json`
- `report.html`
- contact sheets (`sheet-<n>.png`): the scene, the candidate, the decisions, and the sentence

The script prints the latency, the reasoning tokens, and the sentences with a number or without an angle. With `--rounds`, it also prints how many pairs got the same decisions every time. git ignores `scripts/out/`. The photos go to OpenRouter as in the other scripts.

## Evaluate the AI modules

[`scripts/eval_poses.py`](scripts/eval_poses.py) runs the AI module tests of the [Testing Documentation](../wiki/Testing-Documentation.md) (3.1) on the frozen set in [`eval/`](eval/). The set is `pose_cases.json`, with one scene photo of `eval/scenes/` and one description per case. `eval/scenes/SOURCES.md` gives the photos' sources and licences. The script has three main commands:

- `generate` sends each case to a running Pix server twice, with its description and without one, the way the app's `RemotePoseGenerator` does. It keeps every scene, candidate, time, and tip in `scripts/out/eval/<run>/`.
- `judge` has Claude (`claude-opus-5-5`) check every candidate against the rules of [`eval/rubrics.toml`](eval/rubrics.toml). Claude also checks that the four poses of each set are different, and that each tip matches its pose. `judge` uses the Batch API at half price, unless you give `--now`.
- `report` gives the metrics of 3.1 for one run or several.

The script does not import `pix_server`. uv installs the Anthropic SDK for this script alone, from the header of the file. `judge` reads `ANTHROPIC_API_KEY` from the environment.

A run needs a server with high enough limits for 100 sets, 400 candidates, and 100 tips. Start one locally, with the key in `server/.env` and a database of its own. Then run the steps:

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

- `generate` makes one set at a time, as one phone would, so the times are a phone's times. It skips a set that it already made.
- `judge` sends only what has no verdict with the current rubric. Each verdict keeps the rubric's hash, so a changed rubric judges a run again. `--no-wait` sends the batches and stops, and a second run of the command collects them.
- `report` prints the metrics with 95 % intervals and writes `summary.txt`, `summary.json`, and `report.html`. The report shows every set with its candidates and the judge's reasons, with the sets that broke a rule first.

To check the judge (3.1, *Judge validation*):

1. `sample` picks 60 candidates of a judged run into `label.html`. Half are candidates that the judge failed, and half are candidates that it passed.
2. Each labeler opens `label.html` in a browser and answers every rule without the judge's verdicts. Then the labeler downloads the labels.
3. Keep the labels in `eval/labels/` as `<run>-<name>.json`.
4. `judge --repeat 3` judges the same 60 candidates twice more.
5. `agree` compares the labels with each other and with the judge. It also counts the verdicts that changed across the three judgments.

Two more commands help with a changed rubric:

- `judge --sample` judges only those 60 candidates. Use it to try a changed rubric on them before you judge whole runs again.
- `sample --by-rubric HASH` splits a new sample by an earlier rubric's verdicts. Use it to check a changed rubric on the cases that the old rubric failed.

```sh
uv run scripts/eval_poses.py sample run1 --size 60
uv run --env-file .env scripts/eval_poses.py judge run1 --repeat 3
uv run scripts/eval_poses.py agree run2 --labels eval/labels/run2-*.json      # the labels are kept in eval/labels/
```

The scripts above measure the description filter and the person check on their own held-out sets:

- The filter: `compare_filter_models.py --cases eval/filter_cases.toml`.
- The person check: `try_person_check.py` with `eval/person/with/` and `eval/person/without/`. The labels are in `eval/person/labels.json`.

The phone's person check runs on a device with the app's `PersonCheckEval` (`android/app/src/androidTest/`):

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

A full run's images cost an estimated $5 through OpenRouter, and its judgments about $7.50 with the Batch API. The judging cost comes from the 10-case pilot of 2026-11-05. With direct calls, a pose cost about $0.030, a tip $0.018, and a set of suggestions $0.008. Each call had about 4K input and 0.6K output tokens, at $4 and $20 per million tokens. A batch costs half that. The photos go to OpenRouter and to Anthropic.
