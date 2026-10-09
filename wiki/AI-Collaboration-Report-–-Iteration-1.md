**Iteration:** 1 (2026-09-27 ~ 2026-10-09) · **Written by:** 박동제 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Opus 5.5), ChatGPT Codex (GPT-6 Astra), GitHub Copilot code review

## 1. Where AI Was Used

- **Documents:** requirements and design drafts, issues #1–#12, meeting minutes, and the updates after each feature ([#25], [#33]): Claude Code.
- **Server:** signaling ([#17]), pose generation API and model comparison script ([#22]), wiki workflow ([#24]): Claude Code.
- **Android:** camera, zoom, and guide gestures ([#21], [#26], [#31]): ChatGPT Codex. Reference guide ([#28]), streaming ([#23]), remote zoom ([#27]), pose generation ([#30]), guide sync ([#32]), integration and UI ([#34]): Claude Code.
- **Code review:** the Codex reviewer checked 10 PRs ([#13], [#17], [#21], [#22], [#27], [#28], [#30], [#31], [#32], [#34]) and Copilot 2 ([#13], [#16]), with 15 inline findings. Claude helped us judge each finding before we fixed or declined it, and drafted the human reviews on [#23] and [#34].
- **CLAUDE.md:** `/CLAUDE.md`, `/android/CLAUDE.md`, and `/server/CLAUDE.md` give Claude Code our architecture, commands, conventions, pitfalls, and rules ("commit or push only when a human asks"). Feature PRs update them with the code ([[history|Iteration-1-–-CLAUDE-md-History]]).
- **AI-generated code markers:** the first line of every source file names the tool, the date, and the reviewer: [all markers](https://github.com/search?q=repo%3Asnuhcs-course%2Fswpp-2026-project-team-10+%22AI-generated+with%22&type=code) · [the PR that added them](https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/41/files)
- **Not used:** tests on real phones, document review, product decisions, and the design of the specific workflows and requirements in our documents; AI only wrote the text.

## 2. Prompt History

Full prompt logs, one page per task: [[Documents|Iteration-1-–-Prompt-Log-–-Documents]] · [[Reference guide|Iteration-1-–-Prompt-Log-–-Reference-Guide]] · [[Code reviews|Iteration-1-–-Prompt-Log-–-Code-Reviews]] · [[Server and pose generation|Iteration-1-–-Prompt-Log-–-Server-and-Pose-Generation]] · [[Real-time|Iteration-1-–-Prompt-Log-–-Real-time]] · [[Camera and guide overlay|Iteration-1-–-Prompt-Log-–-Camera-and-Guide-Overlay]]

## 3. What AI Did Well

- **Time:** programming tasks P7–P16 and A1–A2 were planned at 39 h and took 18.5 h of human time plus 24.5 h of agent time (team schedule).
- **Small, tested steps:** the reference guide came in seven commits, each passing its tests ([#28]); it takes 0.6 s per photo on the S22 (target 2 s).
- **Streaming in one evening ([#23]):** signaling, data channels, five screens, and 34 tests; the first two-phone test worked unchanged.
- **Model choice from data ([#22]):** four models compared on 128 generated images by latency and cost. Claude also caught our error: we wrote "speed" as the reason, but the table showed our model as the slowest and cheapest; we meant cost.
- **Review triage ([#27]):** of five Codex findings, Claude confirmed two bugs, hardened two cases, and declined one with a written reason.
- **Tests without a device ([#31]):** the overlay tests moved to Robolectric, so all 240 tests run without a phone.
- **Measurement ([#34]):** aligned logs showed remote zoom at 10–30 ms (target 0.5 s).

## 4. Hallucinations / Errors

- **Model assumed ready ([#28]):** the first photos failed on the S22 while the ML Kit model was still downloading; tests and the emulator could not show it. One extra commit.
- **Hotspot assumed reachable ([#27]):** Android hid the hotspot host's interface from WebRTC. Caught in a two-phone test; cost about 15 minutes.
- **Bugs found by the Codex review, not Claude's tests ([#22], [#30]):** an upload checked after parsing, error text in logs, a doubled 30 s limit. Five fixes with tests.
- **One pinch for two features ([#26] → [#31]):** resizing the guide kept zooming the camera, so zoom became a slider.
- **Smaller:** a full skeleton when only contracts were asked for ([#13]) and a theme font that hid bold weights ([#34]).

## 5. Prompt Revisions

- **Scene photo step**
  - Before: "Now implement the client-side pose generation flow (issue #7)." The photo was sent on the tap.
  - After: "After we click generate poses here, there should be some step where we show the camera screen to the user and make them press the shutter to shoot the scene image."
  - Why: it described the flow as the user sees it.
- **Subject's zoom**
  - Before: "That seems wrong, doesn't it?" Claude defended the zoom chips.
  - After: "Make it work the same way as the main camera's zoom instead of buttons."
  - Why: it named an implementation to copy.

## 6. Manual Fixes and Why

- **Few hand edits:** we reviewed every diff and sent corrections back as prompts; by hand we only removed an inline dependency block ([#22]) and an unneeded README section ([#24]).
- **Our decisions:** a cheaper image model than the one recommended, the scene photo inside *Camera*, a zoom slider, contracts instead of a full skeleton, and cross-network sessions deferred to Iteration 2.
- **Ownership:** we kept Claude off another member's `TODO` until its owner agreed ([#32]).
- **Real devices:** the model download, hotspot, gesture, and notice bugs appeared only on phones.

## 7. Takeaway for Iteration 2

- Run the first build on real phones, and once on a hotspot, within the first hour.
- Start prompts with the issue text, the screen flow, and the files we own.
- Tell Claude to ask before departing from the design document or R&S.
- Add AI-generated markers in the same PR as the code.

[#13]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/13
[#16]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/16
[#17]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/17
[#21]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/21
[#22]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/22
[#23]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/23
[#24]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/24
[#25]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/25
[#26]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/26
[#27]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/27
[#28]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/28
[#30]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/30
[#31]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/31
[#32]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/32
[#33]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/33
[#34]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/34
