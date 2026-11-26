Back to [[AI Collaboration Report – Iteration 5|AI-Collaboration-Report-–-Iteration-5]]

## The UI fixes: switches for the grid and the horizon bar, and UAT cycle 2's opinions ([#301])

- [Jaewan, 11-24] "we need to do ui fixes written in #271. Here are some reference images: [Image #1] [Image #2] make the changes in a new branch"
- [Jaewan, 11-24] "can you give me screenshots for what you've changed"
- [Jaewan, 11-24] "i think we should place the whose camera notice on the subject view where Camera shows its notices, above the bottom row \ and when we place text in the shutter region of the subject view, align the whole part vertically with the guide and gallery thumbnails, excluding the '가이드' text part. \ also when writing --'s camera, use the account name first if logged in, and then the phone name next. also what happens if the phone name is long? what's a typical phone name when we use galaxy phones in korean? \ also delete '인물과 배경은 그대로' from the generating poses screen."
- [Jaewan, 11-24] "can you make the screenshot with the phone name '길동의 Galaxy S23 Ultra' \ also the generating poses screen is to be changed in #274 so i think we should just leave it, so don't change the caption part. #274 will change this together."
- [Jaewan, 11-24] "how long do the '보고 있어요' toasts appear? and since the subject view always shows whose camera we're watching, why not just not show this toast in this case?"
- [Jaewan, 11-24] "commit and push this, and open a pr on this. also give me each screenshot section as pngs so i can attach to the pr description"
- [Jaewan, 11-24] "redraw the wireframes and push them"

## A new guide starts with the camera's gestures ([#304])

- [Dongje, 11-24] "When I used the app myself, a guide that is added always seems to start with the guide's gestures, and the camera's gestures need the button. Check this. If it is true: people use the camera's gestures more than the guide's, so the camera's gestures should be the default when a guide is added. Build it." (The agent found it true in `CameraViewModel`: a new guide turned guide-edit mode on, "so that it can be placed at once". It changed the rule test-first: the mode holds only for the guide it was turned on for, so a new guide, and another guide that replaces one, start with the mode off, as on Subject view. Four screen tests that assumed the old start were rewritten. R&S 4.6, Design 4.7, Testing 4.8, the flow 1 wireframe. The tutorial's tip on the toggle, "The guide moves only while this is on", still held, so it left the tip and asked whether to reword it, with "Turn this on to move the guide" as its recommendation.)
- [Dongje, 11-24] "Change the tutorial's line as you recommended." (The tip on the toggle now reads "Turn this on to move the guide", "이 버튼을 켜면 가이드를 옮길 수 있어요" in Korean, since the toggle is off when the tips show.)

## Camera drops the "Pinch to zoom" hint ([#306])

- [Dongje, 11-24] "The plain camera does not show the line 'Pinch to zoom', but with a guide up it shows. The tutorial tells it too, and everyone knows a camera's zoom in and out, so the line can go. It is a small task, so start building at once." (The agent changed the three screen tests that expected the line, saw them fail, and took the line out of Camera's hint chip, which now says only "Guide hidden". It removed the string in both languages and the chip from two frames of the flow 1 wireframe. R&S 4.7, Design 4.8, Testing 4.9.)

## *No person found*: Back returns to *Add a pose guide* ([#294])

- [Dongje, 11-25] "Look at issue #294 and tell me what I have to review, what is to be built, and in what order." (The agent read the issue and the code. The sheet closes when a photo is picked, so Back from *No person found* showed the camera alone. It put up three points, each with a recommendation: the same Back on *Reference confirm*, a line that tells the way once the button is gone, and the generated pose's path left as it is.)
- [Dongje, 11-25] "Go with the recommendations for 1 to 3, and assign it to me." (The agent drew the screen from the real layout, before and after, with a table of where Back goes.)
- [Dongje, 11-25] "1. Can the gallery come up over the sheet too? 2. With an AI pose, is the user not asked to shoot again when no person is found? Is there one more block after that? Check and show me the screens." (The gallery can: the sheet opens its picker once. The pose flow has three blocks: the phone on the scene photo, the server on the same photo, and the phone on the generated image after *Select*, which is this issue's screen. The agent showed the three screens.)
- [Dongje, 11-25] "1. Put the gallery over the sheet. But Back on the confirm screen opens the sheet alone, and from the library it goes to the library. 2. Keep 1 and 2 as they are; would it be awkward to remove only the button on 3, with the line kept?" (The agent answered that it is acceptable, and asked which line "kept" means.)
- [Dongje, 11-25] "The AI pose's path keeps 'Pick a photo where one person is clearly visible.' as it is, and the other paths get 'Go back'. Show it as a picture again, then start building." (Second draft, then built test-first: `backToChoosing` for both screens, `openGallery` on the sheet, the line by `ReferenceViewModel.fromPhoto`, and a test on the real navigation. R&S 4.8, Design 4.9, Testing 4.10, the flow 1 wireframe.)

## Who is zooming, the photographer's actions on *Subject view*, and a zoom step that glides ([#292], [#310])

- [Dongje, 11-25] "Assign issue #292 to me and go on as before. Also: a tap on the zoom button changes the picture to that ratio at once, and the iPhone's camera shows the zoom gliding. Check whether that is possible; if it is, open an issue, assign it, and let us do the two together." (Possible: the camera already applies one zoom per display frame for a pinch. The agent opened #310, and put five points up with recommendations: where the line shows, the same on *Subject view*, the subject's tap, a step that needs another lens, and the length.)
- [Dongje, 11-25] "1 as recommended. For 2, list the lines the photographer's phone shows, and I will think about making Subject view the same. 3 as recommended. For 4, check where the lens changes and tell me exactly what you will do. For 5, go that way first and I will check it myself." (The agent listed the nine lines. It read the S22's cameras with `dumpsys media.camera`: its rear camera reaches 0.6× itself, so only a phone with a separate ultrawide, the S21, changes lens, on the steps to and from the widest zoom. It drew the Camera screen during the subject's pinch and the zoom's curve.)
- [Dongje, 11-25] "For 2, show the lines on the subject's phone too when the photographer acts, and build it like this." (Built test-first in three parts. The line "… is zooming" from `ZoomHold`, held until the ratio's notice. A new message `camera.did` for what the photographer did, since a state alone cannot tell a brightness set by hand from one a tap put back; *Subject view* says it where its labels show. `CameraController.glideZoom` with `ZoomGlide` for every single change of the zoom, on either phone's request.)

## The library: situations and memos, history, favorites, and selection ([#270], [#297], [#308])

- [Joonhyung, 11-25] "Start #270." (The agent read the issue and the code, and built it test-first: `situation` on the guide, in `pose.step`, `guide.image.begin`, and `meta.json`; a chip per situation in the library; a memo of up to 40 characters, edited with a pencil on each cell.)
- [Joonhyung, 11-25] "Show me the changed screens so I can check them. This issue goes together with #297." (The agent captured the library on the emulator in Korean.)
- [Joonhyung, 11-25] "One branch and PR." (The agent built #297 on the same branch: `PoseLibrary` keeps every candidate as history, ♥ and *Save guide* mark favorites, the limit of 100 goes, selection under a long press and ⋮, and Back on *Generating poses* and *Pick a pose* without "Go back?".)
- [Joonhyung, 11-25] "Commit and open a PR." (#308.)
- [Joonhyung, 11-25] "Resolve the conflicts with dev now, and check whether the two issues have a task left." (The agent merged dev and checked each issue's tasks against the code. Three were stale and fixed: the shared *Pick a pose* row in R&S 6.5, the tutorial's tip on *Save guide*, and the old "Removed from My library." texts.)
- [Joonhyung, 11-25] "In this picture, the menu should hold Select and Select all, not Select and Delete all." (⋮'s *Select all* starts selecting with every guide listed; a bulk delete always goes through *Delete* and its question.)
- [Joonhyung, 11-25] "The library is All / Photos (uploaded from my album) / AI poses / Favorites. Keep the AI poses and the album's photos in All by themselves, and a heart puts one in Favorites. Swap Favorites and All. When generation gives four photos, all four go to All by themselves, and a heart puts one in Favorites." (The agent answered that the keeping was built already, and asked which tab opens first and where the heart goes. Chosen: Favorites first while one exists, and a ♥ on each cell.)
- [Joonhyung, 11-25] "1. Remove the Add photo button. 2. Remove the Photos and AI poses tabs, keeping only All and Favorites. 3. All, then Favorites, and the library opens on All." (Built with the empty texts for each tab; IT steps that used *Add photo* got a "Changed" note.)
- [Joonhyung, 11-25] "Change the bookmark button here into a heart, filled in red." (*Save guide* on Camera and Subject view, and its notice, now show the ♥ of *Pick a pose* and the library.)
- [Joonhyung, 11-25] "Apply this review, then commit and push, and update the PR with the changes." (Codex's review of #308 had five findings. The agent fixed each with a test: a pose can be selected though its history cannot be written, *Save guide* marks a history entry written meanwhile, a ♥ that cannot be emptied fills again, "The poses are in My library." waits for a written pose, and TalkBack names the ♥ by Favorites.)

## The self-timer and the camera menu ([#315])

- [Jaewan, 11-25] "let's add a timer to the shutter (#272). can you first give me ui mockups for candidates?"
- [Jaewan, 11-25] "in A, why not just put the number 3 or 10 inside the icon? and i think showing a short notice vs the number 3s in the shutter in design C are an orthogonal choices to A/B/C. can you draw the artifact again"
- [Jaewan, 11-25] "[Image #1] where's the number?"
- [Jaewan, 11-25] "i think the shallow version was better instead of filled yellow"
- [Jaewan, 11-25] "i'll choose a different option. first move the selfie button to the current settings button position, and make the below area only have the guide button on left and gallery button on the right (like the subject view). and then on the current flash button's position, merge flash/timer/grid onoff (from settings)/horizontal line onoff (from settings)/settings into one button that opens a panel. the timer button here should behave like option A. and for the extra choices for timer, i'll choose 'notice after each change' and 'X (big number over the preview)'. can you redraw the ui mockup for this? also the subject view too."
- [Jaewan, 11-25] "expanding the panel veritcally consumes too much space, can we do this horizontally only using icons + a short text beneath it? and give me both in english and korean"
- [Jaewan, 11-25] "what happens if the flash is on? does that also show on the bar like the timer?"
- [Jaewan, 11-25] "let's do it this way. implement this in a new branch"
- [Jaewan, 11-25] "the settings panel is too ugly.. i was thinking of this style [Image #2] can you make it more closer to this"
- [Jaewan, 11-25] "i want the transparency and overall rounded borders similar to the ratio panel. and can we make the height slightly shorter?"
- [Jaewan, 11-25] "i think we should make the icons and text there slightly more thinner, and maybe the text can be slightly smaller?"
- [Jaewan, 11-25] "this is good, but i think we can place the icons slightly lower (and make the text slightly smaller, if needed)"
- [Jaewan, 11-25] "can we make them more thinner"
- [Jaewan, 11-25] "when placing icons on the top-bar, make them thick as before, to match the other icons next to it. especially it should aligned well"
- [Jaewan, 11-25] "is everything done except for the tests? then push this and create a pr first. we'll just look at the test results there"
- [Jaewan, 11-25] "i've checked the screens so also update the docs. but before that, there's also changes to dev so merge it and resolve conflicts. especially pr #312 applies some ui fixes, and check if we have anything conflicting with their rules"
- [Jaewan, 11-25] "when it's done, commit and push everything"
- [Jaewan, 11-25] "remove the grid and horizontal line part from the settings. and can we just remove the 'base brightness' part? so the scrolling up or down on focus click is the only part that controls exposure"
- [Jaewan, 11-25] "when clicking on the three buttons on the top bar, the background coloring that show up have different sizes. can you match those? and also i want a slight animation when opening the panels (setting or ratio)"
- [Jaewan, 11-25] "start the emulator so i can check it"
- [Jaewan, 11-25] "when will the check stop?"
- [Jaewan, 11-25] "close the emulator. it seems good"
- [Jaewan, 11-25] "do it. just don't run the check we'll check what runs on the pr. are the docs updated?"
- [Jaewan, 11-25] "codex left reviews. check them"
- [Jaewan, 11-25] "push, update the pr description, and respond to the review and resolve them."

## The phone's photos in Pix, and the pose flow's screens as one flow ([#321])

- [Jaewan, 11-25] "i think we have some problems. the progress bar in the scene photo taking screen is slightly lower than the top bar, so there's a slight space in between. i want this removed. also, when we show the gallery (not in a session), i want the full gallery as a 5 column grid to be shown first, and then the user can click which photo to look at (while the thumbnail always shows the latest image). can we do this? check the code if we can make these fixes"
- [Jaewan, 11-25] "i think you can stop the emulator. i'll just check it in the phone. also, in the gallery, can we show 'all' photos saved in the phone, not only those took with pix? is this possible?"
- [Jaewan, 11-25] "let's go with all photos (we're not going to deploy to play store so it's okay), and the thumbnail should show just the newest photo on the phone"
- [Jaewan, 11-25] "1. the progress bar now matches the top bar, but the subsequent steps have different top bar heights so i think we should match this to that of the first step \ 2. can we remove the 'Photos(사진)' title of the gallery grid, but instead a center-aligned dropdown button that chooses between albums? but the first one showing should always be all photos \ 3. the thumbnail in the gallery grid is kinda low resolution i think \ 4. we should update the privacy policy \ 5. docs and wireframes not yet \ i'll install this on my own using android studio, so you don't need to build the apks"
- [Jaewan, 11-25] "mark the date in the privacy policy by 11/26"
- [Jaewan, 11-25] "1. when generating poses, after the scene photo, the screen doesn't use all of the phone's width i think, which makes the transition very weird. the font sizes on the top bar is also different too. i think we should match these. \ 2. the dropdown design is truely terrible. give me better ideas \ give me mockups for these fixes first"
- [Jaewan, 11-25] "1. i don't want the scene photo to stay full width. keep that part same as before. we only need to have one bar for all four steps with full width. it also seems good to move flash and ratio to the right, but here i think we also need to have a tool panel (including flash/timer/grid/horizon, no settings) instead of only a flash icon \ 2. i'll go with B"
- [Jaewan, 11-25] "1. the animation (light background) when pressing the <- button from is too large \ 2. i want the transitions when pressing the gallery, pressing each photo, pressing each album, switching between photos-album to be smooth. maybe some animation? \ 3. the progress bar is still not fully wide [Image #1] \ 4. in the gallery, fix photos/사진 to all photos/모든 사진"
- [Jaewan, 11-26] "0. i think all problems are coming from that the top bar's height for the generate photo steps are matched with the main camera screen's header, not the other top bars in settings, album, guide confirm, etc. so i think we should just match these to all other top bars not the main camera screen's header. the size of the <- text and other text should be matched too. instead, then when first entering the scene photo screen the camera view will kinda become lower than the main screen so this transition should be smooth. \ 1. then the <- press highlight in all of these cases should be the same. i think it is slightly big now \ 2. the highlight on the buttons on the upper right region during generate photo ('생성', '선택', '사용') don't match the text size. fix this. also they seem too close to the right end \ 3. when transitioning between the generate photo steps, can we make it more smooth? right now it looks like we are just entering a brand new screen, but we arent- the <- button is always there with the same size, and the progress bar is gradually filling \ 4. transitioning from a specific album back to the 'album' tab is not smooth it shows some intermediate screen (very fast, i don't know that this is) and goes back \ 5. when clicking on a specific photo, the screen doesn't go to that immediately, but first goes to another photo and scrolls very fast to that photo. this also happens very fast and happens occasionally not all the time"
- [Jaewan, 11-26] "I'm actually curious, do you think matching the top bar's height to the main camera screen's would be better? or keep them in two heights like now is better? considering other buttons too"
- [Jaewan, 11-26] "1. make the menu and ratio icons bigger during the scene step \ 2. [Image #2] this happens in some places"
- [Jaewan, 11-26] "this is good, but the scene photo step is also a camera screen, but making the header size different only for this might kinda seem weird, isn't it? whats your thoughts"
- [Jaewan, 11-26] "0. i'd rather just make the change at scene photo -> generate poses. let's keep the scene photo step's header size same as the main camera screen \ 1. the highlight for the share and trash buttons in photo view, and the vertical ellipsis button in the 보관함 are too big too \ 2. the highlight when clicking on a photo in the 보관함 doesn't have rounded borders \ also some more fixes \ 1. can we remove the '인물과 배경은 그대로 . 최대 60초' text in the '포즈 생성 중' step? instead keep the situation choice as in the '포즈 생성' step \ 2. in the 보관함, the drop down box design for '선택' and '모두 선택' doesn't match the overall tone of the app. fix this. \ 3. the text in the tool panel for flash/timer/... are too big i think. i think we can make their aspect ratio and letter spacing slightly more tighter, and make the text thickness or size slightly smaller too if needed."
- [Jaewan, 11-26] "1. in the scene photo screen we should also have the text and icon sizes smaller as before. and we may need a different transition to the next screen (generate poses)? \ 2. some highlights for panels in the settings screen also have wrong sizes or corners"
- [Jaewan, 11-26] "1. can we make the header expand gradually, while the captured image shrink gradually to the position? not just first go to the generate poses with the photo large and then shrink the photo with the animation \ 2. the 'AI 포즈 생성 동의함' panel in the settings should have full background highlight i think, including the description part too \ 3. make the tap to copy Pix ID also have rounded corners \ 4. and i think we can have the selfie transition icon slightly bigger. very slightly \ 5. also i think the font size in [Image #7] this popup and [Image #8] [Image #9] these are different. there may be other popups too. i prefer the smaller font size, can you make these all the same?"
- [Jaewan, 11-26] "the transition between 전체 and 즐겨찾기 in the 보관함 is weird. can we make this always go to the very top? or the position the user was before moving to the other tab (if possible)"
- [Jaewan, 11-26] "update the docs and wireframes now"
- [Jaewan, 11-26] "once done, commit and push this, and create a pr for this. also create an issue for this (Iter5-A13) and link it"
- [Jaewan, 11-26] "if we click on the photo in the '포즈 생성' screen, the <- button's position changes. it slightly moves inwards"
- [Jaewan, 11-26] "is this the same for all situations? settings, gallery, guide from uploading a photo, etc?"
- [Jaewan, 11-26] "in the settings, the header doesn't stay there if we scroll down. actually it kinda does try i think, but it's weird"
- [Jaewan, 11-26] "seems like this is still slightly, very slightly moved inwards can you check this? the screenshots show"
- [Jaewan, 11-26] "yes"

## Impeccable's review, and its UI fixes ([#298], [#300], [#312])

- [Sungmin, 11-23] "Now I'm going to handle issue #298. Take a look first."
- [Sungmin, 11-23] "But what exactly does doing that do...? Is it changing the Android UI code, or what?"
- [Sungmin, 11-23] "Just install everything yourself and review it." (The agent installed the Impeccable skill and reviewed Pix's screens with it.)
- [Sungmin, 11-24] "I'm curious how it would look exactly once fixed. Before doing it, can you draw it first so only I can see it?" (A private page with each fix drawn before and after.)
- [Sungmin, 11-24] "What does TalkBack mean? And weren't there more fixes besides these?"
- [Sungmin, 11-24] "I don't think TalkBack needs to go in. But isn't something like this possible? In guide-edit mode, mark the guide so it looks movable, by painting something over it or drawing a box around it? Also draw the fixes written in the other issues. And the ratio's text can be left out."
- [Sungmin, 11-24] "Apply option B for the edit-mode mark, in the shutter's place. What does showing the photo instead of the outline mean? I think the confirm dialog can go."
- [Sungmin, 11-24] "Put it at the same link, and update GitHub."
- [Sungmin, 11-24] "Did you change the documents too?"
- [Sungmin, 11-24] "Yes, fix them and open the PR." ([#300]: Testing 4.4 records the guide's light teal fill while editing, and Back from the poses without "Go back?".)
- [Sungmin, 11-25] "Now I'm going to build issue #298. First make an implementation plan."
- [Sungmin, 11-25] "Then can you start with the parts that don't overlap issue #308?"
- [Sungmin, 11-25] "If it doesn't change much code, bring the tip back too. Five seconds for Undo seems fine."
- [Sungmin, 11-25] "Commit what's done so far and open the PR." ([#312], as a draft.)
- [Sungmin, 11-25] "Oh, just build the rest too."
- [Sungmin, 11-25] "Commit and push."
- [Sungmin, 11-25] "Fix the description and take it out of draft."
- [Sungmin, 11-25] "PR #312 has conflicts now. Resolve them." (With #308, then #313, merged meanwhile. The library now has two tabs, so the switches' touch target test takes two parts or more.)
- [Sungmin, 11-25] "When done, commit and push."
- [Sungmin, 11-25] "Build an APK from this PR and install it on the connected phone."

## A photo turns into its guide ([#295], [#319])

- [Sungmin, 11-25] "First plan how to build issue #295. Don't change anything." (The agent proposed one animation for every gallery photo, grown from the screenshot's cut, with two choices. The photo goes to the card's bottom right, where a screenshot's photo already goes. AI poses keep their fade, or the subject's phone would have to change too.)
- [Sungmin, 11-25] "Go with the recommendations." (Built test-first: five screen tests failed on the old behaviour. `PhotoToGuide` and `PhotoToGuideView` replace the screenshot's cut. The line is the outline view's own drawing, shown along `OutlineExtractor.lines`. `ReferenceGuide.photo` keeps the photo, dropped on *Use*. R&S 4.19, Design 4.20, Testing 4.22, and Flow 1's note.)
- [Sungmin, 11-25] "Commit and open the PR." ([#319])
- [Sungmin, 11-26] "PR #319 has conflicts now. Resolve them and update the PR." (With #316 and #321: the revision rows renumbered, and Flow 1's images rendered again.)
- [Sungmin, 11-26] "I connected the phone. Install the APK."
- [Sungmin, 11-26] "Checked. Upload the prompt log too. If there are logs I didn't upload while building other features, include them as well." (On the phone, `seg.start`–`seg.end` took 0.49–1.33 s.)
- [Sungmin, 11-26] "Let's just put the prompt logs in a new PR." (This section and the one above, the privacy policy's on its page, and ONE store's on a new page.)

[#270]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/270
[#292]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/292
[#294]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/294
[#295]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/295
[#297]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/297
[#298]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/298
[#300]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/300
[#301]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/301
[#304]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/304
[#306]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/306
[#308]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/308
[#310]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/310
[#312]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/312
[#315]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/315
[#319]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/319
[#321]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/321
