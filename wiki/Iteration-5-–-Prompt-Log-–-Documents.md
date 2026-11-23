Back to [[AI Collaboration Report – Iteration 5|AI-Collaboration-Report-–-Iteration-5]]

## Iteration 5 kickoff: the issues and the documents ([#265], [#286])

- [Sungmin, 11-21] "Now I'm the PM for Iteration 5 and I want to sort out the issues. […] This is what we talked about at today's kickoff meeting. Can you organize it in detail? If something is unclear or you don't understand it, you can ask me. Iteration 5 is 11/22 to 12/4, so with that in mind, first think concretely about how you will draft the issues."
- [Sungmin, 11-21] "1. Only the wording changes. 2. It means the subject can press the AI generation buttons (automatic, upper body, full body, landmark, and so on) while sharing; no situation is restricted. 3. Right. But whoever takes it should first read the code and find out in what situation it happens. 4. The cutout has to show on the subject's phone too. 5. I'm doing all of those. But what does design patterns mean? 6. Do we really have to do this? Can't we skip it?"
- [Sungmin, 11-21] "7. Thirty poses in the list was only an example; how to build it is up to whoever takes the issue. The point is that users often don't know what to do when they enter a pose, so they should be able to go on by choosing a few options, not only by typing. Also, make an issue for the privacy work (deleting an account, what sign-up collects, and so on) as P2. It has to be done as soon as possible, so make it P2, due 11/22."
- [Sungmin, 11-21] "Keep 11/22 and just leave the owner empty. Assign the design patterns to me, and the integration tests and the integration too. Now write the issues."
- [Sungmin, 11-21] "Close the Iteration 4 milestone and let's start P1. Make a plan first."
- [Sungmin, 11-21] "1. Short of time, and judged less important; left out at the kickoff of 11/21. 2. Make it, and put the prompts in English. 3. Neither is done. 4. Just leave it empty."
- [Sungmin, 11-21] "OK, commit and open the PR."

## UAT cycle 2: the results and the follow-up issues ([#267], [#299])

- [Sungmin, 11-23] "We put the UAT sheet that the testers filled in here, in uat_sheet_cycle2_final.xlsx. Now we will sort out the feedback written in it, the way issue #267 says. First write a plan for how you will do it. There are several pieces of feedback, and the team has already decided how to handle each one. So sort out the feedback first, then ask me how each one will be handled. Start by summarizing the results in the sheet itself."
- [Sungmin, 11-23] "1. That rejected looks like a wrong mark; I changed it to accepted. 2. It needs to stand out a bit more. 3. Fix it as the feedback says. 4. Keep only the person button and remove the camera button. While the person button is lit, the guide can be edited; while it is off, it cannot. 5. Fix it as the feedback says. 6. This is feedback on the UAT, so no code change is needed. 7. Raise it to 30 seconds and add a try-again button. 8. Remove the button altogether. Instead, after Back, the choices (AI generation, choose from the gallery) stay up. Before, Back went out to the main camera screen."
- [Sungmin, 11-23] "9. This step is needed. The cut animation for an Instagram screenshot must stay. Rather than removing the step, we decided to add a similar animation for a plain image too: the original image shrinks into the thumbnail, and the outline is drawn. 10. We won't keep the scene photo's confirm step. Instead, as in 9, the photo shrinks naturally after the shot, and the screen moves on to the one where the prompt is typed. On that screen, a tap on the small image enlarges it for a closer look."
- [Sungmin, 11-23] "11. Keep all pose guides as history, and let the user put only the ones they like into favorites in the library. Favorites is a new tab in the library. As in a real gallery, a long press on an image selects it, and select all or select some can delete them. Also add a three-dot button at the top right, with select, delete all, and so on. 12. Agreed; check whether Impeccable can be applied. 13. Skip."
- [Sungmin, 11-23] "1. I see. Then don't add the button. 2. Without a person, should it go to a screen that says there is no person, or should the pose input screen say so? Which is better? 3. Yes, keep all four candidates in the history. And what is this limit of 100? I said nothing about it. 4. Think about what would be good here. 5. I changed it in the real sheet, so don't write that in 4.4. 6. Make it an issue that checks it and actually applies it."
- [Sungmin, 11-23] "The TA had the S22, and the other tester used my phone, an S25 Ultra; that tester is from Team 8. When airplane mode did not turn Wi-Fi off, we just turned Wi-Fi off by hand. The UAT build is the latest dev commit. It took about 40 minutes."
- [Sungmin, 11-23] "2. OK, do it that way. 4. Do that, and also: on the subject's phone the *Leave* button is black now; make it red like the photographer's *End session* button. If there is a way to make it stand out even more, you may add it. 3. Just remove the limit of 100 for now."
- [Sungmin, 11-23] "Create them without owners. Create the seven issues, edit #271's body, and go on to the wiki. Title the issues like Iter5-A1."
- [Sungmin, 11-23] "OK, commit this and open the PR."
- [Sungmin, 11-23] "Add the prompt log too, in English."

[#265]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/265
[#267]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/267
[#286]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/286
[#299]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/299
