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
| 박동제 | Merged the Android project setup and shared contracts (#13) and the PR template (#15); opened and merged the app shell with navigation and the screen skeleton (#16) | Review the server and CI PRs | – |
| 박재완 | FastAPI server skeleton; basic structure of the signaling and pose APIs; researched and tested external pose generation APIs | WebSocket signaling; choose the pose API | – |
| 조성민 | Looked into how to build the WebRTC connection and remote zoom; researched related code and libraries | Choose the WebRTC library; start signaling | – |
| 한준형 | Looked into the CameraX preview and capture setup; camera permission and the basic preview screen | Capture button and photo saving; check on a phone | – |

</details>

<details>
<summary><b>2026-10-02 (Fri)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed and approved the signaling hub server (#17) and the Android CI (#19) | Review the open PRs; check the Design Documentation against the merged work | – |
| 박재완 | Signaling: room create, join, signal, and leave; tested with two local WebSocket clients | Pose generation API adapter and server endpoint | – |
| 조성민 | Chose the WebRTC library; basic signaling structure | WebRTC connection between two phones | – |
| 한준형 | CameraX capture and photo saving; checked that saved photos do not include the on-screen guide | Overlay structure to show the reference guide on the camera | – |

</details>

<details>
<summary><b>2026-10-03 (Sat)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Checked everyone's progress against the schedule; reviewed the R&S and Design Documentation for gaps against the merged work | Review the CameraX, WebRTC, and pose generation PRs; start #5 (reference selection and segmentation) | – |
| 박재완 | Pose generation server with image preprocessing, timeouts, and error handling; tested generating four poses | Connect the pose generation API in the Android client | – |
| 조성민 | WebRTC peer connection and data channels | Camera video streaming | – |
| 한준형 | Showed the guide over the camera preview; guide position and size for different screen sizes | Dragging and pinch zoom for the guide | – |

</details>

<details>
<summary><b>2026-10-04 (Sun)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Reviewed the pose generation APIs (#22), WebRTC streaming (#23), the wiki workflow (#24), and the pose generation wiki (#25); implemented #5: ML Kit segmentation, outline, guide maker, ReferenceViewModel, photo picker and reference screens | Test #5 on the Galaxy S22 and open its PR | GuideRepository from #6 not ready yet; added a stand-in until it lands |
| 박재완 | PoseGenerator: four parallel requests, cancel, and partial results; connected the client and the server | Pose generation UI and the pose selection and guide flow | – |
| 조성민 | Streaming the camera video to the other phone | Room code based session connection | – |
| 한준형 | Moving and resizing the guide, kept from going too far off screen | Connect the opacity and style controls; finish the overlay | – |

</details>

<details>
<summary><b>2026-10-05 (Mon)</b> · 23:00–23:10</summary>

| Member | Done | Plan | Blockers |
|---|---|---|---|
| 박동제 | Fixed the first-use wait for the segmentation model; tested #5 on the Galaxy S22 (about 0.6 s per photo); opened #28; approved remote zoom (#27); prototyped arm and leg lines in the outline and moved them to a later iteration | Address the review comments on #28 | – |
| 박재완 | Connected the Generating, Pick a pose, and failure screens; end-to-end flow that applies a generated pose as the guide | Test the whole pose generation flow and its latency with the real API | – |
| 조성민 | Room code and session connection flow | Test the connection and video on two real phones | – |
| 한준형 | Connected guide opacity and the cutout/outline switch; checked that photos still save without the guide while the overlay is on | Connect the guide state synced with pose generation results to the camera screen | – |

</details>
