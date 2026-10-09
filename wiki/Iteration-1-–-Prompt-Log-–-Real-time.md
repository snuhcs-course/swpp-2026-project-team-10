Back to [[AI Collaboration Report – Iteration 1|AI-Collaboration-Report-–-Iteration-1]]

Prompts were given in Korean; they are translated here. Routine messages (commit, push, start the server, build the APK) are left out.

## WebRTC streaming with room codes (#8)

- [Sungmin, 10-03] "My part of Iteration 1 is streaming one phone's camera to the other phone over the same Wi-Fi with a room code. Read the documents and think through how to build it. Don't implement yet; explain the approach in detail."
- [Sungmin, 10-03] "Is this Android work or server work? Who generates the room code, who checks it, and how do the two phones actually connect? Keep Iteration 2's login and friend list in mind, and tell me which screens this touches."
- [Sungmin, 10-03] "Here is issue #8. Use it as a reference, but you don't have to follow it literally; go the way you think is right. Work on the branch `feat/8-webrtc-session`."
- [Sungmin, 10-03] "Connect the real camera frames now. I will do the two-phone test myself, so finish the rest so that I can test. Implement one feature, commit, then the next."
- [Sungmin, 10-04] "A teammate merged new code. Pull `dev`."
- [Sungmin, 10-04] "Don't work on `dev`. Work on my feature branch so that the PR won't conflict with `dev`."
- [Sungmin, 10-04] "Build the APK for the current Wi-Fi so that I can test on real phones."
- [Sungmin, 10-04] "Can this later work when the phones are not on the same Wi-Fi, and how much delay would that add?"

## Remote zoom (#10)

- [Sungmin, 10-04] "I'll implement remote zoom (#10) on a new branch. Make a detailed plan first."
- [Sungmin, 10-04] "The test harness you propose is for your own verification, right? Where does it live, and it never appears in the app?"
- [Sungmin, 10-04] "Good. Implement it in that order."
- [Sungmin, 10-05] "`dev` was updated. Pull it and change the feature branch so that it won't conflict; the server and the camera zoom code changed."
- [Sungmin, 10-05] "It doesn't connect. The laptop and the other phone are on my phone's hotspot; the room code works, but the connection never comes up."
- [Sungmin, 10-05] "Remote zoom works, but the subject adjusts the zoom with 0.6×/1×/2×/3× buttons. That seems wrong, doesn't it?"
- [Sungmin, 10-05] "Make it work the same way as the main camera's zoom instead of buttons."
- [Sungmin, 10-05] "A review came in on the PR. Check whether it needs a fix; don't fix it yet." (asked for each of the five review comments)
- [Sungmin, 10-05] "Fix those two, commit, and push."
- [Sungmin, 10-05] "The video on the subject's phone looks lower quality than the original. Is that normal?"

## Guide sync (#9)

- [Sungmin, 10-06] "Does the current code already share the guide overlay to the other phone, or does that need new work?"
- [Sungmin, 10-06] "Here is issue #9. The overlay on the photographer's phone should appear on the subject's phone and follow every adjustment. Plan first, don't implement. You don't have to follow the issue literally; plan the best approach."
- [Sungmin, 10-06] "What does the note about `CameraViewModel.onRemoveGuide` mean?" / "Leave that alone and start on my part."
- [Sungmin, 10-06] "All good, but draw the 3×3 grid on the shared screen too. And the outline looks too thin; what do you think?"
- [Sungmin, 10-06] "Even 3 px is too thin."
- [Sungmin, 10-06] "There should be a way to remove the overlay from the photographer's camera screen."
- [Sungmin, 10-06] "The photographer's preview looks too small. Should *End session* take the place of *Shoot together* at the top? Why is *Shoot together* still shown while connected? Don't change anything yet; give me your view."
- [Sungmin, 10-06] "Hide *Shoot together* while connected and put *End session* in its place."
- [Sungmin, 10-06] "Add placeholders for the two screenshots (subject and photographer) in the PR description."
