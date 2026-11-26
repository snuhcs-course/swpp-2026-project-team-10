Back to [[AI Collaboration Report – Iteration 5|AI-Collaboration-Report-–-Iteration-5]]

## Privacy in the app: what Sign up collects, the privacy policy, and deleting an account ([#287])

- [Jaewan, 11-21] "Yes we should fix that, and now i need to do issue #266 overall. we need to notice the user before sign up about what pix collects, and get the user's agreement on that. and we should display the full privacy policy in the app, which we can access through the settings screen. the privacy policy also needs an english translation too. Also, for registering to one store we need a public url for the privacy policy, so i also want a static page on our server which displays the privacy policy. finally we need a 'delete account' option. can you do all of these, in a new branch?"
- [Jaewan, 11-21] "commit, push, and open the PR when done"
- [Jaewan, 11-21] "is the wireframe agent done yet?"
- [Jaewan, 11-21] "why is this taking so long"
- [Jaewan, 11-21] "how long will this take more"
- [Jaewan, 11-21] "no let's wait"
- [Jaewan, 11-21] "in the sign up screen (not google), i think it's better to show the 'what your pix account keeps' first, and then press 'agree' then show the pix id / name / password part in a separate page. and i think there's a bug where if the phone is short and the keyboard hides the input window, we can't scroll so we can't see what we've inputed. fix this too. and the privacy policy currently looks too much like a markdown doc. can you make this look more professional?"
- [Jaewan, 11-21] "commit and push when done, and update the PR description"
- [Jaewan, 11-21] "can you open a galaxy s22 emulator for me? i want to try this"
- [Jaewan, 11-21] "[Image #1] this looks terribly designed. i can't separate '항목/목적/보유기간/받는곳' with the text below. maybe the colors don't differ a lot. also the '개인정보 처리방침 보기' text is placed very awkwardly. and the text above and below this doesn't seem like a professional agreement page. maybe we should have checkboxes for each option and only activate the next button when all are checked. anyway make this better. also the privacy policy page looks too claude-design-like. make it minimal and professional. close the emulator and fix this"
- [Jaewan, 11-21] "can you reopen the emulator?"
- [Jaewan, 11-21] "from now on don't start drawing the wireframe until i confirm by the emulator. only relaunch the app with the updated version and i'll check. the signup agreement page seems good, but i don't think we need the 'Pix ID, 이름, 비밀번는 다음 화면에서 정해요' part. just the title is enough. same for the google signup part. the title is enough fix this first"
- [Jaewan, 11-21] "good. and where is the privacy policy static page?"
- [Jaewan, 11-21] "okay. few parts to fix 1. do we need to write '(com.lastpenguin.pix)'? just say Pix 앱 2. let's remove 'Pix 서버의 /privacy 페이지에서 볼 수 있습니다.'. i want people to just check through the app. 3. in the app screen, unfolding the table makes it too long, so can we just display it as a table? or, at least make the '수집 시점' '목적' parts as 2x2"
- [Jaewan, 11-21] "[Image #2] also not sure but doesn't '동의 안함' part look to close to the right?"
- [Jaewan, 11-21] "also not sure if this is a good idea but [Image #3] why don't we also make the '개인정보' part into one panel?"
- [Jaewan, 11-21] "and in the privacy policy page, can we make the logo in the header slightly larger? also just write '개인정보 처리방침' instead of 'Pix 개인정보 처리방침'"
- [Jaewan, 11-21] "good. close the emulator. now fix the wireframes and anything else needed, and push and update the PR description"
- [Jaewan, 11-21] "there are changes in dev, so merge and resolve conflicts push it/fix the PR description. also, is the privacy policy page always in black background? do you think we should serve this also in light mode?"
- [Jaewan, 11-21] "can i deploy this branch on the main server"
- [Jaewan, 11-21] "what is IT-40?"
- [Jaewan, 11-21] "why is deploying this branch taking so long"
- [Jaewan, 11-21] "i think we can make the overall font size in the privacy policy page smaller. also for tables, just allow scrolling horizontally, and let's not try to squeeze this into the phone width"
- [Jaewan, 11-21] "i've checked all, good"

## The privacy policy for the ONE store listing ([#264])

- [Sungmin, 11-21] "Wait, before doing this, wouldn't it be better to submit that privacy policy URL? What do you think? Should we make one? See this link: https://onestore-dev.gitbook.io/dev/help/faq/legal" (With a screenshot of the console's field.)
- [Sungmin, 11-21] "Making a wiki page seems best. Use my email for questions, and name the operator only as the Last Penguin team."
- [Sungmin, 11-21] "But would it be better to put this document on my personal GitHub, or in our repo...?"
- [Sungmin, 11-21] "OK, commit and open the PR." ([#264])

[#264]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/264
[#287]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/287
