**Iteration:** 2 (2026-10-11 ~ 2026-10-25) · **Written by:** 한준형 · **Contributors:** 박동제, 박재완, 조성민, 한준형

**Tools used:** Claude Code (Claude Fable 5.1; Claude Opus 5.5 for the camera and guide tasks), ChatGPT Codex (reviewer on the feature PRs), GitHub Copilot code review.

**How this report was prepared.** From each PR's *AI usage* section and the prompt logs; the per-PR evidence is on [[Evidence by PR|Iteration-2-–-AI-Collaboration-Report-–-Evidence]].

## 1. Where AI Was Used

- **Documents** (the R&S, design, testing, and CLAUDE.md pages; [#62]–[#76], [#97]) and **every feature PR** ([#71]–[#96]): Claude Code wrote the text, the code, the tests, and the document updates; humans reviewed on phones and decided. Zoom stutter ([#93]) also used ChatGPT Codex.
- **Testing and integration** ([#94], [#98], [#99]): coverage tools, screen tests on every Fragment, JVM tests of the adapters, the integration record from the demo recordings, and the one fix it found. 756 app and 431 server tests at the close.
- **Code review:** Claude Code wrote 박동제's reviews; Codex reviewed seven feature PRs, each finding judged by the author.
- **Markers:** every AI-written source file starts with `AI-generated with <tool>, <date>, reviewed by <name>`. **Not used:** product decisions, phone tests, final review verdicts, the Iteration 3 scope decision.

## 2. Prompt History

Prompts as sent, no responses, one page per task: [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] · [[Server|Iteration-2-–-Prompt-Log-–-Server-and-Deployment]] · [[Friends|Iteration-2-–-Prompt-Log-–-User-CRUD-and-Friends]] · [[Poses|Iteration-2-–-Prompt-Log-–-Pose-Generation]] · [[Camera|Iteration-2-–-Prompt-Log-–-Camera-and-Guide]] · [[Real-time|Iteration-2-–-Prompt-Log-–-Real-time-and-Remote-Control]] · [[Reviews|Iteration-2-–-Prompt-Log-–-Code-Reviews]] · [[Testing|Iteration-2-–-Prompt-Log-–-Testing]]

## 3. What AI Did Well

- **Fast from plan to device:** 15 minutes to 1.5 hours of agent time per feature PR against 1–4 planned hours ([#74], [#80], [#83], [#85]).
- **It checked before it wrote** (overlaps listed, smoothing prototyped in Python; [#72], [#80]) **and found on devices what tests could not:** the cropped subject video, `PreviewView` drawing outside the crop ([#80], [#89]).
- **Screen tests on a container of fakes** took the Views layer from 29 % to 91 % and found a real bug (a hidden guide left the edit toggle drawn as on) ([#94]).
- **It read the recordings as a tester:** matched six demo videos to the scripts, noted what each does not show, and named the observation that became [#99] ([#98]).

## 4. Hallucinations / Errors

- **Platform behaviour asserted, not checked:** `PreviewView` assumed to clip; the subject's renderer said to "fit the stream" when it cropped every frame. Caught on devices; one cost a fix PR ([#80], [#89]).
- **Asynchronous and cross-device states missed:** READY before the exposure was restored, four remote-control edge cases, the edit mode surviving a hidden guide. Caught by the Codex reviewer, each fixed with a test ([#74], [#80], [#85]).
- **Stale context and weak tests:** a revision number from a replaced scheme, an old screenshot, a PR body left at its first numbers; a password rule tested with a Pix ID the length rule refuses first ([#74], [#83], [#94]).
- **Decisions it should have left open:** a guide style and a *Replace* button the team removed ([#67], [#83]).

## 5. Prompt Revisions

- **Name what is about to change** ([#74]): "that row goes away later; put the toggle on the camera screen."
- **Give experiments an exit condition** ([#72]): "if lines cannot read as arms, I will not implement it."
- **Ask before "fix it"** ([#85]): "does this need a fix?" first; no wrong fix went in.
- **Give the facts it cannot see** ([#98]): the phones, the tester, and why cellular pairs could not run came from the team.

## 6. Manual Fixes and Why

No merged PR edited code or a document by hand, so the `manually fixed by <name>, <date>` note was never needed: when the agent was wrong, we said so and it fixed it with a test or a new commit, which is faster than editing and leaves a commit a reader can find. The two 10/13 slips went that way: the version sequence written as "continue as 1.10, 1.11" on the R&S and Design pages, and the R&S renumbering applied to `dev` instead of [#66]'s branch (a worktree held it); the PM caught both in review and the agent redid them in [#66] (`932a915`) and [#68] (`7ee4b9c`). Humans decided instead: the guide-edit toggle and its defaults, outline only, the controls' places, no *Replace* button, the coverage targets, what the recordings prove, the Iteration 3 scope; 한준형 wrote [#98]'s body himself.

## 7. Takeaway for Iteration 3

- Check camera and video code on a device at each step; run the two-phone check before merging anything the subject sees.
- Keep the Codex reviewer on camera and session code; paste its findings as questions first.
- Ask for overlaps before coding; after merging `dev`, have the agent re-read the documents.
- Run the integration scripts with the `Timings` log on, and give the agent the test matrix before it writes the record.

[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#66]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/66
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#68]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/68
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
