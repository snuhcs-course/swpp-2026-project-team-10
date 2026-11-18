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

## The situation choice ([#210])

- [Jaewan, 11-15] "I pushed them. now in a separate branch (assuming this will be merged later), can we do #210? a hypothetical ui design would be similar to [Image #5] this. first implement this on your own ideas and show me the results in screenshots"
- [Jaewan, 11-15] "that versus something kinda like this: [Image #6] which do you think is betteR?"
- [Jaewan, 11-15] "yes, rebuild it that way and show screenshots. and one more comparing the entry screen of the two layouts"
- [Jaewan, 11-15] "the preview images for 'my library' doesn't look good, and the subtitles are too big they almost look like titles. and for v1, why do we need a 'cancel' button? i think we can remove this. give me the comparison screenshots again"
- [Jaewan, 11-15] "the thumbnail images for the six situations are kinda weird. how did you create them? i like that it's not a realistic image but an illustration, but it's kinda too unnatural. and they all have too similar colors. can you fix these?"
- [Jaewan, 11-15] "i think we can use image generation models to create this for us. can't we? can you try this with gemini"
- [Jaewan, 11-15] "yes"
- [Jaewan, 11-15] "yes try it"
- [Jaewan, 11-15] "yes this is good. and can you put '상반신' '전신' to the top row and '배경 강조' '랜드마크' to the bottom row? also write '카페.졸업사진' instead of '졸업.카페'. now give me the comparison preview again"
- [Jaewan, 11-15] "i mean the v1 vs v2 comparison"
- [Jaewan, 11-15] "why are the bottom button texts different? what is the current text?"
- [Jaewan, 11-15] "i think a fixed text is good, just '포즈 만들기' / 'Generate poses' maybe? or should we write that the user's going to take a scene photo"
- [Jaewan, 11-15] "yes"
- [Jaewan, 11-15] "okay. now can you show me the screenshots for the entire flow again?"
- [Jaewan, 11-15] "there's no input history here? and for the instructions, write '랜드마크 전체와 인물이 함께 나오게 찍어 주세요" and "머리부터 발끝까지 나오게 찍어 주세요""
- [Jaewan, 11-15] "should we save history per situatoin? and in the case of selfies, should we restrict the camera to only work in selfie mode? also, should we let the user to change their situation choice after taking the photo? this is kinda weird then why choose it in the first place"
- [Jaewan, 11-15] "yes do all three and show screenshots. also fix the other situations '찍어요' to '찍어 주세요'"
- [Jaewan, 11-15] "show the instructions for all other situations. it's okay to just cut only that part of the screenshots"
- [Jaewan, 11-15] "put this together in the previous png"
- [Jaewan, 11-15] "the thumbnail images are kinda too cartoon-like, i want them slightly more serious. can we try an update?"
- [Jaewan, 11-15] "yes"
- [Jaewan, 11-15] "no.. i don't want realistic, i think it might be just from that the colors are too bright?"
- [Jaewan, 11-15] "give me those in full screens"
- [Jaewan, 11-15] "i'll go with 2. give me the flows again"
- [Jaewan, 11-15] "yes, and then commit, push, and open the PR"
- [Jaewan, 11-15] "update the issue"
- [Jaewan, 11-15] "do that. and also make this show in the subject phone too"
- [Jaewan, 11-15] "yes"
- [Jaewan, 11-15] "give me the screenshots for the subject phone too. and i merged #227 to dev"
- [Jaewan, 11-15] "yes"
- [Jaewan, 11-15] "remove step 2 from how to verify. and there are two reviews now. i think we need both. fix them and give me the screenshots. especially, what does the tutorial look like now?"
- [Jaewan, 11-15] "wait why is the tutorial background so dark"
- [Jaewan, 11-15] "can you give me the real screenshot on the emulator?"
- [Jaewan, 11-15] "okay. not i think that in the tutorial we need to highlight the entire 'choose from the gallery' tab and 'my library' tab. if there's no space to write the description text, we may just write it over those area and not use arrows"
- [Jaewan, 11-15] "ah no.. i think we should just use arrows like before"
- [Jaewan, 11-15] "what if we put the text half-overlapping the tab? right-above area for choose from the gallery, and right-below area for my library"
- [Jaewan, 11-15] "something better than a black backing? any ideas?"
- [Jaewan, 11-15] "i'd go with 1. and also let's remove the subtitles for the situation choices. now give me all flow screenshots and the tutorial, using the emulator, both for korean and english"
- [Jaewan, 11-15] "they don't have the history or saved guides, so can you apply these too"
- [Jaewan, 11-15] "and if i saw correctly, the white ticked borders over the 'choose from the gallery' and 'my library' tabs are not centered vertically, each have slight space above and slight space below. can we fix this?"
- [Jaewan, 11-15] "yes, and resolve the comments"
- [Jaewan, 11-15] "the '상황별 배경 사진 안내' part screenshots are left out. can you add these to the flow pngs?"
- [Jaewan, 11-15] "cut them to only the hint part"
- [Jaewan, 11-15] "there's a new review"
- [Jaewan, 11-15] "yes"
- [Jaewan, 11-15] "after we click 'take a scene photo for poses', we just transition to the camera screen, and this is kinda unclear to the user what they should do there (it sometimes feels like i've just exited to the initial camera screen) and how the pose suggestion works. we do have a toast that gives the scene photo hint, but this is too small and doesn't seem enough. any ideas to fix this?"
- [Jaewan, 11-15] "can you show me these as example screens"
- [Jaewan, 11-15] "1+2+3 is okay, but with 2, i think we don't need the '배경 찍기' below the shutter. only the new color. maybe just color it entirely yellow (or light yellow)? and moving the toast to the instruction card is good but it's kinda too fancy. make it lighter"
- [Jaewan, 11-15] "how do the steps bar show in the subsequent screens? are they in the same position?"
- [Jaewan, 11-15] "i'll go with version 1, but the text above them in the steps (배경 사진 촬영, 이 사진을 사용할까요?, 포즈 생성, 포즈 생성 중, 포즈 선택) and also the buttons on the right don't have same font size, how would it look like if we all make the sizes identical"
- [Jaewan, 11-15] "yes this is good. and can you also left align the instruction text below / and one more test, what if we replace the step bar with a continuous bar? can you give me this version too"
- [Jaewan, 11-15] "with the continuous bar the color is kinda too vivid"
- [Jaewan, 11-15] "B, but can you give me the comparison png again with this? and the instruction text, can you make it look more like it's an additional layer"
- [Jaewan, 11-15] "make the comparison again with 3 (dark panel below), but with no border around the panel"
- [Jaewan, 11-15] "i'll chose continuous bar only. can we just remove '랜드마크' (the situation type) and only leave the instructions here? and add some icon in the left? how would this look like"
- [Jaewan, 11-15] "i'll choose the light bulb. implement this fully, and give me the emulator screenshots again"
- [Jaewan, 11-15] "in the screenshots, put the 'scene photo hint by situation' part 1*6"
- [Jaewan, 11-15] "commit and push this"
- [Jaewan, 11-15] "one more question, since we changed the font size for 'take the scene photo' header text, the header height for the normal camera and the scene photo camera. should we just match the normal camera's header height to this, or should we just add some kind of animation before entering the scene photo camera screen to indicate that we are starting some process?"
- [Jaewan, 11-15] "yes, build the animation and show me frames"
- [Jaewan, 11-15] "commit and push this"
- [Jaewan, 11-15] "close the emulator"

## Better pose tips ([#213])

- [Dongje, 11-16] "The pose tip now gives only a simple tip on where the photographer stands, the zoom, the camera's angle, and its height. We need a finer and more exact guide on how to take the photo like the guide, considering the background and the person. Tell me the prompt and the way to build it that you have in mind." (A proposal: the model only describes each photo, the server derives the distance, the height, and the tilt and writes the words, and the tip becomes a card of up to three lines.)
- [Dongje, 11-16] "What does the threshold mean?" (The share of tips that the judge must find right, 80 % in Iteration 3, where the result was exactly 240 of 300.)
- [Dongje, 11-16] "Let me see the drafts of the card banner first." (Drafts with text labels and with icons, in both languages.)
- [Dongje, 11-16] "Don't include extra explanation such as 'the legs look longer'." (The reason clause is gone from the prompt, the words, and the documents.)
- [Dongje, 11-16] "Go with the text labels, and telling the person is fine. Add, on this branch, that the other phone can also open the tip with a tap when they want it. Put the wording together and pass the situation on the server, in the most fitting expressions. Run the evaluation as planned, with a pass line of 90 %; go on first, and I will do a personal check afterwards." (Built: the card on Camera, `guide.tip` and the tip button on Subject view, `situation` on `POST /pose-tips`, the words in `[tip_words]`. Three runs of 100 sets were generated. Reading 44 of the first run's pairs by eye showed the single request naming a wrong distance in 18, because the model copied the scene's shot and camera to the candidate; the agent then asked about each photo in a request of its own, added the head's box as a second measure, and named a distance only when both agree, which left 2 wrong. A larger model was tried and was no better.)
- [Dongje, 11-16] "Is this much of a tip enough, so that the photo is taken well?" (Counted over the 300 tips: 142 of 298 asked for no change, 226 had the phone level at the chest or the eyes, and "a little closer" stood for anything from a small step to a full body turned into a half body. Two changes proposed: name the shot to move to, and say only what changes.)
- [Dongje, 11-17] "Go ahead with 1 and 2. And could the tip say more about how to take the photo well?" (Built: "Closer, a knees-up shot", no same distance, no usual hold, and "Stay where you are" alone when nothing changes. A trial of the model naming what spoils a scene photo, on the 50 scenes, flagged 18, mostly choices of style, so it was not built.)
- [Dongje, 11-17] "Put 1 into this PR. We will not do 2 and 3, now or later." (Of three ways to help take the photo well: a close shot is reached with the zoom, built; live matching against the guide on the phone and a rule of thumb per shot, dropped for good.)
- [Dongje, 11-17] "Can't you do the judging? An API key will be hard to get." (The tips judged by Claude Opus as Claude Code subagents, 25 tips each, with exactly what the API judge is sent. The first run: 89 of 100 matched; 5 of the 11 failures named a shot the candidate did not show.)
- [Dongje, 11-17] "Would it help to make an HTML so our teammates can do it too?" (`sample --kind tip` and `agree --kind tip`: a page of 40 tips to label, and the labels compared with the judge.)
- [Dongje, 11-17] "Go with 1 and judge all three runs. Do the teammates have to label three times too?" (The move no longer names the shot. The labels check the judge, so one sample is enough.)
- [Dongje, 11-17] "A teammate asked whether the place is really needed for the pose. Can the tip say more about the camera's framing here?" (Three drafts without the place: more lines under the labels, a how-to card, and plain sentences.)
- [Dongje, 11-17] "Giving it label by label is not essential either. It should really teach how to take the photo like that."
- [Dongje, 11-17] "Go with C, and can it include finer points such as the camera's brightness, what a professional photographer would say, even if it gets longer? It can be opened and closed anyway." (The tip as sentences. The model also reports the room above the head, a backlit face, a leaning level line, and a pose in motion.)
- [Dongje, 11-17] "What comes out if we give only the two photos and a plain prompt, without the labels we want? Try three photos." (The text read well, but its distances were wrong or vague, and it told the person how to pose, so the server keeps writing every word.)
- [Dongje, 11-17] "In the examples it keeps talking about the level line even when the line is already level. Is that right?" (It was said for every photo with such a line; now only when the line leans in the scene photo.)
- [Dongje, 11-17] "Say each thing only when it differs, and do not talk about the background at all, such as 'frame it so the bridge shows behind'. Checking for backlight and advising on it is fine." (The final tip: the move, the phone, the side, the room above the head, the brightness, upright, and several photos, each only when it applies; no place and no background.)
- [Dongje, 11-17] "For the tips' evaluation, a personal check by the four of us seems better than checking with the API or Claude Code. Make an HTML and we will check whether they are fine." (`label-tips.html`: the 100 tips of one run, each sentence answered pass or fail, and whether the tip leaves something out; `agree --kind tip` counts the answers. The judge no longer checks the tips.)
- [Dongje, 11-18] "Put the guide's outline sentence into the tips without a distance sentence too. And I said not to give a tip for what is already alike, but it feels as if too many tips are gone: just check whether I said it too strictly." (The outline sentence whenever the distance is unsure. The check, on 100 pairs with what the model saw of each photo: the phone is said in 25 tips; of the 75 without it, 55 are held as in the scene, and 20 differ from the scene but are the usual hold. The distance's cutoff hides about 4 moves. Nothing was changed.)
- [Dongje, 11-18] "Go with the second one." (Of four rules for the phone's sentence: the usual hold is now said too when the scene was taken two heights or more from it, or with another tilt; 33 of 100 tips have the sentence, from 25. The English word for no tilt became "not tilted", since "at eye level, level" could now be said.)
- [Dongje, 11-18] "The brightness sentence mentions the face's brightness now. I think it should not." ("The light is behind the person: raise the brightness.", in Korean "역광이니 밝기를 올리세요.")
- [Dongje, 11-18] "With that in, list every sentence that can come out now and show it as a PDF or an image." (A sheet of all the sentences in both languages, with when each is said and how often it came in the 100 tips.)
- [Dongje, 11-18] "Let's drop the advice to press the shutter several times for a pose in motion." (The sentence, its words, and the question to the model are gone; the candidate's request has the scene's schema.)
- [Dongje, 11-18] "Apart from the tip, is backlight the only case where the brightness should be raised when taking a photo? For backlight, a guide that another spot would also do seems fine. When should a tip to raise the brightness come, in general?" (When the person comes out darker than what is behind them: backlight, a very bright background, or a person in shade; not for a scene that is dark as a whole.)
- [Dongje, 11-18] "Don't ask the model whether the main light is behind the person and the face darker than the background. Isn't it better to ask whether the brightness needs changing, compared with the photo now? Whether backlight will spoil the photo could be taken too, for a guide of its own. 'The person darker than the background' has too little to do with brightness." (Measured and tried: the candidate is never over 0.3 stops brighter than the scene in 100 pairs. Asked about 50 scenes, the model found none that needed a change, and it also missed 9 of 12 copies made clearly darker or brighter; the 3 scenes it said backlight spoils looked fine.)
- [Dongje, 11-18] "Take the brightness sentence out." (The sentence, its words, and the backlight question to the model are gone.)
- [Dongje, 11-18] "Let the distance and the phone's height and tilt always come. When the distance is like the photo the pose was made from, say to keep the distance as before; and give the phone's height and tilt every time, as fits the photo, not only when they differ. Then run it once more and make the image like this one." (Every tip now starts with the distance, "Keep the same distance." when it is the same, and the phone's sentence. Run 1's 100 tips were asked again: 70 have two sentences, 28 three, and 2 four.)
- [Dongje, 11-18] "Can the photos of the check page be cut to half? It will take too long." (50 of the run's 100 tips, drawn with a fixed seed, for the four of us.)
- [Dongje, 11-18] "Check the other 50 through Claude Code." (Two subagents on Claude Opus 5.5 read 25 tips each, with what the page shows one of us: 49 of 50 match their pose, and 116 of 117 sentences are right. They passed, by the rules' letter, 4 tips that say "a little closer" for a person about twice as big.)

[#199]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/199
[#200]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/200
[#203]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/203
[#209]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/209
[#210]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/210
[#211]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/211
[#213]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/213
