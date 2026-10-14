Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

One Claude Code session (Claude Fable 5.1) for the Iteration 2 documents, 10/11–10/14. Prompts are given in the order they were sent, translated from Korean where needed and shortened to the instruction; follow-ups that only confirmed a result are left out. The session was told, through `CLAUDE.md`, to commit or push only when asked, so each "commit" below is an explicit instruction.

## AI rules in CLAUDE.md and the PR template (#44, [#62])

- [Joonhyung, 10-11] "Add the AI-generated marker rule to `CLAUDE.md` and an AI usage section to the PR template, based on the files as they are now."
- [Joonhyung, 10-11] "Does this cover everything in issue #44?" (The agent compared the diff with the issue, found two missing items, and added them: one marker line per file and source files only; the 'what we had to fix' and 'prompts' lines.)
- [Joonhyung, 10-11] "Write the PR description for this, using the new template."

## Requirements and Specifications v1.0 ([#66])

- [Joonhyung, 10-12] "We are going to update the requirements for Iteration 2. Based on the kickoff meeting notes and the current R&S, explain in detail which parts to write or change." (The agent read the live wiki page of the 2026-10-09 meeting, the R&S v0.8, and the Iteration 2 issues, and listed the changes section by section.)
- [Joonhyung, 10-12] "Now write the Iteration 2 R&S from the current page. The guide shows only the outline, never the reference image's background; the exact style is decided in P23. Every 'if possible' item is Could."
- [Joonhyung, 10-12] "Write this PR's description with the new template, and explain the changes in detail."

## Design Documentation v1.0 ([#67])

- [Joonhyung, 10-12] "Now we write the Design Documentation from the current R&S and the team meeting. What has to be written?"
- [Joonhyung, 10-12] "Explain the five 'Decided' items in detail, and what has to be settled for each."
- [Joonhyung, 10-12] "I did not understand item 3 (the guide style). Explain it in detail and simply."
- [Joonhyung, 10-12] "Put all of that under Still open or TODO; the owners will update the design document, version by version, as they do their tasks. Write the design document from what is decided now. The switch is a guide-edit mode toggle, the slider is discarded, and the subject receives only the smoothed mask."
- [Joonhyung, 10-12] "Discarding the slider is right, but we are also considering showing it under a detail-settings button. And the switch should be on the subject's phone too."
- [Joonhyung, 10-13] "Now write the PR description."

## Issue numbering

- [Joonhyung, 10-13] "Renumber the open and closed issues: from P21 on as `[Iter2-P1]`, and the Iteration 1 milestone's issues as `[Iter1-P1]`, `[Iter1-A1]`. Leave #14 as it is." (The agent mapped P21–P44 to Iter2-P1–P24 by subtracting 20, so the schedule's numbers still match, and renamed 31 titles.)
- [Joonhyung, 10-13] "Change the numbering inside the issue bodies too."
- [Joonhyung, 10-13] "Change the local documents too, each on its own branch (`wiki/iteration-2-design`, `wiki/iteration-2-requirements`); the R&S first. The branch is not accessible right now, so go to `wiki/iteration-2-requirements` and push a new commit, then commit to `wiki/iteration-2-design`. And from Iteration 2 the versions start again at 1.0." (The branch was held by a worktree the agent had created; it committed, pushed, removed the worktree, and retitled the two PRs to v1.0.)
- [Joonhyung, 10-13] "The numbers go 1.0, 1.1, 1.2, …, 1.10, 1.11, right?" (The agent had written "continue as 1.10, 1.11"; it corrected both pages, found that [#66] and [#67] had been merged meanwhile, and opened [#68] from `dev` for the fix.)
- [Joonhyung, 10-13] "I merged the PR. Undo the pushes to each branch." (The two feature branches were reset to the commits their merged PRs used, with `--force-with-lease`.)

## Testing Documentation v1.0 (#69, [#70])

- [Joonhyung, 10-13] "We are adding a `[Iter2-A1]` issue to create a Testing Documentation page on the wiki, following the course's Testing Documentation Guidelines (PDF attached). Make a format from the guideline, starting at version 1.0. First write the issue title and body."
- [Joonhyung, 10-13] "Write the `wiki/Testing-Documentation.md` format."
- [Joonhyung, 10-14] "Based on these two lecture decks (Week 6 Testing Part I, Week 7 Testing Part II), reflect the important parts of testing in the Testing Documentation." (The agent extracted the PDFs to text and added the verification-vs-validation map, the test quality rules, the test design techniques with Pix's partitions, mutation analysis, and the CI and flaky-test policies.)
- [Joonhyung, 10-14] "Write the PR description for the format."

## CLAUDE.md: follow and update the Testing Documentation ([#76])

- [Joonhyung, 10-14] "Now that the test document format exists, reflect in `CLAUDE.md` that any task that develops a feature or tests must write how the tests were run and what the results were in the Testing Documentation, with a version update. And make it always refer to that document, or whenever there are tests."
- [Joonhyung, 10-14] "Write the PR." (The branch was already pushed, so the agent opened [#76] with the template.)
- [Joonhyung, 10-14] "Write the changes since the 10/12 revised version of the CLAUDE.md history as a new Iteration 2 page in the wiki, and commit it to that PR." (The diff blocks were generated from `git show` for each PR.)
- [Joonhyung, 10-14] "Create the AI Collaboration Report – Iteration 2 page as a format for now, and write the prompt log for the documents from our conversation so far, and commit."

[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#66]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/66
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#68]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/68
[#70]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/70
[#76]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/76
