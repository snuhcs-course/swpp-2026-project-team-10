Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## Color tokens and a DayNight theme ([#202])

- [Dongje, 11-09] "Light mode needs the colors turned into tokens first. Check the code, and tell me the order of the work and what I need to review." (The agent counted 199 colors written as values in 60 resource files and 15 in Kotlin, most of them the icons' white and transparent, and listed five decisions: the camera's bars, the camera's shared styles, dark until light mode, the icons, and the names and the test's scope.)
- [Dongje, 11-09] "1. Give the camera's top and bottom bars and the shutter tokens of their own. 2. I don't get 'camera tokens and three style variants': isn't there only one style now? 3. Keep it dark until #212. 4. Do the icons even have to change color? 5. Go with your recommendations." (The icons stay white and take their tint where they are used. Tracing every view then showed about sixty on the camera screens reaching page tokens through shared styles and text appearances, not five, so Camera and Subject view are inflated in the dark configuration instead of getting copies of the styles; the 62 screens of `KoreanScreensTest`, rendered before and after, were identical.)
- [Dongje, 11-09] "Delete the old app and install it. But did you make the app follow the phone's setting? Don't follow it: for now always dark by default, with light or dark chosen in Settings." (Pix already ignored the phone's setting, since it starts dark; the wording of the documents was made plain, and the choice in Settings goes to light mode, [#212]. On the S22 in light mode Camera, Settings, and the *Shoot together* sheet stayed dark.)
- [Dongje, 11-09] "I plan to make Camera and Subject view light too. Does what you built now conflict with light mode later?" (Yes: drawing those two screens dark would have to go, and the 51 views over the image get fixed colors instead; in 9:16 and 1:1 the bottom controls lie over the image, which light mode has to decide.)
- [Dongje, 11-09] "Take only that part out now." (The dark drawing of the two screens, its test, and the sentences about it were removed; `DayNightThemeTest` checks that Pix starts dark and that the theme follows the configuration.)

## The screens after the user test ([#204])

- [Joonhyung, 11-12] "Check which tasks in #204 are already built." (The top-right actions came with #199 and the "upload a reference photo" wording was already gone. The save notice, ✕, the toggle icon, the scene step, the tests, and the documents were still open.)
- [Joonhyung, 11-12] "While both phones stream, neither P nor S can reach Settings. Put a Settings button at the top right on both. Also drop the round yellow pill and keep only the yellow text, and draw this in each flow image." (Flows 1, 2, 4, and 5 were redrawn: the action as yellow text, and the gear beside *End session* and *Leave*.)
- [Joonhyung, 11-12] "Change only the top right, and add the Settings button on the streaming screens." (Full-width primaries and the other yellow buttons stay.)
- [Joonhyung, 11-12] "Yes, implement." (`Widget.Pix.Button.Header` lost its pill. The gear stays on Camera in a session, except during the scene photo, and Subject view got one. Settings over Subject view goes back to it when the session ends. Tests: `HeaderActionTest` and three in `SettingsOnScreensTest`.)
- [Joonhyung, 11-12] "Right-align the text at the top right; it leans a little to the left." (The text had been centered in the 88 dp touch area. It now ends at the area's right edge, 20 dp from the screen's.)
- [Joonhyung, 11-13] "On the uploaded photo's view the title is '참고 사진'. Make it '가이드', and 'Guide Outline' in English, and check the pose generation flow too." (The generated pose already said "가이드". Both now use one title, as does the subject's button for that step.)
- [Joonhyung, 11-13] "Commit, push, and open the PR, with a table of placeholders for the photos." (This PR.)
- [Joonhyung, 11-13] "Remove the save notice." (A saved photo now shows only in the thumbnail; "Saving…" and the failure reasons stay. The green chip look and its two drawables went with it, and Flow 1 lost the chip.)
- [Joonhyung, 11-13] "Which parts change for ✕ and the toggle icon?" (✕ is Remove guide on the guide's panel, and the toggle is the guide-edit button on Camera and Subject view, both drawn as plain icons.)
- [Joonhyung, 11-13] "For E3, the tutorial never says ✕ takes the guide off, so add it, and make ✕ light red. For E2, a person's outline in guide mode and a camera in camera mode. Draw it for review first." (Flows 1, 4, and 5 were redrawn, with a mock of the guide's tutorial tips.)
- [Joonhyung, 11-13] "Yes, implement." (`showGuideEditing` sets the toggle's icon from the mode, ✕ takes the danger text color, and the guide's tips gain "Take the guide off". `ic_move` went unused and was removed.)
- [Joonhyung, 11-13] "Commit and push, with screenshot placeholders on the PR." (A five-slot table for the toggle and the tutorial.)
- [Joonhyung, 11-13] "The Korean tutorial's *Got it* is in an odd place. Put it in one place for both languages, and commit." (Placed after the captions, *Got it* took whatever was left, which depended on the captions' length. It is now placed first, from the rings alone, at the bottom's middle, and the captions keep clear of it.)

[#202]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/202
[#212]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/212
[#204]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/204
