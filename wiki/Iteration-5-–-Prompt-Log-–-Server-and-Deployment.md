Back to [[AI Collaboration Report – Iteration 5|AI-Collaboration-Report-–-Iteration-5]]

## Pix on ONE store, and release builds on the AWS servers over HTTPS ([#261], [#262])

- [Sungmin, 11-20] "Nice, that looks good. Now I'm going to register Pix on ONE store, and I have to fill this in. What's our package name?" (This prompt and the next eight came with screenshots of the console's forms.)
- [Sungmin, 11-20] "What should I check here?"
- [Sungmin, 11-20] "Tell me what to check in each of these."
- [Sungmin, 11-20] "Now tell me how to check each one here."
- [Sungmin, 11-20] "Should I answer yes or no here?"
- [Sungmin, 11-20] "How do I do this?"
- [Sungmin, 11-20] "What does it want me to do here?"
- [Sungmin, 11-20] "How do I fill this in?"
- [Sungmin, 11-20] "How do I fill in all of these?"
- [Sungmin, 11-20] "But 'a camera where the person in the photo matches the pose while watching the framing on their own phone' says too little about the pose suggestions, doesn't it? Anyone would think it's just a screen-sharing camera app."
- [Sungmin, 11-20] "Where did you put the graphic resources?"
- [Sungmin, 11-20] "Ah, I think it's better if no real person's image appears..."
- [Sungmin, 11-20] "Hmm, this wireframe looks a bit cheap... Can't you do it with a real person's image somehow?"
- [Sungmin, 11-20] "No, I mean you make it: take a real image and make it in that wireframe's form."
- [Sungmin, 11-20] "Could you use a photo of a standing woman instead of that one?"
- [Sungmin, 11-20] "Can't you use this photo of a woman with her arms spread?" (With the photo.)
- [Sungmin, 11-20] "Wait, no, the previous photo seems better."
- [Sungmin, 11-20] "Hey, it says they have to be like this: [pasted: at most 1300 px on a side, 16:9 or 9:16 recommended, JPG or PNG, at most 1 MB, 2 to 8 images]"
- [Sungmin, 11-20] "I have to upload a new binary, and it says to upload an AAB file. How do I do that?" (With a screenshot.)
- [Sungmin, 11-21] "I want to switch the release server to AWS. How do I do it?"
- [Sungmin, 11-21] "No need to make an issue. Commit and open the PR." ([#261])
- [Sungmin, 11-21] "Jaewan sent me this. Is what we did right?" (This prompt and the next three came with screenshots of Jaewan's messages.)
- [Sungmin, 11-21] "So can this PR set up HTTPS?"
- [Sungmin, 11-21] "Jaewan said this. What exactly does he mean?"
- [Sungmin, 11-21] "To be precise, he said this."
- [Sungmin, 11-21] "He sent this too."
- [Sungmin, 11-21] "Jaewan added work to the PR I opened. Take a look. He said: [pasted: merge into dev, then dev into main once more; the HTTPS addresses of the main and dev servers; release builds use main and debug builds dev, so a `local.properties` with the old HTTP address should drop it or use the HTTPS one; pose generation is limited to 5 an hour on main and 50 on dev]"
- [Sungmin, 11-21] "So I merge #261 first, right?"
- [Sungmin, 11-21] "Merged. Open the dev → main PR too." ([#262])
- [Sungmin, 11-21] "I merged dev → main."
- [Sungmin, 11-21] "What exactly does it mean by signing?"
- [Sungmin, 11-21] "I signed it. Check that I did it right."
- [Sungmin, 11-21] "I have to fill this in. How should I fill it?" (With a screenshot.)
- [Sungmin, 11-21] "[pasted, the agent's own note: Google sign-in fails in the store build until the SHA-1 of the signing key that ONE store manages is in the Google Cloud project, and it could not find where the console shows it] How do I do this?"
- [Sungmin, 11-21] "The signing key doesn't show up." (With a screenshot.)
- [Sungmin, 11-22] "There are new merges on dev and main. Pull them."
- [Sungmin, 11-22] "Privacy is built now. Should I send Pix to ONE store's review again as it is?"
- [Sungmin, 11-22] "Build the release AAB first."
- [Sungmin, 11-22] "I signed it. Check that I did it right."
- [Sungmin, 11-22] "Can you write the notes for the reviewer again, from start to end?"
- [Sungmin, 11-22] "Only 300 characters fit. Shorten it, and remove item 7."
- [Sungmin, 11-22] "Also, I downloaded two files into `downloads/` that look like the signing keys. Check whether they're right."
- [Sungmin, 11-22] "[pasted: the console's signing key and upload key, each with its MD5, SHA-1, and SHA-256 certificate fingerprints]"
- [Sungmin, 11-22] "Should it be the signing key, not the upload key?"

[#261]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/261
[#262]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/262
