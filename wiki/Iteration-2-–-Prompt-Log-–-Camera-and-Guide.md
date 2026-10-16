Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

Prompts were given in Korean to Claude Code (Claude Opus 5.5) and are translated here, shortened to the instruction; status checks are left out. The reviews of teammates' pull requests from the same session are on [[Code reviews|Iteration-2-–-Prompt-Log-–-Code-Reviews]].

## Smooth guide outlines (#49, [#72])

**Smoothing, and lines for arms and legs (10/13)**

- [Dongje, 10-13] "Start issue #49 (Iter2-P7). Implement it from the issue, the updated Design Documentation, and the requirements. Do the smooth outline first, then the arms and legs. The outline should be smoother than now, like this screenshot." (a screenshot of another camera app's outline guide)
- [Dongje, 10-13] "Does the smoothing run on the device?"
- [Dongje, 10-13] "Before you start the arms and legs, think through two or three approaches and tell me. Say concretely how you would use what we tried locally in Iteration 1, for example which attempt you reject and because of which problem."
- [Dongje, 10-13] "Go ahead as planned. Use the photos left from the Iteration 1 local branch if they still exist; otherwise find suitable ones. The arms and legs do not need to be detected perfectly: an outline good enough to refer to while taking a photo is enough. Ask me to review that part with screenshots."
- [Dongje, 10-13] "No need to consider black-and-white photos."
- [Dongje, 10-13] "In the test results, the arms do not fit the rest of the outline and look unnatural. Is there a way to make them more natural?"
- [Dongje, 10-13] "With lines alone you cannot tell they are arms. Can you make them more concrete? If this is the limit, I will not implement this part." (The agent compared three versions side by side; the lines were dropped.)

**Documents and PR (10/14)**

- [Dongje, 10-14] "Keep the 3 dp white line and update the documents first. They go in this task's PR."
- [Dongje, 10-14] "Record the arms and legs in front of the body as out of scope, not as work for the next iteration. Also, PR #71 is merged: update the branch locally, recheck the documents, and make the changes on top of them."
- [Dongje, 10-14] "Yes, no fill, and fix that line too. Draft the PR: attach the before-and-after images from the test, say briefly why the arm and leg lines were dropped, and link the matching part of the documents, or do it a better way. From this task on, after each task, record my prompts in my local draft, without filler, in English, with the date and time, by task, in one md file."
- [Dongje, 10-14] "Leave the wiki prompt log page as it is; I will edit it later. I checked it on the S22. The photos are fine if they are credited, but confirm we may use them, then open the PR."
- [Dongje, 10-14] "Why is the prompt log in the PR? I said to keep it local only." (The agent had added a wiki prompt log page to the PR; it was taken out.)
- [Dongje, 10-14] "Add this task to the CLAUDE.md history. From now on, do not finish a task in one commit: commit at each step that needs my check, and put the final document edits in a separate commit."

**Review (10/14)**

- [Dongje, 10-14] "Check the review on PR #72 and fix what needs fixing; skip it if nothing does." (Of the two Codex findings, the blur radius now comes from the person's height, not from the box around every region; the other was kept, with the documents made clearer.)
- [Dongje, 10-14] "Yes, commit and push it as it is."

## Camera controls (#51, [#80])

**Plan (10/15)**

- [Dongje, 10-15] "Let's start Iter2-P10. First list exactly what P10 has to build, and before implementing, review what to watch for so that it does not conflict with other work." (The agent listed every place that assumed the 3:4 frame and the overlaps with Iter2-P5, P11, P12, P16, and P17.)
- [Dongje, 10-15] "Go with your proposal for the control placement, and allow changing the ratio during a session. Hold the coordination with Sungmin (the Iter2-P5 overlap and the Iter2-P17 boundary) and ask me again once the other tasks are done; we are discussing it. The shutter sound did not play in our demo tests, so we keep it as it is: remove that task from the issue and from the documents. Before each commit, tell me what needs my review."

**Camera controller and Camera screen (10/15)**

- [Dongje, 10-15] "Commit it and continue with the Camera screen UI. Before that, pull what has been merged, and take the recent CLAUDE.md changes into account."
- [Dongje, 10-15] "Install it on my S22; I will check it."
- [Dongje, 10-15] "Checked. Commit it and continue."

**The subject's side (10/15)**

- [Dongje, 10-15] "What should we do about the overlap with Iter2-P5 and about the ratio of pose generation?"
- [Dongje, 10-15] "Would it be bad to match the pose to the ratio every time the ratio changes?"
- [Dongje, 10-15] "So on the photographer's camera, the pose follows every ratio change?"
- [Dongje, 10-15] "Once the subject's side is done, does the subject's phone match too? Is there a ratio the server has to decide?"
- [Dongje, 10-15] "So only the generated poses come in 3:4, and once connected everything follows the photographer's phone?"
- [Dongje, 10-15] "OK, go ahead with the task."

**Documents and PR (10/15)**

- [Dongje, 10-15] "Commit it and do the documents. Put what Sungmin needs to check in the PR later, in detail. The bitrate cap cannot be changed, right?"
- [Dongje, 10-15] "Remove that requirement from the 10/09 decision line too. What is IT-8?"
- [Dongje, 10-15] "Remove everything about it, completely. The open PR will update its documents after mine is merged, so use the versions on `dev` now. Apply it and commit."
- [Dongje, 10-15] "Leave the meeting notes as they are and draft the PR."
- [Dongje, 10-15] "Leave the markers as they are and open the PR."

**Review (10/15–10/16)**

- [Dongje, 10-15] "Check the reviews on PR #80 and fix what needs fixing." (Of the two Codex findings, the zoom readout was fixed as suggested; for the subject's guide the agent found a case the suggestion would not fix, a subject joining late, and fixed it another way.)
- [Dongje, 10-15] "Commit it and push it."
- [Dongje, 10-15] "Review the PR once more yourself and fix what needs fixing."
- [Dongje, 10-15] "Codex added a new review too; fix it if it must be fixed." (Both were fixed: the camera waits for the exposure after a rebind, and a lens without a flash reports none.)
- [Dongje, 10-15] "OK. What did your own review find?"
- [Dongje, 10-16] "More has been merged: check for conflicts, and check last that the document versions are right; fix whatever is off."
- [Dongje, 10-16] "#82 is merged too. Put the prompts and the AI collaboration notes I kept locally into the documents now, as the others do."

## Daily standup rows (10/14)

- [Dongje, 10-14] "Write my rows of the daily standup log from Tuesday on. For Tuesday and Wednesday, split the task we merged into two parts. Over Thursday, Friday, and Saturday I will work on #52 (Iter2-P11) and #51 (Iter2-P10): split them into tasks per day, very briefly."
- [Dongje, 10-14] "Give it as plain text I can paste, as `10/13`, `Done:`, `Plan:`."
- [Dongje, 10-14] "Save it like this." (with the rows edited by hand)

[#72]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/72
[#80]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/80
