**Iteration:** 4 (2026-11-08 ~ 2026-11-20) · **Written by:** 조성민 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Opus 5.5), ChatGPT Codex (code review), and Gemini through OpenRouter for the situation tiles' pictures ([#228]).

## 1. Where AI Was Used

- **Feature development** (20 PRs, [#219]–[#256]): Claude Code planned each task and wrote the code, the tests, the wireframes, and the document updates. The author chose the scope and the look, and checked the result on phones. This covers the situations, landscape photos and the controls that turn, the session's photos and Pix's viewer, the tips, crash reports and usage data, and the fixes from UAT cycle 1.
- **Usability improvements** ([#222], [#223], [#225], [#232], [#250]): the problems of the user test and the heuristic evaluation (Testing Documentation 4.1, 4.2). The agent rendered the real screens at the Galaxy S22's size before changing them, and the team chose among drafts from those screenshots.
- **Testing** ([#229], [#253], [#255]): 397 app and 90 server tests were added (2,108 and 881 in all). The agent checked its coverage split against Iteration 3's numbers and made a flaky server test fail every time before fixing it. Claude Code subagents read the 50 tips outside our sample of the tips check (49 of 50 right); the threshold counts only our own answers.
- **Code review:** Codex left 43 inline findings and 19 reviews with findings on 21 code PRs, and 21 reviews with no major issue. The author had Claude Code check each finding, then fix it or reply.
- **Documents** ([#218], [#220], [#226], [#233], [#251]): the Iteration 4 plan and issues from the kickoff notes, the UAT results, and the standup logs.
- **CLAUDE.md:** the root file did not change. Each feature PR added its rules to `android/CLAUDE.md` (16 PRs) or `server/CLAUDE.md` (5 PRs) ([[history|Iteration-4-–-CLAUDE-md-History]]).
- **Not used:** product decisions, the phone runs of the integration scripts and the UAT, our answers in the tips check, and final review verdicts.

## 2. Prompt History

One page per topic: [[Documents|Iteration-4-–-Prompt-Log-–-Documents]] · [[Camera|Iteration-4-–-Prompt-Log-–-Camera-and-Guide]] · [[Real-time|Iteration-4-–-Prompt-Log-–-Real-time]] · [[Poses|Iteration-4-–-Prompt-Log-–-Pose-Generation]] · [[UI|Iteration-4-–-Prompt-Log-–-UI-and-Light-Mode]]

## 3. What AI Did Well

- **It measured before it proposed:** the shutter's timings on the S22, which found the focus lock of `MAXIMIZE_QUALITY` ([#230]); the scene photo step's sizes on the S22 ([#232]); 44 photo pairs read by eye, which showed the tip model copying the scene's values to the candidate ([#240]); and the 199 color values in the resources before the tokens ([#219]).
- **It showed choices as real screens:** each failure screen at the S22's size before any code ([#223]), two real layouts for the situation tiles ([#228]), and three drafts for the hidden thumbnail and for the tiles as buttons ([#241], [#250]).
- **Its tests could fail:** new tests failed on the old code first ([#222], [#245], [#249], [#250]). Where tests came after the code, it broke the code on purpose, 7 ways for [#239] and 13 for [#248].

## 4. Hallucinations / Errors

- **Claims it did not check:** that gallery apps swipe to older photos ([#239]); that `MainActivity` was missing from the APK, when the phone was in safe mode ([#222]); that a failed test run passed, as it read the shell's exit code ([#241]); and that `t213-run1` was Iteration 3's run ([#255]).
- **Causes guessed before measuring:** the photo's size for the slow shutter ([#230]), memory ([#239]), and a hung test ([#241]). Each cost a round of measuring.
- **Tests that could not see the bug:** [#221]'s tests ran the scheduler after every message, so two `StateFlow` races passed them; Codex found both.
- **Scope added or misread:** two issues the kickoff never decided ([#218]; fixed in [#220], with link labels the renumbering broke); "only when it differs" read so strictly that 20 of 100 tips hid a phone hold that differed ([#240]); and prompt versions compared where we asked for situations ([#227]).
- **Actions nobody asked for:** paid image calls on OpenRouter ([#227]) and a tap on the flash of the real phone ([#232]).

## 5. Prompt Revisions

- **The tip's sentences ([#213], [#240])**
  - Before: "Say each thing only when it differs, and do not talk about the background at all…"
  - After: "Let the distance and the phone's height and tilt always come… not only when they differ."
  - Why: the first rule hid 20 of 100 phone holds that differed, and 57 of 100 tips were one sentence.
- **The failure messages ([#203], [#223])**
  - Before: "…check that every message is two lines."
  - After: "The first line is the reason, right? Keep only the reason, drop lines like 'try again', and render again."
  - Why: the second line said what to do, which the screen's button already said.
- **Paid calls ([#209], [#227])**
  - After the agent made image calls on its own: "never run openrouter calls before i confirm".
  - Why: each image costs money, so a run waits for a yes.

## 6. Manual Fixes and Why

Fixes were made by prompting Claude Code again. These are decisions the team made against the agent's first proposal, and why:

- **No ID-photo situation ([#227]):** the issue planned one. Why: Pix keeps the place behind the person, so the result can never be a usable ID photo.
- **Back on *Pick a pose* drops the set ([#222]):** the agent built a set kept behind *Generate poses*, with *See the last poses*. Why: on the phone it was confusing, so Back now asks first.
- **Camera's bar on the scene photo step ([#232]):** the agent proposed a smaller header of the step's own; we asked for the main camera screen's bar. Why: with Camera's bar, the preview no longer slides when the step starts.
- **The saved photo, not the preview ([#230]):** we had the agent compare the photos saved before and after the change. Why: the saved photo is what users keep.
- **What a tip says ([#240]):** the distance and the phone in every tip, with no reason and nothing about the background or the place, decided in the team's review of 11/17–11/18 (Design 2.6.3).
- **Dropped features ([#212], [#214]):** light mode and the QR connection. Why: the team judged them unnecessary for Pix's flows.

## 7. Takeaway for Iteration 5

- Ask for a measurement before a cause: three guessed causes each cost a round this iteration.
- Say which actions need a yes first: paid calls, and taps on a real phone.
- Trim `android/CLAUDE.md`, which grew from 5,525 to 7,410 words this iteration, and every session reads it whole.

[#203]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/203
[#209]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/209
[#212]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/212
[#213]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/213
[#214]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/214
[#218]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/218
[#219]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/219
[#220]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/220
[#221]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/221
[#222]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/222
[#223]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/223
[#225]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/225
[#226]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/226
[#227]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/227
[#228]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/228
[#229]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/229
[#230]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/230
[#232]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/232
[#233]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/233
[#239]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/239
[#240]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/240
[#241]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/241
[#245]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/245
[#248]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/248
[#249]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/249
[#250]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/250
[#251]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/251
[#252]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/252
[#253]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/253
[#255]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/255
[#256]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/256
