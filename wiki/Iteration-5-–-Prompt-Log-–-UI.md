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

[#270]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/270
[#292]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/292
[#294]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/294
[#297]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/297
[#301]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/301
[#304]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/304
[#306]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/306
[#308]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/308
[#310]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/310
