**Iteration:** 2 (2026-10-11 ~ 2026-10-25) · **Written by:** TBD · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Fable 5.1; Claude Opus 5.5 for the camera and guide tasks and 박동제's code reviews), ChatGPT Codex (TBD), GitHub Copilot code review (TBD)

This page is written as we go: each pull request's *AI usage* section (tools used, what AI did well, what we had to fix, prompts) feeds sections 3–6 below, so the report is not written at the end as in Iteration 1 (Team Meetings 2026-10-09, item 8). Items marked **TBD** are filled in by the owner of the task when its PR is merged.

## 1. Where AI Was Used

- **Documents:** the AI rules in `CLAUDE.md` and the PR template ([#62]), the Iteration 2 requirements (R&S v1.0, [#66]) and design (Design v1.0, [#67]), the issue renumbering to `Iter1-Pn` / `Iter2-Pn`, the Testing Documentation format (v1.0, [#70]), the Testing Documentation rule in `CLAUDE.md` and the CLAUDE.md history ([#76]): Claude Code.
- **Server:** deployment on the course servers with Docker and nginx ([#71]), sessions and room codes in Redis ([#75]), accounts and friends (TBD), invitations (TBD), pose descriptions (TBD): TBD.
- **Android:** smooth guide outlines ([#72]: Claude Code wrote the mask smoothing, the contour tracing, 19 tests, and the documents; the lines for arms and legs it prototyped were dropped), pinch zoom with the guide-edit mode ([#74]: Claude Code wrote `PinchZoomController`, the overlay's edit-mode rule, both screens' changes, 17 tests, and the documents), the kept room code on *Join with code* ([#81]: Claude Code wrote the issue text, the `SessionViewModel` rule, the Join screen's navigation argument, 7 tests, and the documents), zoom stutter (TBD), camera controls ([#80]: Claude Code wrote the camera contract additions, the controller's focus, exposure, flash, and aspect ratio, the Camera screen's controls, the subject's side of the ratio, 45 tests, and the documents), guide controls ([#83]: Claude Code wrote the outline-only guide on both screens, *Hide* / *Show* and the replace prompt, the anchor and the starting position, the reference thumbnail with its corner drag, 29 tests, and the documents), saved guides (TBD), sign-in and friends screens (TBD), remote brightness, flash, and guide moves (TBD): TBD for the rest.
- **Code review:** Claude Code (Claude Opus 5.5, effort xhigh) wrote 박동제's reviews of [#74] (4 comments), [#75] (1), and [#78] (2, both fixed; then 1 in a second round) ([[log|Iteration-2-–-Prompt-Log-–-Code-Reviews]]). The Codex reviewer on [#72] (2 findings: 1 fixed, 1 answered with clearer documents) [#80] (4, all fixed, one in a different way than suggested), and [#83] (1, fixed). TBD for the rest (which PRs the Codex reviewer and Copilot checked, how many findings, and how they were judged).
- **CLAUDE.md:** `/CLAUDE.md`, `/android/CLAUDE.md`, and `/server/CLAUDE.md` are updated by the feature PRs with the code; the Iteration 2 rules add the AI-generated marker rule and the Testing Documentation rule ([[history|Iteration-2-–-CLAUDE-md-History]]).
- **AI-generated code markers:** every AI-written source file starts with the marker, added or updated in the same PR as the code ([all markers](https://github.com/search?q=repo%3Asnuhcs-course%2Fswpp-2026-project-team-10+%22AI-generated+with%22&type=code)).
- **Not used:** TBD (for example tests on real phones, product decisions, the kickoff decisions themselves; AI wrote the text and the code against them).

## 2. Prompt History

Full prompt logs, one page per task: [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] · [[Server and deployment|Iteration-2-–-Prompt-Log-–-Server-and-Deployment]] · [[Android camera and guide|Iteration-2-–-Prompt-Log-–-Camera-and-Guide]] · Accounts, friends, and invitations (TBD) · [[Real-time and remote control|Iteration-2-–-Prompt-Log-–-Real-time-and-Remote-Control]] · [[Code reviews|Iteration-2-–-Prompt-Log-–-Code-Reviews]]

## 3. What AI Did Well

- **Time:** TBD (planned hours of the Iteration 2 tasks against the human and agent time from the team schedule).
- **Documents before code ([#66], [#67]):** the R&S and the design for Iteration 2 were written from the kickoff notes and the issues before feature work, with every undecided detail listed under *Still open* with an owner, so the owners could build against the interfaces from 10/13.
- **Pinch zoom with the guide-edit mode ([#74]):** from the approved plan to a device-testable APK in about 30 minutes of agent time (planned 2.5 h; about 0.5 h of mine for the plan review, the two-phone check, and the PR). `PinchZoomController` and its six Robolectric tests, which drive the real `ScaleGestureDetector` with two-finger `MotionEvent`s, passed on the first run, as did the view-level test that one pinch zooms or edits the guide by the mode alone. The four review comments on the PR were triaged and fixed in one pass (two view-model rules with tests, a checkable toggle, the subject's toggle hidden until #57).
- **Join with code's kept code ([#81]):** from a one-line bug report to the issue text in the team's format, then from the issue to the fix in about 15 minutes of agent time (planned 1 h; about 0.2 h of mine for the title scheme and the review). The agent found both halves of the cause, the code never cleared in `SessionViewModel` and the Join screen filling it however it was opened, kept the two cases the requirements need (FR-8.3, FR-6.11), and its seven state-based tests in `SessionViewModelTest` passed on the first run.
- **Smooth guide outlines ([#72]):** before writing Kotlin, the agent prototyped the smoothing in Python on a mask from our Iteration 1 demo video and checked that the subject's copy, sent as WebP, draws the same line. On the emulator with the real ML Kit models a photo becomes a guide in 0.25–0.35 s. For the arms and legs it ran three approaches on four Wikimedia test photos and showed them side by side, which made the decision to drop them take one look.
- **Camera controls ([#80]):** before writing code, the agent listed every place that assumed the 3:4 frame and what overlapped with five other tasks, so the subject's side waited until its boundary with Iter2-P17 was settled. Each step ran on the emulator with screenshots, which showed `PreviewView` drawing the camera image outside the 16:9 and 1:1 crops and confirmed the saved photo's size in each ratio. It found WebRTC's size limit without an aspect ratio, so the stream keeps the frame's shape with a one-line change. From the task review to the PR in about 1.5 hours of one session (planned 4 h), with one check on the S22 by 박동제.
- **Guide controls ([#83]):** before writing code, the agent listed what P11 changes in the shared contracts and which parts belong to other owners, so the mask-only transfer was left to 조성민 and nothing in the sync code changed. Each of the four UI steps came with emulator screenshots of the real app, which is where 박동제 changed two of them (no *Replace* button, no close button on the thumbnail). The starting position was checked with two references, one taller and one wider than the frame, in all three ratios. From the plan to the PR in about 1.5 hours of one session (planned 3 h).
- TBD (one bullet per feature PR, taken from its *AI usage* section: what the agent got right first time, with a number where there is one).

## 4. Hallucinations / Errors

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
- **A screenshot from an older build ([#83]):** the review image of the hidden guide reused a screenshot taken before the *Replace* button was removed, so it still showed the button; caught by 박동제 and retaken.
- **A disabled slider that looked enabled ([#83]):** the opacity slider is disabled while the guide is hidden, but the slider style had no disabled colors, so it looked usable; the agent saw it on the retaken screenshot and added dimmed colors.
- **A render path that skipped a rule ([#83]):** the thumbnail is re-rendered alone after a layout change, and that path did not know the scene photo step hides it, so an earlier guide's reference could come back over the scene photo. Caught by the Codex reviewer; the agent had not seen the scene photo step on the emulator, which needs the AI consent first.
- **A control that duplicated another ([#83]):** the agent built *Replace* as its own button, as the screen list in R&S 6.2 had it, although *Guide* already opens the same sheet; 박동제 asked how the two differ, and the button became the sheet's replace prompt.
- TBD (one bullet per PR where the agent was wrong, what caught it, and what it cost).

## 5. Prompt Revisions

- **Guide style**
  - Before: "Write the design document from the decided items." The agent proposed its own guide style.
  - After: "The guide shows only the outline, never the reference's background; the exact style is decided in P23. If-possible items are Could."
  - Why: it fixed the decision and the priority rule before the text was written.
- **Where the toggle goes ([#74])**
  - Before: "I agree that the shutter turns the mode off; implement the toggle as an icon button." The agent put it in the guide's Cutout / Outline row.
  - After: "That row will go away later anyway. Put the toggle on the camera screen itself, at the top or the bottom." It moved beside the zoom readout under the preview, the same place as on Subject view.
  - Why: naming what another issue (Iter2-P11) is about to change made the agent choose a place that stays.
- **An unscheduled issue's title ([#81])**
  - Before: "Write the issue like #47." The agent titled it `[Bug] …`, because the issues it had seen only showed `Iter2-Pn` for scheduled tasks.
  - After: "The title should be like `[Iter2-A2] Move live preview room sessions to Redis`." It found the `Iter2-An` series for items added outside the schedule and numbered the issue `Iter2-A3`.
  - Why: pointing at one example of the convention was enough; the rule itself is written nowhere.
- **Lines for arms and legs ([#72])**
  - Before: "Do the smooth outline first, then the arms and legs." The first lines also traced the upper arms along the silhouette and drew lines on loose shorts.
  - After: "They do not need to be detected perfectly: an outline good enough to refer to while shooting. Show me screenshots." Then: "If lines alone cannot read as arms, I will not implement this part."
  - Why: an acceptance criterion and an exit condition turned an open-ended attempt into a decision after three tries.
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
- TBD

## 6. Manual Fixes and Why

- **Version and numbering (10/13):** the agent wrote "versions continue as 1.10, 1.11", which read as a jump from 1.0; corrected to 1.0, 1.1, 1.2, …, 1.10. The R&S edits were first applied on `dev` while [#66] already held the file; redone on that branch.
- **Our decisions:** the guide-edit mode toggle on both phones, the zoom slider out of the main layouts, only the smoothed mask sent to the subject, the Could list for Iteration 2.
- **Guide-edit mode ([#74]):** no code was edited by hand. Decisions made in review rather than by the agent: the mode turns off on the shutter; the toggle is an icon beside the zoom readout; the subject's toggle stays hidden until guide moves exist (#57), from 박동제's review; a hidden guide cannot be edited on either phone.
- **Smooth outlines ([#72]):** no code was edited by hand. Decisions by 박동제: keep the 3 dp white line with no fill; the lines for arms and legs in front of the body are out of scope rather than moved to a later iteration; the test photos are credited in the PR and not added to the repository.
- **Camera controls ([#80]):** no code was edited by hand. Decisions by 박동제 and the team: the controls' places (flash and ratio in the top bar, brightness behind a button, no detail panel); the ratio may change during a session; the subject's side of the ratio is built in P10 and the remote requests are left to Iter2-P17; the daily standup rows the agent drafted were edited by hand before they were saved.
- **Guide controls ([#83]):** no code was edited by hand. Decisions by 박동제: the mask-only transfer is 조성민's; the four open points as the agent proposed (a new guide keeps the height and center of the person's box, *Reference confirm* on a neutral single color, a replacement starts at its own reference's place, the thumbnail in the top-left corner); no *Replace* button, *Guide* asks to replace instead; a tap, not a close button, makes the thumbnail small again, and a drag moves it only between the four corners; the size limits stay fixed for every start (FR-3.3 revised).
- **Join with code ([#81]):** no code edited by hand. That the code is offered only from *Session not found*, and selected so that typing replaces it, was the agent's proposal in the issue text, accepted when the issue was posted.
- TBD

## 7. Takeaway for Iteration 3

- Before placing a control, tell the agent which neighbouring parts other issues are about to change ([#74]).
- After merging `dev`, have the agent re-read the document's current numbering and decisions instead of reusing what it read before ([#74]).
- Write the task-ID scheme for unscheduled items (`Iter2-An`) into the docs, so the agent does not have to be told per issue ([#81]).
- Give experimental work an acceptance criterion and an exit condition up front ([#72]).
- Ask the agent for the overlaps with other tasks before it writes code, and hold the parts another owner has to agree on ([#80]).
- Check camera code on an emulator with screenshots at each step: CameraX behaviour such as `PreviewView` drawing outside the crop is invisible to JVM tests ([#80]).
- Have the Codex reviewer read camera and session code before merging: it found asynchronous states the agent's own review missed ([#80]).
- Review UI on screenshots of the current build, and ask what each planned control adds over the existing ones ([#83]).
- TBD

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
