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
- [Joonhyung, 10-28] "Run the S→P protocol on the emulator, take screenshots, and report." (No phone was connected, so two read-only instances of the one emulator image were started and a saved guide was written into the subject's files, for want of a photo of a person. Both booted, but adb kept them offline even with the emulators and adb started outside the sandbox, so the run was stopped.)
- [Joonhyung, 10-28] "The emulator is not running now. First commit what is implemented and update the PR." (Committed with `Closes #113`; the PR took the issue's title, both directions, and the device checks still to do.)
- [Joonhyung, 10-28] "Update the prompt log too, without committing."
- [Joonhyung, 10-28] "Redraw Flow 5 with the changed UI, the zoom row's toggle included, commit and push it, and resolve the conflicts." (Subject view in Figure 5 gained the guide-edit mode toggle beside the zoom readout, rendered in both languages. `dev` had brought [#146], which took Design 2.8 and Testing 2.10, so this branch's rows moved to 2.9 and 2.11, and the technique rows kept both sides' cases. 935 unit tests passed.)
- [Joonhyung, 10-28] "Commit the prompt log and push it to PR #145."

## Reconnection after a short drop ([#118], PR [#149])

**The plan and the build (10/27)**

- [Sungmin, 10-27] "Now I'll take issue #118. Pull `dev` first, then write a concrete, detailed plan of how to build it. Do not implement yet." (The server holds a dead socket for up to about 40 s, until Uvicorn's pings go unanswered, so a phone coming back over a new socket would find its place taken. The plan: a rejoin token per place, which takes the place back even from the old socket; `Reconnecting` with a 10 s budget on both phones; an ICE restart from the photographer on the same peer connection, so the channels and the video track stay; six decisions, each with a recommendation.)
- [Sungmin, 10-27] "Can you implement it now?" (With the recommended decisions. On the server, the token in `room.created` and `session.joined`, stored only as a SHA-256, `session.join`, and the `REJOIN` Lua script; in the app, `SessionState.Reconnecting`, `SessionEntry.Rejoin`, local candidates held until their description is out, and the socket opened again on every drop. 13 new server tests and 12 manager tests; a mutation check showed that the candidate and `peer.left` tests catch their regressions. 860 unit tests passed.)

**`dev` again (10/28)**

- [Sungmin, 10-28] "Pull `dev` again and resolve the conflicts." (Seven PRs had come in, [#138] to [#148], and 15 files conflicted. [#144] had already replaced the "same Wi-Fi" text on *Connection lost*, so this branch's text and its redrawn Flow 5 were dropped; `reconnecting` got its Korean string; IT-13 was taken by the Korean walk-through, so the Wi-Fi-off script became IT-16; the revision rows moved to Design 2.10, R&S 2.11, and Testing 2.13. One gap had no conflict marker: the reconnect path built `PeerInfo` without [#143]'s `haveReferenceId`, so the photographer would have sent the reference photo again after every drop. 961 unit tests passed.)

**Two phones (10/28 – 10/29)**

- [Sungmin, 10-28] "How do I test this?" / "Why a local server? Can't I test with the AWS server?" (AWS runs `dev`, which gives no tokens, so the app would take the path for older servers: the subject would not open its socket again and would miss the restart offer. Running this branch there would need Jaewan, since the server is shared and serves the user test of 10/31.)
- [Sungmin, 10-28] "I connected a phone: install it." (The S25 Ultra, and the emulator as the second phone, against a server on the laptop, with an APK built for its address.)
- [Sungmin, 10-28] "OK, done." (The phone had been the photographer, in airplane mode both times. A short drop came back in 3.8 s. The long one ended on time, but the photographer's socket waited 30 s to come back, because the tries while reconnecting had counted toward its backoff, and a subject's *Reconnect* made while the photographer was off the server would never get an offer.)
- [Sungmin, 10-28] "OK, go ahead with the fixes." (The tries while reconnecting no longer count, and a subject waiting for an offer sends `session.join` again when `peer.rejoined` says the photographer is back. The second test at first passed without its fix, because the repeated `session.join` equals the *Reconnect*'s first one; it now compares only the messages sent after `peer.rejoined`.)
- [Sungmin, 10-28] "Install it again." (Now on the S23 Ultra; both devices' logs went to files, so they outlast a closed emulator.)
- [Sungmin, 10-28] "Why doesn't the outline show on the emulator? It's painfully slow." (Play services never download ML Kit's segmentation module on the emulator, so the guide maker waits 60 s and fails. The guide came from the phone instead, through *Guide* on Subject view ([#145]).)
- [Sungmin, 10-28] "Testing is done." (The system logs showed airplane mode on the emulator, the photographer, not on the phone. The backoff fix held: the photographer's socket tried again 1, 2, and 4 s after giving up and was back on the third try, and the *Reconnect* connected at once. Earlier, a tap on the phone had been followed by its realtime channel closing alone, with no drop on either side, and the new rule that a closed channel ends the session turned that into *Connection lost*; `dev` had only logged it.)
- [Sungmin, 10-28] "OK, go ahead with the fix." (Only the reliable channel closing ends the session; after the realtime channel closes alone, its steps and pings go on the reliable one, and reconnecting ends with the reliable channel alone. One mutant ran for 24 minutes: its failing assertion left the session connected, and the ping loop kept `runTest` draining virtual time.)
- [Sungmin, 10-29] "Done." (IT-4 as written, with airplane mode on the subject's phone for 6 s: `session.reconnected` 4102 ms and 3917 ms, and the video and the guide back on the same connection. With 20 s both phones gave up at 10 s; a *Reconnect* tapped 12 ms before Wi-Fi was back failed and kept the screen, and the next one connected in 0.7 s.)

**The PR (10/29)**

- [Sungmin, 10-29] "I think this can be committed and pushed as it is." (Before the commit, the AI markers of the files this PR changed were dated and named for it.)
- [Sungmin, 10-29] "Do the reviews on PR #149 need fixing? If so, fix them, commit, and push." (Three Codex findings, all right. Two restarts asked for at once, when both phones take their places back, could both be answered while the photographer applied only the first answer, leaving the phones with different ICE credentials: a restart now waits for the pending answer, at most 2 s, because an offer that went to the subject's old socket is never answered, and plain serializing would have held back the restart that saved IT-4. A *Reconnect* that failed before the server answered dropped its token, so the next one joined the code, which a server still holding the old socket refuses as `FULL`: the token is now kept until the server refuses it, which also explains the first failed tap. The background limit skipped `Reconnecting`: it now counts, and its deadline holds when the link comes back. Each fix has a test that fails without it; 967 unit tests passed.)
- [Sungmin, 10-29] "Write the AI prompt log for this too, then commit and push it."

## Invitations with push notifications ([#119], PRs [#153] and [#154])

**The plan (10/29)**

- [Sungmin, 10-29] "Pull `dev`: it has been updated." ([#149] and [#150] had come in, so the branch for #119 could start from `dev` with reconnection in it.)
- [Sungmin, 10-29] "I'm starting #119: write a detailed plan first. Do I need other screens for it? And there are hardly any screens for friends, such as a friend's profile: is that worth adding, in another issue? The plan first." (Two PRs, the server first. The documented flow had two gaps: accepting created the session, but the photographer's phone got no token for its place, and `invitation.accepted` would be lost while the photographer's socket reconnected. The plan opened the session when the invitation is sent instead, so both phones take their places with `session.join` as after a drop. Four decisions with a recommendation each: when the session opens, the link's format, the Firebase setup, and whether 30 s is enough for the link. The screens were already in R&S 6.5 and 6.6: *Invite sent*, *Invite not accepted*, and *Invitation*, whose "no longer available" became a state of its own screen. A friend's profile adds little to shooting together and would bring photo storage; a row sheet, initials, and a request badge were offered for after the user test.)
- [Sungmin, 10-29] "Why is Firebase needed here?" (Android keeps no socket open for a closed app; Google Play services' one connection per phone, reached through FCM, is what wakes it, and other push services use it too. Only FCM is used, which is free.)
- [Sungmin, 10-29] "What else do I have to do besides signing up for Firebase? In detail." (A Firebase project with Analytics off, the Android app `com.lastpenguin.pix` for `google-services.json`, and a service account with only the FCM role for the server's key, kept outside the repository; Jaewan to install it on the AWS server.)
- [Sungmin, 10-29] "I put `google-services.json` in `android/app` and the key file in the repository's folder. Check them." (The key belonged to another Google Cloud project than the app's, `pix-swpp-fall-2026` against `pix-swpp`, so the server could not have sent to the app's tokens; it also sat in the public repository's folder, which nothing ignored. The check read only the key's type, project, and account, never the private key.)
- [Sungmin, 10-29] "I made it again: `pix-swpp-5b…json`." (The right project. On the questions that followed: the key moved out of the repository to `~/pix-secrets/`, the session opens when the invitation is sent, and the link is a page served at `/i/<id>`.)

**The server (10/29)**

- (The server PR: the `invitations` table, the `INVITE`, `ACCEPT_INVITATION`, and `END_INVITATION` scripts, `REJOIN` sending `peer.joined` to a photographer that comes after the subject, the FCM sender over HTTP v1 with `google-auth`'s signer instead of `firebase-admin`, and the link page; FCM keeps the data key `from` for itself, so the name went as `fromName`. A mutation check found the 30 s boundary's tests passing with the comparison broken, since the session store also refuses an expired room; decline and cancel at the boundary, which need no room, now catch it. A script that restored the mutated files with `git checkout` was blocked by the session's guard before it ran, because it would also have thrown away the uncommitted work; the mutants ran again from copies in the scratchpad. The real key was checked against Google with `validate_only`, sending nothing. 597 server tests passed.)
- [Sungmin, 10-29] "Can't I test it yet? Which key do I install on AWS?" (Not until the app registers its token and shows the notification, which is the second PR; the key to install is the service account's, not `google-services.json`.)
- [Sungmin, 10-29] "Commit and push it." (PR [#153].)
- [Sungmin, 10-29] "Can't I install the key on AWS myself? See whether I can." (This Mac had no SSH key, host entry, or AWS credentials for that server, although its SSH port answered; Jaewan could add the public key, or install the key themselves.)
- [Sungmin, 10-29] "So I ask for `fcm-sender.json` to be installed on the AWS server?" / "Before my PR is merged, or after?" (Yes, handed over in person rather than through a messenger; before the merge it needs no restart, since the deploy starts the new copy with it.)
- [Sungmin, 10-29] "It's installed on AWS." / "Merged." (The deploy of `00e9396` served the link page and the endpoints.)

**The app (10/29)**

- [Sungmin, 10-29] "Make a branch from the latest `dev` and start." (The `google-services` plugin worked with AGP 9.3. `InvitationManager`, `PushTokenRegistrar`, the `push/` package, `SessionEntry.Invite` and `SessionEntry.Invitation`, `Inviting`, the three screens, the deep links, and the access token on every session socket, so a friend in a session is `BUSY`. An invitation's photographer could not go back to `Waiting` without a code, which would have crashed, so its session ends there. Both phones got the APK against the AWS server.)
- [Sungmin, 10-29] "Is the link really working? Check it. The rest seems to work." (On the emulator's Chrome the page's *Open in Pix* opened *Invitation*. The phone's log showed the link opened on the inviting phone itself, where it cannot be accepted, just as the 30 s ran out.)
- [Sungmin, 10-29] "Let's make it 60 s; 30 s is too short. It works. Make it 60 s, then commit and push." (60 s on the server, the photographer's own wait at 80 s, and "within a minute"; one test that could not fail, since another timer ended the session first, now checks that a connected session outlasts the invitation's wait. 1007 unit tests passed.)
- [Sungmin, 10-29] "Go on with the rest." (Tests of the three screens and their Korean, the token in the socket's upgrade, Flows 3, 3b, 4, and 5 redrawn, and the documents. Two Korean strings followed the wireframe's earlier translation.)
- [Sungmin, 10-29] "The PR is [#154]. Fix what its review asks for, then commit and push." (Codex found three: *Cancel invite* did nothing once the friend had accepted and the phones were connecting, yet went to *Friends*, so it now ends the session, while closing the sheet then keeps it, as on *Room code*; *Invitation* navigated on transitions, which a screen stopped with Pix in the background, or recreated, missed, leaving it on "Connecting…", so it follows the session's state with its flag in its ViewModel; and *Accept* with another session open spent the invitation, since the session ignores a second start, so it is refused before the server is called. `JoinCodeFragment` and *Connection lost* also navigate on transitions; this PR leaves them as they are. Six mutants of the fixes each failed a new test; 1040 unit tests passed.)

[#117]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/117
[#134]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/134
[#139]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/139
[#113]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/113
[#145]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/145
[#138]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/138
[#144]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/144
[#143]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/143
[#146]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/146
[#118]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/118
[#149]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/149
[#148]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/148
[#119]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/119
[#150]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/150
[#153]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/153
[#154]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/154
