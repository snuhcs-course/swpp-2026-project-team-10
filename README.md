# Pix

Pix is an Android camera app that helps two people get the portrait one of them has in mind. When friends photograph each other, the person in the photo usually knows the pose and framing they want, but only the photographer sees the screen. Pix gives both people the same view: the photographer puts a pose guide on the camera, and the friend's phone shows the live camera with the same guide and can adjust the shot. The photographer takes the photo, and it is saved at full quality without the guide.

This branch is the **Iteration 2 build**: the Pix server runs on the course's public servers, Pix accounts and friends exist, and the subject's phone can move the guide and change the brightness and the flash as well as the zoom. Two phones still connect with a room code on one Wi-Fi network; invitations and sessions across networks come in Iteration 3.

## Contents

- [Demo videos](#demo-videos)
- [What Iteration 2 does](#what-iteration-2-does)
- [Known limitations and to-dos](#known-limitations-and-to-dos)
- [How it works](#how-it-works)
- [Technology stack](#technology-stack)
- [Development environment](#development-environment)
- [Run the demo](#run-the-demo): [1. Get the code](#1-get-the-code) · [2. Choose a server](#2-choose-a-server) · [3. Install the app](#3-install-the-app-on-both-phones) · [4. Try the flows](#4-try-the-flows)
- [Tests and checks](#tests-and-checks)
- [Repository](#repository)
- [Documents](#documents)
- [Team](#team)

## Demo videos

Recorded on a Galaxy S23 Ultra (photographer) and a Galaxy S25 Ultra (subject) with the Iteration 2 build on `dev`, against the course server (#98). They are also the evidence of the integration run in [Testing Documentation 2.2.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#222-iteration-2--integration-tests-215); the full-size files are in the team's shared folder.

| Video | What it shows | Player |
|---|---|---|
| **Camera basics** (47 s) | The aspect ratio 4:3 → 16:9 → 1:1 with a photo saved in each, the brightness slider, the flash button, zoom, and the last-photo thumbnail | <video src="https://github.com/user-attachments/assets/42a0c887-c326-4025-810d-a0bbcffeb2ee" controls width="220"></video> |
| **Guide from a photo** (37 s) | *Add a pose guide* with saved guides, a reference photo turned into an outline guide, drag and pinch, opacity, hide, *Save guide*, and *Replace the pose guide?* | <video src="https://github.com/user-attachments/assets/8e64ef8c-8fef-4398-ba66-bbedf2b953d0" controls width="220"></video> |
| **Sign-in and account** (42 s) | *Friends* signed out, *Continue with Google*, the account sheet with *Edit name*, *Add a password*, and *Sign out* | <video src="https://github.com/user-attachments/assets/88f5413d-0cb8-4226-9d46-2cf4788362fc" controls width="220"></video> |
| **Shoot together** (81 s, both phones) | A room-code session: the live view, a guide from a reference on both phones, the photographer's moves followed on the subject's phone, a ratio change, the scene photo with a typed description and *Generating poses*, and the subject's brightness applied on the photographer's phone | <video src="https://github.com/user-attachments/assets/724a8990-4207-4f40-8cce-ba2a923e4c07" controls width="320"></video> |
| **Midterm demo: guide** (43 s, both phones) | Shown at the midterm presentation on 10/21, recorded on 10/15: a room-code session and a guide from a reference synced to the subject | — |
| **Midterm demo: pose generation** (44 s, both phones) | The same session with *Generate poses here*, "3 of 4 ready", and the chosen pose as the guide on both phones | — |

## What Iteration 2 does

| Feature | What you can do |
|---|---|
| **Camera** | Pix opens straight to the rear camera. Choose the aspect ratio 4:3, 16:9, or 1:1; the preview, the grid, the guide, the saved photo, and the subject's live view all follow it. Tap to focus, drag the brightness slider (in EV), cycle the flash, and pinch to zoom across the phone's real range, including an ultrawide lens where the phone has one, without stutter. Photos are saved at full resolution to a "Pix" album, without the guide. |
| **Guide from a photo** | Pick a gallery photo. Pix separates the person on the phone with ML Kit Subject Segmentation and draws a smooth white outline, never the photo's pixels. The guide starts where the person stands in the reference, and a thumbnail of the reference sits in a corner of the preview (tap to enlarge, drag to another corner). The photo never leaves the phone. |
| **Guide controls** | Drag the guide, pinch it to 30–300 % of its size, fade it to 10–90 %, hide and show it, replace it, or remove it. A guide-edit mode toggle decides what a pinch does: with it on, gestures edit the guide; with it off, a pinch zooms the camera. *Save guide* keeps up to 20 guides on the phone, listed on *Add a pose guide* to reuse or delete. |
| **AI pose generation** | Take a photo of the scene on the Camera screen, then describe the pose in words (with idea chips) or let Pix suggest four poses for the scene. The Pix server asks an image-editing model for four candidates of the same person in the same place, one prompt per candidate; a description gets the description plus three variations of it. Candidates appear as they arrive; the chosen one becomes the guide. |
| **Pix ID and friends** | Sign up with a Pix ID and a password, or continue with Google and choose a Pix ID; a Google account can add a password and a password account can add Google. Find friends by their exact Pix ID, send and answer friend requests, withdraw a sent request, and see which friends are online. Everything else in Pix works without signing in. |
| **Shoot together** | *Shoot together* opens *Friends*, where *Use a room code* gives the photographer a 6-digit code; the friend joins with it on the same Wi-Fi. The friend's phone shows the live camera over WebRTC in the photographer's aspect ratio with the same guide. |
| **Remote controls from the friend's phone** | Pinch to zoom the photographer's camera, drag the brightness slider, tap the flash, and, with the guide-edit toggle on, move and resize the guide. The photographer's phone applies each request, echoes what it applied so both phones agree, and shows a short notice ("Dongje moved the guide"). Only the photographer takes photos. |

The plan for every iteration is in [Requirements and Specifications §4.1](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications#41-scope-by-iteration).

## Known limitations and to-dos

Moved from Iteration 2 to Iteration 3 (decided 2026-10-23; the design is written in Design Documentation 2.6.5–2.6.7):

- **Invitations** (Iter2-P15): *Invite* on *Friends* is disabled. There are no push notifications and no invite link yet; two phones connect with a room code.
- **Sessions across networks** (Iter2-P16): no TURN relay and no automatic reconnection. Both phones must be on the same Wi-Fi; from another network a join opens *Subject view* and ends on *Connection lost*. The course servers are reachable only from the campus network, which is also why the cellular pairs of the test matrix did not run.
- **Could items**: the switch that lets the photographer block remote control, remote tap-to-focus, the guide color switch, and the shutter freeze.

Known limitations of the Iteration 2 build:

- The server asks the image model for a 3:4 candidate whatever the scene's ratio, so a 16:9 or 1:1 scene gets a guide of the wrong shape (Design 2.6.3; the fix is planned for Iteration 3).
- The guide image sent to the subject is still the cutout as a WebP with alpha; the mask-only transfer decided on 2026-10-16 is not built (Design 2.5.1).
- The zoom fix (Iter2-P5) is measured on the S23 Ultra, which zooms to 0.6× natively; the S21, the test phone with a separate ultrawide lens, is still to be measured (Testing Documentation IT-10).
- The timing targets (live-view delay, control round trip) were not measured in Iteration 2; the integration run was recorded on video without a `Timings` log.
- Google sign-in needs the phone's debug key registered in the team's Google Cloud project; sign-up with a Pix ID and a password works on every build.
- The first segmentation downloads the model through Google Play services, which can take a minute; the first pose set takes 15–60 s.

### Next: Iteration 3

- Invitations with push notifications and an invite link, the TURN relay, and reconnection (the items above)
- Connecting two phones without internet with a QR code and a hotspot
- Selfie mode, a settings screen with the guide color and a frame-delay indicator, and a Korean language setting
- Sending the photos to the subject
- The heuristic evaluation and the user acceptance test stories

## How it works

![Architecture](wiki/images/design_target_architecture.png)

- **Pix server** (FastAPI, on the course's two servers behind nginx, two workers each): creates room codes and relays the WebRTC connection setup between the two phones (a session store in Redis lets any worker serve any socket), keeps accounts, Pix IDs, friends, and presence in PostgreSQL, and forwards pose requests to the image and vision models through OpenRouter, which keeps the API key off the phones. It never sees the video. Server A runs `main` for release builds; Server B runs `dev`. A new commit on either branch is built into a Docker image and deployed blue-green by a timer on the server.
- **Phone to phone** (WebRTC): the live video, the guide image, guide positions, and the subject's zoom, brightness, flash, and guide moves go directly between the two phones over two data channels. Each phone draws the guide itself, so it never appears in the video or in saved photos.
- **On the phone**: the camera (CameraX), the person separation for guides (ML Kit), saved guides, and the sign-in tokens stay on the phone.

The dashed parts of the figure, push notifications and the relay, are Iteration 3. The full design is in the [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation).

## Technology stack

| Part | Stack |
|---|---|
| Android app | Kotlin 2.2, XML Views with ViewBinding, Navigation, ViewModel and coroutines · CameraX 1.6 · ML Kit Subject Segmentation · WebRTC (`io.github.webrtc-sdk:android` 137) · OkHttp 4.12 and Retrofit 2.11 with kotlinx.serialization · Jetpack DataStore · Credential Manager with Google ID |
| Server | Python 3.11 · FastAPI and Uvicorn (WebSocket signaling and REST API) · SQLAlchemy 2 with asyncpg and Alembic on PostgreSQL 18 · Redis (session store) · PyJWT and argon2 · pydantic-settings · httpx2 · Pillow · Docker, nginx, and a blue-green deploy script on the course servers |
| AI | OpenRouter: GPT Image 2.5 Flare at low quality for the pose candidates, Gemini 3.1 Flash Lite for the pose suggestions (both server settings) |
| Tests and CI | JUnit 4, Robolectric 4.17, and FragmentScenario with JaCoCo · pytest with pytest-cov, fakeredis, and SQLite · Spotless with ktlint · Ruff · GitHub Actions (lint, tests, the server image, the deploy, the wiki) |

## Development environment

| Part | Requirements |
|---|---|
| Android app | Android Studio with its bundled JDK (21 or later is needed for the unit tests), Kotlin 2.2, Gradle 9.5 through the wrapper. minSdk 29 (Android 10), target and compile SDK 36. |
| Server | Only for a local server: Python 3.11 or later and [uv](https://docs.astral.sh/uv/getting-started/installation/), and an [OpenRouter](https://openrouter.ai/) API key with credits for pose generation. Redis and PostgreSQL are optional locally (the server keeps rooms in the process and accounts in a SQLite file without them). |
| Tested on | Galaxy S21, S22, S23 Ultra, and S25 Ultra |
| Network | Both phones on one Wi-Fi network that allows traffic between devices and reaches the course server (the campus network does; a phone hotspot works for the phones and needs a local server for the laptop). Cellular data cannot reach the course servers yet. |

## Run the demo

### 1. Get the code

```sh
git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git
cd swpp-2026-project-team-10
git checkout iteration-2-demo
```

### 2. Choose a server

**The public server (no laptop needed).** Server A, `http://swpp26t10.147-46-242-169.sslip.io/`, runs this iteration's `main`; a release build of the app uses it by default. For a debug build, create `android/local.properties` with

```properties
pix.serverUrl=http://swpp26t10.147-46-242-169.sslip.io/
```

(or Server B, `http://swpp26t10.147-46-242-174.sslip.io/`, which runs `dev`). Check `http://swpp26t10.147-46-242-169.sslip.io/health` from a phone's browser on the campus network: it answers `{"status":"ok"}`.

**A server on the laptop.** From the repository root:

```sh
cd server
uv sync --locked
cp .env.example .env
```

Put your OpenRouter key in `server/.env` as `OPENROUTER_API_KEY=...`; without it everything except pose generation works. Then start one worker:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

Rooms stay in the process and accounts in `server/pix.db`. Find the laptop's Wi-Fi address (on macOS, `ipconfig getifaddr en0`), check `http://<laptop-IP>:8000/health` from a phone, and put `pix.serverUrl=http://<laptop-IP>:8000/` in `android/local.properties`. On an emulator, skip `local.properties`: the default `http://10.0.2.2:8000/` already reaches the laptop. More in the [server README](server/README.md).

### 3. Install the app on both phones

Connect a phone with USB debugging on, then from `android/`:

```sh
./gradlew :app:installDebug
```

Repeat for the second phone. For Google sign-in on a debug build, the phone's debug key must be an Android OAuth client in the team's Google Cloud project (`./gradlew :app:signingReport`, then a project owner adds the `SHA1` of `Variant: debug`); sign-up with a Pix ID and a password needs nothing.

### 4. Try the flows

1. **Camera.** Pix opens on the camera. Tap the ratio button in the top bar to cycle 4:3, 16:9, and 1:1; tap the sun button for the brightness slider; tap the flash button; tap the preview to focus; pinch to zoom. Take a photo: "Saved without the guide" appears and the thumbnail changes.
2. **Guide from a photo.** Tap *Guide* › *Upload a reference*, pick a photo of a person, and tap *Use this guide*. The outline starts where the person stands in the photo. With the guide-edit toggle on (it turns on with a new guide), drag and pinch the guide; the bar below the preview fades, hides, saves (bookmark), or removes it. Turn the toggle off and a pinch zooms the camera again. *Guide* now asks whether to replace the guide and lists the saved ones. The first time, the segmentation model is downloaded through Google Play services, which can take a minute.
3. **AI poses.** Tap *Guide* › *Generate poses here*, accept the notice, take a photo of the scene, and tap *Use this photo*. Type a pose (or tap an idea chip) and *Generate 4 poses*, or *Skip, suggest poses for me*. Candidates fill in as they arrive, usually within 30 s; tap one, *Use this pose*, and *Use this guide*. Every set is billed as up to four images.
4. **Pix ID and friends** (two phones). Tap *Shoot together*. On one phone *Sign in to add friends* › *New to Pix? Sign up* with a Pix ID and a password; on the other *Continue with Google* and choose a Pix ID. Tap *Add a friend*, search the other's Pix ID, *Add*; the other phone sees the request on *Friends* within 10 s and accepts. Each friend shows as online while Pix is open. The account icon opens the sheet with *Edit name*, *Add a password* or *Add Google*, and *Sign out*.
5. **Shoot together.** On the photographer's phone, *Shoot together* › *Use a room code* shows a code over the live camera. On the friend's phone, *Shoot together* › *Use a room code instead* › *Join with a code instead*, enter the code, and *Join*. The friend sees the live camera in the photographer's ratio with the same guide. On the friend's phone: pinch to zoom, drag the brightness slider, tap the flash, and turn the guide-edit toggle on to move and resize the guide; the photographer's phone follows and shows a notice for each. The photographer takes the photo. *Leave* or *End session* ends it.

With one phone, `server/tools/fake_photographer.py` or `server/tools/fake_subject.py` can play the other side against a local server; see the [server README](server/README.md#testing-the-photographers-phone-without-a-second-phone).

## Tests and checks

Every pull request runs these in GitHub Actions; run them locally before you push.

| Part | From | Command |
|---|---|---|
| Android | `android/` | `./gradlew spotlessCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` |
| Android, with coverage | `android/` | `./gradlew :app:jacocoTestReport` → `app/build/reports/jacoco/jacocoTestReport/html/index.html` |
| Server | `server/` | `uv run ruff check . && uv run ruff format --check . && uv run pytest` |
| Server, with coverage | `server/` | `uv run pytest --cov=pix_server --cov-branch --cov-report=html` → `htmlcov/index.html` |

At the close of Iteration 2: 756 app tests and 431 server tests, app line coverage 79.6 % (managers 93.5 %, ViewModels 95.4 %, Views 91.1 %, platform adapters 43.2 %), server 98.1 %; the targets, the integration scripts, and the results are in the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation). More detail is in the [Android README](android/README.md) and the [server README](server/README.md).

## Repository

| Folder | Contents |
|---|---|
| `android/` | The Android app (open this folder in Android Studio) |
| `server/` | The Pix server, its tests, the deploy files for the course servers, and tools for testing with one phone |
| `wiki/` | The source of the GitHub wiki, published on every push to `dev` |
| `.github/` | CI workflows and the pull request template |

Branches: `main` holds the stable version and `dev` is the integration branch. Each task is a branch from `dev` that returns through a reviewed, squash-merged pull request. `iteration-N-demo` holds the code shown in that iteration's demo, and at the end of an iteration `dev` is merged into `main`.

## Documents

- [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications)
- [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation)
- [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation)
- [AI Collaboration Report – Iteration 2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/AI-Collaboration-Report-%E2%80%93-Iteration-2)
- [Team Meetings](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Team-Meetings) and [Daily Standup Meeting Log](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Daily-Standup-Meeting-Log)

## Team

SWPP 2026 Team 10: 한준형 (PM in Iteration 2; design, testing, and integration), 박동제 (camera controls, reference guide, guide controls, saved guides), 박재완 (server, deployment, accounts and friends, pose generation), 조성민 (real-time session, guide sync, remote controls, zoom).
