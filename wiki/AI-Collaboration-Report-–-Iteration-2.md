**Iteration:** 2 (2026-10-11 ~ 2026-10-25) · **Written by:** 한준형 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Fable 5.1; Claude Opus 5.5 for the camera and guide tasks and 박동제's code reviews), ChatGPT Codex (the reviewer on the feature PRs, and parts of the zoom plan in [#93]), GitHub Copilot code review

**How this report was prepared.** Each PR's *AI usage* section (tools, what AI did well, what we fixed, its input prompts) was written when the PR was opened, so the material was collected as we went (Team Meetings 2026-10-09, item 8). For the iteration-level findings, every member's Claude Code and Codex sessions for 10/11–10/25 are reviewed with the course's AI Agent Session Exporter after a check for personal or sensitive information; the sessions are source material only and are not attached. The findings below were drawn from those sections and from the prompt logs by 한준형 at the close (10/23). The per-PR evidence is folded under *Evidence by PR* so the findings read as one page.

## 1. Where AI Was Used

- **Documents:** the AI rules ([#62]), the Iteration 2 R&S and design ([#66], [#67]), the issue renumbering, the Testing Documentation ([#70]), and the CLAUDE.md rules and history ([#76]): Claude Code.
- **Server:** deployment on the course servers with Docker and nginx ([#71]), sessions and room codes in Redis ([#75]), accounts and friends ([#78]), pose descriptions and suggested poses ([#90]), invitations were not built (Iter2-P15, moved to Iteration 3). Claude Code wrote the server code; the markers name each file.
- **Android:** smooth outlines ([#72]), pinch zoom and the guide-edit mode ([#74]), the Join code fix ([#81]), camera controls ([#80]), guide controls and the thumbnail ([#83]), remote brightness, flash, and guide moves ([#85]), Subject view's video fit ([#89]), saved guides ([#96]): Claude Code wrote the code, the tests (190 in all), and the documents; humans reviewed on phones and decided. Zoom stutter ([#93]): Claude Code, with ChatGPT Codex on `CameraZoomPlan`; sign-in and friends screens ([#92]): Claude Code.
- **Testing and integration:** the coverage tools, the screen tests on every Fragment with a container of fakes, the JVM tests of the adapters, the coverage targets and the adapter plan ([#94]); the integration record from the demo recordings and the mapping of the 98 acceptance rows to tests ([#98]); the paused-notice fix found by that run ([#99]): Claude Code, with 한준형 deciding the targets, the scope, and what the recordings prove. The app has 756 tests and the server 431 at the close.
- **Code review:** Claude Code wrote 박동제's reviews of [#74], [#75], [#78], and [#92] ([[log|Iteration-2-–-Prompt-Log-–-Code-Reviews]]); the Codex reviewer commented on seven feature PRs, and the author judged each finding before fixing or declining it (section 4).
- **CLAUDE.md:** updated by the feature PRs with the code; Iteration 2 adds the marker, Testing Documentation, and no-force-push rules ([[history|Iteration-2-–-CLAUDE-md-History]]).
- **Markers in the code:** every AI-written source file starts with `AI-generated with <tool>, <date>, reviewed by <name>` ([all markers](https://github.com/search?q=repo%3Asnuhcs-course%2Fswpp-2026-project-team-10+%22AI-generated+with%22&type=code)), more than guidelines v1.1 require. The `manually fixed by <name>, <date>` note it asks for next to hand-edited code has not been needed: no merged PR edited code by hand.
- **Not used:** the kickoff and product decisions (what the guide shows, where controls sit, what is Could), tests on real phones, and the final review verdicts; AI wrote text and code against them. Nor the integration run on the phones ([#98]) and the decision to move Iter2-P15/P16 and the Could items to Iteration 3.

## 2. Prompt History

Full input prompt logs, one page per task, with the prompts as they were sent and none of the agents' responses: [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] · [[Server and deployment|Iteration-2-–-Prompt-Log-–-Server-and-Deployment]] · [[User CRUD and Friends|Iteration-2-–-Prompt-Log-–-User-CRUD-and-Friends]] · [[Pose Generation|Iteration-2-–-Prompt-Log-–-Pose-Generation]] · [[Android camera and guide|Iteration-2-–-Prompt-Log-–-Camera-and-Guide]] · [[Real-time and remote control|Iteration-2-–-Prompt-Log-–-Real-time-and-Remote-Control]] · [[Code reviews|Iteration-2-–-Prompt-Log-–-Code-Reviews]] · [[Testing|Iteration-2-–-Prompt-Log-–-Testing]]

## 3. What AI Did Well

- **Fast from plan to device:** 15 minutes to 1.5 hours of agent time per feature PR against 1–4 planned hours ([#74], [#81], [#80], [#83], [#85]), with first test runs mostly passing.
- **It checked before it wrote:** listing the overlaps with other tasks before coding ([#80], [#83]), prototyping the smoothing in Python before Kotlin ([#72]), and reusing what the previous PR had laid down instead of re-doing it ([#85]).
- **It found what tests could not:** running the server on a laptop and two phones found the cropped subject video that unit tests cannot see ([#89]); emulator screenshots showed `PreviewView` drawing outside the crop ([#80]) and the hidden-guide states ([#83]).
- **Tests that run the real screens found a real bug ([#94]):** a container of fakes installed in `PixApplication` let `FragmentScenario` launch every Fragment and bottom sheet, which took the Views layer from 29 % to 91 % line coverage in two days and found that a hidden guide left the edit toggle drawn as on (`MaterialButton` ignores `setChecked` while disabled); the JVM tests of the adapters (a field-based segmenter double, a fake `ImageProxy`, MockWebServer's WebSocket upgrade, the stats reader split out of the WebRTC client) took that layer from 23 % to 43 %.
- **It read the recordings as a tester ([#98]):** given six screen recordings, the agent matched each to the integration scripts and the user stories, wrote down what each recording does not show instead of marking the script as passed, and named the one observation (the paused notice before the first video) that became the fix of [#99].
- **Documents before code ([#66], [#67]):** the R&S and the design were written before feature work, with every undecided detail under *Still open* with an owner, so the owners built against the interfaces from 10/13.

<details>
<summary>Evidence by PR</summary>

- **Pinch zoom with the guide-edit mode ([#74]):** from the approved plan to a device-testable APK in about 30 minutes of agent time (planned 2.5 h; about 0.5 h of mine for the plan review, the two-phone check, and the PR). `PinchZoomController` and its six Robolectric tests, which drive the real `ScaleGestureDetector` with two-finger `MotionEvent`s, passed on the first run, as did the view-level test that one pinch zooms or edits the guide by the mode alone. The four review comments on the PR were triaged and fixed in one pass (two view-model rules with tests, a checkable toggle, the subject's toggle hidden until [#57]).
- **Join with code's kept code ([#81]):** from a one-line bug report to the issue text in the team's format, then from the issue to the fix in about 15 minutes of agent time (planned 1 h; about 0.2 h of mine for the title scheme and the review). The agent found both halves of the cause, the code never cleared in `SessionViewModel` and the Join screen filling it however it was opened, kept the two cases the requirements need (FR-8.3, FR-6.11), and its seven state-based tests in `SessionViewModelTest` passed on the first run.
- **Smooth guide outlines ([#72]):** before writing Kotlin, the agent prototyped the smoothing in Python on a mask from our Iteration 1 demo video and checked that the subject's copy, sent as WebP, draws the same line. On the emulator with the real ML Kit models a photo becomes a guide in 0.25–0.35 s. For the arms and legs it ran three approaches on four Wikimedia test photos and showed them side by side, which made the decision to drop them take one look.
- **Camera controls ([#80]):** before writing code, the agent listed every place that assumed the 3:4 frame and what overlapped with five other tasks, so the subject's side waited until its boundary with Iter2-P17 was settled. Each step ran on the emulator with screenshots, which showed `PreviewView` drawing the camera image outside the 16:9 and 1:1 crops and confirmed the saved photo's size in each ratio. It found WebRTC's size limit without an aspect ratio, so the stream keeps the frame's shape with a one-line change. From the task review to the PR in about 1.5 hours of one session (planned 4 h), with one check on the S22 by 박동제.
- **Guide controls ([#83]):** before writing code, the agent listed what P11 changes in the shared contracts and which parts belong to other owners, so the mask-only transfer was left to 조성민 and nothing in the sync code changed. Each of the four UI steps came with emulator screenshots of the real app, which is where 박동제 changed two of them (no *Replace* button, no close button on the thumbnail). The starting position was checked with two references, one taller and one wider than the frame, in all three ratios. From the plan to the PR in about 1.5 hours of one session (planned 3 h).
- **Remote brightness, flash, and guide moves ([#85]):** from the plan to a build that passed every item of the two-phone check in about 1.5 hours of agent time (planned 4 h; about 1 h of mine for the plan, the two-phone check, and four review rounds). The plan started from what [#80] had already laid down (`camera.state` with exposure, flash, and aspect; `setExposure` and `setFlash`) instead of re-doing it, reused the zoom path's step-then-final and echo-held rules for the two new controls through one small class, and sent guide moves through the existing repository so the photographer's limits held without new clamping code. Each of the four Codex findings was assessed first and then fixed with a test in about ten minutes.
- **Subject view's video ([#89]):** with the course servers closed off campus, the agent ran the server on the laptop and joined the emulator and the S22 over the same Wi-Fi, which found a bug that [#80]'s unit tests could not: the subject's video was cropped while the grid and the guide fit the frame. It measured the offset in each ratio, traced it to WebRTC's renderer cropping a `match_parent` view, tried the fix on the S22 before committing, and wrote a test that fails on the old layout.
- **Saved guides ([#96]):** before writing code, the agent listed what the Design already fixed and what needed the team. Asked to look again after [#92] and [#93] were merged, it found that [#94] gives `AppContainer` a constructor and adds tests for the same sheet and Camera screen, and that [#92]'s device-transfer rules would copy saved guides to a new phone; the work was built to follow [#94], with an in-memory store its test container can take. Each new test class was checked by breaking the code on purpose (ten deliberate bugs, each caught), each UI step came with emulator screenshots, and IT-9 ran after a force-stop.
- **Coverage and screen tests ([#94]):** JaCoCo and pytest-cov were configured with the first run's real numbers (the agent found AGP 9's class output directories from an existing build), then 122 screen tests and 33 adapter tests followed over two days, each batch pushed with the coverage per layer reported; the Testing Documentation's targets were set from the measured run and the adapter layer got a per-iteration plan instead of an exclusion list.
- **Integration record ([#98]):** the four recordings of 10/23 and the two of the midterm presentation were read frame by frame; the record names the steps each one does not show (the subject's own drag, *Skip*, the kill-and-reopen, the two-phone friend flow), why the cellular pairs could not run, and which NFRs stayed unmeasured. A subagent mapped the 98 acceptance rows to test methods, every name checked by script.
- **Integration fix ([#99]):** the paused notice was traced to the watchdog counting from the screen's opening rather than the first frame, fixed in one rule, and tested with a frame fake that needs no native WebRTC buffer; the `Timings` marks were checked against the measurements the Testing Documentation names.

</details>

## 4. Hallucinations / Errors

- **Platform behaviour asserted, not checked:** `PreviewView` assumed to clip to the viewport ([#80]); the subject's renderer said to "already fit the stream" when at `match_parent` it crops every frame, which put the guide 26–41 % off in 16:9 and 1:1 ([#80], found in [#89]). Both were caught on a device, not by the 45 unit tests; the second cost a separate fix PR.
- **Asynchronous and cross-device states missed:** the camera READY before the exposure was restored, a flash reported on an ultrawide without one ([#80]); four edge cases in the remote controls, including the protocol version left at 1 ([#85]); the subject's edit mode surviving a hidden guide ([#74]). All caught by the Codex reviewer, none by the agent's own review or its new tests; each fixed with a test in about ten minutes.
- **Stale context after a merge:** a revision number from the scheme that [#68] had replaced ([#74]); a review screenshot from an older build ([#83]); a PR number written before it was looked up ([#85]). Caught in human review.
- **Numbers and claims that went stale while the work moved ([#94], [#98]):** the PR body kept the first commit's test count and coverage through three later pushes; an estimate of the adapter layer after four new tests (41 %) was off because the merge of `dev` had added three account adapters at 0 %; the first draft of the integration record left the subject phone and the tester as "to confirm". All caught by the author's re-reading, none by a tool.
- **Decisions it should have left open:** the first design plan chose a guide style and put the subject's pinch zoom under Could ([#67]); a *Replace* button that duplicated *Guide* ([#83]); a fix for a bug that recordings showed was not there ([#83]).

<details>
<summary>Evidence by PR</summary>

- **Open items decided by the agent ([#67]):** the first design plan left the guide style open and put the subject's pinch zoom under Could; the team decided both (outline only, the toggle on both phones) and the text was corrected.
- **The subject's edit mode survived a hidden guide ([#74]):** the Camera screen cleared the mode when the guide was hidden, but the agent's Subject view did not, which would have left the video unresponsive to pinches. Caught by the Codex reviewer, not by the 17 new tests; fixed in ten minutes with a test.
- **A revision number from the old scheme ([#74]):** after merging `dev` the agent numbered its Design Documentation row 1.10, following the sentence it had read before [#68] changed the scheme to 1.1, 1.2, …; caught by 박동제 in review, renumbered to 1.4 after the next merge.
- **Smaller slips ([#74]):** a commit message said the mode's defaults were "agreed with the guide owner" before they were (amended); `git add -A` staged an untracked Gradle file (dropped from the commit); `isChecked` on a `<Button>` view binding did not compile until the layout named `MaterialButton` (caught by the build).
- **A prompt log in the PR ([#72]):** asked to keep the prompt log local, the agent still added a wiki prompt log page to the PR; caught by 박동제 and removed before review.
- **The blur sized from every region ([#72]):** the blur radius came from the box around all foreground regions, so a stray blob far from the person made it larger; caught by the Codex reviewer and fixed with the height of the largest region.
- **`PreviewView` assumed to clip ([#80]):** the agent expected `PreviewView` to show only the viewport's crop; its first emulator screenshots of 16:9 and 1:1 showed the camera image around it, and the Camera screen now clips the preview itself.
- **Asynchronous camera states missed ([#80]):** the camera became READY before the exposure was restored after a rebind, and a separately exposed ultrawide without a flash still reported the flash on. Both were caught by the Codex reviewer, not by the agent's own review or the 45 new tests, which cannot run CameraX.
- **An incomplete claim about message order ([#80]):** the PR said the subject's guide ends in the same place whatever order `camera.state` and `guide.state` arrive in. When Codex questioned it, the agent found the case it had missed, a subject joining late, and changed how the subject's copy is clamped, since the reviewer's suggestion would not have covered that case either.
- **A test with the wrong direction ([#80]):** a test expected the guide to move when the frame changed from 4:3 to 16:9, but a narrower frame widens the clamp range; caught by the test run and fixed to check 16:9 and then 1:1.
- **A wrong claim about the renderer ([#80], found in [#89]):** the agent wrote in [#80] that the subject's renderer "already fits the stream", because it is set to aspect-fit scaling. At `match_parent` it crops every frame to the view's shape, so in 16:9 and 1:1 the subject's guide lay 26–41 % away from the photographer's; only the two-phone check on 10/18 showed it.
- **A screenshot from an older build ([#83]):** the review image of the hidden guide reused a screenshot taken before the *Replace* button was removed, so it still showed the button; caught by 박동제 and retaken.
- **A disabled slider that looked enabled ([#83]):** the opacity slider is disabled while the guide is hidden, but the slider style had no disabled colors, so it looked usable; the agent saw it on the retaken screenshot and added dimmed colors.
- **A render path that skipped a rule ([#83]):** the thumbnail is re-rendered alone after a layout change, and that path did not know the scene photo step hides it, so an earlier guide's reference could come back over the scene photo. Caught by the Codex reviewer; the agent had not seen the scene photo step on the emulator, which needs the AI consent first.
- **A bug that was not there ([#83]):** in its own review the agent reasoned that the thumbnail could glide in from the screen's corner when the Camera screen comes back, and wrote a fix. Before committing it, recordings of the same steps on the old and the new build showed the thumbnail in place from the first frame on both, so the fix was dropped.
- **A control that duplicated another ([#83]):** the agent built *Replace* as its own button, as the screen list in R&S 6.2 had it, although *Guide* already opens the same sheet; 박동제 asked how the two differ, and the button became the sheet's replace prompt.
- **Four edge cases in the remote controls ([#85]):** a final brightness request that CameraX failed to apply got no echo, because the controller republishes an unchanged value that the StateFlow drops, so the subject kept its optimistic value; the protocol version stayed at 1 although Design 2.5.1 had planned 2 for the new requests, so a mixed build would show controls that do nothing; the "already at that value" shortcut confirmed a final brightness or flash value while an earlier request was still in flight, which could then move the camera away from it; and `guide.move` did not name its guide, so a move from before a replacement would have landed on the new guide. All four caught by the Codex reviewer, none by the 34 new tests; each fixed with a test in about ten minutes.
- **A wrong PR number in a commit message ([#85]):** the agent wrote "the Codex review of [#83]" before looking the number up; corrected to [#85] with an amended commit and a force push of its own branch.
- **Failure paths the tests did not cover ([#96]):** 박재완's review of the saved guides found what the agent's tests had not tried: a saved guide still applied after the sheet was closed, a result lost while the screen was stopped, an exception other than `IOException` ending the app while saving, a damaged `meta.json` that would crash the placement, a list ordered by the wall clock, folders that a failed delete left behind, and thumbnails smaller than their item. Each was fixed with a test that fails without the fix.
- **A stale PR body ([#94]):** "How to verify" still said 404 tests and 50.2 % after the branch had 482 tests and 73.8 %; found when the author asked for a re-check, fixed in one edit.
- **A test that could not fail for the right reason ([#94]):** the password rule "not the same as the Pix ID" was tested with a five-character Pix ID, which the length rule refuses first; the test was given a nine-character Pix ID.
- **A pinch that never began ([#94]):** the Camera screen's pinch test used a 100 px span, below `ViewConfiguration`'s minimum scaling span, so `ScaleGestureDetector` never started; widened to 300 px, and the rule written into `android/CLAUDE.md`.
- **A plain-JVM `Rect` ([#94]):** `CameraFrameSourceTest` on the plain JVM took the native crop path because the stub `Rect` returns 0 for every method; moved under Robolectric.
- **A wrong row count ([#98]):** the revision row said 89 acceptance rows where the table had 98; caught by counting the table and corrected in the next commit.
- **A finding filed as "check on the phones" before the cause was in reach ([#98], [#99]):** the paused notice before the first video was first recorded as something to confirm with a `Timings` log in Iteration 3; when the integration task started, the cause was in the code in minutes.

</details>

## 5. Prompt Revisions

- **Name what is about to change ([#74])**
  - Before: "Implement the toggle as an icon button." The agent put it in the guide's Cutout / Outline row.
  - After: "That row will go away later anyway. Put the toggle on the camera screen itself." It moved beside the zoom readout, where it stayed.
  - Why: naming what another issue (Iter2-P11) is about to change made the agent choose a place that lasts.
- **Give experiments an exit condition ([#72])**
  - Before: "Do the smooth outline first, then the arms and legs." Three tries traced the wrong lines.
  - After: "An outline good enough to refer to while shooting. Show me screenshots. If lines alone cannot read as arms, I will not implement this part."
  - Why: an acceptance criterion and an exit condition turned an open-ended attempt into a decision.
- **Ask before "fix it" ([#85])**
  - Before: paste the review comment and "fix it".
  - After: paste it and "does this need a fix?", then "fix it, commit, and push" once the answer holds up.
  - Why: the agent checked each code path first and said what the fix would be; all four findings were real, and no wrong fix went in.

<details>
<summary>More revisions by PR</summary>

- **Guide style ([#67])**
  - Before: "Write the design document from the decided items." The agent proposed its own guide style.
  - After: "The guide shows only the outline, never the reference's background; the exact style is decided in P23. If-possible items are Could."
  - Why: it fixed the decision and the priority rule before the text was written.
- **An unscheduled issue's title ([#81])**
  - Before: "Write the issue like [#47]." The agent titled it `[Bug] …`, because the issues it had seen only showed `Iter2-Pn` for scheduled tasks.
  - After: "The title should be like `[Iter2-A2] Move live preview room sessions to Redis`." It found the `Iter2-An` series for items added outside the schedule and numbered the issue `Iter2-A3`.
  - Why: pointing at one example of the convention was enough; the rule itself is written nowhere.
- **Conflicts before code ([#80])**
  - Before ([#72]): "Start the issue."
  - After: "First list exactly what P10 has to build, and before implementing, review what could conflict with other work."
  - Why: the agent found the overlaps with Iter2-P5 and P17 before writing them, and the subject's side was held until they were settled.
- **A commit for each check ([#72], [#80])**
  - Before: one commit per task.
  - After: "Commit at each step that needs my check, and put the document edits in a separate last commit; before each commit, tell me what to review."
  - Why: [#80] went in as four reviewable commits (contract, Camera screen, subject's side, documents), each checked on the emulator or the S22 first.
- **Screenshots for each UI step ([#83])**
  - Before ([#80]): "Before each commit, tell me what to review."
  - After: "Since UI matters here, prepare screenshots for me to review each time a task is done."
  - Why: two of the four UI steps changed after a screenshot review (the *Replace* button, the thumbnail's close button and its fixed corner), before any of them was committed.
- **Asking what a control adds ([#83])**
  - Before: the screen list in R&S 6.2, which named a *Replace* button.
  - After: "How is it different from the Guide button below?", then "Remove the Replace button; with a guide on the camera, Guide asks whether to replace the pose."
  - Why: asking for the difference showed that the planned button repeated *Guide*.
- **A scope question with a yes-or-no answer ([#85])**
  - Before: "the subject's view doesn't follow the photographer's ratio change, why?"
  - After: "...is this in this issue's scope? Just tell me whether it is or not."
  - Why: the answer named the owning task ([#80]) in two sentences, and the finding became a separate bug issue (Iter2-A4) instead of growing this PR.
- **Looking again after merges ([#96])**
  - Before: "Let's start issue [#58]. Is there anything I should consider or discuss with the team?"
  - After, two days later: "Some PRs were merged. Update the local branch, review [#58] again, and tell me."
  - Why: the second look found what the first could not: an open PR ([#94]) rebuilding `AppContainer` and testing the same screens, and new transfer rules ([#92]) that would have copied saved guides to a new phone.
- **One revision row per PR ([#97])**
  - Before: one revision row per change, six rows on one day for one task.
  - After: "revision 버전이 날짜마다 너무 계속 번잡하게 바뀌는 것 같아서, 가능하면 간략하게 버전을 합쳐 줘." The branch's rows became one per page; rows already on `dev` stayed, because merged PR bodies cite them.
  - Why: the history should read by PR, not by edit.
- **Give the agent the facts it cannot see ([#98])**
  - Before: "이 데모 영상들을 바탕으로 Integration을 진행하려고 한다." The first record left the phone, the tester, and the networks open.
  - After: "subject 폰은 S25 Ultra였고, 테스터는 조성민이다 … 셀룰러는 학교 서버(eduroam)로 하는 경우에는 지금 외부와 연결이 되지 않는 상황이라 …"
  - Why: the recordings show screens, not the test matrix; the record was right only once the team supplied what was outside the frame.

</details>

## 6. Manual Fixes and Why

- **No code was edited by hand** in the merged feature PRs ([#72], [#74], [#80], [#81], [#83], [#85]): when the agent was wrong, we told it what was wrong and it fixed it with a test. What humans did instead was decide: the guide-edit mode toggle on both phones and its defaults, the zoom slider out of the main layouts, only the smoothed mask sent to the subject, the Could list, the controls' places, no *Replace* button, the thumbnail's corners, and the scope boundaries between owners ([#80], [#83], [#85]). Through [#99] no merged PR edited code by hand.
- **Documents edited by hand (10/13):** the agent's version-sequence sentence ("continue as 1.10, 1.11") and the R&S edits it first applied on `dev` while [#66] held the file; both redone. The daily standup rows the agent drafted were edited before they were saved ([#80]).

<details>
<summary>Decisions by PR</summary>

- **Guide-edit mode ([#74]):** decisions made in review rather than by the agent: the mode turns off on the shutter; the toggle is an icon beside the zoom readout; the subject's toggle stays hidden until guide moves exist ([#57]), from 박동제's review; a hidden guide cannot be edited on either phone.
- **Smooth outlines ([#72]):** decisions by 박동제: keep the 3 dp white line with no fill; the lines for arms and legs in front of the body are out of scope rather than moved to a later iteration; the test photos are credited in the PR and not added to the repository.
- **Camera controls ([#80]):** decisions by 박동제 and the team: the controls' places (flash and ratio in the top bar, brightness behind a button, no detail panel); the ratio may change during a session; the subject's side of the ratio is built in P10 and the remote requests are left to Iter2-P17.
- **Guide controls ([#83]):** decisions by 박동제: the mask-only transfer is 조성민's; the four open points as the agent proposed (a new guide keeps the height and center of the person's box, *Reference confirm* on a neutral single color, a replacement starts at its own reference's place, the thumbnail in the top-left corner); no *Replace* button, *Guide* asks to replace instead; a tap, not a close button, makes the thumbnail small again, and a drag moves it only between the four corners; the size limits stay fixed for every start (FR-3.3 revised).
- **Join with code ([#81]):** that the code is offered only from *Session not found*, and selected so that typing replaces it, was the agent's proposal in the issue text, accepted when the issue was posted.
- **Remote controls ([#85]):** decisions taken at the plan: the brightness panel copied into Subject view's layout instead of a shared include, so the Camera layout stays 박동제's; `by` on `guide.state` through the syncer's attribution instead of a repository change; the Could items (remote focus, the permission switch) left as protocol messages only, with a Design TODO.
- **Saved guides ([#96]):** decisions by 박동제: keep the cutout as stored without changing Design 2.8, which names the mask; a used saved guide starts where the person stands in its reference, not where it was saved, so IT-9's expected result changed; build to merge after [#94]; the agent's proposals for the screens (a bookmark in the guide's panel, the list under *Generate poses here*, an ✕ that asks before deleting).
- **Coverage and targets ([#94]):** decisions by 한준형: the targets of Testing Documentation 2.1.2 (90 % for the app's managers, ViewModels, and Views and for every server module, 30 % for the platform adapters, with the proposals kept), no exclusion list for the adapter layer, and the per-iteration plan to raise it; the account screens and the Camera screen's remaining paths tested before the targets were set.
- **Integration record ([#98]):** decisions by the team: the subject phone and the tester, the networks run and not run, the two midterm recordings counted as a second pass, the frame sheets dropped and the compressed recordings kept as evidence, and the PR body written by hand.
- **Scope at the close ([#97], [#99]):** decisions by the team: Iter2-P15, Iter2-P16, and the four Could items move to Iteration 3 with the original plan kept next to the change; the Iteration 1 architecture figure removed; the paused-notice fix merged as the integration PR.

</details>

## 7. Takeaway for Iteration 3

- Check camera and video code on a device or emulator with screenshots at each step, and run the two-phone check before merging anything the subject sees; JVM tests did not show `PreviewView` drawing outside the crop or the cropped subject video ([#80], [#89]).
- Keep the Codex reviewer on camera and session code: it found the asynchronous and cross-device states that the agent's own review and tests missed ([#74], [#80], [#85]).
- Before coding, ask the agent for the overlaps with other tasks and for what nearby parts other issues will change; hold what another owner must agree on ([#74], [#80], [#83]).
- Paste a review finding as a question before "fix it", and give experiments an acceptance criterion and an exit condition ([#85], [#72]).
- After merging `dev`, have the agent re-read the documents' current numbering and decisions ([#74]); write conventions such as `Iter2-An` and the protocol version rule into the documents so they need not be told per issue ([#81], [#85]).
- Run the integration scripts with `adb logcat -s PixTimings` on, not only a screen recording: the recordings proved the flows but left every timing NFR unmeasured ([#98]).
- Install a container of fakes before launching a screen and test the Fragment, not only its layout class; the screen tests found a bug the layout tests could not, and the account Fragments were at 0 % until they had them ([#94]).
- Ask for the PR body to be re-read after every push, and give the agent the test matrix (phones, networks, servers) before it writes the record ([#94], [#98]).

[#47]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/47
[#57]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/57
[#58]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/58
[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#66]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/66
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#68]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/68
[#70]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/70
[#71]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/71
[#72]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/72
[#74]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/74
[#75]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/75
[#76]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/76
[#78]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/78
[#80]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/80
[#81]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/81
[#83]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/83
[#85]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/85
[#89]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/89
[#90]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/90
[#92]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/92
[#93]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/93
[#94]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/94
[#96]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/96
[#59]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/59
[#60]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/60
[#69]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/69
[#97]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/97
[#98]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/98
[#99]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/99
