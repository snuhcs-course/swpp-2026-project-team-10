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

[#301]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/301
[#304]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/304
