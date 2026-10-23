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

## Guide controls (#52, [#83])

**Plan (10/16)**

- [Dongje, 10-16] "I will do Iter2-P11 too. Report to me first, as you did when starting P10." (The agent listed the tasks, the overlaps with Iter2-P12, P17, and P18, and four open points with a proposal for each.)
- [Dongje, 10-16] "Sungmin implements the mask-only transfer. For items 1–4, do as you proposed. Since UI matters here, prepare screenshots for me to review each time a task is done."

**Outline only, then hide, show, and replace (10/16)**

- [Dongje, 10-16] "Commit it and continue with the next task."
- [Dongje, 10-16] "How is it different from the Guide button below?" (about the new *Replace* button)
- [Dongje, 10-16] "How about removing the Replace button, and when a guide is on the camera, the Guide button asks whether to replace the pose?"
- [Dongje, 10-16] "Isn't the replace button still in the third photo?" (The review image reused a screenshot from before the button was removed; retaken, and the disabled opacity slider, which looked enabled, now dims.)
- [Dongje, 10-16] "Commit it and continue with the next task."

**Starting position (10/16)**

- [Dongje, 10-16] "Commit it and continue with the next task. Put the decisions above into the PR, and summarize what Sungmin has to do, as last time." (The size limits stay fixed for every start, so FR-3.3 is revised.)

**Reference thumbnail (10/16)**

- [Dongje, 10-16] "What did sending only the mask in P10 mean?" (It came from the P11 plan: the subject still receives the cutout with the reference's pixels, and the design sends only the smoothed mask.)
- [Dongje, 10-16] "So P10 and P11 change nothing on the subject's phone yet?" (Both do, through the existing sync: the ratio, the outline only, hiding, and the starting position.)
- [Dongje, 10-16] "Then what has to be done in #57?"
- [Dongje, 10-16] "I checked the screens. A tap enlarges the photo, and only the X button makes it small again. It would be better if the same tap made it small instead of an X. Also, the reference sits fixed at the top left: it would be good to drag it to the top right, bottom right, or bottom left, but do not let it sit anywhere else on the camera screen."
- [Dongje, 10-16] "When the photographer changes the ratio, the subject's phone changes too, and the guide's size follows the ratio there as well, right?"
- [Dongje, 10-16] "Commit it and continue with the next task."

**Review (10/18)**

- [Dongje, 10-18] "Update the branch to the latest `dev`, apply whatever that changes, and commit again. Fix what the review found if it needs fixing, and push the fix." (After #88 the CLAUDE.md history records only the root file's project instructions, so this PR's entry for `android/CLAUDE.md` was dropped. The one Codex finding was fixed: a layout change could bring the thumbnail back during the scene photo.)
- [Dongje, 10-18] "Reply to it and resolve it."
- [Dongje, 10-18] "Another review came in; check it the same way. Also review the PR once yourself." (Codex asked to anchor the thumbnail to the camera frame's corners instead of the preview's; 박동제 kept the preview's corners, like the chips and the guide's panel, and the reply says why. The agent's own review found a thumbnail that seemed to glide in from the screen's corner; recordings of the old and new builds showed it never does, so that change was dropped, and only two comments were fixed.)

## Subject view's aspect ratio (#86, [#89])

**Two phones (10/18)**

- [Dongje, 10-18] "Check it with two phones, the S22 and the emulator." (The course servers did not answer, so the server ran on the laptop and both phones joined it over the same Wi-Fi. The starting position, hiding, and a replacement reached the subject's phone.)
- [Dongje, 10-18] "The external servers can be used only on the school network; that has to wait until it opens."
- [Dongje, 10-18] "When the ratio changes, the subject's phone should change the guide's ratio and the screen's ratio too. Can you check?" (The grid and the guide followed, but the video was cropped to the view's shape, so the guide lay on another part of the scene; the agent found the cause and tried a fix on the S22 without committing it.)
- [Dongje, 10-18] "I posted an A4 issue; is this related?" (It was #86, Sungmin's issue for the same symptom.)
- [Dongje, 10-18] "No, assign me to issue A4 and open a new PR that fixes it."
- [Dongje, 10-18] "Stop the server, and fix the documents and the rest for the conflicts with Sungmin's PR, now merged. Then bring our code up to date, check it, and commit again if nothing is wrong." (#85 kept Design 1.8 and Testing 1.7, so this PR's rows became 1.9 and 1.8.)
- [Dongje, 10-18] "Shut down the emulator. And add one later task to the documents: the subject's phone shows the guide's photo the same way. Whether we do it in this iteration is not decided." (A *Still open* row in the Design Documentation; it needs the reference sent to the subject, which AD-6 and AD-15 now rule out.)
- [Dongje, 10-18] "Commit it to #89 as well and push it."

## Saved guides (#58, [#96])

**Plan (10/21–10/23)**

- [Dongje, 10-21] "Let's start issue #58. Is there anything I should consider or discuss with the team?" (The agent listed what the Design already fixes and the open points: the new contracts, the stored format against the mask-only transfer, saving on the subject's phone, the schedule, and eight screen choices with a recommendation each.)
- [Dongje, 10-23] "Some PRs were merged. Update the local branch, review #58 again, and tell me." (#94 gives `AppContainer` a constructor and tests the same sheet and Camera screen; #92's device-transfer rules would copy saved guides to a new phone.)
- [Dongje, 10-23] "Is it bad to keep the cutout as it is?"
- [Dongje, 10-23] "No, don't change the Design; for this task we keep it as it is. And why did #94 touch the AppContainer? Should I develop from the start expecting to merge after it?"
- [Dongje, 10-23] "No need to review it; let's go on with ours, from step 1. Follow the recommendations, but wouldn't it be better for a loaded guide to start where the person stands in the reference?" (IT-9's expected result changed with it.)

**Store, Save guide, and the list (10/23)**

- [Dongje, 10-23] "Yes, commit it and go on. We'll fix things after #94 is merged, but build it expecting #94 to be merged." (*Save guide* came with an in-memory store that #94's test container can take.)
- [Dongje, 10-23] "Yes, commit it and go on." (The saved guides on *Add a pose guide*, with emulator screenshots.)
- [Dongje, 10-23] "Yes, commit it and go on." (IT-9 after a force-stop on the emulator, then the documents.)
- [Dongje, 10-23] "Yes, commit it and open the PR now."

**Review (10/23)**

- [Dongje, 10-23] "I merged #94." (`dev` merged into the branch: `AppContainer`'s constructor and `create()`, `TestAppContainer`'s in-memory store, and saved-guide cases in #94's sheet and Camera screen tests.)
- [Dongje, 10-23] "Yes, commit it and push it."
- [Dongje, 10-23] "Check the reviews on the PR." (Jaewan's review had fifteen comments; each was checked against the code, and the agent recommended fixing twelve, part of one, and keeping two with their reasons.)
- [Dongje, 10-23] "Go ahead as recommended." (A load cancelled when the sheet closes, results kept while a screen is stopped, any failure while saving or placing a guide turned into a message, unusable or extra folders deleted at a start, the list ordered by a counter instead of the clock, icons set only when they change, 3:4 thumbnails, the outline traced outside the store's lock, and a line in Design 2.8 for `cutout.webp`; the store keeps its own encoding.)

## Daily standup rows (10/14)

- [Dongje, 10-14] "Write my rows of the daily standup log from Tuesday on. For Tuesday and Wednesday, split the task we merged into two parts. Over Thursday, Friday, and Saturday I will work on #52 (Iter2-P11) and #51 (Iter2-P10): split them into tasks per day, very briefly."
- [Dongje, 10-14] "Give it as plain text I can paste, as `10/13`, `Done:`, `Plan:`."
- [Dongje, 10-14] "Save it like this." (with the rows edited by hand)

[#72]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/72
[#80]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/80
[#83]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/83
[#89]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/89
[#96]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/96
