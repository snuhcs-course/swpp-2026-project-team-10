# AI Collaboration Report – Iteration 1: Dongje's part (detailed)

**Iteration:** 1 (2026-09-27 ~ 2026-10-09) · **Written by:** 박동제 (PM) · **Tool:** Claude Code

**Tasks covered:** P1–P6 (kickoff, issues, requirements, design, shared contracts, app shell), P9 reference guide (#5), and the code reviews on #23 and #34. Hours below come from the team schedule.

---

## 1. 🤖 Where AI Was Used

**Documents and project management**
- **Kickoff minutes (P1):** Claude Code transcribed the 9/28 Zoom recording locally with an open-source speech-to-text model, then wrote the minutes and a PDF; they later became the [[Team Meetings|Team-Meetings]] page (wiki `8c7bd0e`).
- **Issues (P2):** drafted issues [#1]–[#12] from the schedule rows, each with the goal, tasks, *Done when*, references, dependencies, and the branch and PR names, under the "Iteration 1" milestone.
- **Requirements and design (P3, P5):** drafted [[Requirements and Specifications|Requirements-and-Specifications]] v0.1–v0.2 and [[Design Documentation|Design-Documentation]] v0.1–v0.2, including the architecture figure (wiki `8318fa4`, `1fc59c9`, `c8bfcde`, `b9cde5b`, `8dd9a1d`).
- **Daily standup log:** translated the teammates' Korean notes into English ([#29]).

**Code**
- **Shared contracts and app shell (P6):** [#13] shared contracts (29 files), [#15] PR template, [#16] app shell with navigation and every screen (54 files).
- **Reference guide (P9, [#5]):** [#28] (`414bb79`): `guide/SubjectSegmenter.kt`, `guide/MaskMath.kt`, `guide/OutlineExtractor.kt`, `guide/GuideSizes.kt`, `guide/MlKitReferenceGuideMaker.kt`, `ui/guide/ReferenceViewModel.kt`, `ui/guide/PhotoPicker.kt`, the *Reference confirm* and *No person found* screens, and the tests `MaskMathTest`, `GuideSizesTest`, `ReferenceViewModelTest`.
- **Arm and leg lines inside the outline:** a prototype on the local branch `feat/5-limb-lines` (`5581861`), parked for Iteration 2.

**Code reviews**
- Drafted and posted the reviews on [#23] (WebRTC streaming: a summary and 15 inline comments) and [#34] (integration: a summary and four inline comments).

**AI-generated code markers:** to be added (`TODO`: mark the files above in a follow-up PR and list the markers here).

**Not used**
- Testing on a real phone (Galaxy S22): every build was checked on the phone by me.
- Team meetings and the decisions made there.
- Document review: I read every draft before it went to the wiki and sent it back with corrections (section 6).

---

## 2. 💬 Prompt History

- [[Documents|AI-Collaboration-Iteration-1-Prompts-Documents]]
- [[Reference guide (#5)|AI-Collaboration-Iteration-1-Prompts-Reference-Guide]]
- [[Code reviews|AI-Collaboration-Iteration-1-Prompts-Code-Reviews]]

---

## 3. ✅ What AI Did Well

- **Reference guide in seven small commits ([#28]).** Each commit built and passed its unit tests before the next one. A photo becomes a guide in about 0.6 s on the Galaxy S22, against the 2 s target (NFR-3). Schedule P9: planned 4 h; actual 1 h of my time and 3 h of agent time.
- **Issues that teammates could start from (P2).** All 12 issues share one structure (goal, tasks, *Done when*, references, dependencies, branch and PR names), so each owner could open a branch without asking. Schedule P2: planned 1 h; actual 0.5 h of my time and 0.5 h of agent time.
- **App shell (P6, [#16]).** Navigation and every Iteration 1 screen in one PR of 54 files, built against the shared contracts. Schedule P6: planned 1.5 h; actual 1 h of my time and 2 h of agent time.
- **Reviews that found real problems.** On [#23], Claude found a race in which a camera frame could reach an already disposed WebRTC video source (a possible native crash), and four session-state problems such as the photographer being stuck in a dead *Waiting* state. On [#34], the author added the missing revision rows to both documents before merging.

---

## 4. ⚠️ Hallucinations / Errors

- **The model download was treated as finished (#5).** Claude's first `SubjectSegmenter` ran segmentation before Google Play services had finished downloading the ML Kit model. On the S22, the first two photos failed with `IllegalStateException` (logcat, 10/4 20:28) while the download was still running. The unit tests and the emulator could not show this. I caught it in the first test on the phone. Fix: `SubjectSegmenter.ensureModule()` now polls `areModulesAvailable` every 500 ms for up to 60 s (in [#28]). Cost: one extra commit and a re-test the next day.
- **It built more than I asked for (P6).** Asked for the shared contracts, Claude produced a full runnable skeleton of 103 files with screens, implementations, fakes, and a server, which took over my teammates' tasks. I caught it in review and cut it back to the contracts ([#13], 29 files). Cost: the extra work was discarded; it is kept only as a local reference.
- **Wrong kickoff date in the wiki.** The [[Team Meetings|Team-Meetings]] page gives the kickoff as 9/27, the planned date in the schedule, but the recording shows 9/28. Claude found it while matching task dates to PRs (10/6). Still to fix.
- **Dead ends on the arm and leg lines.** Tracing edges near the pose skeleton drew noisy lines on printed photos and missed crossed arms, because the pose landmarks sat beside the arm. Skin-color regions worked better but still missed covered or black-and-white limbs. Cost: about a day of prototyping, now parked on `feat/5-limb-lines`.
- **Smaller tool errors.** Diagrams in the design-document PDF did not render until each got its own render id and its `<<...>>` labels were escaped. `connectedAndroidTest` uninstalled the app from the phone and deleted the test output, so the probe had to be run again a different way.

---

## 5. 🔁 Prompt Revisions

- **Arm lines**
  - Before: "It only traces the outer silhouette now. Can it also trace the inner outline of the arms?"
  - After: "Only arms and legs. If a limb isn't visible, leave it out."
  - Why it worked: it limited the target to limbs and gave a rule for hidden ones, so Claude stopped trying to trace every inner edge and looked for each limb separately.
- **Review on #34**
  - Before: "Review PR #34 and leave comments." Claude started running the app on an emulator to check the new UI.
  - After: "Don't focus so much on the UI; review the code."
  - Why it worked: it set the scope, and the review came back as four code-level comments.

---

## 6. ✋ Manual Fixes and Why

I did not edit code or documents by hand. I reviewed every output, then sent it back with specific corrections. These are the corrections that mattered.

**Documents**
- **Contracts only, not a skeleton (9/30):** each owner should build their own module, so I had Claude keep only the Gradle project and the shared contracts ([#13]).
- **Contracts limited to Iteration 1 (9/30):** after the team meeting, the contracts kept only the 12 data channel messages Iteration 1 needs.
- **Later iterations at the plan level:** the design document describes what each later iteration adds (components, flows, key decisions), but messages, APIs, and database columns are added only when that iteration starts. The revision table lists additions and never drops content.
- **Only team decisions in the requirements:** items still waiting for a team decision were tagged NEW in the draft and left out of v0.1 until the 9/30 meeting decided them (v0.2).
- **Wireframes matched to the decisions (10/1):** removed the remote shutter and the control toggle, because only the photographer takes photos; added names in the badges, the code expiry, and *Reconnect* / *Leave*.
- **Room code in the Waiting state (10/1, wiki `8dd9a1d`).**

**Code**
- **Temporary fake until #6:** `InterimGuideRepository` let *Use this guide* work before #6 was merged. I had the PR stress that it must be reverted, and [#31] removed it.
- **Arm and leg lines parked (10/5):** after checking the results on the phone, I stopped prompting and had Claude go back to the finished version. The outline already met #5, and the lines were not accurate enough.
- **Real-device testing:** the model-download bug (section 4) only showed up on the phone.

The document tasks took more of my time than planned (P3: 2 h against 1 h; P5: 2 h against 1.5 h) because of these review rounds.

---

## 7. 📌 Takeaway for Iteration 2

- Run the first build on a real phone within the first hour, not after the feature is done.
- Tell Claude the boundary of my task (which modules are mine) before it starts.
- Say the scope of a review (code, UI, or documents) in the first request.

---

## Summary for the team report

> **Dongje (PM, reference guide).** Claude Code drafted the kickoff minutes, issues #1–#12, the requirements and design documents (v0.1–v0.2), the shared contracts (#13), the app shell (#16), and the reference guide (#5, #28), and posted the reviews on #23 and #34. It saved the most time on #5 (planned 4 h; 1 h of mine and 3 h of agent time) and #16. Its main errors: it ran segmentation before the ML Kit model had downloaded, which only a real phone showed, and it built a full skeleton when only the contracts were asked for. I fixed things by reviewing every output and sending it back with specific corrections, not by editing by hand. Next iteration: test on a phone first, and state my task's boundary and the review scope up front.

[#1]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/1
[#5]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/5
[#12]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/12
[#13]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/13
[#15]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/15
[#16]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/16
[#23]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/23
[#28]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/28
[#29]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/29
[#31]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/31
[#34]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/34
