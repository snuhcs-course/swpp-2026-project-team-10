**Iteration:** 3 (2026-10-25 ~ 2026-11-06) · **Written by:** 박재완 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Opus 5.5), ChatGPT Codex and GitHub Copilot (code review), and the Claude API as the AI module test's judge.

## 1. Where AI Was Used

- **Every feature and fix PR** (36 PRs, [#133]–[#189]): Claude Code planned each task and wrote the code, the tests, the wireframes, and the document updates. The author chose the scope and the look, and checked the result on phones.
- **Documents** ([#130], [#131], [#140], [#191], [#193]): the Iteration 3 plan and issues from the meeting notes, the wireframes moved into the repository and redrawn from the app's screens, and the Design and R&S rewritten as built.
- **Testing** ([#190]): 1,711 app and 791 server tests, and an AI module test whose Claude judge matched our labels on 99.0 % of verdicts.
- **Code review:** Codex left 94 findings on 18 PRs, and Copilot 3 on [#130]. The author had Claude Code check each finding, then fix it or reply.
- **CLAUDE.md:** these files are Claude Code's standing instructions. We added two rules this iteration: a PR that changes a screen must also update its wireframe, and the documents must use concise, simple technical English. [#147] reorganized the files to keep only the rules, grouped by topic ([[history|Iteration-3-–-CLAUDE-md-History]]).
- **Not used:** product decisions, phone tests, the labels that checked the judge, and final review verdicts.

## 2. Prompt History

One page per topic: [[Documents|Iteration-3-–-Prompt-Log-–-Documents]] · [[Camera|Iteration-3-–-Prompt-Log-–-Camera-and-Guide]] · [[Real-time|Iteration-3-–-Prompt-Log-–-Real-time]] · [[Poses|Iteration-3-–-Prompt-Log-–-Pose-Generation]] · [[Settings|Iteration-3-–-Prompt-Log-–-Settings]] · [[Server|Iteration-3-–-Prompt-Log-–-Server-and-Deployment]] · [[Friends|Iteration-3-–-Prompt-Log-–-User-CRUD-and-Friends]] · [[Logo|Iteration-3-–-Prompt-Log-–-Logo-and-Branding]]

## 3. What AI Did Well

Each case saved us roughly 30 minutes.

- **It tried ideas cheaply before building them:** seven tip prompts on candidates made once, at about a cent each ([#135]); the screenshot cut prototyped in Python on 48 real screenshots ([#176], [#178]); seven filter models and four ML Kit detectors compared on fixed test sets ([#166], [#169]).
- **It checked that its tests can fail:** it broke the code on purpose and confirmed that a test failed, in 11 places for [#159] and 17 for [#167]. This found tests that passed on broken code ([#153], [#167], [#176]).
- **It found bugs in the phones' logs that unit tests missed:** three reconnection bugs from a two-phone run ([#149]), an invitation link opened on the inviting phone just as it expired ([#154]), and a room left open after its subject left ([#189]).

## 4. Hallucinations / Errors

- **Facts it did not check:** it said the `Jev` model was not on OpenRouter. The author knew the model and gave its exact id, which cost a few prompts ([#166]).
- **Tests that could not fail:** boundary tests passed with the expiry check broken ([#153]), a timer test was always beaten by another timer ([#154]), and a mutation script read only stdout, so it reported every mutant as surviving ([#175]). Most were caught by breaking the code on purpose, and cost rewriting those tests and the script.
- **Stale context and platform limits:** a PR cut from an old `origin/dev` ([#147]), revision numbers already taken by a PR merged meanwhile ([#163], [#168]), and an API 33 call in an app with `minSdk` 29 ([#159]). The first two were caught when merging `dev` and cost a rebase and renumbered rows; the API call was fixed before the PR.

## 5. Prompt Revisions

- **Codex reviews of the Korean tips ([#157])**
  - Before: "Check the Codex review and fix it, as before." Each fix closed one case, and the next review found another.
  - After: "The reviews keep coming: while fixing them, structure the code so that edge cases are as unlikely as possible."
  - Why: it asked for the cause, not the case. The Korean check became one table over one word list, and tips change through one function.

## 6. Manual Fixes and Why

No code was edited by hand: every fix was made by prompting Claude Code again. Instead, here are some manual decisions that the team made, and why:

- **Tutorial ([#167], [#173]):** the agent proposed a walk through the live Camera; we chose slides, since real taps would start streaming and image generation. We then put the tips over the live screens, since a new user had never seen the screens on the slides.
- **Description filter ([#166]):** we removed the agent's checks that refused a change of clothing or place, and dropped a `refuse` field for Gemini. Why: the filter should stop only harmful or manipulative text, and a new field could shift Gemini's normal outputs.
- **No *View* on the saved-pose notice ([#175]):** the agent planned one. Why: leaving *Pick a pose* mid-set would leave the subject's *Pick a pose* open.
- **Korean wording ([#138]):** we replaced the draft's awkward words with those of ordinary Korean camera apps, such as 격자선, 배율, and 실시간. Why: users already know these words from their camera apps.
- **Wireframes in the repository ([#131]):** we moved them from a canvas to `wiki/design/`. Why: a screen and its wireframe change in one PR.

## 7. Takeaway for Iteration 4

- After two review rounds on the same code, ask for a structure that removes the cases, not another fix.
- Keep breaking the code on purpose in every PR, and first check that the mutation script fails on a known bad version.
- Keep `CLAUDE.md` short: `android/CLAUDE.md` grew from 2,718 words after [#147] to 5,525, and every session reads it whole.

[#130]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/130
[#131]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/131
[#133]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/133
[#135]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/135
[#138]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/138
[#140]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/140
[#147]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/147
[#149]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/149
[#153]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/153
[#154]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/154
[#157]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/157
[#159]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/159
[#163]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/163
[#166]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/166
[#167]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/167
[#168]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/168
[#169]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/169
[#173]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/173
[#175]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/175
[#176]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/176
[#178]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/178
[#189]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/189
[#190]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/190
[#191]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/191
[#193]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/193
