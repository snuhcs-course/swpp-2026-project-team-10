Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## The photo sooner, and the shutter free while it saves ([#208])

- [Dongje, 11-12] "Is there a way to improve the camera quality and the latency of #208?" (From the code alone: Pix asked for the largest photo in `MAXIMIZE_QUALITY` and locked the shutter until the photo was saved. The agent listed the candidates, with the largest size as the likeliest cause, and said that nothing was measured yet.)
- [Dongje, 11-16] "Let's go ahead." (Measured on the S22, eight photos per setting. The largest size was not the cause: the photo was already 12 MP, and the option changed nothing. `MAXIMIZE_QUALITY` locks the focus before every photo, so the picture was taken 0.7 s after the press; `MINIMIZE_LATENCY` takes it in 0.26 s to 0.39 s with the same image. Zero-shutter-lag was faster but twice as noisy, and CameraX turns it off on the S22's US models. Built: `CaptureTuning`, `takePhoto`'s `onCaptured`, the shutter free while a photo is saved, no "Saving…" label, and the *Shutter* table of `zoom_timings.py`. The installed Pix has another signing key, so the builds were measured as a copy beside it.)
- [Dongje, 11-16] "Make me the assignee and delete the test photos. Can you make an image for the PR that makes the photo quality before and after easy to compare? And tell me what was built to improve the quality." (The 140 test photos went to the gallery's trash. The image puts a saved photo of each build side by side, with three crops at 100 %: equal size, sharpness, and noise. Nothing in this change improves the image; it only stops the wait.)
- [Dongje, 11-16] "You did take photos, right? Camera quality means the saved photo." (Yes: both are the JPEGs saved in the Pix album, not preview frames.)
- [Dongje, 11-16] "The issue stays open, and the quality part goes to the next PR." (This PR is the latency part: the comparison with the phone's own camera and CameraX Extensions follow.)

[#208]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/208
