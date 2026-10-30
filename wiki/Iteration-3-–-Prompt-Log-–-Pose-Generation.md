Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

## *Pick a pose* one candidate at a time ([#114], [#108])

**The pager (10/24)**

- [Dongje, 10-24] "Generating poses now shows the four photos split on one screen, and they are too small. Can you make it a slide so each photo can be seen large, one at a time, and a swipe shows the next suggested pose? Update dev first." (A `ViewPager2` on *Pick a pose*; *Generating poses* keeps its grid. No candidate could be generated off campus, so the review images were rendered under Robolectric with stand-in photos.)
- [Dongje, 10-24] "Was *Try other poses* there before?"
- [Dongje, 10-24] "Can the branch name be changed later? I want to open the PR from a branch named after the feature and rename it just before merging." (The answer was that a rename on GitHub keeps the open PR. It does not: renaming closed #108 and #110, which were opened again as [#133] and [#135].)
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
- [Dongje, 10-26] "Drop ML Kit, and let the model judge everything." (The phone no longer measures how much closer to get; the model judges it with the height and the angle.)
- [Dongje, 10-26] "Document the tips as built, as compactly as possible." (The plan had a tip for each candidate on *Pick a pose*; the documents now follow the build.)
- [Dongje, 10-26] "Check the Codex review on the PR, decide what to fix, and let me review the plan." / "Make the button position an A2 issue, with the camera screen's buttons and preview size; fix #2 too." (Fixed: the server cancels a dropped tip request, a tip names its guide, and numbers in words are refused; the request size stays, measured at 604 KB at most; the top bar went to [#136].)

## Pose candidates in the scene photo's aspect ratio ([#150])

- [Jaewan, 10-30] "Let's build issue #112. Tell me your plan and what I need to decide for you."
- [Jaewan, 10-30] "do as you recommended, and only test with fakes no paid runs"
- [Jaewan, 10-30] "commit and open the PR. also give me some screenshots of the new cases"
- [Jaewan, 10-30] "The 'creating...' text in the 'Generating poses' screen is not vertically centered in each area. fix this"
- [Jaewan, 10-30] "The 'creating...' text in the 'Generating poses' screen is not vertically centered in each area (in the screenshots). or are we missing a spinner here?"
- [Jaewan, 10-30] "there are new changes in dev"

## Pose tips in Korean ([#156])

- [Dongje, 10-30] "On a new branch: the pose tips are written only in English; when the app's language is Korean, the tip should be in Korean too. Create the issue, list the tasks, and go ahead on your own." (The app sends the screens' language from a resource that `values-ko` overrides; the server adds a Korean instruction with Korean examples, and the number check learns Korean counts, units, and degrees. Tried on the 16 trial pairs: every Korean sentence usable, the decisions within the spread of English's own rounds.)
- [Dongje, 10-30] "Commit, push, and open the PR." / "Update dev, resolve the conflicts, then check the Codex review." (Four findings, all taken.)
- [Dongje, 10-30] "Fix it as recommended, commit and push, then reply and resolve." (A sentence not in the language asked for is refused; Korean counts run together or with an ending are caught; the trial script records the Korean instruction and checks 80 characters.)
- [Dongje, 10-30] "Check the new Codex review." / "Go ahead that way." (A Korean sentence over 80 characters is refused; a one-digit degree with an ending is caught.)
- [Dongje, 10-30] "Fix it as recommended, commit and push, then reply and resolve." (Third review: every letter of a tip must be of the language's script, and a kept tip is asked again when the language changes.)
- [Dongje, 10-30] "Check the new Codex review." / "Fix it as recommended, commit and push, then reply and resolve." (Fourth review: a Korean unit counts only as a word of its own, a degree may have three digits, and a newer guide no longer cancels the tip of the guide on Camera.)
- [Dongje, 10-31] "Check the Codex review and fix it, as before." (Fifth review: a unit, or a degree of a whole number, is refused whatever ending follows, and `PoseTips` asks or drops a tip one change at a time, so a discarded guide is never asked again.)
- [Dongje, 10-31] "Check the Codex review again and fix it; a new one came in." (Sixth review: `PoseTips` follows the language from its first value and cancels the request of a guide that leaves Camera.)
- [Dongje, 10-31] "A new review came in." / "Get the Codex reviews yourself: fix, ask for another, and repeat until no more come." (Seventh review: 몇 and 반 count before a step.)
- [Dongje, 10-31] "The reviews keep coming: while fixing them, structure the code so that edge cases are as unlikely as possible." (Ninth review, with a restructure: the Korean check is a table of measures over one count vocabulary, with 영 before a degree, and `PoseTips` changes its tips through one function whose difference starts and cancels the requests.)

## A light for the scene photo ([#158])

- [Dongje, 10-30] "One more thing to build, without a new branch or PR: add an issue and make it a bug fix. Before generating poses, the flash cannot be turned on while taking the photo (there is no button), so that button has to be added." (The scene photo is a frame of the preview, which a photo's flash would not reach, so the flash button turns the phone's light on while the scene is framed.)
- [Dongje, 10-30] "Install it on the S22; I'll check it myself." (The debug build installed over the one on the phone.)
- [Dongje, 10-31] "Check the Codex review and fix it, as before." (The button says the light is on only once the camera has it, and a scene shot before that waits for the light.)
- [Dongje, 10-31] "Check the Codex review again and fix it; a new one came in." (An answer to the light that arrives after the camera closed is dropped.)
- [Dongje, 10-31] "A new review came in." / "Get the Codex reviews yourself: fix, ask for another, and repeat until no more come." (A lens without a flash takes the scene without waiting for the light. Eighth review: a shot waits until the light has been on for half a second, also right after it came on, and a light that does not go off is asked once more.)
- [Dongje, 10-31] "The reviews keep coming: while fixing them, structure the code so that edge cases are as unlikely as possible." (A shot waits until the light is as chosen, on or off, for half a second; a failed light request is asked once more either way.)

[#108]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/108
[#110]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/110
[#114]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/114
[#115]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/115
[#133]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/133
[#135]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/135
[#136]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/136
[#150]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/150
[#156]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/156
[#158]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/158
