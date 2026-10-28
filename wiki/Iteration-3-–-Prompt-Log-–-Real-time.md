Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

Prompts were given in Korean to Claude Code (Claude Opus 5.5) and are translated here, shortened to the instruction; status checks are left out.

## Sessions on different networks ([#117], PRs [#134], [#139])

**The AWS server and the plan (10/25)**

- [Sungmin, 10-25] "Now I have to build the connection between different networks, and the server has moved to AWS (its public IP, DNS name, and user). Set the app up to reach the AWS server, working on a new branch." (Port 80 answered `/health`, `/version` with the latest `dev`, and a WebSocket upgrade; the course servers answer only on campus. Debug builds defaulted to the AWS server, and release builds kept Server A.)
- [Sungmin, 10-25] "Can I work on the connection between networks now? Write a concrete plan first, and do not implement yet." (Three parts: the TURN relay, reconnection with `session.join` and an ICE restart, and the cellular notice with the measurements; the decisions that were open, such as Cloudflare or coturn and how a room-code phone proves it may rejoin.)
- [Sungmin, 10-25] "Is there anything I have to do?" / "Does this cost money? How do I make the TURN key?" (Cloudflare's documents: 1,000 GB a month free, then $0.05 per GB from the relay to the phone; the dashboard steps.)
- [Sungmin, 10-25] "Can't you put it in? I wrote it in `.turn_env`." (The two values went into `server/.env` without being printed, a request to Cloudflare's API confirmed the key, and `.turn_env` was kept out of git. SSH to the AWS server timed out, so its `.env` was left to the person who runs it.)
- [Sungmin, 10-25] "Can't I put it on AWS myself? I know the AWS secret key too." / "Does it have to be on the AWS server before I can work?" (Only for the tests on cellular data: the code, its tests, and the relay on one Wi-Fi need the key on the laptop alone.)
- [Sungmin, 10-25] "OK, start implementing." (The server's `turn` module and settings; in the app the `ice.pair` line and a relay-only debug option; tests and documents. A local server with the real key handed each phone its own credentials.)
- [Sungmin, 10-25] "I want to tell the server's operator the key. Is the name `PIX_TURN_API_TOKEN` enough?" (Both names are needed, with `PIX_TURN_KEY_ID`.)
- [Sungmin, 10-25] "Is the implementation done? Do I test it now?"

**Two PRs, as the issue asks (10/26)**

- [Sungmin, 10-26] "A new issue is open for this, #117. Pull `dev`, open new branches from it, move the code there, and write the wiki documents again to match." (The issue asks for the server first on `feat/117-turn-credentials`, then the app on `feat/117-turn-relay`. The work was kept as patches and split, and the documents were written again for the Iteration 3 pages, which now describe only the current design.)
- [Sungmin, 10-26] "So I open the server PR first?" / "Commit and push it first."

**The server PR ([#134], 10/26)**

- [Sungmin, 10-26] "The PR has a review: fix it. And `dev` changed the wiki, so my PR's wiki documents conflict: fix them, then commit and push." (One Codex finding, and it was right: while the subject waited up to 3 s for its credentials, the photographer could leave, and a photographer who opened a new room on the same socket got `peer.joined` there. `peer.joined` now waits for the credentials and goes out only while the subject still has its place and the photographer is still there (`SessionStore.seated`); two of the three new tests fail on the old code. `dev` was merged in rather than rebased, so nothing was force-pushed.)

**The app PR ([#139], 10/26 – 10/27)**

- [Sungmin, 10-26] "It is merged. Pull `dev` again, open the new branch, and put in the rest." (The app's part moved over cleanly. Then the mobile-data notice (FR-6.13) and the wrong-code message, taken from the Design's *Still open* proposal; `SessionManager.path` and `EndReason.RATE_LIMITED` as contract changes; R&S section 6. The "same Wi-Fi" texts were left for a decision, since room codes now work across networks.)
- [Sungmin, 10-27] "The phones are connected: install the APK." / "I connected the other phone: install it there too." (The S23 Ultra and the S25 Ultra, against the AWS server.)
- [Sungmin, 10-27] "Testing works well. I'll open the PR: commit and push." (#135 had come in meanwhile; its revision rows stayed and this branch's moved one number up.)
- [Sungmin, 10-27] "Check whether the review on the PR needs fixing." / "Commit and push." (One Codex finding, and it was right: the flag was stored before the notice was sent, across two DataStore calls, so a screen that stopped in between could miss the notice for good. The notice became a pending state that the Camera screen or Subject view shows and then confirms, and only that stores the flag.)
- [Sungmin, 10-27] "Yes, go ahead." (Merging `dev` again after #137: the R&S screen rows and the Testing tables took #137's text with this branch's additions.)
- [Sungmin, 10-27] "The prompt log has to go up as a Markdown file too, so write it."

## The mask-only guide transfer ([#113], PR [#145])

**Before the work (10/27)**

- [Joonhyung, 10-27] "Iteration 3 has started. Check the issues and the documents, and explain in detail what was decided and which parts are mine."
- [Joonhyung, 10-27] "Before implementing, write the protocol v3 messages in the format of Design 2.5.1. I will use it to agree with Jaewan on 10/28." (A proposal for the shared steps' messages and the guide in either direction, published as a page for the meeting.)
- [Joonhyung, 10-27] "Explain what AD-7 is, and in the end what exactly needs deciding."
- [Joonhyung, 10-27] "Explain the part labeled 'guide renderer (local copy)'. When the photographer loads a reference now, does the image go to the subject and the subject make the outline there?" (Yes, but what went was a WebP cutout with the person's pixels; the subject only traced its alpha, so the mask alone would do.)
- [Joonhyung, 10-27] "I am now taking P9. First explain P9 in detail."

**The photographer's direction (10/27)**

- [Joonhyung, 10-27] "Make `feat/113-mask-only-guide` from `dev`, and as the first PR implement `ReferenceGuide.mask`, the `mask-png` codec, `mask.png` for saved guides with the move from the existing `cutout.webp`, and protocol 3. The subject-to-photographer direction goes in the next PR." (The mask is written and read as an 8-bit grayscale PNG in plain Kotlin, so its bytes do not depend on the platform and the JVM tests reach it; a first comment claimed, unchecked, that `Bitmap.compress` puts `ALPHA_8` in a platform-dependent channel, and was reworded to what is known: there is no grayscale config. 850 unit tests passed.)

**Checks on two phones (10/27 – 10/28)**

- [Joonhyung, 10-27] "Before committing, tell me the procedure to check guide sync and the move of saved guides on two phones." (Seven steps with `adb`, `run-as`, and the `PixTimings`, `PixGuideSync`, `PixSession`, and `PixSavedGuides` lines, and a pass condition for each.)
- [Joonhyung, 10-27] "Streaming does not connect, maybe because I have no server running. I used to run a local server; can I use the AWS server I have open now?" / "I started the server at the EC2 address. How do I set `pix.serverUrl` to it?" (`local.properties` pointed debug builds at a laptop address; the EC2 name is the same server as the default, and `-Ppix.serverUrl` sets it for one build.)
- [Joonhyung, 10-28] The `adb` commands as written failed in zsh, which reads `<P>` as a redirection. (The connected serials were listed, and the one guide folder on the Note9 turned out to be saved by the new build, so it did not test the move.)
- [Joonhyung, 10-28] "I checked 4-3 with screenshots, and 4-4 too. It saves like this." / "I checked 4-5 myself: a guide loaded comes up on the subject too." (With the logs: a saved guide's 9,149-byte `mask.png` went in one chunk, a subject joining again got only the state, and a new guide sent its image once under its new id.)
- [Joonhyung, 10-28] The protocol 2 build failed in the check worktree for want of `local.properties`. (Its SDK path and the server address were added there, and the build was copied out.)
- [Joonhyung, 10-28] "If the versions differ, the session ends with *The session has ended* and does not connect. So let's skip step 3." (That is step 5's pass condition; the move from `cutout.webp` stays covered by `FileSavedGuideStoreTest` only, and the PR says so.)

**The PR (10/28)**

- [Joonhyung, 10-28] "Then put me in the AI marker, bring in `dev`, commit on the existing branch, write the PR, and push. Then write the prompt history needed for this feature, but do not commit it yet." (`dev` brought [#138] and [#144]; the Testing Documentation's revision rows and the Views row conflicted, and this branch's rows moved to Design 2.7 and Testing 2.9. 889 unit tests passed.)
- [Joonhyung, 10-28] "Commit the prompt log to the P9 branch and push it to PR #145."
- [Joonhyung, 10-28] "Check this review and fix and commit what it asks." / "Push the fix to PR #145." (One Codex finding, and it was right: a move that stopped after `mask.png` was in place, or whose delete failed, left `cutout.webp` with the reference's pixels for good, because a folder with a mask was never moved again. The delete is now tried at every read while the cutout is there; the new test fails on the old code.)
- [Joonhyung, 10-28] "PR #145 has merge conflicts with its base branch. Merge the base branch in, resolve the conflicts, and push." (`dev` had brought [#143], which sends a 512 px reference copy to the subject for its thumbnail. `AppContainer` built `GuideSyncer` with the removed `WebpGuideImageCodec`, the revision rows moved to Design 2.8 and Testing 2.10, AD-6 and 2.6.1 kept #143's sentences without the one about the WebP cutout, and `android/README.md`, which still described the cutout, was brought up to date. 905 unit tests passed.)
- [Joonhyung, 10-28] "Update PR #145's summary so it says only the guide image is mask-only, since #143 sends a 512 px reference copy for the thumbnail." (`gh pr edit` failed on GitHub's retired Projects (classic) API, so the description went through the REST API.)

**The subject's direction (10/28)**

- [Joonhyung, 10-28] "'Either phone can send a guide image, for the shared steps' seems to be needed now. Write the spec to implement it." (Scope, AD-7 kept, `anchor` and `source` in `guide.image.begin` within version 3, the photographer adopting the guide without sending it back, the subject drawing it at once, the edge cases, the files, the tests, and the documents, with one decision left: whether the PR includes the *Guide* button on Subject view.)
- [Joonhyung, 10-28] "Check whether I understood this right and explain it simply: before, the photographer kept `reference.webp` and sent the cutout, from which the subject traced the outline; now it keeps `reference.webp` and `mask.png` and sends the mask. Then is the remaining task S→P done as a mirror image?" (Mostly right, with three corrections: a guide is written to disk only on *Save guide*, the old cutout held the person's colored pixels, and since #143 a reference copy also goes to the subject. The sending is the same both ways; what differs is that the photographer owns the guide, so it adopts the subject's guide, needs its `anchor`, and must not send it back.)
- [Joonhyung, 10-28] "Is showing the reference copy on the subject already built?" (Yes, by #143: `MirrorReference`, the thumbnail on Subject view, and its tests.)
- [Joonhyung, 10-28] "Implement the S→P protocol with the spec we made, sending the `anchor` to the photographer too. First draw the flow again, then update the documents (Design, Testing) without a version bump, and include the *Guide* button on Subject view." (The flow was drawn first. Not in the spec, and decided while building it: the subject's answer cannot be told from the photographer's earlier messages, so they wait until a `guide.state` names the offered guide, at most 5 s; the subject's thumbnail uses its own reference photo; the guide screens go back with `backToShooting`, because a fixed `popBackStack(cameraFragment)` from Subject view would have ended the session. Without a version bump the S→P part went into the existing Design 2.8 and Testing 2.10 rows; R&S got its first row for this PR, and Figure 5 was redrawn with the button. 923 unit tests passed.)
- [Joonhyung, 10-28] "Is the photographer the one that first places the outline? Is that a design choice, and why the subject sends an `anchor` in S→P, or is it because P16 is not done yet? Does P16 also have to show the outline first on the subject?" (A design choice: the start position needs the photographer's camera ratio, so the photographer computes it, and the `anchor` is that computation's input, which the mask alone does not hold. The subject already draws its guide at once, and the photographer's state then settles it; P16 reuses `offer` for a candidate the subject picks.)

[#117]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/117
[#134]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/134
[#139]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/139
[#113]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/113
[#145]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/145
[#138]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/138
[#144]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/144
[#143]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/143
