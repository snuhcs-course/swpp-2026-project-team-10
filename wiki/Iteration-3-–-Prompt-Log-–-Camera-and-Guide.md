Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

## Camera screen controls and preview size ([#137])

- [Dongje, 10-26] "The buttons and icons are needlessly large: shrink them sensibly for my review; the S22 is connected. Then let the camera screen grow as much as they shrank; 16:9 now narrows at the sides, so keep the sides and let it grow up and down, even over the buttons. Plan the order and the method for my review."
- [Dongje, 10-26] "Install the debug build over the S22's app. For 16:9, grow up as far as possible and down by what is left. Subject view looks the same, with the icons as small."
- [Dongje, 10-26] "Take the circle off the flash icon, like brightness, and off the ratio; make the top bar's icons, text, and buttons 0.8 of their size."
- [Dongje, 10-26] "Drop the Live banner and the subject's *Dongje's camera*; blink a red light at the camera's top right instead. Make hints such as *Tap Guide to add a pose* the size of *Shoot together*."
- [Dongje, 10-26] "Put the screens from before the task beside the current ones, then go on to the next step." / "Commit here. In 16:9 Guide, the shutter, and the album overlap the image awkwardly: fit them to the space left below; the zoom readout and its text did not shrink."
- [Dongje, 10-26] "Not only in 16:9: the same in every ratio. Keep the shutter large and let it go over the camera image; the zoom as translucent white text at the bottom of the camera image whatever the ratio; the shutter's place and size never change with the ratio. The bottom may look empty: how would you fix it?"
- [Dongje, 10-26] "In 16:9 keep the top bar and let the image go straight down; check where the shutter and Guide end up." / "Center the top bar's icons vertically."
- [Dongje, 10-26] "Make Guide and the album translucent over the image, and raise the buttons so that in 16:9 the text and the buttons sit above its bottom edge."
- [Dongje, 10-26] "Commit, and add the quick zoom steps: a tap on the zoom readout goes to 0.6×, 1×, and 2×." / "Commit and go on to step 3."
- [Dongje, 10-27] "Check the Codex review on the PR and bring it to me for review." / "Go ahead as recommended. Nothing we see changes with item 4, right?"
- [Dongje, 10-27] "Check the new review and bring it to me as before." / "Go with the recommendation, then commit, push, and resolve."

## Reference thumbnail on Subject view ([#143])

- [Dongje, 10-27] "Start with #132: as before, work out what to do in what order and whether anything needs my or the teammates' review."
- [Dongje, 10-27] "Go ahead with all of it. Each phone keeps its own corner, not synced, and the thumbnail stays like the photographer's."
- [Dongje, 10-27] "Commit, then do the documents."
- [Dongje, 10-28] "The S22 is connected: check the Codex review, fix what needs it, and run the check on the phones." / "Wi-Fi is on, go on with the check." / "Commit, push, then reply and resolve."

## Selfie mode ([#152])

- [Dongje, 10-29] "I'll work on #116; do it as before. Go ahead on your own; selfie mode will probably need prompts of its own, which can follow our base prompt format. Update dev first and tell me what I have to decide."
- [Dongje, 10-29] "No need to enforce the rule against selfie mode during a session."
- [Dongje, 10-29] "Open it as a draft PR for now."
- [Dongje, 10-29] "Go ahead as recommended, and take the selfies from Wikimedia. Also lower the top bar's black area very slightly and make its buttons that much larger, never too big like before; lower the shutter, Guide, and the other bottom buttons by as much, and make them a bit larger too."
- [Dongje, 10-29] "You can use the S22."
- [Dongje, 10-30] "Update dev, resolve the conflicts, then check the Codex review and fix it."
- [Dongje, 10-30] "Mark it ready for review; one CI check failed, check it and fix it."
- [Dongje, 10-30] "It failed again."
- [Dongje, 10-30] "Update dev and resolve the conflicts."

## The guide library ([#172])

- [Sungmin, 11-03] "Pull dev again. I want to build the guide library in this iteration, so I need to post issues: work out how many and how, and write them so I can paste them."
- [Sungmin, 11-03] "Are editing a guide in the library, editing the image, or cropping only the photo out of a screenshot separate issues?"
- [Sungmin, 11-03] "Fill in the issue numbers yourself, after checking." / "No, check the open issues."
- [Sungmin, 11-03] "New code was merged into dev; pull it again. I'll build #170 first: plan how, concretely. Don't start yet."
- [Sungmin, 11-03] "Do it all as you recommend, make the branch, and start."
- [Sungmin, 11-03] "Can't you check it on the emulator? Show me with screenshots that it works, then commit, push, and open the PR."
- [Sungmin, 11-03] "I meant the emulator screenshots in the PR body."
- [Sungmin, 11-03] "PR #172 has a conflict now: resolve it, commit, and push."
- [Sungmin, 11-04] "PR #172 has a conflict now: resolve it."
- [Sungmin, 11-04] "I connected my phone; can you install this PR's APK on it?"

## Saving a generated pose ([#175])

- [Sungmin, 11-04] "I'll build #171 first: write a concrete plan, and don't implement it yet." / "Pull dev first, then go on."
- [Sungmin, 11-04] "What exactly is the View button?" / "I think the View button shouldn't be made at all."
- [Sungmin, 11-04] "Do that. Fix the issue first."
- [Sungmin, 11-04] "I connected my phone; install the APK." / "The heart is white now: would red be better, or should it stay white?" / "OK, make it red only when filled, and install it on the phone again."
- [Sungmin, 11-04] "Nicely done. Now commit, push, and open the PR."
- [Sungmin, 11-04] "PR #175 got a review: address it, then commit and push."

## Cutting a screenshot to its photo ([#176])

- [Sungmin, 11-04] "This iteration I want to crop only the photo out of an Instagram screenshot and save the guide and the photo: how should I write the issue?"
- [Sungmin, 11-04] "Instagram photos come in different ratios: should the library show each one, or should the camera's ratio follow the reference?" / "Shouldn't a 4:5 or 2:3 photo set exactly 4:5 or 2:3?"
- [Sungmin, 11-04] "It's #174's turn: pull dev and write a concrete plan. Don't implement it yet."
- [Sungmin, 11-04] "Can't the cut show with a natural transition, landing on the photo? The user can't tell whether it was cut well."
- [Sungmin, 11-04] "We'll add guide editing next iteration anyway, cropping or changing the original or editing the overlay, so keep that in mind. No replay; let's not add needless features." / "I put a few example screenshots in .screenshots/."
- [Sungmin, 11-04] "I added light-mode examples to the screenshots too. Check again whether it works."
- [Sungmin, 11-04] "How does it cut? Does it use an AI model?"
- [Sungmin, 11-04] "Would it be better to crop to the nearest standard ratio, like 4:5 or 9:16?"
- [Sungmin, 11-04] "OK, start implementing it."
- [Sungmin, 11-04] "Put .gitignore back as it was, and keep the screenshot folder outside this repository, so it never goes to GitHub."

## Screenshots from other apps ([#178])

- [Sungmin, 11-04] "I merged it, but these photos from Google Images don't seem to be cut properly. Check, and post the bug as an issue."
- [Sungmin, 11-04] "So Instagram works but Google doesn't?" / "So what's the reason?"
- [Sungmin, 11-04] "Could it work well for everything, not only Instagram and Google?" / "Yes, broaden it."
- [Sungmin, 11-04] "I added new images. Test them."
- [Sungmin, 11-04] "I think handling Instagram stories too would be good."
- [Sungmin, 11-04] "These are stories: check them."
- [Sungmin, 11-04] "Just fix it this way, then commit, push, and open the PR."

## Animating the camera's changes ([#184])

- [Sungmin, 11-04] "Where else in the app do we need natural transitions and animations like this one? When the camera's aspect ratio changes, I thought going black for a moment and then unfolding the screen naturally would feel much more natural. Look at which parts need one. Don't implement it."
- [Sungmin, 11-04] "How many issues should I make, to build them this iteration?" / "Write A12–A14. Assign me, and don't add 'written by Claude Code'."
- [Sungmin, 11-04] "I'm going to implement #179 now. Pull dev first and make a plan; don't implement it yet."
- [Sungmin, 11-04] "OK, do it as recommended."
- [Sungmin, 11-05] "Galaxy's own camera seems to blur the screen during these transitions. And when it switches to the selfie camera, there's an animation in the middle where the screen turns 180° top over bottom. Can we do that too?"
- [Sungmin, 11-05] "But why is 16:9 zoomed in? Shouldn't it just get longer at the top and bottom?"
- [Sungmin, 11-05] "OK, transition with the blur, and add the top-over-bottom flip for the selfie switch; build the APK and install it on my phone again."
- [Sungmin, 11-05] "I'm thinking about doing the aspect ratios like this app. Take a look at how it would be. Don't change anything."
- [Sungmin, 11-05] "No, implement it on this branch. No need to go as far as the tutorial; make the panel, drop full, and drop the circle too. And let's label them 3:4 and 9:16. Start."

## Animating the guide and the pose flow ([#187])

- [Sungmin, 11-05] "I'm not implementing it yet, but what exactly does #180 do?"
- [Sungmin, 11-05] "When I implement #180: right now the subject's phone shows nothing while the poses are generated, only that the other phone stopped. Could the subject's phone also show the screen where the images are generated?"
- [Sungmin, 11-05] "Match the slot positions and show the failed slots too. If there's no reason for asymmetry, I think symmetric is better. And I'm implementing it in this issue, so edit the issue."
- [Sungmin, 11-05] "The wireframe has one button, but the app has two, and with an empty field they do the same thing, which is confusing. What if one button changed its text between an empty field and a typed one?" / "Use those labels, add the ✕, and add it to #180." / "Shouldn't the issue's title change by now? And make the estimate 3 hours." / "Shorten it."
- [Sungmin, 11-05] "Write a concrete implementation plan first. Don't implement yet." / "Go with your recommendations. Start."

## Transitions between screens ([#186])

- [Sungmin, 11-05] "I'm going to implement #181 later. What exactly does it want? What happens when a screen changes?"
- [Sungmin, 11-05] "Do apps usually add a subtle transition like this?"
- [Sungmin, 11-05] "Implement #181 now."

[#137]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/137
[#143]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/143
[#152]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/152
[#172]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/172
[#175]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/175
[#176]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/176
[#178]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/178
[#184]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/184
[#186]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/186
[#187]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/187
