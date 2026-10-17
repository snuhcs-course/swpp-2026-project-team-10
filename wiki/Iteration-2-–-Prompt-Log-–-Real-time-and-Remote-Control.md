Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

Prompts were given in Korean to Claude Code (Claude Fable 5.1) and are translated here.

## Pinch zoom with the guide-edit mode (#48, PR #74)

**Plan and build (10/13)**

- [Sungmin, 10-13] "Iteration 1 is over, so I'm starting Iteration 2. Clean up my local branches and pull the whole repository again."
- [Sungmin, 10-13] "Shouldn't there be a `main` branch too?"
- [Sungmin, 10-13] "Check which issues are assigned to me this iteration. They are at (issues link)."
- [Sungmin, 10-13] "What exactly does issue #48 implement?"
- [Sungmin, 10-13] "So one toggle button decides whether a pinch resizes the guide or zooms the whole camera, and the user switches between the two?"
- [Sungmin, 10-13] "Good. Write the implementation plan."
- [Sungmin, 10-13] "I agree that the shutter turns the mode off. Implement the toggle as an icon button."
- [Sungmin, 10-13] "Build the APK first."
- [Sungmin, 10-13] "I think we decided to drop the cutout and handle the outline's sharpness and settings separately. Is there an issue for that?"
- [Sungmin, 10-13] "The toggle sits above the Cutout / Outline row, which will go away later anyway. I'd rather have it on the camera screen itself, at the top or the bottom."
- [Sungmin, 10-13] "Good. Does the toggle work on the subject's phone too?"
- [Sungmin, 10-14] "Push this branch."

**Review and merge (10/14)**

- [Sungmin, 10-14] "Someone merged into `dev` and the Design Documentation conflicts. Check it."
- [Sungmin, 10-14] "`dev` moved again and the PR is blocked, so the Design Documentation has to be updated to match. Several comments came in as well: look at PR #74 and plan how to address them."
- [Sungmin, 10-14] "Go ahead."
- [Sungmin, 10-14] "Like PR #77, add my section to the Iteration 2 AI collaboration report and an English prompt log for this PR."

## Sessions across networks (#56, planning only)

Questions asked while #74 was in review; no code was changed.

- [Sungmin, 10-14] "`dev` now has the public server, so we no longer run the server on a laptop. We have to make streaming work across different networks, not only the same Wi-Fi. Look at the code on `dev` and think about whether that is possible and how. Don't implement anything and don't change code."
- [Sungmin, 10-14] "But doesn't this only work on the school Wi-Fi so far?"
- [Sungmin, 10-14] "If it only works from the school network, then it doesn't work across networks, right? Or is that a separate thing?"
- [Sungmin, 10-14] "(pasted a teammate's comment on #55 and #56: the invite link cannot open the app directly without HTTPS, so a static page with buttons; TURN cannot run on the course servers, so Cloudflare TURN) Is this right?"
- [Sungmin, 10-14] "So does streaming across networks work or not?"
- [Sungmin, 10-14] "So if the server is reachable only from inside the school, is it impossible?"

## Join with code keeps the previous room's code (#81)

**Issue and fix (10/15)**

- [Sungmin, 10-15] "I want to open a new issue for a bug: the code you type to join another room does not seem to reset. Write it in the format of #47 (pasted), so I can paste it in; I'll post it myself."
- [Sungmin, 10-15] "The title should be like \"[Iter2-A2] Move live preview room sessions to Redis\"."
- [Sungmin, 10-15] "`dev` has new commits: pull it, make a new branch, and fix this issue."
- [Sungmin, 10-15] "Push it."
- [Sungmin, 10-15] "Write the PR body."
- [Sungmin, 10-15] "Add the prompt log and the AI collaboration report entries too, and push."

## Remote brightness, flash, and guide moves (#57, PR #85)

**Plan and build (10/16)**

- [Sungmin, 10-16] "Now I want to implement issue #57. Write the implementation plan first; don't change any code."
- [Sungmin, 10-16] "OK, start implementing."
- [Sungmin, 10-16] "What should I test on the phones now?"
- [Sungmin, 10-16] "Remote moves and brightness work. But when the photographer changes the aspect ratio, the subject's screen does not change. Is that in this issue's scope? Just tell me whether it is or not."
- [Sungmin, 10-16] "It works. Push it, and write the PR body so I can paste it; I'll open the PR myself."
- [Sungmin, 10-16] "(pasted a screenshot of Subject view) Not fixing this now, but I want to file it as a bug: the ratio on the subject's phone looks off, the 3×3 grid leaves space above and below, and the subject does not follow the photographer's ratio change. Write the issue text so I can paste it."

**Review (10/16)**

- [Sungmin, 10-16] "(pasted a Codex comment: a final exposure request CameraX fails to apply gets no echo) Does this need a fix?"
- [Sungmin, 10-16] "OK, fix it, commit, and push."
- [Sungmin, 10-16] "(pasted a Codex comment: bump the protocol version for the new request types) Does this need a fix?"
- [Sungmin, 10-16] "OK, fix it, commit, and push."
- [Sungmin, 10-16] "(pasted two Codex comments: supersede queued settings before confirming the current value; bind guide moves to the guide being edited) Do these need fixes?"
- [Sungmin, 10-16] "OK, fix them, commit, and push."
- [Sungmin, 10-16] "Now update the AI collaboration report and the prompt log, commit, and push."
