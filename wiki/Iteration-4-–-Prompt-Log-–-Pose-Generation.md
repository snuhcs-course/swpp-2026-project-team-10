Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## Back on the pose screens and the top bar ([#199])

- [Joonhyung, 11-09] "I am building #199. Give every screen's button to the next screen one place and size, and match the corner radii and the spacing between components. On *Pick a pose* the back button is an ✕ that returns to the first camera screen, not to the description: change that, remove *Try other poses*, and replace *Use this pose* with *Select* at the top right. From now on Back sits at the top left and the next step at the top right, not at the bottom. Draw the flows first, and build the UI only after I review them." (The agent compared the pose screens' layouts and styles and proposed one top bar, a set of radii, and a spacing scale. It asked three questions: whether #204's primary-button item comes into #199, where Back goes from each screen, and the label of *Generate poses*' button.)
- [Joonhyung, 11-09] "All in #199. Back goes one step at a time. Short labels by state." (Flows 1, 2, 4, and 5 were redrawn and rendered in English and Korean, with a before/after sheet of the six pose screens. The open points were listed: the session's failure screens, *Cancel* on *Generating poses*, and the chooser's name on *Reference confirm*.)
- [Joonhyung, 11-10] "Remove *Choose another photo* from *Reference confirm*, put a button on *Pick a pose* that regenerates from the same prompt, and center the scene photo on its review, a little low. Render the review images again." (*Regenerate* went to the bottom of *Pick a pose*, outside a session only; the photo sits in the middle of the space under the top bar.)
- [Joonhyung, 11-10] "Implement as drawn." (Built test-first: the ViewModel tests failed on their assertions, then passed. The set stays behind *Generate poses* outside a session, *Pick a pose* stays under *Reference confirm*, and in a session Back drops the set on both phones. The camera's review and its two Backs were checked on the emulator. A string key clashed with an existing one and was renamed.)
- [Joonhyung, 11-10] "Joonhyung Han." (The reviewer named in the AI markers.)
- [Joonhyung, 11-10] "Android Studio says MainActivity doesn't exist on the real phone." (The S22 had booted in safe mode, which hides every downloaded app; the build was fine. A first scan of the APK's code wrongly reported MainActivity missing, until a scan that read dex strings correctly found it.)
- [Joonhyung, 11-10] "On *Generating poses* and *Pick a pose* the AI was already called, so Back must ask first: 'Are you sure you want to go back?' with 'The images are being generated' or 'The images have been generated'." (A dialog on both screens, which closes with the screen when the set ends meanwhile; a Flow 2 card shows it.)
- [Joonhyung, 11-10] "Put these screenshots in order, commit, push, and open the PR, with the dialog from the flow." (This PR.)
- [Joonhyung, 11-10] "Instead of 'The images have been generated (are being generated)', stress that going back makes the images disappear for good." (Outside a session, Back on *Pick a pose* keeps the set under *See the last poses*, so the warning would be false there. Asked, the user chose to warn only where the images are lost: *Generating poses*, and *Pick a pose* in a session, with "되돌아가면 생성 중인 이미지가 사라지며, 다시 복구할 수 없습니다." and its *Pick a pose* twin. The Flow 2 card moved over *Generating poses*, and Flow 4 notes the session's question.)
- [Joonhyung, 11-10] "Back on *Pick a pose* now leads to the description with *See the last poses* under it, which is confusing; alone too, *Pick a pose* needs the same question." (The kept set went: Back on *Pick a pose* asks, then drops the set in every case, and *See the last poses*, the button's *Regenerate* label, and their arrows left the code, the tests, the documents, and Flow 2, which now shows both questions.)
- [Joonhyung, 11-10] "When a generated pose is selected and outlined, the title says '참고 사진'; make it '가이드'. On the error screen, break the text after each period." (*Reference confirm* takes *Guide* for a chosen pose on both phones and keeps *Your reference* for a photo; the three pose-generation failure messages break after each sentence, in English and Korean; Flows 2, 4, and 5 were rendered again.)

## A refused description out of the prompt history ([#200])

- [Dongje, 11-10] "We block wrong photos and prompts with Jev and with ML Kit's pose and face detection, but a blocked prompt stays in the history. We need something that keeps such a prompt from being saved." (The agent found the path in the code and reproduced it in a test: a description goes out as five requests, each judged by itself, and the filter lets a request through when it cannot answer in 2 s, so a refused description could still get a candidate, from a variation or from such a request, and was kept at that first candidate. IT-22's three descriptions asked 20 times each gave the same answer every time, and 1 call of 60 took over 2 s. Now the description's own request decides: when the service refuses the description as written, neither phone keeps it, and an entry kept before is removed. The photographer's phone tells the subject's in `pose.candidate.failed`.)
- [Dongje, 11-10] "Are the image service's refusal and the filter's both kept out of the history now? Then keep blocking both, and fix the wording." (Both arrive as `REJECTED`, so both already counted; the documents had called the image service's part temporary, until [#203] gives it a code of its own. They now say that both count, and that [#203] must keep it so.)

## A message for each way pose generation fails ([#203])

- [Dongje, 11-11] "I'm going to work on #203. Now that P2 is done, check what needs another look in the current state." (#199 had rebuilt the failure screen: one action at the top right, no *Edit description* or *Back to camera*. So the eight cases became a message and at most one action each; the server's new codes, the suggestions' lost reason, and the subject's notice were still open.)
- [Dongje, 11-11] "Show me the wording and the actions as images first." (The real failure screen rendered at the S22's size for each case, in English and Korean.)
- [Dongje, 11-11] "No *Try again* on 3. In 1, 'try' instead of 'give it a try'. 3: 'The last hour's usage has been exceeded.' 4: 'Today's usage has been exceeded.' 5: drop the prompt's text, 'Received a harmful prompt request.' 6: no *Try again*, 'Change the description or the photo.' 7: drop 'The AI service' at the start. The same in English, and check that every message is two lines." (Rendered again, with each message's lines counted: all two.)
- [Dongje, 11-11] "Change every 'give it a try' to 'try'."
- [Dongje, 11-11] "The first line is the reason, right? Keep only the reason, drop lines like 'try again', and render again." (One line each, in both languages.)
- [Dongje, 11-11] "Confirm it as it is and build it. Keep 'prompt'." (Server: `422 DECLINED` for the image service and `429 DAILY_LIMITED` for the day, apart from `REJECTED` and `RATE_LIMITED`. App: two new errors, the suggestions' failure with its reason, one case per failed set chosen in a fixed order, the message and *Try again* only offline, timed out, or after a server error, and the reason on the subject's phone through `pose.step`. Tests first; mutations of the order, the actions, and the reason's name each failed a test.)
- [Dongje, 11-11] "I think the *Shoot again* button on 7 can go too." (No person now has no action; Back reaches the photo's review.)

## Situation prompts ([#209])

- [Jaewan, 11-13] "I need to test issue #209. We are planning to separate image generation scenarios (the ui will kinda look like [Image #1]), and the six templates we are trying to do is: 배경 강조(background, which covers 바닷가, 여행지, etc) / 인물 강조(person, which covers 졸업사진, 카페, etc) / 랜드마크(landmark) / 증명사진(ID photo) / 기본(default, or we may call this 자동 too) / 셀카(selfie). As before, the user can insert their specific use cases for each, and we need to prepare the prompt templates for each. Can you help me with this? The current prompt formats may go to 기본 and 셀카."
- [Jaewan, 11-13] "how much did you spendn on openrouter"
- [Jaewan, 11-13] "never run openrouter calls before i confirm. now tell me how i can see the test results"
- [Jaewan, 11-13] "keep background wider (v2) and put landmark back to never-wider (v1 framing, plus the "never add a landmark" sentence). then rerun the tests and show me the results, only the current version photos. Use this key (key omitted)"
- [Jaewan, 11-13] "can we also see a version where we compare the same photo on the different versions"
- [Jaewan, 11-13] "nono not for v1 and v2, the 배경 강조 vs 인물 강조 vs ..."
- [Jaewan, 11-13] "for the upstream error image in that report, can you try once more"
- [Jaewan, 11-13] "judge that too"
- [Jaewan, 11-13] "what are the current prompts?"
- [Jaewan, 11-13] "can you try the landmark test with [Image #2] this image"
- [Jaewan, 11-13] "yes retry and judge"
- [Jaewan, 11-13] "add that to the first report before"
- [Jaewan, 11-13] "can you just run the other situations for this image too and add that to the side by side report?"
- [Jaewan, 11-13] "judge them too"
- [Jaewan, 11-13] "are we getting pose suggestions according to the situation type? or is the pose suggestion prompt also the same for all situations?"
- [Jaewan, 11-13] "no. let's just use this for now. now back to report 2. for the eiffel tower photo, the landmark images are terrible i think. the tower is not upstraight, aligned, and hmm it's too small maybe. i'm not sure if there is some good rule photographers use for their filming, but can you improve this?"
- [Jaewan, 11-14] "skyline, clock tower, bridge -- what are these images?"
- [Jaewan, 11-14] "test with the eiffel photo and [Image #3] this one. and dont run judges from now on. don't put them into the reports too"
- [Jaewan, 11-14] "i think we should instruct the user to have the landmark (or part of it which they want to have in the photo) in the scene photo, and the nwe can crop freely"
- [Jaewan, 11-14] "run the tests again, and for the triumphal arch picture, use this [Image #4]"
- [Jaewan, 11-14] "not sure, but it seems like v5 kinda mkaes the landmark smaller. i think removing the crowd is okay, since the user may give an input for that. and are we using some professional tips in the prompt?"
- [Jaewan, 11-14] "yes"
- [Jaewan, 11-14] "i think v6 is much better. regarding professional tips, do you think we can also enhance the other situations' prompts by adding some professional tips?"
- [Jaewan, 11-14] "i think they are too restrictive, maybe slightly looser?"
- [Jaewan, 11-14] "try them"
- [Jaewan, 11-14] ", and where the pose allows, turn them toward the view / where a small step aside does it, keep anything behind the head, such as a pole or a tree, from seeming to grow out of it -> remove these. and also i think we can revert the selfie phrase. test them again"
- [Jaewan, 11-14] "yes let's use this, can you cleanup the report to contain only the final prompt photos"
- [Jaewan, 11-14] "can i share this html file standalone? if not can you give me a minimal standalone file i can share"
- [Jaewan, 11-14] "can you capture the eiffel tower results (no description) into a png"
- [Jaewan, 11-14] "i think 증명사진 kinda doesn't fit into this app since people won't really take those images with their phones, right? any other situations we can set?"
- [Jaewan, 11-14] "let's just remove 증명사진, and split 인물사진 into 전신/반신. i think the current version slightly more prefers 반신"
- [Jaewan, 11-14] "yes"
- [Jaewan, 11-14] "do you think 배경 강조 and 전신 may be too similar? or in what scenarios may this differ?"
- [Jaewan, 11-14] "yes"
- [Jaewan, 11-14] "okay now we decided to discard #211 (four prompt templates), and just do the current (one template with three rewrites). now can you commit and push the current fixes, and create a PR? before that, if there are changes in dev, merge it. also, the Android UI screens are developed in #210, right? so our current fix is only in the server-side?"
- [Jaewan, 11-14] "can you give me some png cards i can attach to the pr descriptionn"
- [Jaewan, 11-14] "don't use my own photos. can you give me a card that tests a single image with all scenarios (except for selfie) from the CC0 scenes?"
- [Jaewan, 11-14] "yes"
- [Jaewan, 11-14] "overall can we fix '반신' to '상반신'?"
- [Jaewan, 11-15] "yes"

## Four prompt templates, tried and dropped ([#211])

- [Jaewan, 11-13] "we also are planning issue #211, not using the paraphrasing llm call and just preparing four prompt templates. hmm, do you think this is a good idea? or would paraphrasing be the better option"
- [Jaewan, 11-13] "when paraphrasing, do we show the image to the model too? or only the input?"
- [Jaewan, 11-13] "can you try creating four versions of the 'default' situation prompt, and test with the graudation photo image you used, with the input "졸업사진"? and compare it with the previous three paraphrases version?"
- [Jaewan, 11-13] "added credit. try it again"
- [Jaewan, 11-13] "what are the four prompt templates that were used?"
- [Jaewan, 11-13] "no i want like totally different text (though similar meaning overall)"
- [Jaewan, 11-13] "i don't like the 'scene story' style prompt"
- [Jaewan, 11-13] "yeah. can you now show me the full prompts all?"
- [Jaewan, 11-13] "at least for one we can use the previous prompt cant we"
- [Jaewan, 11-13] "drop A. and then can you compare this with the parahrased version"
- [Jaewan, 11-13] "what if we do '일어서서 왼쪽을 보며' with that image"
- [Jaewan, 11-13] "what is the current paraphrase prompt? how does it make differences"

[#199]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/199
[#200]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/200
[#203]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/203
[#209]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/209
[#211]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/211
