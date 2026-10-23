**Iteration:** 2 (2026-10-11 ~ 2026-10-25) · **Written by:** 한준형 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Fable 5.1; Claude Opus 5.5 for the camera and guide tasks), ChatGPT Codex (reviewer on the feature PRs), GitHub Copilot code review.

## 1. Where AI Was Used

- **Documents** (the R&S, design, testing, and CLAUDE.md pages; [#62]–[#76], [#97]) and **every feature PR** ([#71]–[#96]): Claude Code wrote the text, the code, the tests, and the document updates; humans reviewed on phones and decided. Zoom stutter ([#93]) also used ChatGPT Codex.
- **Testing and integration** ([#94], [#98], [#99]): coverage tools, screen tests on every Fragment, JVM tests of the adapters, the integration record from the demo recordings, and the one fix it found. 756 app and 431 server tests at the close.
- **Code review:** Claude Code wrote 박동제's reviews; Codex reviewed seven feature PRs, each finding judged by the author.
- **Markers:** every AI-written source file starts with `AI-generated with <tool>, <date>, reviewed by <name>`. **Not used:** product decisions, phone tests, final review verdicts, and the Iteration 3 scope decision.

## 2. Prompt History

One page per task: [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] · [[Server|Iteration-2-–-Prompt-Log-–-Server-and-Deployment]] · [[Friends|Iteration-2-–-Prompt-Log-–-User-CRUD-and-Friends]] · [[Poses|Iteration-2-–-Prompt-Log-–-Pose-Generation]] · [[Camera|Iteration-2-–-Prompt-Log-–-Camera-and-Guide]] · [[Real-time|Iteration-2-–-Prompt-Log-–-Real-time-and-Remote-Control]] · [[Reviews|Iteration-2-–-Prompt-Log-–-Code-Reviews]] · [[Testing|Iteration-2-–-Prompt-Log-–-Testing]]

## 3. What AI Did Well

- **Fast from plan to device:** 15 minutes to 1.5 hours of agent time per feature PR against 1–4 planned hours, first test runs mostly passing ([#74], [#80], [#83], [#85]).
- **It checked before it wrote** (overlaps listed, smoothing prototyped in Python; [#72], [#80]) **and found on devices what tests could not:** the cropped subject video, `PreviewView` drawing outside the crop ([#80], [#89]).
- **Screen tests on a container of fakes** took the Views layer from 29 % to 91 % in two days and found a real bug (a hidden guide left the edit toggle drawn as on) ([#94]).
- **It read the recordings as a tester:** matched six demo videos to the scripts, wrote down what each does not show, and named the observation that became [#99] ([#98]).

## 4. Hallucinations / Errors

- **Platform behaviour asserted, not checked:** `PreviewView` assumed to clip; the subject's renderer said to "fit the stream" when it cropped every frame. Caught on devices, not by the unit tests; one cost a fix PR ([#80], [#89]).
- **Asynchronous and cross-device states missed:** READY before the exposure was restored, four remote-control edge cases, the edit mode surviving a hidden guide. Caught by the Codex reviewer, each fixed with a test in ten minutes ([#74], [#80], [#85]).
- **Stale context and weak tests:** a revision number from a replaced scheme, a screenshot from an older build, a PR body left at its first numbers; a password rule tested with a Pix ID the length rule refuses first ([#74], [#83], [#94]).
- **Decisions it should have left open:** a guide style and a *Replace* button the team removed ([#67], [#83]).

## 5. Prompt Revisions

- **Guide-edit toggle ([#74])**
  - Before: "Implement the toggle as an icon button." The agent put it in the guide's Cutout / Outline row.
  - After: "The toggle sits above the Cutout / Outline row, which will go away later anyway. I'd rather have it on the camera screen itself, at the top or the bottom."
  - Why: it named what another task (Iter2-P11) was about to remove, so the agent chose a place that lasts, beside the zoom readout.
- **Arm and leg lines ([#72])**
  - Before: "Do the smooth outline first, then the arms and legs." The first lines traced the upper arms and loose shorts.
  - After: "With lines alone you cannot tell they are arms. Can you make them more concrete? If this is the limit, I will not implement this part."
  - Why: an exit condition turned open-ended retries into a decision; the agent showed three versions side by side, and the lines were dropped at one look.

## 6. Manual Fixes and Why

No code was edited by hand. What the team decided over the agent's proposals, and why:

- **Deployment ([#71]):** the agent planned a new deploy user and an in-place container swap with a few seconds' gap; we kept one member's account and a blue-green switch. Why: we may not add users on the course servers, and even a short gap ends the rooms still waiting for the subject.
- **Outline only ([#72]):** we dropped the agent's arm and leg lines and kept the smoothed 3 dp outline with no fill. Why: the guide only has to be good enough to refer to while shooting, and lines that do not read as arms mislead more than they guide.
- **No *Replace* button ([#83]):** the agent built one, as R&S 6.2 listed it; with a guide on the camera, *Guide* now asks to replace it. Why: both buttons opened the same sheet, so the second one only added a choice.
- **Pose variations ([#90]):** a description goes out as itself and three variations, and *Try other poses* returns to *Generate poses*; we reverted the agent's follow-up that kept the old set. Why: the same text four times gave four nearly identical images, and keeping the old set made back move forward in the flow.
- **Saved guides ([#96]):** a used guide starts where the person stands in its reference, not where it was saved, as the agent recommended. Why: every guide, saved or new, then starts by one rule (FR-3.1); IT-9's expected result changed with it.
- **Coverage targets ([#94]):** 90 % for the app's managers, ViewModels, and Views and every server module, 30 % for the platform adapters, with no exclusion list. Why: the adapters' remaining lines call CameraX, libwebrtc, ML Kit, or Credential Manager, which need a device; reporting the layer instead of excluding it keeps that gap visible for Iterations 3 and 4.

## 7. Takeaway for Iteration 3

- Check camera and video code on a device at each step, and run the two-phone check before merging anything the subject sees.
- Keep the Codex reviewer on camera and session code; paste its findings as questions first.
- Ask for overlaps before coding; after merging `dev`, have the agent re-read the documents.
- Run the integration scripts with the `Timings` log on, and give the agent the test matrix before it writes the record.

[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#71]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/71
[#72]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/72
[#74]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/74
[#76]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/76
[#80]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/80
[#83]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/83
[#85]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/85
[#89]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/89
[#90]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/90
[#93]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/93
[#94]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/94
[#96]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/96
[#97]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/97
[#98]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/98
[#99]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/99
