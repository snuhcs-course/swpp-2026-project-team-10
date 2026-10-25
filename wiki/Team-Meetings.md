Notes from our longer team meetings: the iteration kickoff, mid-iteration syncs, the iteration review, and working meetings. Short daily meetings are in the [[Daily Standup Meeting Log|Daily-Standup-Meeting-Log]].

Each note lists the attendees, the agenda, the decisions for each agenda item, and the action items with an owner and a due date.

**Members:** 박동제 (PM in Iteration 1), 박재완, 조성민, 한준형 (PM in Iteration 2)

## Iteration 1

| Date | Meeting | Topics |
|---|---|---|
| [2026-09-27 (Sun)](#2026-09-27-sun-iteration-1-kickoff) | Iteration 1 kickoff | Goal and scope, roles, technical choices, Git rules |
| [2026-09-30 (Wed)](#2026-09-30-wed-team-meeting) | Team meeting | Document review, remote control scope, later features, test setup |

### 2026-09-27 (Sun): Iteration 1 kickoff

**Attendees:** 박동제, 박재완, 조성민, 한준형

**Agenda**
1. Goal and demo scenario
2. Scope of the prototype
3. Roles and tasks
4. Technical choices
5. Git and GitHub rules
6. Meetings, records, and risks

**1. Goal and demo scenario**
- Iteration 1 (9/27–10/9) builds a working prototype of the core flow. The review is on 10/11.
- Demo: the photographer opens Pix on the camera, makes a pose guide from a gallery photo or a generated pose, and connects the subject's phone with a room code. The subject sees the live camera with the same guide and controls the zoom and the shutter from their phone.

**2. Scope**
- In Iteration 1:
  - Camera preview and capture
  - Reference photo from the gallery, turned into a cutout and an outline of the person (ML Kit Subject Segmentation)
  - Generated poses: four predefined pose templates through one image-editing API
  - Guide overlay: drag, pinch, and opacity
  - Live view on a second phone over WebRTC, on the same Wi-Fi, with a room code
  - The same guide on both phones
  - Remote zoom and shutter from the subject's phone
- Later iterations:
  - Friends, invitations, and connections over cellular data
  - Describing a pose in free text
  - Remote focus, exposure, and flash
  - Comparing several image-editing APIs

**3. Roles and tasks**

| Member | Role | Iteration 1 tasks |
|---|---|---|
| 박동제 | PM | Task setup and GitHub issues; requirements and demo scenario; test environment; reference selection and segmentation; demo README; schedule tracking |
| 한준형 | Camera and overlay | CameraX preview and capture; overlay rendering; integrating the prototype |
| 박재완 | Server, AI, and sync | Backend server and the image-editing API choice; pose generation; synced guides |
| 조성민 | Real-time | Architecture and data channel protocol; WebRTC streaming; remote zoom and shutter |
| All | | Navigation and screen skeleton; tests on the Galaxy S22 and S23 Ultra |

- Development work is about 10 hours per person. Meetings are counted separately.

**4. Technical choices**
- One repository: `android/` for the app and `server/` for the server.
- App UI: XML Views.
- Server: FastAPI. In Iteration 1 it runs on a laptop. A cloud server is decided later, when cellular connections and accounts need one.
- The image-editing API key stays on the server. The app calls our server, never the API directly.
- To decide by 10/2: the image-editing API (박재완) and the WebRTC build for Android (조성민).

**5. Git and GitHub rules**
- `main` holds stable versions. `dev` is the integration branch.
- Each task gets a branch from `dev`, named `<type>/<issue#>-<name>`, and a pull request that needs one approval. Task PRs are squash-merged into `dev`.
- At the end of an iteration, `dev` is merged into `main` with a merge commit, and `iteration-N-demo` is created from `main`.
- Commit messages and PR titles follow Conventional Commits (`feat`, `fix`, `docs`, …).
- Every task that ends in a PR gets a GitHub issue.

**6. Meetings, records, and risks**
- A 10-minute standup every day at 23:00; mid-iteration sync on 10/4; review on 10/11.
- Everyone records actual hours, AI agent hours, and token usage in the team schedule.
- Campus Wi-Fi may block traffic between phones, so we test on a separate hotspot or router.

**Action items**

| Owner | Item | Due |
|---|---|---|
| 박동제 | Set up the wiki pages | 9/28 |
| 박동제 | Create GitHub issues for the Iteration 1 tasks | 9/28 |
| 박동제 | Requirements, demo scenario, and out-of-scope list | 9/29 |
| 박동제 | Test environment (two phones, ML Kit model, Wi-Fi, API key) | 9/29 |
| 조성민 | Architecture and data channel protocol | 9/30 |
| All | Navigation and screen skeleton | 9/30 |
| 한준형 | CameraX preview and capture | 10/2 |
| 박재완 | Backend server; choose the image-editing API | 10/2 |
| 조성민 | Choose the WebRTC build for Android | 10/2 |

[↑ Back to the list](#iteration-1)

### 2026-09-30 (Wed): Team meeting

**Time:** 17:00–19:00

**Attendees:** 박동제, 박재완, 조성민, 한준형

**Updates before the meeting** (박동제)
- Requirements and Specifications v0.1 and Design Documentation v0.1 are on the wiki. Design 2.10 lists the Conventional Commits types.
- Issues [#1]–[#12] and the "Iteration 1" milestone cover every task that ends in a PR. Meetings, documents, and device tests have no issue.
- [#13] puts the project draft (Android project setup and shared contracts) up for review.
- Asked the TA for admin rights to set up branch rules.

**Agenda**
1. Review of the R&S and Design drafts
2. Remote control scope
3. Features for later iterations
4. The Iteration 1 test setup
5. How the documents grow each iteration
6. Review of the shared contracts ([#13])

**Decisions**
1. **Drafts.** We reviewed R&S v0.1 and Design v0.1. The decisions below go into v0.2.
2. **Remote control**
   - No remote shutter. Only the photographer takes photos; the subject adjusts the camera.
   - Iteration 1: remote zoom only.
   - Iteration 2: move and resize the guide, brightness, flash, and a switch to allow or block remote control.
3. **Later features**
   - Iteration 2: friends and invitations (signing in is needed only for friends) and saved guides. Room codes stay, so Pix works without signing in.
   - Iterations 3–4: an offline connection by QR code, and guides that keep the reference's background.
   - QR connection: the QR code carries the LocalOnlyHotspot details, and the hotspot accepts one phone. After "Connected" appears, both people tap *Resume* before the session starts, so a stranger who connects first cannot join.
4. **Iteration 1 test setup.** The photographer's phone, the subject's phone, and a laptop running the server are all on the same Wi-Fi and connect with a room code.
5. **Documents**
   - Design describes the current iteration in detail. Later iterations stay at the plan level: what each one adds, its components, flows, and key decisions. Messages, APIs, and database columns are added when that iteration starts.
   - Protocol sections use a summary table and a JSON example for each message, in a collapsible block.
6. **Shared contracts** cover Iteration 1 only (12 data channel message types).

**Action items**

| Owner | Item | Due |
|---|---|---|
| 박동제 | Update R&S and Design to v0.2 with these decisions | 9/30 (done) |
| 박동제 | Limit the contracts in [#13] to Iteration 1 | 9/30 (done) |
| 박동제 | Address the bot review comments on [#13] and merge it | 10/1 |
| 박동제 | Rename the remote-control task in the schedule to "remote zoom" | – |
| 박동제 | Remove *Take photo* from the subject's screen in the wireframes | – |
| 박재완 | Choose the image-editing API and record the reason | 10/2 |
| 조성민 | Choose the WebRTC build for Android | 10/2 |

[↑ Back to the list](#iteration-1)
 
## Iteration 2
 
| Date | Meeting | Topics |
|---|---|---|
| [2026-10-09 (Fri)](#2026-10-09-fri-iteration-2-kickoff) | Iteration 2 kickoff | Iteration 1 results, scope and priorities, camera and guide changes, server and friends, roles, AI records |
 
### 2026-10-09 (Fri): Iteration 2 kickoff
 
**Attendees:** 박동제, 박재완, 조성민, 한준형
 
**Agenda**
1. What Iteration 1 built
2. Iteration 2 scope and priorities
3. Camera and remote controls
4. The guide
5. Server, friends, and invitations
6. Pose description
7. Roles
8. AI records, documents, and the schedule

**1. What Iteration 1 built**
- Camera: zoom slider, composition grid, and horizon bar.
- Guide: cutout and outline with drag, pinch, and opacity, and removing the guide.
- Server: WebSocket signaling and the pose generation APIs (`POST /poses`, `GET /pose-templates`), with GPT Image 2.5 Flare at low quality through OpenRouter.
- Live view: the photographer's camera on the subject's phone, the same guide on both phones, and remote zoom from either phone.
- The demo flow works from start to end, which is enough for the midterm presentation.

**2. Iteration 2 scope and priorities**
- Already planned in the R&S: friends and invitations, sessions across networks, more camera and remote controls, hiding and replacing the guide, saved guides, and describing a pose in words.
- Added at this meeting: smoother guide outlines, a fix for the zoom stutter, pinch zoom, aspect ratios, no shutter sound, and a reference thumbnail.
- Must: smoother outlines and the zoom stutter. ML Kit finds the arms and legs, but the edges are jagged and the limbs come out too thick or too long. The preview stutters on every zoom, and on the S21 it pauses to load when the zoom goes below 1×.
- If possible: pinch zoom instead of the slider, and the short pause when the shutter is pressed.
- The rest is picked up one by one when there is time. Iteration 2 overlaps with midterms, so one or two items may move to Iteration 3.
- Iteration 3: selfie mode, and a settings screen with the guide color and a frame delay indicator for remote sessions.
- Later or dropped: video comes later, because it gets complicated with remote sessions. A guide that keeps the reference's background is not worth it. Choosing the image model in the app is dropped, because it would show our costs.
- The UI stays in English. Korean comes at the end, with a language setting, if there is time.
- Small UI fixes, such as long device names cut off in the live view, are made as they come up, without a task.

**3. Camera and remote controls**
- Brightness and flash on the photographer's camera and from the subject's phone. Focus too, if possible.
- Aspect ratios 4:3, 16:9, and 1:1 only. 16:9 fits Instagram stories without cropping. This replaces the fixed 3:4 frame (AD-10).
- No shutter sound.
- Pinch zoom comes back if possible, with a button that switches between zooming the camera and editing the guide, so the two pinch gestures do not clash.
- Apart from brightness and flash, the remote controls need fewer additions than we expected.

**4. The guide**
- The guide no longer shows the reference photo's pixels. A small thumbnail of the reference sits in a corner of the Camera screen instead, as in Ulike.
- The guide starts where the person stands in the reference photo, not at the center, and the user adjusts it from there. Framing the person in the photo matters more than the background.
- Both people can move and resize the guide, so the subject can fix a guide that the photographer placed badly.
- The guide can be hidden for a moment and replaced. Removing it already works.
- Saved guides stay in the app on the phone, not in the gallery and not on the server.
- If there is time: a guide color that contrasts with the background (black or white by default), which the user can switch.

**5. Server, friends, and invitations**
- The course-provided server first, AWS otherwise, with Docker and nginx. GitHub hosting serves only static pages.
- Sessions on different networks need the deployed server for signaling. Streaming across networks is still its own task after the server is up.
- Sign-in with Google. The team makes choices like this one; AI designs details such as the database schema.
- Invitations arrive as push notifications. If a push does not arrive, the photographer copies an invite link and sends it to the friend. Sending invitations only through KakaoTalk would need KakaoTalk on every test phone.
- The friends feature needs both the server (박재완) and the connection code (조성민), so the two agree on the order of the work.

**6. Pose description**
- Idea chips are the default, and the user can also type a description.
- The *Generate poses* screen design was accepted: a thumbnail of the scene, a 100-character field in Korean or English, idea chips, *Generate 4 poses*, and *Skip, use the preset poses*.
- The default prompt must keep good framing. The owner decides the details.
- We still need to collect preset images.

**7. Roles**
 
| Member | Iteration 2 tasks |
|---|---|
| 한준형 | PM: schedule and issues; the Iteration 2 design in the R&S and Design; the AI rules in `CLAUDE.md` and the PR template |
| 박재완 | Server deployment; accounts and friends; invitations; pose description; pinch zoom |
| 조성민 | Sessions across networks; remote brightness, flash, and guide moves; the zoom stutter |
| 박동제 | Smoother outlines; camera controls; guide controls, the reference thumbnail, and the starting position; saved guides |
 
- After the meeting, the Iteration 2 schedule (10/11) moved pinch zoom and invitations to 조성민.

**8. AI records, documents, and the schedule**
- We write the AI collaboration notes as we go: each PR says what AI did well and what we had to fix. In Iteration 1 the report was written at the end.
- The `AI-generated with …` line stays in source files only, one line per file, not per function. When a file changes, the line names the latest tool, date, and reviewer; `git blame` shows the history. The rule goes into `CLAUDE.md`.
- The Iteration 2 design is written with AI before feature work, and the team reviews it. Decisions that come up during the work are made in the team chat.
- The wiki documents stay one page each and get new versions, not a page per iteration.
- Team meeting notes go up on the day of the meeting. The standup log is filled through 10/7, with a short entry for 10/8 (demo recording, tests, and presentation prep). Weekend standups are flexible.

**Action items**
 
| Owner | Item | Due |
|---|---|---|
| 한준형 | Post these notes | 10/9 |
| 한준형 | Fill the standup log through 10/8 | – |
| 한준형 | Iteration 2 schedule and GitHub issues, as in Iteration 1 | 10/11 |
| 한준형 | Iteration 2 design in the R&S and Design | Before feature work |
| 한준형 | AI marker rule in `CLAUDE.md` and an AI section in the PR template | – |
| 박재완 | Check the course-provided server; otherwise use AWS | – |
| 박동제 | Smooth the guide outline (top priority) | – |
| 조성민 | Fix the zoom stutter (top priority) | – |
| All | Note AI use in every PR | – |
 
[↑ Back to the list](#iteration-2)

## Iteration 3

| Date | Meeting | Topics |
|---|---|---|
| [2026-10-25 (Sun)](#2026-10-25-sun-iteration-3-kickoff) | Iteration 3 kickoff | Features, open questions, testing, tasks |

### 2026-10-25 (Sun): Iteration 3 kickoff

**Attendees:** 박동제, 박재완, 조성민, 한준형

**Agenda**
1. Iteration 3 features
2. Open questions
3. Testing
4. Tasks

**1. Iteration 3 features**
- UI and UX:
  - A Korean translation. English or Korean is chosen in the settings screen, and the default follows the phone's language.
  - *Pick a pose* shows the candidates one at a time, with a swipe ([#108]).
- New features:
  - Pose tips. After the poses are generated, a short tip on how to take the shot sits in the space at the bottom of each screen. It covers four things:
    - the photographer moving forward or back from where they stand;
    - zooming in or out;
    - holding the camera higher or lower;
    - tilting the camera down or up.
  - How tips are made:
    - A tip is text. The server sends the original scene photo and the generated pose to Gemini through its API and gets the tip back.
    - Four poses are generated. Each one shows as soon as it arrives, and its tip request goes out at the same time, so the tips are likely ready while the user is choosing.
    - The tip then shows on the Camera screen, in a panel that opens and closes.
    - A guide made from an uploaded reference photo has no tip.
  - Live preview:
    - sessions on different networks (planned for Iteration 2, [#56]);
    - an offline connection with a QR code and a hotspot;
    - reconnecting after a drop of a few seconds.
- Other (small bugs and features not finished):
  - Friend invitations and friend requests, with push notifications (planned for Iteration 2, [#55]).
  - Generated poses are no longer fixed at 3:4; they follow the ratio of the photo that is sent.

**2. Open questions and our decisions**
- **Personalization: what each phone offers, the subject's and the photographer's.**
  - The subject's phone can also open guide selection or pose generation.
  - Pose generation and selection run on the Camera screen today. While the scene photo is taken, both phones show the same screen, the camera.
  - On *Generate poses* and *Pick a pose*, both phones also show the same screen. Changes inside it are not streamed: whichever phone decides first moves both to the next screen.
  - Saving a pose or guide: both phones get the button. To use a guide saved on the subject's phone, start from the subject's phone.
  - Photos that are taken are saved on both phones by default.
- **Prompt history**
  - Shows the descriptions used before. The user chooses between the whole history and the history of one photographer and subject pair.
  - In the whole history, the photographer sees the photos they took, and the subject the photos they were in.
  - UI: a toggle between all and with this person, and an ✕ that deletes an entry.
- **Selfie mode**
  - Only when there is no live preview.
  - Pose suggestions: check whether the current prompt works for selfies; it probably needs its own.
- **Composition and background: four candidates that differ**
  - With a description, the server writes three variations today. The idea is to prepare four prompt templates with the same `{pose}` instead.
  - The default template is to be improved. Open: how to handle a request such as "two photos saluting and two with hands up".
  - The prompt says that `{pose}` is the user's request and should be read sensibly, and it keeps rules such as: do not change the face or the clothes, and do not put several poses in one photo.
- **Settings screen**
  - The overlay's color and thickness. Still open: a latency indicator and a how-to-use help.
  - A tutorial runs on first use only, and can be turned on again from the settings screen.

**3. Testing**
- Testing is a focus of this iteration.
- Features to test: taking photos with the camera, pose suggestions and the guide, and live sharing.
- An LLM (VLM) judge test. We set rules that every generated pose must keep, consistent with the prompt, and measure the failure rate, with the Claude API as the judge.
- A user test, not settled yet:
  - five people 박동제 knows;
  - a questionnaire (Google Forms), or asking them in person and recording the answers, summarized in the Testing Documentation.
- Latency tests: where they apply and their limits are decided later.

**4. Tasks**

| Owner | Item | Due |
|---|---|---|
| 박재완 | Deploy the server on AWS (temporary, for the user test) | 10/26 (Mon) |
| 박재완 | PM work: add to and revise the documents | 10/26 (Mon) |
| 박재완 | Korean translation | 10/26 (Mon) |
| 박동제 | *Pick a pose* one candidate at a time, with a swipe | 10/30 (Fri) |
| 박동제 | Pose tips after generation | 10/30 (Fri) |
| 조성민 | Sessions on different networks | 10/30 (Fri) |
| 조성민 | Reconnecting after a short drop | 10/30 (Fri) |
| 조성민 | Friend invitations and friend requests, with push notifications | 10/30 (Fri) |
| 박재완 | Generated poses in the ratio of the photo that is sent, not fixed at 3:4 | 10/30 (Fri) |
| 박동제 | Selfie mode, and check whether the pose prompt works for it | 10/30 (Fri) |
| 한준형 | Personalization | 10/30 (Fri) |

The rest continues next week, TBD.

[↑ Back to the list](#iteration-3)

[#1]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/1
[#12]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/12
[#13]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/13
[#55]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/55
[#56]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/56
[#108]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/108
