## Log Format

- Every day from 23:00 to 23:10, with all four members: 박동제, 박재완, 조성민, 한준형.
- Each member says what they did that day (Done), what they will do the next day (Plan), and what is blocking them (Blockers).
- On days with a team meeting, the meeting replaces the standup. Its notes are in [[Team Meetings|Team-Meetings]].
- Click a date to open that day's log.

## Iteration 1

<details>
<summary><b>2026-09-28 (Mon)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Set up the wiki pages; updated the task breakdown and schedule; defined the Iteration 1 specs with the team | Wireframes; project draft | – |
| 박재완 | Defined the Iteration 1 specs with the team | Check the R&S and Design drafts | – |
| 조성민 | Defined the Iteration 1 specs with the team | Check the R&S and Design drafts | – |
| 한준형 | Defined the Iteration 1 specs with the team | Check the R&S and Design drafts | – |

</details>

<details>
<summary><b>2026-09-29 (Tue)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Wireframes; project draft (Android project and shared contracts) | R&S and Design v0.1 on the wiki; GitHub issues; PR for the project draft; team meeting | – |
| 박재완 | Checked the R&S and Design drafts and listed what to fix | Team meeting: agree on the fixes | – |
| 조성민 | Checked the R&S and Design drafts and listed what to fix | Team meeting: agree on the fixes | – |
| 한준형 | Checked the R&S and Design drafts and listed what to fix | Team meeting: agree on the fixes | – |

</details>

<details>
<summary><b>2026-10-01 (Thu)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Merged the Android project setup and shared contracts ([#13]) and the PR template ([#15]); opened and merged the app shell with navigation and the screen skeleton ([#16]) | Review the server and CI PRs | – |
| 박재완 | FastAPI server skeleton; basic structure of the signaling and pose APIs; researched and tested external pose generation APIs | WebSocket signaling; choose the pose API | – |
| 조성민 | Looked into how to build the WebRTC connection and remote zoom; researched related code and libraries | Choose the WebRTC library; start signaling | – |
| 한준형 | Looked into the CameraX preview and capture setup; camera permission and the basic preview screen | Capture button and photo saving; check on a phone | – |

</details>

<details>
<summary><b>2026-10-02 (Fri)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed and approved the signaling hub server ([#17]) and the Android CI ([#19]) | Review the open PRs; check the Design Documentation against the merged work | – |
| 박재완 | Signaling: room create, join, signal, and leave; tested with two local WebSocket clients | Pose generation API adapter and server endpoint | – |
| 조성민 | Chose the WebRTC library; basic signaling structure | WebRTC connection between two phones | – |
| 한준형 | CameraX capture and photo saving; checked that saved photos do not include the on-screen guide | Overlay structure to show the reference guide on the camera | – |

</details>

<details>
<summary><b>2026-10-03 (Sat)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Checked everyone's progress against the schedule; reviewed the R&S and Design Documentation for gaps against the merged work | Review the CameraX, WebRTC, and pose generation PRs; start [#5] (reference selection and segmentation) | – |
| 박재완 | Pose generation server with image preprocessing, timeouts, and error handling; tested generating four poses | Connect the pose generation API in the Android client | – |
| 조성민 | WebRTC peer connection and data channels | Camera video streaming | – |
| 한준형 | Showed the guide over the camera preview; guide position and size for different screen sizes | Dragging and pinch zoom for the guide | – |

</details>

<details>
<summary><b>2026-10-04 (Sun)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed the pose generation APIs ([#22]), WebRTC streaming ([#23]), the wiki workflow ([#24]), and the pose generation wiki ([#25]); implemented [#5]: ML Kit segmentation, outline, guide maker, ReferenceViewModel, photo picker and reference screens | Test [#5] on the Galaxy S22 and open its PR | GuideRepository from [#6] not ready yet; added a stand-in until it lands |
| 박재완 | PoseGenerator: four parallel requests, cancel, and partial results; connected the client and the server | Pose generation UI and the pose selection and guide flow | – |
| 조성민 | Streaming the camera video to the other phone | Room code based session connection | – |
| 한준형 | Moving and resizing the guide, kept from going too far off screen | Connect the opacity and style controls; finish the overlay | – |

</details>

<details>
<summary><b>2026-10-05 (Mon)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Fixed the first-use wait for the segmentation model; tested [#5] on the Galaxy S22 (about 0.6 s per photo); opened [#28]; approved remote zoom ([#27]); prototyped arm and leg lines in the outline and moved them to a later iteration | Address the review comments on [#28] | – |
| 박재완 | Connected the Generating, Pick a pose, and failure screens; end-to-end flow that applies a generated pose as the guide | Test the whole pose generation flow and its latency with the real API | – |
| 조성민 | Room code and session connection flow | Test the connection and video on two real phones | – |
| 한준형 | Connected guide opacity and the cutout/outline switch; checked that photos still save without the guide while the overlay is on | Connect the guide state synced with pose generation results to the camera screen | – |

</details>

<details>
<summary><b>2026-10-06 (Tue)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed guide sync ([#32]); merged the Flow 2 wireframe redraw ([#33]) | Review the integration PR ([#34]); prepare the lab meeting | – |
| 박재완 | Merged the Android pose generation flow ([#30]): the scene photo on the Camera screen, the consent notice, and a 30 s limit per run; approved the guide overlay ([#31]) and guide sync ([#32]); redrew the Flow 2 wireframe ([#33]) | Organize the documents for the lab meeting | – |
| 조성민 | Merged guide sync ([#32]): the guide image sent over the data channel and shown on Subject view, the composition grid on Subject view, and *End session* while connected; approved the pose generation flow ([#30]) | Review the integration PR ([#34]) | – |
| 한준형 | Merged the guide overlay ([#31]): drag, pinch, opacity, and the zoom slider; started the Iteration 1 integration ([#11]) | Finish the integration and the UI update ([#34]); record the demo videos | – |

</details>

<details>
<summary><b>2026-10-07 (Wed)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Lab meeting: prepared the presentation, organized the documents, and discussed how to run the demo; created the `iteration-1-demo` branch with its README and demo videos ([#35], [#37]) | Write the AI collaboration report | – |
| 박재완 | Lab meeting: organized the documents and discussed how to run the demo | Review the AI collaboration report | – |
| 조성민 | Lab meeting: organized the documents and discussed how to run the demo; approved the integration PR ([#34]) | Review the AI collaboration report | – |
| 한준형 | Lab meeting: organized the documents and discussed how to run the demo; merged the Iteration 1 integration and UI update ([#34]) with the demo videos | Review the AI collaboration report | – |

</details>

<details>
<summary><b>2026-10-08 (Thu)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Wrote the AI collaboration report with the team's input ([#39], [#40]) | Submit the final documents | – |
| 박재완 | Reviewed the AI collaboration report | – | – |
| 조성민 | Reviewed the AI collaboration report | – | – |
| 한준형 | Reviewed the AI collaboration report | – | – |

</details>

## Iteration 2

The kickoff on 2026-10-09 replaced that day's standup ([[Team Meetings|Team-Meetings]]). Weekend standups are flexible.

<details>
<summary><b>2026-10-12 (Mon)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | – (weekend) | Iter2-P7 ([#49]): smooth the guide outline | – |
| 박재완 | Merged dev into main for Iteration 1 ([#63]); approved the AI marker rule and PR template ([#62]) and the Iteration 2 requirements ([#66]); updated the CLAUDE.md history on the wiki ([#64]) | Review the Iteration 2 design ([#67]); start deploying the server to the course servers ([#46]) | – |
| 조성민 | – (weekend) | Close Iteration 1 (the AI collaboration report draft); start Iter2-P6 ([#48]) | – |
| 한준형 | Wrote the Iteration 2 R&S (v1.0, [#66]) from the kickoff notes and the issues, and the Iteration 2 design (v1.0, [#67]) with the undecided details under *Still open* | Renumber the issues to the Iteration numbering; the Testing Documentation issue and format (Iter2-A1) | – |

</details>

<details>
<summary><b>2026-10-13 (Tue)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter2-P7 ([#49]): made the guide outline a smooth line; tried lines for arms and legs in front of the body on test photos | Decide on the arm and leg lines; update the documents and open the PR | – |
| 박재완 | Approved the Iteration 2 design ([#67]) and the Testing Documentation ([#70]); decided the hosting: the two course servers over HTTP on port 80, Cloudflare TURN, and Redis for sessions (Design 1.1); built the Docker, nginx, and blue-green deploy pulled by a timer, and opened [#71] | Merge [#71] and set up Server B on dev; file the issue for the Redis session store and start it | – |
| 조성민 | Closed Iteration 1 (the AI collaboration report draft); started Iteration 2: cleaned up the branches and went through the assigned issues; Iter2-P6 ([#48]): pinch zoom on both screens with the guide-edit mode toggle in place of the zoom slider, the mode on when a guide is added and off on the shutter; tried on the S25 Ultra and S23 Ultra | Push the branch, open the P6 PR, and address the review comments | – |
| 한준형 | Before today: the AI marker rule in `CLAUDE.md` and the AI usage section in the PR template ([#62]); the Iteration 2 schedule and issues ([#44]–[#61]); the R&S v1.0 ([#66]) and Design v1.0 ([#67]) merged. Today: renumbered all issues to `Iter1-Pn` / `Iter2-Pn` in titles and bodies and the two documents; fixed the version sequence ([#68]); wrote the Testing Documentation issue ([#69]) and the page format v1.0 from the course guidelines ([#70]) | Add the testing lectures' principles to the Testing Documentation; the `CLAUDE.md` rule to follow and update it | – |

</details>

<details>
<summary><b>2026-10-14 (Wed)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Recorded the arm and leg lines as out of scope; updated Design and R&S to 1.2; fixed the review comments and merged [#72] | Iter2-P10 ([#51]): tap to focus, brightness, flash | – |
| 박재완 | Merged the server deploy ([#71]) and set up Server B on dev with the deploy timer; redrew the target architecture figure; moved sessions and room codes to a Redis session store so the server runs two workers ([#73], [#75]), with its tests in the Testing Documentation (v1.1); added `pix` shortcuts for operating the servers ([#77]); approved [#76] | Start the accounts server ([#50]): decide the database engine and the session token lifetime, set up the Google OAuth client, and build the tables, migrations, and Google sign-in | – |
| 조성민 | Iter2-P6 ([#48]): resolved the Design Documentation conflicts with dev (1.4), fixed the Codex and review comments, added the Testing Documentation entries and the Iteration 2 AI collaboration report with the prompt log, and merged [#74]; assessed sessions across networks for Iter2-P16 ([#56]): the app already takes `iceServers`, a TURN relay is needed, and the server's reachability off campus is still to be checked; started Iter2-P5 ([#47]): `Timings` marks and the measurement script, zoom requests coalesced per frame, the lens held during a pinch, and the last frame kept on screen through a lens switch | Measure before and after on the S23 Ultra and S25 Ultra (S21 if available), fill in the NFR-19 numbers, and open the P5 PR | – |
| 한준형 | Testing Documentation v1.0 merged ([#70]) with the Week 6 and 7 lecture principles: test quality rules, design techniques with Pix's partitions, mutation analysis, CI and flaky-test policies; the `CLAUDE.md` rule to always refer to and update the Testing Documentation, with the Iteration 2 CLAUDE.md history, the AI collaboration report format, and the documents prompt log ([#76]) | Review the feature PRs; prepare the demo recording with 조성민 | – |

</details>

<details>
<summary><b>2026-10-15 (Thu)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter2-P10 ([#51]): tap to focus, brightness slider, flash, and the `CameraController` additions | Aspect ratio; open the P10 PR | – |
| 박재완 | Accounts server ([#50]): the database in the Compose file, with migrations for users, devices, friend requests, and friendships; Google sign-in that returns our own session token; Pix ID rules; recorded the database engine and the token lifetime in the Design | Pix ID lookup, friend requests, the friend list with online status, and removing a friend; tests with a fake Google verifier; open the [#50] PR | – |
| 조성민 | Iter2-P5 ([#47]): before and after numbers on the S23 Ultra, S25 Ultra, and S21; decided the preview mode and the shutter capture mode on them; opened the PR | Address the review comments on the P5 PR; Iter2-P17 ([#57]): guide moves from Subject view and the guide-edit mode toggle shown there | – |
| 한준형 | Recorded the Iteration 2 demo video with 조성민: the Iteration 2 features merged so far on two phones | Edit the demo video; prepare the midterm presentation materials | – |

</details>

<details>
<summary><b>2026-10-16 (Fri)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter2-P10 ([#51]): aspect ratio (4:3, 16:9, 1:1); opened the PR ([#80]); started Iter2-P11 ([#52]): hiding and replacing the guide | Reference thumbnail and the guide's starting position | – |
| 박재완 | Finished the accounts server ([#50]) and merged its PR ([#78]), with the Design and Testing Documentation updated; two test accounts became friends through the API on Server B; agreed the order of the invitation work with 조성민 ([#55]) | Over the weekend, both due 10/18: pose description on the server ([#53]), an optional description in `POST /poses` and the prompt, tried with `compare_image_edit_apis.py`; Sign-in, Pix ID, and Friends screens ([#54]) | – |
| 조성민 | Iter2-P17 ([#57]): guide moves from Subject view (`guide.move` steps every 50 ms and a reliable final value), the toggle shown on Subject view, and the notices on the photographer's phone; remote brightness and flash on the P10 `CameraController` additions; opened the PR | Iter2-P16 ([#56]): STUN and TURN from `room.created` and `session.joined`, and a first session across Wi-Fi and LTE | – |
| 한준형 | Edited the demo video and prepared the midterm presentation materials (slides and the demo flow) | Finish the presentation for the midterm on 10/21; Iter2-P21 ([#59]): integration tests for the Iteration 2 flows | – |

</details>

<details>
<summary><b>2026-10-19 (Mon)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter2-A4 ([#86]): checked Subject view against the photographer's aspect ratio on two phones and fixed it ([#89]) | Keep checking the fix in each ratio | – |
| 박재완 | Prepared the server, deployment, and accounts parts of the midterm presentation, and checked the demo flow against Server B | Pose description on the server ([#53]), which was due 10/18: an optional description in `POST /poses` and its prompt, tried on the example photos. Then the Sign-in, Pix ID, and Friends screens ([#54]) | – |
| 조성민 | Checked the remote brightness, flash, and guide moves from [#85] (merged 10/18) on the S25 Ultra and S23 Ultra over Server B, together with [#89]'s aspect-ratio fit on Subject view. Read through the camera changes of [#80] and [#83] (the rebind on a ratio change, the exposure restored after a rebind, the last frame held during a ratio change) to plan the zoom fix ([#47]) on top of them | Zoom stutter ([#47]), overdue since 10/15: measure first. `Timings` marks for zoom requests, lens switches, and camera status, and a per-frame trace of the applied zoom | – |
| 한준형 | Finished the Iteration 2 demo video and the midterm presentation materials; rehearsed the presentation with the demo flow on two phones | Give the midterm presentation (10/21); as PM, go through each member's remaining Iteration 2 issues and their due dates ([#47], [#53], [#54] overdue) and settle the order of the rest | – |

</details>

<details>
<summary><b>2026-10-20 (Tue)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter2-A4 ([#86]): kept checking and fixing [#89] in 4:3, 16:9, and 1:1 | Review the pose description PR ([#90]); plan Iter2-P18 ([#58]) | – |
| 박재완 | Pose description on the server ([#53]): an optional `description` in `POST /poses` with its own prompt, the prompts moved into `prompts.toml`, and descriptions tried through OpenRouter on the eight example photos. Decided that the idea chips are string resources in the app (no `GET /pose-ideas`) and that a response to a description has `templateId: null`. Opened [#90], and Codex found no major issues. Added the pose description part to the midterm presentation | Midterm presentation. The Generate poses screen in the app ([#53]). Address the reviews on [#90] and merge it | – |
| 조성민 | Zoom measurement for [#47]: `zoom.request`, `zoom.apply`, `zoom.lens`, and `camera.status` marks, and a Camera2Interop capture callback that logs each frame's sensor time and applied zoom. A script (`zoom_timings.py`) turns the log into frame intervals, dropped and frozen frames, request-to-frame latency, and the time of each lens switch | The fix for [#47]: zoom requests applied once per display frame, the lens held during a pinch, and the last frame kept on screen through a lens switch | – |
| 한준형 | Testing: went through the AI Collaboration Report guidelines v1.1 against the Iteration 2 report and listed what to add (the prompt logs become pages of their own, linked from the report's Prompt History); checked the test state of the merged features against the Testing Documentation for the presentation's testing section. PM: checked the schedule with each owner; [#47], [#53], and [#54] are being finished this week, with [#90] in review | Revise the AI Collaboration Report for v1.1 ([#91]); write the testing progress for the midterm presentation (latency, CI, image tests, unit tests) | – |

</details>

<details>
<summary><b>2026-10-21 (Wed)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed and approved pose descriptions ([#90]); suggested prompt wording for camera position and framing; summarized the arm and leg approaches tried in Iterations 1 and 2; planned Iter2-P18 ([#58]) | Iter2-P18 ([#58]): saved guides | – |
| 박재완 | Midterm presentation. Reworked pose generation ([#53]) after the 10/20 results. Every candidate now uses one prompt. Without a description, a vision model suggests four poses for the scene. With one, three candidates follow variations of it, because four requests with the same text gave nearly the same image. A run may take 60 s, because single requests took up to 46 s. Added the Generate poses screen, with "Skip, suggest poses for me" and with "Try other poses" going back to it. Updated Design 1.10, R&S 1.6, the Testing Documentation 1.9 (IT-7), and the Flow 2 wireframe. Fixed the Codex finding on refusal messages and merged [#90] | Sign-in, Pix ID, and Friends screens ([#54]), overdue since 10/18. First settle the four "Still open" rows for Iter2-P13 in the Design, then build Sign in, Sign up, Continue with Google, Pix ID setup, and Friends, and open the PR. Run IT-7 on a phone ([#59]) | – |
| 조성민 | [#47]: `CameraZoomPlan.lensFor(ratio, current, final)`, which keeps the bound lens until the pinch is 5 % past 1×, with its tests at the band's edges. Built the demo APK from the latest dev ([#85], [#89], [#90]) on Server B and installed it on the S25 Ultra and S23 Ultra | Finish [#47] on the latest dev (`setZoom(ratio, final)`, the last frame through a switch), measure before and after on a phone, and open the PR | – |
| 한준형 | Midterm presentation. Testing: wrote the testing progress section from the repository's history (the latency measurements, the `android-test` and `server-test` CI runs, the image-API comparison tests, and the unit tests) and the feature status of [#54] and [#47]; revised the AI Collaboration Report for guidelines v1.1 with the prompt logs as separate pages and merged [#91]. PM: confirmed with each owner what lands before 10/25 ([#47], [#54], [#58]) and what moves to Iteration 3 | Iter2-A1 ([#69]): the coverage tools (JaCoCo, pytest-cov) and the first measured run against the proposed targets | – |

</details>

<details>
<summary><b>2026-10-22 (Thu)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed sign-in and friends ([#92]); Iter2-P18 ([#58]): saved guide store, Save guide button, and the saved list on Add a pose guide; opened [#96] | Address the review on [#96] after [#94] is merged | – |
| 박재완 | Sign-in, Pix ID, and Friends screens ([#54]), with the PR opened. Two phones signed in with different accounts and added each other on Server B | Address the reviews on [#54] and merge it. Integration tests for pose generation and friends on Wi-Fi and cellular with the S21, S22, and S23 Ultra ([#59]). Help with the integration on the test phones ([#60]) and the latency and connection-rate measurement per network pair. Fix what the integration finds | – |
| 조성민 | Zoom stutter fix ([#47]): rebuilt on the latest dev, with `setZoom(ratio, final)`, the lens held during local and remote pinches, and `PreviewHold`, which keeps the last frame through a lens switch, shared with [#80]'s ratio change. Measured before and after on the S23 Ultra: no stutter either way, 30 fps (the most it offers apps), and no lens switch, because it zooms to 0.6× natively. Opened PR [#93] and resolved its conflicts with [#92]. Tested [#92]'s sign-in and friends screens on a phone against a local server | Address the review on [#93]. Measure [#47] on the S21, the only test phone with a separate ultrawide. Integration tests for the room-code session with the guide and remote control ([#59]) | – |
| 한준형 | Iter2-A1 ([#69], [#94]): JaCoCo and pytest-cov in the repository; screen tests on every Fragment and bottom sheet with `FragmentScenario` on a container of fakes (Views 29 → 86 %); JVM tests for the adapters that can run without a device (23 → 35 %); the first measured results in Testing Documentation 2.2.1 with the report screenshots. PM: reviewed the schedule for the last three days of the iteration ([#93], [#94], [#96] in review; [#59] and [#60] from 10/23) | Merge dev into [#94] and fold its Testing Documentation changes into one revision; the account screens' tests; set the coverage targets from the measured run | – |

</details>

## Iteration 3

The kickoff on 2026-10-25 replaced that day's standup ([[Team Meetings|Team-Meetings]]).

<details>
<summary><b>2026-10-26 (Mon)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter3-P10: *Pick a pose* one candidate at a time ([#133]); Iter3-P11: pose tips ([#135]); opened the Camera screen layout PR ([#137]) | Address the review on [#137]; Iter3-A1: the reference photo on Subject view ([#132]) | – |
| 박재완 | Iteration 3 issues and plan in the documents ([#109], [#130]); moved the wireframes into the repository ([#131]); set up the AWS server for the user test; started the Korean translation ([#111]) | Open the Korean translation PR; Iter3-P8: candidates in the scene photo's ratio ([#112]) | – |
| 조성민 | Iter3-P13: sessions across networks on the server ([#134]); started the app side | Finish the app side of [#117] and test it on mobile data | – |
| 한준형 | Planned the shared steps ([#120]) and photos saved on both phones ([#121]) | Take over Iter3-P9: the mask-only guide transfer ([#113]), which the shared steps need first | – |

</details>

<details>
<summary><b>2026-10-27 (Tue)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Merged the Camera screen layout ([#137]) and its fixes ([#142]); approved [#138] and [#140]; opened the reference photo on Subject view ([#143]) | Address the review on [#143] and test it on two phones; Iter3-P12: selfie mode ([#116]) | – |
| 박재완 | Redrew the wireframes to match the app ([#140]); merged the Korean translation ([#138]); updated the room code screens for any network and fixed Korean wording ([#144]); approved [#137] | Review [#143] and the mask-only transfer PR; candidates in the scene photo's ratio ([#112]) | – |
| 조성민 | Merged sessions across networks in the app ([#139]) and tested it on two phones | Iter3-P14: reconnection after a short drop ([#118]) | – |
| 한준형 | Took over the mask-only guide transfer ([#113]); approved [#144] | Open its PR and test it on two phones; then send guides from the subject's phone too | – |

</details>

<details>
<summary><b>2026-10-28 (Wed)</b> · 17:30–18:00</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Addressed the review on [#143], tested it on two phones, and merged it; cleaned up the `CLAUDE.md` files and the READMEs ([#147]) | Selfie mode ([#116]) | – |
| 박재완 | Approved [#145] and [#147]; account changes now show on the user's other phones ([#146]) | Candidates in the scene photo's ratio ([#112]) | – |
| 조성민 | Started reconnection after a short drop ([#118]) | Continue [#118]; Iter3-P15: invitations and friend requests ([#119]) | – |
| 한준형 | Finished the mask-only guide transfer, with guides from the subject's phone too, tested it on two phones, and merged it ([#145]); approved [#146] | Test guides from the subject's phone on two phones; then the shared steps ([#120]) and photos on both phones ([#121]) | – |

</details>

<details>
<summary><b>2026-10-29 (Thu)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Iter3-P12: selfie mode, PR opened ([#152]) | Address the review on [#152] and merge it | – |
| 박재완 | The AWS server now deploys from GitHub Actions ([#150]); approved [#149] | Iter3-P8: candidates in the scene photo's ratio ([#112]); review [#151] | – |
| 조성민 | Merged reconnection after a short drop ([#149]); invitations and friend requests as push notifications on the server ([#153]); opened the app side ([#154]) | Test invitations on two phones and merge [#154] | – |
| 한준형 | Iter3-P16: the guide and pose steps shared on both phones; opened [#151] and addressed the review | Merge [#151]; then photos saved on both phones ([#121]) | – |

</details>

<details>
<summary><b>2026-10-30 (Fri)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Merged selfie mode ([#152]); pose tips in Korean and a light while taking the scene photo, PR opened ([#157]); reviewed the user test questions and format | Run the user test; address the review on [#157] and merge it | – |
| 박재완 | Approved [#151]; pose candidates now follow the scene photo's ratio ([#155]); prepared the user test planned for the weekend (10/31–11/1) | Run the user test | – |
| 조성민 | Merged inviting a friend to shoot together ([#154]); reviewed the user test questions and format | Run the user test | – |
| 한준형 | Merged the shared steps ([#151]); started photos saved on both phones ([#121]); reviewed the user test questions and format | Run the user test; open the PR for [#121] | – |

</details>

[#5]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/5
[#6]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/6
[#11]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/11
[#13]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/13
[#15]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/15
[#16]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/16
[#17]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/17
[#19]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/19
[#22]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/22
[#23]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/23
[#24]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/24
[#25]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/25
[#27]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/27
[#28]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/28
[#30]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/30
[#31]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/31
[#32]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/32
[#33]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/33
[#34]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/34
[#35]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/35
[#37]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/37
[#39]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/39
[#40]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/40
[#44]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/44
[#46]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/46
[#47]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/47
[#48]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/48
[#49]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/49
[#50]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/50
[#51]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/51
[#52]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/52
[#53]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/53
[#54]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/54
[#55]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/55
[#56]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/56
[#57]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/57
[#59]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/59
[#61]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/61
[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#63]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/63
[#64]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/64
[#66]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/66
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#68]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/68
[#69]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/69
[#70]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/70
[#71]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/71
[#72]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/72
[#73]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/73
[#74]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/74
[#75]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/75
[#76]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/76
[#77]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/77
[#78]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/78
[#80]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/80
[#58]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/58
[#60]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/60
[#83]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/83
[#85]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/85
[#86]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/86
[#89]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/89
[#90]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/90
[#91]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/91
[#92]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/92
[#93]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/93
[#94]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/94
[#96]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/96
[#109]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/109
[#111]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/111
[#112]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/112
[#113]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/113
[#116]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/116
[#117]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/117
[#118]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/118
[#119]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/119
[#120]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/120
[#121]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/121
[#130]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/130
[#131]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/131
[#132]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/132
[#133]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/133
[#134]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/134
[#135]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/135
[#137]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/137
[#138]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/138
[#139]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/139
[#140]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/140
[#142]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/142
[#143]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/143
[#144]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/144
[#145]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/145
[#146]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/146
[#147]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/147
[#149]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/149
[#150]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/150
[#151]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/151
[#152]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/152
[#153]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/153
[#154]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/154
[#155]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/155
[#157]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/157
