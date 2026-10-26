Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

Prompts were given in Korean to Claude Code (Claude Opus 5.5) and are translated here, shortened to the instruction; status checks are left out.

## *Pick a pose* one candidate at a time ([#114], [#108])

**The pager (10/24)**

- [Dongje, 10-24] "Generating poses now shows the four photos split on one screen, and they are too small. Can you make it a slide so each photo can be seen large, one at a time, and a swipe shows the next suggested pose? Update dev first." (A `ViewPager2` on *Pick a pose*; *Generating poses* keeps its grid. No candidate could be generated off campus, so the review images were rendered under Robolectric with stand-in photos.)
- [Dongje, 10-24] "Was *Try other poses* there before?"
- [Dongje, 10-24] "Can the branch name be changed later? I want to open the PR from a branch named after the feature and rename it just before merging." (A rename on GitHub keeps the open PR.)
- [Dongje, 10-24] "Commit it and open the PR. This will go into Iteration 3, so keep that in mind for the documents." / "Keep the documents only locally for now, and open the PR for the code only."

**Documents (10/26)**

- [Dongje, 10-26] "The Iteration 3 plan is merged into the wiki. Update dev and check, starting from PR #108, whether anything has to be fixed against the documents. If not, put the changes into the documents." (The code already matched FR-4.6; the screen in R&S 6.3, Figure 2's wireframe, Design 2.6.3, and the Testing Documentation's state-based partitions were brought to the pager.)

## Pose tips ([#115], [#110])

- [Dongje, 10-25] "Implement pose tips on the Camera screen after generation: step forward or back, zoom, the camera's height and tilt, as text from an API. Report the method and order, and every UI or feature choice for me to make, with recommendations." (Seven steps and twelve choices.)
- [Dongje, 10-25] "Base it on the scene photo used for generation; the height and angle that suit this pose, in words, not numbers. A translucent banner at the bottom of Camera, closed by a touch anywhere, opened again from an icon in the top bar; show Preparing."
- [Dongje, 10-25] "Don't keep generating images for the tips; once made, try tips on them and let's check together." (`scripts/try_pose_tips.py`; seven prompt versions on 16 scenes.)
- [Dongje, 10-25] "The camera angle, tilted down from above or up from below, doesn't seem to be there?" (The schema and the rules were reworked so the angle is always said.)
- [Dongje, 10-25] "The model applies the rules and writes the sentence; raise the reasoning effort. Why icons for the blue text, can't it just be text?" (`low` reasoning; the banner shows the sentence only.)
- [Dongje, 10-26] "So only closer, keep, and back use the model on the phone?" (Yes: the phone measures the framing with ML Kit and sends it.)
- [Dongje, 10-26] "Proceed so it merges without conflicts with #108." / "Cancel the emulator check and open the PR; I'll check on the S22 later." / "Remake the review image with openly licensed photos; that was my face."
- [Dongje, 10-26] "Document the tips as built, as compactly as possible." (The plan had a tip for each candidate on *Pick a pose*; the documents now follow the build.)

[#108]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/108
[#110]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/110
[#114]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/114
[#115]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/115
