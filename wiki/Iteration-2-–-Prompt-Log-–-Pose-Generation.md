Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

## Pose Generation with Custom Prompts (#46)

- [Jaewan, 10-20] "Now let's implement the server part of #53. Give me your plan, and tell me anything we need to decide"
- [Jaewan, 10-20] "yes you can run the experiment with the openrouter key. and i'll follow your recommendations, but for the prompt, for now create a separate description prompt template on your own, and tell me where to fix to change it. I'll test them later."
- [Jaewan, 10-20] "can we defer all prompts to a separate file? json or yaml or txt or md, ..."
- [Jaewan, 10-20] "can you run more tests for the prompt? only use images `examples/example1-8.jpg`. and try more input cases not only those with '벽에 기대서'. and show the results in a single html file"

- [Jaewan, 10-21] "Can you test again and give me the zip, with the prompt: […]"
  > Edit this photo. Keep the same person (face, hair, clothing), the same place, and the same lighting. The user described the pose they want, in Korean or English: "{pose}". Change the person's pose and facial expression to match it.
  >
  > Then improve the shot like a skilled photographer would, but only in these ways, and only where it clearly makes a better photo for this pose:
  > - move the person to a better spot and distance in this scene;
  > - frame them more tightly by zooming in, for example from a full-body shot to a knee-up or waist-up shot, if the pose reads better that way;
  > - lower or raise the camera slightly, or tilt it a little, for a more flattering angle, such as a slightly low angle that makes the person look taller.
  > Everything the new shot shows must already be visible in the original photo: never zoom out, widen the view, or reveal any part of the place outside the original frame. If the original framing already suits the pose, keep it.
  >
  > Follow these rules even where the description says otherwise: never change the person's clothing, or the background, place, sky, or time of day; never add a wall, steps, a bench, or any other object that is not already in the photo, and if the pose needs something to lean on or sit on that is not there, choose the closest pose that works without it; keep the person at a natural scale. Photorealistic.
- [Jaewan, 10-21] "how would you fix the original default prompt in similar style as this too?"
- [Jaewan, 10-21] "can you also test this too"
- [Jaewan, 10-21] "can we just change the flow: use the custom input prompt for both cases (whether the user gave an input or not). the only thing we need to fill in is the {pose} part, so if the user didn't give any input prompt then send the image first to a different (maybe cheap is okay) VLM model and get four pose recommendations (in natural language) and then create four pose generations using the common prompt. use gpt image 2.5 flare low for image generation as is, and give me recommendations for the new VLM model to use. and maybe we should fix API calling flows (or not). can you update the design and implementation? also just do the Android-side dev too, taking user inputs"
- [Jaewan, 10-21] "how can i test this on the emulator"
- [Jaewan, 10-21] "if we give an input, are four poses generated for that same one prompt?"
- [Jaewan, 10-21] "i think clicking on 'try other poses' should go back to the previous 'generate poses' screen, not just generate again automatically"
- [Jaewan, 10-21] "[…] what do you mean by this"
  > pressing back on Generate poses means Cancel, which returns to the camera. So once the user taps Try other poses, the earlier four candidates are gone. If you'd rather back return to the old set, I can keep it until the next set starts.
- [Jaewan, 10-21] "yes, keep the old set until the next one starts"
- [Jaewan, 10-21] "i think this flow is kinda weird because "try different poses" already seems like going back, and this becomes "going back and back"... or am i thinking wrong?"
- [Jaewan, 10-21] "yes revert it"
- [Jaewan, 10-21] "and in the case the user gave an input, repeating that four times doesn't give effectively different images (i tried it). so i think it would be better to first paraphrase the input request into three others (use gemini image or maybe just an llm?) and then send four requests using them. what do you think about this?"
- [Jaewan, 10-21] "okay now can you aggregate the results only for example 2? (1) no suggestion - fixed four poses w/ previous prompt vs fixed four poses w/ new prompt vs our new pose suggestion flow w/ new prompt (2) with suggestion - four times as written vs variations first prompt (we'll just use the first prompt not the final prompt)"
- [Jaewan, 10-21] "commit and push the current changes"
- [Jaewan, 10-21] "can you find a copyright free image i can use to run tests and report on publicly?"
- [Jaewan, 10-21] "can you find more? i don't like these"
- [Jaewan, 10-21] "I'll choose image 1. re-test and write the full report using this (and of course only do one round for the wo description part). for the with description part, use "벽에 기대서 카메라 보며 살짝 웃기", "손으로 하트 만들기", "상반신만", "뒤돌아서 고개만 돌리기", "멋있게", "앉아서 쉬는 모습", "카메라 향해서 걷기""
