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

[#1]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/1
[#12]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/12
[#13]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/13
