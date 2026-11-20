<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/png/pix-horizontal-on-dark.png">
    <img alt="Pix" src="branding/png/pix-horizontal.png" width="320">
  </picture>
</p>

<p align="center"><strong>Get the portrait you have in mind, together.</strong></p>

<p align="center">
  <a href="https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications">Requirements and Specifications</a> ·
  <a href="https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation">Design Documentation</a> ·
  <a href="https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation">Testing Documentation</a> ·
  <a href="https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki">Wiki</a>
</p>

---

Pix is an Android camera app that helps two people get the portrait one of them has in mind. When friends photograph each other, the person in the photo usually knows the pose and framing they want, but only the photographer sees the screen. Pix gives both people the same view: the photographer puts a pose guide on the camera, and the friend's phone shows the live camera with the same guide and can adjust the shot. The photographer takes the photo, and it is saved at full quality without the guide.

This branch is the **Iteration 4 build**, after the user test, the heuristic evaluation, and UAT cycle 1. Pix now asks what you are taking and frames the poses for it. Photos held sideways are saved as taken, and the controls turn with the phone. Both phones see the session's photos in Pix's own viewer, and one phone zooms at a time. Each failure says why, and the pose tip says how far to stand and how to hold the phone.

## Contents

- [Demo videos](#demo-videos)
- [Features implemented](#features-implemented): [Major progress in Iteration 4](#major-progress-in-iteration-4)
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

The video shows the photographer's phone and the subject's phone side by side, on a debug build of Iteration 4 with the AWS server.

<!-- TODO: the Iteration 4 demo video. Paste the uploaded video's URL on its own line here. -->

## Features implemented

This table lists everything the Iteration 4 build can do. The subsection below lists what is new in Iteration 4.

| Feature | What you can do |
|---|---|
| **Camera** | Pix opens on the rear camera. Choose the ratio 3:4, 9:16, or 1:1. Tap to focus, then drag the bar beside the focus box to change the brightness. Pinch to zoom, or tap the zoom readout to step through the zoom levels. Use the flash, the grid, and the level. The camera switch beside the shutter turns on selfie mode. Held sideways, the icons turn with the phone, and the photo is saved as the phone was held. The shutter is free again as soon as the camera has taken the photo. Pix saves each photo at full resolution to the "Pix" album, without the guide. The thumbnail opens the album in Pix's own viewer, with a strip of thumbnails, zoom, *Share*, and *Delete*. |
| **Guide from a photo** | Tap *Guide* › *Choose from the gallery* and pick a photo of a person. A screenshot of a post from Instagram or another app also works: Pix cuts the screenshot to its photo. ML Kit finds the person on the phone, and Pix draws a white outline, never the photo's pixels. The guide starts where the person stands in the photo. A thumbnail of the photo stays in a corner of the preview; drag it to the side to hide it. The Pix server never receives the photo. |
| **Guide controls and library** | Drag the guide, pinch to resize it, fade it, hide it, replace it, or remove it. The guide-edit toggle sets what a pinch does: it moves the guide or zooms the camera. *Save guide* keeps the guide in *My library*. The library holds up to 100 guides, from photos and from AI poses. |
| **AI pose generation** | Tap *Guide* and choose what you are taking: *Auto*, *Scenery*, *Upper body*, *Full body*, *Landmark*, or *Selfie*. Take a photo of the scene, upright or sideways. Pix checks that the photo shows a person. Describe a pose, or let Pix suggest four poses for the situation. The Pix server checks the description with a filter model. Then an image model makes four candidates of the same person in the same place, in the scene's ratio. Swipe through the candidates and pick one. It becomes the guide, and a tip on Camera says how far to stand and how to hold the phone. ♥ saves a candidate to *My library*. Descriptions used before show under the text field. Back goes one step back, and a failure says why: offline, an hourly or daily limit, or a refused description. |
| **Pix ID and friends** | Sign up with a Pix ID and a password, or continue with Google. Find friends by their exact Pix ID. Send and answer friend requests. A friend request arrives as a push notification. Everything else in Pix works without an account. |
| **Shoot together** | Tap *Shoot together* and choose who takes the photo. The photographer invites a Pix friend or shows a 6-digit code. The friend gets a push notification and accepts the invitation. Without an account, the subject enters the code. The phones connect over Wi-Fi or cellular data. When the phones have no direct path, a TURN relay forwards the encrypted video. After a drop of less than 10 s, the session comes back by itself. When the subject leaves or drops, the photographer's phone says so until they are back. |
| **Both phones in a session** | The subject's phone shows the live camera in the photographer's ratio, as a mirror, with the same guide and thumbnail. Either phone can add a guide from a photo, and the other phone shows its thumbnail. Both phones show the pose description as either person types it, and the candidates as they arrive. The first phone to pick a candidate decides. The subject can zoom, change the brightness and the flash, move the guide, and open the photographer's tip. One phone zooms at a time, and the other says who is zooming. Only the photographer takes the photo. Both phones save it, and each phone's thumbnail shows the session's photos. |
| **Settings and help** | The gear on Camera opens *Settings*, also during a session. It has the base brightness, the guide's color and thickness, and the live-view delay. It also has the language (English or Korean), notifications, the AI agreement, and the Pix account. At first use, a tutorial shows tips over each screen, and a tap anywhere closes them. *Settings* can show the tutorial again. |

The plan for every iteration is in [Requirements and Specifications §4.1](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications#41-scope-by-iteration).

### Major progress in Iteration 4

These features are new since the Iteration 3 build:

- **Poses by situation.** Pix asks what you are taking first. Each situation has its own framing, prompt, and four default poses ([#227], [#228], [#250]).
- **Photos held sideways.** Photos and scene photos are saved as the phone was held, and a sideways scene gives landscape poses. The controls turn with the phone ([#242]).
- **The session's photos.** Each phone's thumbnail shows the session's photos. Pix's album opens in Pix's own viewer, as in a gallery app ([#239], [#248]).
- **Fixes from the user test and the heuristic evaluation.** Back goes one step back on the pose screens ([#222]). A refused description stays out of the history ([#221]), and each failure says why ([#223]). The subject sees a mirror ([#224]). The step's action sits at the top right, Settings opens in a session, and the scene photo keeps the camera's bar ([#225], [#232]). Every color is a token ([#219]).
- **Fixes from UAT cycle 1.** One phone zooms at a time ([#252]). The notice that the subject left stays on screen ([#249]). The reference thumbnail hides at the side, and it shows for a guide from the subject ([#241], [#245]).
- **Better tips and a faster shutter.** The tip says how far to stand and how to hold the phone, and the subject can open it ([#240]). The camera takes the photo 0.26 s after the press instead of 0.70 s ([#230]).
- **Tests.** The Iteration 4 flows passed on two phones, and four of us checked the tips: 92 % match their pose ([#253], [#255]).

## Known limitations and to-dos

Dropped on 11/20 as not needed: the offline connection with a QR code ([#214]) and light mode ([#212]). Phones connect with a room code or an invitation, and Pix stays dark.

Left for Iteration 5 ([Design Documentation 2.9](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#29-module-ownership-and-build-order)):

- **The remote-control switch and remote focus** (FR-7.5, FR-7.7): not built. The photographer cannot block the subject's controls, and the subject cannot focus.
- **Search, situations, and memos in the library** (FR-3.11): not built.

Known limitations of the Iteration 4 build ([Testing Documentation 3.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#32-results)):

- The person check on the phone misses people who are far away or in low light. Pix then refuses the scene photo, so these users get no poses.
- Suggested poses are often too alike for close-up scenes and selfies. This was not measured again with the situations.
- When the tip cannot tell the distance, it says to fit the person to the guide's outline.
- The course servers answer only on the campus network. So debug builds use the AWS server.

### Next: Iteration 5

- Cycle 2 of the user acceptance test (UAT) on 11/23, with the changes from cycle 1 ([Testing Documentation 4.4](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#44-uat-results-in-class-iteration-4))
- In-class testing on 11/30 and a test with real users on 12/2
- The remote-control switch, remote focus, and search in the library

## How it works

![Architecture](wiki/images/design_target_architecture.png)

- **Pix server** (FastAPI behind nginx on port 80): connects the two phones (signaling) and keeps accounts, friends, and invitations. It sends push notifications through Firebase Cloud Messaging (FCM). It checks each description and scene photo with a decision model. Then it sends the pose requests to the image and vision models through OpenRouter. The API key stays on the server. The server never sees the video.
- **Servers**: Server A on the course servers runs `main` for release builds. Server B runs `dev` for staging. The AWS server runs `dev` for debug builds and answers from any network. Each server deploys a new commit blue-green. The course servers pull it with a timer, and GitHub Actions deploys the AWS server.
- **Phone to phone** (WebRTC): the live video and the photos go directly between the two phones. So do the guide, the shared steps, the candidates, and the subject's controls. Without a direct path, Cloudflare's TURN relay forwards the encrypted packets. Each phone draws the guide itself, so the guide never appears in the video or in saved photos.
- **On the phone**: CameraX runs the camera, and ML Kit makes the outline and the person check. The guide library, the settings, and the sign-in tokens stay on the phone.

The full design is in the [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation).

## Technology stack

| Part | Stack |
|---|---|
| Android app | Kotlin 2.2, XML Views with ViewBinding, Navigation, ViewModel, and coroutines · CameraX 1.6 · ML Kit Subject Segmentation, Pose Detection, and Face Detection · WebRTC (`io.github.webrtc-sdk:android` 137) · OkHttp 4.12 and Retrofit 2.11 with kotlinx.serialization · Jetpack DataStore · Credential Manager with Google ID · Firebase Cloud Messaging |
| Server | Python 3.11 · FastAPI and Uvicorn (WebSocket signaling and REST API) · SQLAlchemy 2 with asyncpg and Alembic on PostgreSQL 18 · Redis (session store) · PyJWT and argon2 · google-auth (Google sign-in and FCM) · pydantic-settings · httpx2 · Pillow · Docker and nginx with blue-green deploys |
| Services | Cloudflare Realtime (TURN relay) · Firebase Cloud Messaging · OpenRouter · AWS EC2 (the AWS server) |
| AI | Through OpenRouter: GPT Image 2.5 Flare at low quality for the candidates · Gemini 3.1 Flash Lite for the suggestions and the tips · Jev 1.14 (`typesafe/jev-1.14`) for the description filter and the person check. The AI module test uses Claude Opus 5.5 as its judge. |
| Tests and CI | JUnit 4, Robolectric 4.17, and FragmentScenario with JaCoCo · pytest with pytest-cov, fakeredis, and SQLite · Spotless with ktlint · Ruff · GitHub Actions (lint, tests, the server image, the AWS deploy, the wiki) |

## Development environment

| Part | Requirements |
|---|---|
| Android app | Android Studio with its bundled JDK (the unit tests need JDK 21 or later), Kotlin 2.2, and Gradle 9.5 through the wrapper. minSdk 29 (Android 10), target and compile SDK 36. |
| Server | Only for a local server: Python 3.11 or later and [uv](https://docs.astral.sh/uv/getting-started/installation/). Pose generation needs an [OpenRouter](https://openrouter.ai/) API key with credits. Redis and PostgreSQL are optional locally. Without them, the server keeps rooms in the process and accounts in a SQLite file. |
| Tested on | Galaxy S21, S22, S23 Ultra, S25 Ultra, and Note20 |
| Network | Wi-Fi or cellular data on each phone. The AWS server answers from any network. The course servers answer only on the campus network. |

## Run the demo

### 1. Get the code

```sh
git clone https://github.com/snuhcs-course/swpp-2026-project-team-10.git
cd swpp-2026-project-team-10
git checkout iteration-4-demo
```

### 2. Choose a server

**The AWS server (no setup).** A debug build uses the AWS server, `http://3.105.185.251/`, by default. It runs `dev` and answers from any network, cellular data included. To check it, open `http://3.105.185.251/health` in a phone's browser. It answers `{"status":"ok"}`.

**Server A (campus network only).** A release build uses Server A, `http://swpp26t10.147-46-242-169.sslip.io/`. It runs `main`. To use it in a debug build, create `android/local.properties` with this line:

```properties
pix.serverUrl=http://swpp26t10.147-46-242-169.sslip.io/
```

**A server on the laptop.** Run these commands from the repository root:

```sh
cd server
uv sync --locked
cp .env.example .env
```

Put the keys you need in `server/.env`. The server runs without them, but some features stop:

| Key | Without it |
|---|---|
| `OPENROUTER_API_KEY` | No pose generation |
| `PIX_TURN_KEY_ID`, `PIX_TURN_API_TOKEN` | No TURN relay, so sessions across networks often fail |
| `PIX_FCM_CREDENTIALS_FILE` | No push notifications. The invite link still works. |
| `PIX_GOOGLE_CLIENT_ID` | No Google sign-in. Sign-up with a Pix ID and a password still works. |

Then start one worker:

```sh
uv run uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536
```

The server keeps rooms in the process and accounts in `server/pix.db`. Find the laptop's Wi-Fi address (on macOS, `ipconfig getifaddr en0`). Check `http://<laptop-IP>:8000/health` from a phone. Then put `pix.serverUrl=http://<laptop-IP>:8000/` in `android/local.properties`. For an emulator, use `pix.serverUrl=http://10.0.2.2:8000/`. The [server README](server/README.md) has more detail.

### 3. Install the app on both phones

Connect a phone with USB debugging on. Then run this command from `android/`:

```sh
./gradlew :app:installDebug
```

Do the same for the second phone. Google sign-in on a debug build needs the phone's debug key in the team's Google Cloud project. Run `./gradlew :app:signingReport`, and a project owner adds the `SHA1` of `Variant: debug` as an Android OAuth client. Sign-up with a Pix ID and a password needs no setup.

### 4. Try the flows

Pix follows the phone's language, English or Korean. The steps below use the English labels.

1. **Camera.** Pix opens on the camera. At first use, the tutorial shows tips over the screen. Tap anywhere to close them. Tap the ratio button and choose 3:4, 9:16, or 1:1. Tap the preview to focus, then drag the bar beside the focus box. Pinch to zoom, or tap the zoom readout. Tap the camera switch beside the shutter for selfie mode. Turn the phone on its side: the icons turn with it. Take a photo. The thumbnail beside the shutter shows it, without the guide, and opens it in Pix's viewer.
2. **Guide from a photo.** Tap *Guide* › *Choose from the gallery* and pick a photo of a person, or a screenshot of a post. Tap *Use* at the top right. The outline starts where the person stands in the photo. With the guide-edit toggle on, drag and pinch the guide. The bar under the preview fades, hides, saves, or removes the guide. Drag the thumbnail in the corner to the side to hide it. A saved guide shows in *My library*. At first use, Google Play services downloads the segmentation model. This can take a minute.
3. **AI poses.** Tap *Guide* and a situation, such as *Auto* or *Full body*. Accept the notice. Take a photo of the scene with a person in it, upright or sideways, and tap *Use* at the top right. Type a pose and tap *Generate* at the top right. Or leave the field empty and tap *Suggest*. The candidates appear as they are ready, usually within 30 s. Swipe through them, tap *Select*, and then *Use*. Back at the top left goes one step back on each screen; *Regenerate* makes a new set of the same request. A tip on how to take the pose shows on Camera. The bulb button shows it again. Each set costs up to four images on OpenRouter.
4. **Pix ID and friends** (two phones). Tap *Shoot together* › *I'll take the photo* › *Sign in to add friends*. Sign up with a Pix ID and a password (*New to Pix? Sign up*), or continue with Google. Do the same on the other phone. Tap *Add a friend* and search for the other Pix ID. The other phone gets a push notification and accepts the request.
5. **Shoot together with an invitation.** On the photographer's phone, tap *Shoot together* › *I'll take the photo*, then *Invite* next to the friend. The subject's phone gets a notification. Tap it, then tap *Accept*. The invitation also waits for a minute under *Shoot together* › *I'll be in the photo* › *Received invitations*. The subject's phone shows the live camera with the same guide. Try it with one phone on cellular data.
6. **Shoot together with a code** (no sign-in). On the photographer's phone, tap *Shoot together* › *I'll take the photo* › *Show a code*. On the subject's phone, tap *Shoot together* › *I'll be in the photo*. Enter the code and tap *Connect*.
7. **In a session.** On the subject's phone, pinch to zoom, change the brightness and the flash, and move the guide with the guide-edit toggle on. The photographer's phone follows. Add a guide or generate poses: the other phone shows each step. Pinch on both phones at once: only the first zooms, and the other says who is zooming. On the subject's phone, the light bulb opens the photographer's tip. The photographer takes the photo, and both phones save it; each phone's thumbnail opens the session's photos. Turn off Wi-Fi on one phone for a few seconds. The session comes back by itself. *Leave* or *End session* ends the session.

With one phone, `server/tools/fake_photographer.py` or `server/tools/fake_subject.py` can be the other phone against a local server. See the [server README](server/README.md#testing-the-photographers-phone-without-a-second-phone).

## Tests and checks

GitHub Actions runs these checks on every pull request. Run them locally before you push.

| Part | From | Command |
|---|---|---|
| Android | `android/` | `./gradlew spotlessCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` |
| Android, with coverage | `android/` | `./gradlew :app:jacocoTestReport` → `app/build/reports/jacoco/jacocoTestReport/html/index.html` |
| Server | `server/` | `uv run ruff check . && uv run ruff format --check . && uv run pytest` |
| Server, with coverage | `server/` | `uv run pytest --cov=pix_server --cov-branch --cov-report=html` → `htmlcov/index.html` |

At the close of Iteration 4, all 2,108 app tests and 881 server tests pass. App line coverage is 88.3 % (managers 94.7 %, ViewModels 97.9 %, Views 94.5 %, platform adapters 49.9 %). Server line coverage is 98.4 %. The Iteration 4 flows passed on two phones. In the AI module test, four of us checked the rewritten tips: 46 of 50 (92.0 %) match their pose, over the 90 % threshold. The targets, the integration scripts, and all results are in the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation). The [Android README](android/README.md) and the [server README](server/README.md) have more detail.

## Repository

| Folder | Contents |
|---|---|
| `android/` | The Android app (open this folder in Android Studio) |
| `server/` | The Pix server, its tests, the AI module test, the deploy files, and tools for tests with one phone |
| `wiki/` | The source of the GitHub wiki. A push to `dev` publishes it. `wiki/design/` holds the sources of the figures and the wireframes. |
| `branding/` | The logo: SVG masters, PNG exports, and the usage guide |
| `.github/` | CI workflows and the pull request template |

Branches: `main` holds the stable version, and `dev` is the integration branch. Each task gets a branch from `dev`. The branch returns to `dev` through a reviewed, squash-merged pull request. `iteration-N-demo` holds the code of that iteration's demo. At the end of each iteration, the team merges `dev` into `main`.

## Documents

- [Requirements and Specifications](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications)
- [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation)
- [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation)
- [AI Collaboration Report – Iteration 4](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/AI-Collaboration-Report-%E2%80%93-Iteration-4)
- [Team Meetings](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Team-Meetings) and [Daily Standup Meeting Log](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Daily-Standup-Meeting-Log)

## Team

SWPP 2026 Team 10:

- 박동제: PM in Iteration 1. Camera, reference guide, guide controls, pose tips, selfie mode, and the prompt history.
- 박재완: PM in Iteration 3. Server, deployment, accounts and friends, pose generation, the AI checks and the AI module test, and the documents.
- 조성민: PM in Iterations 4 and 5. Real-time session, sessions across networks, reconnection, invitations, remote controls, settings, the guide library, and the animations.
- 한준형: PM in Iteration 2. Guide transfer, shared steps, photos on both phones, the tutorial, and Camera's controls after the heuristic evaluation.

[#212]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/212
[#214]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/214
[#219]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/219
[#221]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/221
[#222]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/222
[#223]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/223
[#224]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/224
[#225]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/225
[#227]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/227
[#228]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/228
[#230]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/230
[#232]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/232
[#239]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/239
[#240]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/240
[#241]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/241
[#242]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/242
[#245]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/245
[#248]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/248
[#249]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/249
[#250]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/250
[#252]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/252
[#253]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/253
[#255]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/255
