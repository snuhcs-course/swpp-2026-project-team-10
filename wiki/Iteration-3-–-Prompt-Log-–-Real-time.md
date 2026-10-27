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

[#117]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/117
[#134]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/134
[#139]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/139
