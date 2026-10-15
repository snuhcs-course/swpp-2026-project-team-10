**Iteration:** 2 (2026-10-11 ~ 2026-10-25) · **Written by:** TBD · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Fable 5.1), ChatGPT Codex (TBD), GitHub Copilot code review (TBD)

This page is written as we go: each pull request's *AI usage* section (tools used, what AI did well, what we had to fix, prompts) feeds sections 3–6 below, so the report is not written at the end as in Iteration 1 (Team Meetings 2026-10-09, item 8). Items marked **TBD** are filled in by the owner of the task when its PR is merged.

## 1. Where AI Was Used

- **Documents:** the AI rules in `CLAUDE.md` and the PR template ([#62]), the Iteration 2 requirements (R&S v1.0, [#66]) and design (Design v1.0, [#67]), the issue renumbering to `Iter1-Pn` / `Iter2-Pn`, the Testing Documentation format (v1.0, [#70]), the Testing Documentation rule in `CLAUDE.md` and the CLAUDE.md history ([#76]): Claude Code.
- **Server:** deployment on the course servers with Docker and nginx ([#71]), sessions and room codes in Redis ([#75]), accounts and friends (TBD), invitations (TBD), pose descriptions (TBD): TBD.
- **Android:** smooth guide outlines ([#72]), pinch zoom with the guide-edit mode ([#74]: Claude Code wrote `PinchZoomController`, the overlay's edit-mode rule, both screens' changes, 17 tests, and the documents), the kept room code on *Join with code* ([#81]: Claude Code wrote the issue text, the `SessionViewModel` rule, the Join screen's navigation argument, 7 tests, and the documents), zoom stutter (TBD), camera controls (TBD), guide controls and the reference thumbnail (TBD), saved guides (TBD), sign-in and friends screens (TBD), remote brightness, flash, and guide moves (TBD): TBD for the rest.
- **Code review:** TBD (which PRs the Codex reviewer and Copilot checked, how many findings, and how they were judged).
- **CLAUDE.md:** `/CLAUDE.md`, `/android/CLAUDE.md`, and `/server/CLAUDE.md` are updated by the feature PRs with the code; the Iteration 2 rules add the AI-generated marker rule and the Testing Documentation rule ([[history|Iteration-2-–-CLAUDE-md-History]]).
- **AI-generated code markers:** every AI-written source file starts with the marker, added or updated in the same PR as the code ([all markers](https://github.com/search?q=repo%3Asnuhcs-course%2Fswpp-2026-project-team-10+%22AI-generated+with%22&type=code)).
- **Not used:** TBD (for example tests on real phones, product decisions, the kickoff decisions themselves; AI wrote the text and the code against them).

## 2. Prompt History

Full prompt logs, one page per task: [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] · [[Server and deployment|Iteration-2-–-Prompt-Log-–-Server-and-Deployment]] · Android camera and guide (TBD) · Accounts, friends, and invitations (TBD) · [[Real-time and remote control|Iteration-2-–-Prompt-Log-–-Real-time-and-Remote-Control]] · Code reviews (TBD)

## 3. What AI Did Well

- **Time:** TBD (planned hours of the Iteration 2 tasks against the human and agent time from the team schedule).
- **Documents before code ([#66], [#67]):** the R&S and the design for Iteration 2 were written from the kickoff notes and the issues before feature work, with every undecided detail listed under *Still open* with an owner, so the owners could build against the interfaces from 10/13.
- **Pinch zoom with the guide-edit mode ([#74]):** from the approved plan to a device-testable APK in about 30 minutes of agent time (planned 2.5 h; about 0.5 h of mine for the plan review, the two-phone check, and the PR). `PinchZoomController` and its six Robolectric tests, which drive the real `ScaleGestureDetector` with two-finger `MotionEvent`s, passed on the first run, as did the view-level test that one pinch zooms or edits the guide by the mode alone. The four review comments on the PR were triaged and fixed in one pass (two view-model rules with tests, a checkable toggle, the subject's toggle hidden until #57).
- **Join with code's kept code ([#81]):** from a one-line bug report to the issue text in the team's format, then from the issue to the fix in about 15 minutes of agent time (planned 1 h; about 0.2 h of mine for the title scheme and the review). The agent found both halves of the cause, the code never cleared in `SessionViewModel` and the Join screen filling it however it was opened, kept the two cases the requirements need (FR-8.3, FR-6.11), and its seven state-based tests in `SessionViewModelTest` passed on the first run.
- TBD (one bullet per feature PR, taken from its *AI usage* section: what the agent got right first time, with a number where there is one).

## 4. Hallucinations / Errors

- **Open items decided by the agent ([#67]):** the first design plan left the guide style open and put the subject's pinch zoom under Could; the team decided both (outline only, the toggle on both phones) and the text was corrected.
- **The subject's edit mode survived a hidden guide ([#74]):** the Camera screen cleared the mode when the guide was hidden, but the agent's Subject view did not, which would have left the video unresponsive to pinches. Caught by the Codex reviewer, not by the 17 new tests; fixed in ten minutes with a test.
- **A revision number from the old scheme ([#74]):** after merging `dev` the agent numbered its Design Documentation row 1.10, following the sentence it had read before [#68] changed the scheme to 1.1, 1.2, …; caught by 박동제 in review, renumbered to 1.4 after the next merge.
- **Smaller slips ([#74]):** a commit message said the mode's defaults were "agreed with the guide owner" before they were (amended); `git add -A` staged an untracked Gradle file (dropped from the commit); `isChecked` on a `<Button>` view binding did not compile until the layout named `MaterialButton` (caught by the build).
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
- TBD

## 6. Manual Fixes and Why

- **Version and numbering (10/13):** the agent wrote "versions continue as 1.10, 1.11", which read as a jump from 1.0; corrected to 1.0, 1.1, 1.2, …, 1.10. The R&S edits were first applied on `dev` while [#66] already held the file; redone on that branch.
- **Our decisions:** the guide-edit mode toggle on both phones, the zoom slider out of the main layouts, only the smoothed mask sent to the subject, the Could list for Iteration 2.
- **Guide-edit mode ([#74]):** no code was edited by hand. Decisions made in review rather than by the agent: the mode turns off on the shutter; the toggle is an icon beside the zoom readout; the subject's toggle stays hidden until guide moves exist (#57), from 박동제's review; a hidden guide cannot be edited on either phone.
- **Join with code ([#81]):** no code edited by hand. That the code is offered only from *Session not found*, and selected so that typing replaces it, was the agent's proposal in the issue text, accepted when the issue was posted.
- TBD

## 7. Takeaway for Iteration 3

- Before placing a control, tell the agent which neighbouring parts other issues are about to change ([#74]).
- After merging `dev`, have the agent re-read the document's current numbering and decisions instead of reusing what it read before ([#74]).
- Write the task-ID scheme for unscheduled items (`Iter2-An`) into the docs, so the agent does not have to be told per issue ([#81]).
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
[#81]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/81
