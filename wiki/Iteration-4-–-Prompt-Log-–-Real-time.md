Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## The subject's live view as a mirror ([#201])

- [Joonhyung, 11-11] "I am going to build #201. Explain what has to be prepared first." (The agent traced the video path: the photographer's phone flips the front camera's frames before sending, and the subject's phone shows them as they arrive. The subject's phone did not know which camera was in use, so `camera.state` had to carry it first.)
- [Joonhyung, 11-11] "For the subject to follow the guide while looking at their phone, the view must be flipped left to right. How should that be built first?" (This reversed the issue's goal of an unmirrored rear view. The agent proposed that `camera.state` name the camera, and that the subject flip the rear camera's video with the renderer's mirror and flip the grid, the guide, and the reference photo with the view's scale. The front camera's video is already a mirror and stays as sent.)
- [Joonhyung, 11-11] "Implement it." (Built test-first: the message's `facing`, its echo when the camera switches, the subject's `mirrored` state, and the flipped views. A drag on the flipped guide still follows the finger, since the framework turns the touch. The Design, R&S, and Testing pages, IT-18, and Flow 5 were updated.)
- [Joonhyung, 11-11] "Commit and open PR." (This PR.)
- [Joonhyung, 11-11] Merge dev into the branch and resolve the conflicts. (The AI markers took both reviewer lists, and the #201 rows in the three revision tables moved one version up, behind #203's.)
- [Joonhyung, 11-11] "It works on both phones now, and the test is done. Attach the prompt log, commit, and push." (The two-phone check of IT-18's added step passed.)

[#201]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/201
