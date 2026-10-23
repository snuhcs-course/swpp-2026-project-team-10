**Iteration:** 2 (2026-10-11 ~ 2026-10-25) · **Written by:** 한준형 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Fable 5.1; Claude Opus 5.5 for the camera and guide tasks and 박동제's reviews), ChatGPT Codex (reviewer on the feature PRs, parts of the zoom plan in [#93]), GitHub Copilot code review.

**How this report was prepared.** Each PR's *AI usage* section (tools, what AI did well, what we fixed, prompts) was written when the PR was opened; this page is the iteration-level reading of those sections and the prompt logs, made by 한준형 at the close. The per-PR evidence is on [[Evidence by PR|Iteration-2-–-AI-Collaboration-Report-–-Evidence]].

## 1. Where AI Was Used

- **Documents:** the AI rules, the Iteration 2 R&S and design, the Testing Documentation, the CLAUDE.md rules, and the close-of-iteration sync ([#62], [#66], [#67], [#70], [#76], [#97]): Claude Code.
- **Server and Android:** every feature PR ([#71]–[#96]): Claude Code wrote the code, the tests, and the document updates; humans reviewed on phones and decided. Zoom stutter ([#93]) also used ChatGPT Codex.
- **Testing and integration:** coverage tools, screen tests on every Fragment, JVM tests of the adapters, the integration record from the demo recordings, and the one fix it found ([#94], [#98], [#99]): Claude Code. 756 app and 431 server tests at the close.
- **Code review:** Claude Code wrote 박동제's reviews; the Codex reviewer commented on seven feature PRs, each finding judged by the author (section 4).
- **Markers:** every AI-written source file starts with `AI-generated with <tool>, <date>, reviewed by <name>`; no merged PR edited code by hand. **Not used:** product decisions, phone tests, final review verdicts, and the decision to move Iter2-P15/P16 and the Could items to Iteration 3.

## 2. Prompt History

One page per task, prompts as sent, no responses: [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] · [[Server and deployment|Iteration-2-–-Prompt-Log-–-Server-and-Deployment]] · [[User CRUD and Friends|Iteration-2-–-Prompt-Log-–-User-CRUD-and-Friends]] · [[Pose Generation|Iteration-2-–-Prompt-Log-–-Pose-Generation]] · [[Camera and guide|Iteration-2-–-Prompt-Log-–-Camera-and-Guide]] · [[Real-time and remote control|Iteration-2-–-Prompt-Log-–-Real-time-and-Remote-Control]] · [[Code reviews|Iteration-2-–-Prompt-Log-–-Code-Reviews]] · [[Testing|Iteration-2-–-Prompt-Log-–-Testing]]

## 3. What AI Did Well

- **Fast from plan to device:** 15 minutes to 1.5 hours of agent time per feature PR against 1–4 planned hours, with first test runs mostly passing ([#74], [#80], [#83], [#85]).
- **It checked before it wrote:** listed overlaps with other tasks, prototyped the outline smoothing in Python first, and reused the previous PR's groundwork ([#72], [#80], [#85]).
- **It found what tests could not:** two-phone and emulator runs showed the cropped subject video and `PreviewView` drawing outside the crop ([#80], [#89]).
- **Screen tests on a container of fakes** raised the Views layer from 29 % to 91 % in two days and found a real bug (a hidden guide left the edit toggle drawn as on); the adapters went from 23 % to 43 % on the JVM ([#94]).
- **It read the recordings as a tester:** matched six demo videos to the integration scripts, wrote down what each does not show, and named the one observation that became the fix of [#99] ([#98]).

## 4. Hallucinations / Errors

- **Platform behaviour asserted, not checked:** `PreviewView` assumed to clip; the subject's renderer said to "fit the stream" when it cropped every frame. Caught on devices, not by 45 unit tests; one cost a fix PR ([#80], [#89]).
- **Asynchronous and cross-device states missed:** READY before the exposure was restored, four remote-control edge cases, the edit mode surviving a hidden guide. All caught by the Codex reviewer, each fixed with a test in ten minutes ([#74], [#80], [#85]).
- **Stale context:** a revision number from a replaced scheme, a screenshot from an older build, a PR body left at the first commit's numbers through three pushes ([#74], [#83], [#94]).
- **Tests that could not fail for the right reason:** a password rule tested with a Pix ID the length rule refuses first; a pinch below the minimum scaling span that never began ([#94]).
- **Decisions it should have left open:** a guide style and a *Replace* button the team removed ([#67], [#83]).

## 5. Prompt Revisions

- **Name what is about to change** ([#74]): "that row goes away later; put the toggle on the camera screen" moved the control to a place that lasted.
- **Give experiments an exit condition** ([#72]): an acceptance criterion and "if lines cannot read as arms, I will not implement it" turned three tries into a decision.
- **Ask before "fix it"** ([#85]): "does this need a fix?" first; all four findings were real and no wrong fix went in.
- **Give the facts it cannot see** ([#98]): the phones, the tester, and why cellular pairs could not run came from the team; the record was right only then.

## 6. Manual Fixes and Why

No merged PR edited code by hand: when the agent was wrong, we said what was wrong and it fixed it with a test. Humans decided instead: the guide-edit toggle and its defaults, outline only, the controls' places, no *Replace* button, the coverage targets (90/90/90/30, server 90), what the recordings prove, and the Iteration 3 scope. Two document edits were redone by hand on 10/13 (a version-sequence sentence, edits applied on the wrong branch).

## 7. Takeaway for Iteration 3

- Check camera and video code on a device with screenshots at each step, and run the two-phone check before merging anything the subject sees.
- Keep the Codex reviewer on camera and session code; paste its findings as questions before "fix it".
- Before coding, ask for overlaps with other tasks; after merging `dev`, have the agent re-read the documents' current state.
- Run the integration scripts with the `Timings` log on, not only a recording, and give the agent the test matrix before it writes the record.

[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#66]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/66
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#70]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/70
[#71]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/71
[#72]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/72
[#74]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/74
[#76]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/76
[#80]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/80
[#83]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/83
[#85]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/85
[#89]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/89
[#93]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/93
[#94]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/94
[#96]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/96
[#97]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/97
[#98]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/98
[#99]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/99
