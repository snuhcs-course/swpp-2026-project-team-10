Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## The subject's live view as a mirror ([#201])

- [Joonhyung, 11-11] "I am going to build #201. Explain what has to be prepared first." (The agent traced the video path: the photographer's phone flips the front camera's frames before sending, and the subject's phone shows them as they arrive. The subject's phone did not know which camera was in use, so `camera.state` had to carry it first.)
- [Joonhyung, 11-11] "For the subject to follow the guide while looking at their phone, the view must be flipped left to right. How should that be built first?" (This reversed the issue's goal of an unmirrored rear view. The agent proposed that `camera.state` name the camera, and that the subject flip the rear camera's video with the renderer's mirror and flip the grid, the guide, and the reference photo with the view's scale. The front camera's video is already a mirror and stays as sent.)
- [Joonhyung, 11-11] "Implement it." (Built test-first: the message's `facing`, its echo when the camera switches, the subject's `mirrored` state, and the flipped views. A drag on the flipped guide still follows the finger, since the framework turns the touch. The Design, R&S, and Testing pages, IT-18, and Flow 5 were updated.)
- [Joonhyung, 11-11] "Commit and open PR." (This PR.)
- [Joonhyung, 11-11] Merge dev into the branch and resolve the conflicts. (The AI markers took both reviewer lists, and the #201 rows in the three revision tables moved one version up, behind #203's.)
- [Joonhyung, 11-11] "It works on both phones now, and the test is done. Attach the prompt log, commit, and push." (The two-phone check of IT-18's added step passed.)

## The session's photos on the subject's phone ([#235])

- [Jaewan, 11-18] "Fix issue #235." (The issue left open where the photo opens. The agent chose a viewer inside Pix, since the phone's gallery puts Pix in the background, and said so. It found that the 60 s background limit the issue names applies only to the photographer's phone. Built test-first, each new test failing on an assertion: the session's newest photo in `PhotoSyncer`, Subject view's thumbnail on *Guide*'s line where Camera's sits, the viewer over the screen, which holds the photographer's next step until it closes, and Camera's thumbnail after the session. Seven mutations of the code each failed a test. R&S, Design, Testing, IT-20, and Flow 5 were updated.)
- [Jaewan, 11-18] "Give me screenshots of the fix. Open the emulator with Galaxy S22." (No S22 AVD existed, so the agent made one at 360 × 780 dp and ran a room-code session through the AWS server with a second emulator as the photographer, its camera on the emulator's virtual room, never the webcam. The thumbnail, the viewer, Back to the same session, and Camera's thumbnail after *Leave* all showed as built.)
- [Jaewan, 11-18] "Close the emulators."
- [Jaewan, 11-18] "Add some description for what each photo is in the overview png." Then: "No, just simple and short, and in white background. I need to post that to the GitHub PR." (Six screenshots with a short label under each, on white.)
- [Jaewan, 11-18] "Commit and push this and open a PR." (This PR.)

[#201]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/201
[#235]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/235
