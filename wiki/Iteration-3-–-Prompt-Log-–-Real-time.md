Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

## Sessions on different networks ([#134], [#139])

**The AWS server and the plan (10/25)**

- [Sungmin, 10-25] "Now I have to build the connection between different networks, and the server has moved to AWS (its public IP, DNS name, and user). Set the app up to reach the AWS server, working on a new branch."
- [Sungmin, 10-25] "Can I work on the connection between networks now? Write a concrete plan first, and do not implement yet."
- [Sungmin, 10-25] "Is there anything I have to do?" / "Does this cost money? How do I make the TURN key?"
- [Sungmin, 10-25] "Can't you put it in? I wrote it in `.turn_env`."
- [Sungmin, 10-25] "Can't I put it on AWS myself? I know the AWS secret key too." / "Does it have to be on the AWS server before I can work?"
- [Sungmin, 10-25] "OK, start implementing."
- [Sungmin, 10-25] "I want to tell the server's operator the key. Is the name `PIX_TURN_API_TOKEN` enough?"
- [Sungmin, 10-25] "Is the implementation done? Do I test it now?"

**Two PRs, as the issue asks (10/26)**

- [Sungmin, 10-26] "A new issue is open for this, #117. Pull `dev`, open new branches from it, move the code there, and write the wiki documents again to match."
- [Sungmin, 10-26] "So I open the server PR first?" / "Commit and push it first."

**The server PR ([#134], 10/26)**

- [Sungmin, 10-26] "The PR has a review: fix it. And `dev` changed the wiki, so my PR's wiki documents conflict: fix them, then commit and push."

**The app PR ([#139], 10/26 – 10/27)**

- [Sungmin, 10-26] "It is merged. Pull `dev` again, open the new branch, and put in the rest."
- [Sungmin, 10-27] "The phones are connected: install the APK." / "I connected the other phone: install it there too."
- [Sungmin, 10-27] "Testing works well. I'll open the PR: commit and push."
- [Sungmin, 10-27] "Check whether the review on the PR needs fixing." / "Commit and push."
- [Sungmin, 10-27] "Yes, go ahead."
- [Sungmin, 10-27] "The prompt log has to go up as a Markdown file too, so write it."

## The mask-only guide transfer ([#145])

**Before the work (10/27)**

- [Joonhyung, 10-27] "Iteration 3 has started. Check the issues and the documents, and explain in detail what was decided and which parts are mine."
- [Joonhyung, 10-27] "Before implementing, write the protocol v3 messages in the format of Design 2.5.1. I will use it to agree with Jaewan on 10/28."
- [Joonhyung, 10-27] "Explain what AD-7 is, and in the end what exactly needs deciding."
- [Joonhyung, 10-27] "Explain the part labeled 'guide renderer (local copy)'. When the photographer loads a reference now, does the image go to the subject and the subject make the outline there?"
- [Joonhyung, 10-27] "I am now taking P9. First explain P9 in detail."

**The photographer's direction (10/27)**

- [Joonhyung, 10-27] "Make `feat/113-mask-only-guide` from `dev`, and as the first PR implement `ReferenceGuide.mask`, the `mask-png` codec, `mask.png` for saved guides with the move from the existing `cutout.webp`, and protocol 3. The subject-to-photographer direction goes in the next PR."

**Checks on two phones (10/27 – 10/28)**

- [Joonhyung, 10-27] "Before committing, tell me the procedure to check guide sync and the move of saved guides on two phones."
- [Joonhyung, 10-27] "Streaming does not connect, maybe because I have no server running. I used to run a local server; can I use the AWS server I have open now?" / "I started the server at the EC2 address. How do I set `pix.serverUrl` to it?"
- [Joonhyung, 10-28] "I checked 4-3 with screenshots, and 4-4 too. It saves like this." / "I checked 4-5 myself: a guide loaded comes up on the subject too."
- [Joonhyung, 10-28] "If the versions differ, the session ends with *The session has ended* and does not connect. So let's skip step 3."

**The PR (10/28)**

- [Joonhyung, 10-28] "Then put me in the AI marker, bring in `dev`, commit on the existing branch, write the PR, and push. Then write the prompt history needed for this feature, but do not commit it yet."
- [Joonhyung, 10-28] "Commit the prompt log to the P9 branch and push it to PR #145."
- [Joonhyung, 10-28] "Check this review and fix and commit what it asks." / "Push the fix to PR #145."
- [Joonhyung, 10-28] "PR #145 has merge conflicts with its base branch. Merge the base branch in, resolve the conflicts, and push."
- [Joonhyung, 10-28] "Update PR #145's summary so it says only the guide image is mask-only, since #143 sends a 512 px reference copy for the thumbnail."

**The subject's direction (10/28)**

- [Joonhyung, 10-28] "'Either phone can send a guide image, for the shared steps' seems to be needed now. Write the spec to implement it."
- [Joonhyung, 10-28] "Check whether I understood this right and explain it simply: before, the photographer kept `reference.webp` and sent the cutout, from which the subject traced the outline; now it keeps `reference.webp` and `mask.png` and sends the mask. Then is the remaining task S→P done as a mirror image?"
- [Joonhyung, 10-28] "Is showing the reference copy on the subject already built?"
- [Joonhyung, 10-28] "Implement the S→P protocol with the spec we made, sending the `anchor` to the photographer too. First draw the flow again, then update the documents (Design, Testing) without a version bump, and include the *Guide* button on Subject view."
- [Joonhyung, 10-28] "Is the photographer the one that first places the outline? Is that a design choice, and why the subject sends an `anchor` in S→P, or is it because P16 is not done yet? Does P16 also have to show the outline first on the subject?"
- [Joonhyung, 10-28] "Run the S→P protocol on the emulator, take screenshots, and report."
- [Joonhyung, 10-28] "The emulator is not running now. First commit what is implemented and update the PR."
- [Joonhyung, 10-28] "Update the prompt log too, without committing."
- [Joonhyung, 10-28] "Redraw Flow 5 with the changed UI, the zoom row's toggle included, commit and push it, and resolve the conflicts."
- [Joonhyung, 10-28] "Commit the prompt log and push it to PR #145."

## Reconnection after a short drop ([#149])

**The plan and the build (10/27)**

- [Sungmin, 10-27] "Now I'll take issue #118. Pull `dev` first, then write a concrete, detailed plan of how to build it. Do not implement yet."
- [Sungmin, 10-27] "Can you implement it now?"

**`dev` again (10/28)**

- [Sungmin, 10-28] "Pull `dev` again and resolve the conflicts."

**Two phones (10/28 – 10/29)**

- [Sungmin, 10-28] "How do I test this?" / "Why a local server? Can't I test with the AWS server?"
- [Sungmin, 10-28] "I connected a phone: install it."
- [Sungmin, 10-28] "OK, done."
- [Sungmin, 10-28] "OK, go ahead with the fixes."
- [Sungmin, 10-28] "Install it again."
- [Sungmin, 10-28] "Why doesn't the outline show on the emulator? It's painfully slow."
- [Sungmin, 10-28] "Testing is done."
- [Sungmin, 10-28] "OK, go ahead with the fix."
- [Sungmin, 10-29] "Done."

**The PR (10/29)**

- [Sungmin, 10-29] "I think this can be committed and pushed as it is."
- [Sungmin, 10-29] "Do the reviews on PR #149 need fixing? If so, fix them, commit, and push."
- [Sungmin, 10-29] "Write the AI prompt log for this too, then commit and push it."

## Shared steps on both phones ([#151])

**Before the work (10/26)**

- [Joonhyung, 10-26] "Make `feat/120-shared-steps`. I want to build the *Guide* and *Generate poses here* buttons on Subject view first, so draw the flow for it first."

**The spec (10/28)**

- [Joonhyung, 10-28] "Check issue #120 and explain in detail what has to be built on top of P9."
- [Joonhyung, 10-28] "Leave *Generate poses* out of the subject's phone, but first write a spec that puts in P16 the photographer's candidates on the subject's *Pick a pose* too, with the side that picks first deciding."

**The build (10/28 – 10/29)**

- [Joonhyung, 10-28] "Make `feat/120-shared-steps` from `dev` with this spec, and first draw the wireframes of Flow 5 (Subject view, the subject's *Pick a pose*) and the photographer's *Pick a pose*. Then fix the documents (R&S, Design, Testing) and implement it." / "Continue from where it stopped."
- [Joonhyung, 10-29] "First bring in what may conflict with `dev`, and fix it."
- [Joonhyung, 10-29] "Fix the stale PICKING race in PoseSyncer's sender here, with a test."

**The PR ([#151], 10/29)**

- [Joonhyung, 10-29] "Commit this to feat/120-shared-steps, push it, and open a PR to dev titled 'feat: share the guide and pose steps on both phones' with Closes #120."
- [Joonhyung, 10-29] "Start two emulators, S and P, for this feature, and report with screenshots of the right moments."

**After Jaewan's review (10/29)**

- [Joonhyung, 10-29] "Check this review. In particular, after a photo is chosen and the photographer's phone makes the outline with ML Kit, the subject's phone has no *Use this guide* for that outline. Build it so the subject can tap *Use this guide* too."
- [Joonhyung, 10-29] "Commit this to feat/120-shared-steps, push it, and update the PR #151 description to match."
- [Joonhyung, 10-29] "*Generate poses* does not let P and S both write the pose description yet (review item 3): change it so they write it together. Also resolve the merge conflicts." / "While P or S is typing, the other cannot type and sees something like 'The other person is typing.' A button the first one taps, or simply tapping the field first, either works."
- [Joonhyung, 10-29] "Commit the shared Generate poses to feat/120-shared-steps, push it, and update the PR #151 description to match."

**Codex and CI through Auto-fix (10/29 – 10/30)**

- [Joonhyung, 10-29] "Turn on Auto-fix for PR #151, so CI failures on it get fixed here."

**Two emulators (10/29 – 10/30)**

- [Joonhyung, 10-29] "The Android emulator runs very slowly on Apple Silicon. Find a way to fix it, apply it, and test this PR with it. Take screenshots of the right moments during the test, like the photos in the PR's review comment, and return them with explanations."
- [Joonhyung, 10-30] "Write the PR again with these photos included."

## Invitations with push notifications ([#153], [#154])

**The plan (10/29)**

- [Sungmin, 10-29] "Pull `dev`: it has been updated."
- [Sungmin, 10-29] "I'm starting #119: write a detailed plan first. Do I need other screens for it? And there are hardly any screens for friends, such as a friend's profile: is that worth adding, in another issue? The plan first."
- [Sungmin, 10-29] "Why is Firebase needed here?"
- [Sungmin, 10-29] "What else do I have to do besides signing up for Firebase? In detail."
- [Sungmin, 10-29] "I put `google-services.json` in `android/app` and the key file in the repository's folder. Check them."
- [Sungmin, 10-29] "I made it again: `pix-swpp-5b…json`."

**The server (10/29)**

- [Sungmin, 10-29] "Can't I test it yet? Which key do I install on AWS?"
- [Sungmin, 10-29] "Commit and push it."
- [Sungmin, 10-29] "Can't I install the key on AWS myself? See whether I can."
- [Sungmin, 10-29] "So I ask for `fcm-sender.json` to be installed on the AWS server?" / "Before my PR is merged, or after?"
- [Sungmin, 10-29] "It's installed on AWS." / "Merged."

**The app (10/29)**

- [Sungmin, 10-29] "Make a branch from the latest `dev` and start."
- [Sungmin, 10-29] "Is the link really working? Check it. The rest seems to work."
- [Sungmin, 10-29] "Let's make it 60 s; 30 s is too short. It works. Make it 60 s, then commit and push."
- [Sungmin, 10-29] "Go on with the rest."
- [Sungmin, 10-29] "The PR is #154. Fix what its review asks for, then commit and push."
- [Sungmin, 10-30] "There is a new review on #154. Fix it if it needs fixing, then commit and push."
- [Sungmin, 10-30] "Something new was merged and #154 has a lot of conflicts. Resolve them, then commit and push."
- [Sungmin, 10-30] "Did you fix this review?" / "Fix it, then commit and push."
- [Sungmin, 10-30] "The new one is in `dev`. Resolve the conflicts, then commit and push. Don't fix the test: the new `dev` changed it."
- [Sungmin, 10-30] "A new review came in on #154. Fix it."
- [Sungmin, 10-30] "There's a new review. Fix it, then commit and push."

## Session photos on both phones ([#159])

**The spec (10/30)**

- [Joonhyung, 10-30] "Now I am going to build P17. Check the tasks and review the requirements for writing the spec."
- [Joonhyung, 10-30] "Write the P17 spec with the recommended choices, before creating the branch or changing any code."

**The build (10/30 – 10/31)**

- [Joonhyung, 10-30] "Yes, start."

**The PR ([#159], 10/31)**

- [Joonhyung, 10-31] "First put everything together, the screenshots of the tests you ran included, commit it, and open the PR." / "Continue from where it stopped."

## Choosing who takes the photo ([#189])

**The idea and the sketch (11/05)**

- [Sungmin, 11-05] "Before the QR issue, I want to add a feature in a new issue. On Shoot together, with a room code or with a friend, it is confusing who shoots and who is shot. The user should choose a role first and then do what that role does; now, for example, the person in the photo has to enter the room code, but nobody knows who should enter it. How should we solve it? Can you sketch a UI that only I can see?"
- [Sungmin, 11-05] "Option A is good, but it looks like an app an AI made: too many needless explanations, and the first screen is especially bad."
- [Sungmin, 11-05] "Now it is too plain. And a top bar that says 'the photographer' and 'the person in the photo' is strange. I didn't ask to drop every explanation; the needed ones should stay, since the point is to reduce the confusion."
- [Sungmin, 11-05] "It keeps saying 'friend', but it could be a parent. Isn't 'friend' too narrow?"
- [Sungmin, 11-05] "This is much better. Write a new issue with this image, and attach the image to the issue body."

**The plan (11/05)**

- [Sungmin, 11-05] "Write a concrete plan for #188 first. Don't start implementing yet."

**The build (11/05)**

- [Sungmin, 11-05] "Let's go with all your recommendations. Start implementing."

**Received invitations (11/05)**

- [Sungmin, 11-05] "Is there a place where I can see the invitations I received, like an invitation list? I might dismiss a notification by mistake."
- [Sungmin, 11-05] "No, I'll build it in this issue. Can you sketch the UI, only for me?"
- [Sungmin, 11-05] "C3 is much better. Add this to issue 188." / "So make it the C3 UI even when only one arrives."
- [Sungmin, 11-05] "First make an implementation plan." / "I'll implement it as planned, with your recommendations."
- [Sungmin, 11-05] "This is strange. I'm not sharing another camera, yet accepting the invitation says to end it first."
- [Sungmin, 11-05] "Fix it for now."

[#134]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/134
[#139]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/139
[#145]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/145
[#149]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/149
[#151]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/151
[#153]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/153
[#154]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/154
[#159]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/159
[#189]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/189
