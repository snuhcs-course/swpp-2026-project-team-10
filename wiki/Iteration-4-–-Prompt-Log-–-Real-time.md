Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## The subject's live view as a mirror ([#201])

- [Joonhyung, 11-11] "I am going to build #201. Explain what has to be prepared first." (The agent traced the video path: the photographer's phone flips the front camera's frames before sending, and the subject's phone shows them as they arrive. The subject's phone did not know which camera was in use, so `camera.state` had to carry it first.)
- [Joonhyung, 11-11] "For the subject to follow the guide while looking at their phone, the view must be flipped left to right. How should that be built first?" (This reversed the issue's goal of an unmirrored rear view. The agent proposed that `camera.state` name the camera, and that the subject flip the rear camera's video with the renderer's mirror and flip the grid, the guide, and the reference photo with the view's scale. The front camera's video is already a mirror and stays as sent.)
- [Joonhyung, 11-11] "Implement it." (Built test-first: the message's `facing`, its echo when the camera switches, the subject's `mirrored` state, and the flipped views. A drag on the flipped guide still follows the finger, since the framework turns the touch. The Design, R&S, and Testing pages, IT-18, and Flow 5 were updated.)
- [Joonhyung, 11-11] "Commit and open PR." (This PR.)
- [Joonhyung, 11-11] Merge dev into the branch and resolve the conflicts. (The AI markers took both reviewer lists, and the #201 rows in the three revision tables moved one version up, behind #203's.)
- [Joonhyung, 11-11] "It works on both phones now, and the test is done. Attach the prompt log, commit, and push." (The two-phone check of IT-18's added step passed.)

## The session's photos on the subject's phone ([#235])

- [Jaewan, 11-18] "fix issue #235"
- [Jaewan, 11-18] "give me screenshots of the fix. Open the emulator with Galaxy S22"
- [Jaewan, 11-18] "close the emulators"
- [Jaewan, 11-18] "add some description for what each photo is in the overview png"
- [Jaewan, 11-18] "commit and push this and open a pr"
- [Jaewan, 11-18] "does the gallery show all images that were taken 'within this app launch'? or 'within this share session'?"
- [Jaewan, 11-18] "what about in  other cases? like the photographer view, or when not using sharing?"
- [Jaewan, 11-18] "oh so if we tap it, then we can see all images in the phone? not only those saved during the current app launch? is this the normal case?"
- [Jaewan, 11-18] "wouldn't it be better to just always show the newest photo in the phone whether it took or recieved from pix, regardless of it being in or after a sharing session? what's your thoughts on this"
- [Jaewan, 11-18] "is the gallery opened within pix? or does it open the gallery app"
- [Jaewan, 11-18] "what if multiple photos were taken during a session?"
- [Jaewan, 11-18] "we can't save and show multiple photos?"
- [Jaewan, 11-18] "what was the behavior before this PR?"
- [Jaewan, 11-18] "should we show the photographer only the photos taken during the session too? like the subject? or leave it as now"
- [Jaewan, 11-18] "why 3? and for 4, so you mean the photographer also only shows photos taken during the session? what if the initial thumbnail had a photo taken from before?"
- [Jaewan, 11-18] "let's just not show the first thumbnail in this case, just the same as the subject"
- [Jaewan, 11-18] "how long will this take"

## The reference thumbnail for a guide from the subject ([#234])

- [Jaewan, 11-18] "fix issue #234"
- [Jaewan, 11-18] "yes"

## Pix's photos in Pix's own viewer, as in a gallery app ([#247])

- [Jaewan, 11-18] "when not in the session, can we only view the latest image from the album? can't scroll through all images?"
- [Jaewan, 11-18] "i'm on samsung galaxy but it doesn't scroll, we can only see that specific photo only. also it doesn't the app, it just opens inside pix, though the ui just looks like the gallery app not pix's own. can you check for this?"
- [Jaewan, 11-18] "yes i want scrolling. can you do this fix in a new branch?"
- [Jaewan, 11-18] "can we make this pix in-app viewer more like a normal gallery app? for example small thumbnails at the bottom"
- [Jaewan, 11-18] "add all. and i think we don't need the (7/12) counter?"

## The notice that the subject left ([#238])

- [Jaewan, 11-19] "issue #238. what's the current status of these problems"
- [Jaewan, 11-19] "let's increase the notice time. in a new branch"
- [Jaewan, 11-19] "yes i'll handle the reconnection quality part later. commit and open the PR"

## One phone zooms at a time ([#236])

- [Sungmin, 11-20] "Pull dev again. Then I'm going to implement issue #236 […]. First, plan how it could be built." (The agent traced the zoom path: the photographer's phone applied every request, from either phone, so two pinches at once fought. It proposed a hold on the photographer's phone, taken by the first pinch and freed at its final value, and a new `camera.zoom.hold` message for Subject view. It asked whether the guide and brightness were in scope, and whether a held-back pinch should take over once the other one ends.)
- [Sungmin, 11-20] "Zoom only. For the second question, I'll go with your recommendation. Create a new branch and start implementing." (A held-back pinch stays held back until its fingers lift. Built test-first: `ZoomHold`, the handler's check with a 2 s timeout, the checks on both phones, the label naming who is zooming, and the R&S, Design, and Testing pages.)
- [Sungmin, 11-20] "The reviewer should be me, Sungmin Jo." (The AI markers had a placeholder reviewer.)
- [Sungmin, 11-20] "Commit and open the PR." ([#252])
- [Sungmin, 11-20] "Resolve the issues raised on PR #252." (Codex found two gaps. A Subject view opened after `camera.zoom.hold` missed it, so the session now keeps it as `SessionManager.peerZoomHolder`. An older subject's pinch could take the zoom once the photographer let go, so the photographer's phone now drops a held-back pinch up to its final value.)
- [Sungmin, 11-20] "Yes, commit and push."
- [Sungmin, 11-20] "A new comment came in on PR #252. Resolve it." (The final value that ended a held-back pinch could undo the photographer's zoom when none of its steps had arrived. It became a cancel that changes no zoom.)
- [Sungmin, 11-20] "Commit and push."
- [Sungmin, 11-20] "Another review came in. Fix it if it needs fixing." (A step that arrived after the cancel could hold back the next pinch. The cancel became a `camera.zoom.set` with `cancel`, whose seq makes an earlier step stale.)
- [Sungmin, 11-20] "Commit and push."
- [Sungmin, 11-20] "Write the AI prompt log too, then commit and push." (This section.)

[#201]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/201
[#234]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/234
[#235]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/235
[#236]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/236
[#238]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/238
[#247]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/247
[#252]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/252
