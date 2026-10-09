Back to [[AI Collaboration Report – Iteration 1|AI-Collaboration-Report-–-Iteration-1]]

`[…]` marks text pasted into the prompt. It is quoted under the prompt.

## Signaling server (#4), with review and network questions

- [Jaewan, 10-01] "Read AGENTS.md for the project's context and instructions. For convenience, I have downloaded the design documentation and requirements and specifications to the ./wiki directory. You can refer to them for detailed information on the project specifications and design considerations. I need you to create a server app at ./server that meets the requirements outlined in the documentation. Only implement requirements for iteration 1; but don't create the APIs for image generation yet and only implement the signaling hub part. Use uv for package management with pyproject.toml and uv.lock files. Also use ruff for linting and formatting. I have a preferred ruff.toml configuration file at the repo root. Put this configuration file in the ./server directory as well.
Use FastAPI for the server framework. We would have to use sqlalchemy and alembic for database management in the next iterations, but since we don't need database management for iteration 1, you can skip that for now."
- [Jaewan, 10-01] "i find some places that mark this app as 'Pix signaling hub' or 'Pix signaling server' as if the server is only for signaling, but this server will also serve its role for other APIs like friend invitations, pose generation, etc. so can you fix these and just write neutrally like 'Pix server' or so"
- [Jaewan, 10-01] "I don't think we need load_signaling.py as a separate script and mention it in README.md. It's just a temporary test script for now. I think I can just mention it in the PR explanation for people who want to test on their own."
- [Jaewan, 10-02] "I'm developing the server-side signaling part for iteration 1. Someone gave me a review: […] What does this mean?"
  > On the documented Android test-phone path, this ws:// endpoint is blocked by Android's cleartext policy: the app targets SDK 36, while android/app/src/main/AndroidManifest.xml has neither usesCleartextTraffic nor a network-security configuration, and a repo-wide search finds no debug-specific exception. Thus the claimed exception does not exist and an OkHttp signaling client cannot connect to this server as instructed; add a debug-only cleartext configuration (or serve wss://) before relying on this workflow.
- [Jaewan, 10-02] "Oh, the android part isn't implemented yet, so we'll wait for this. Not my problem yet right?"
- [Jaewan, 10-02] "In the later iterations, how should this be changed? like if we deploy the server on AWS EC2 or so"
- [Jaewan, 10-02] "oh we need to buy a domain name?"
- [Jaewan, 10-05] "How fast should our server instance's network be?"
- [Jaewan, 10-05] "if phones are not on the same wifi, then the videos are sent through internet? will this be fast enough?"
- [Jaewan, 10-05] "how can i test these on phones"
- [Jaewan, 10-05] "finding 1 -> pinch on B or A?"
- [Jaewan, 10-05] "so the problem here is, after B joins with the code and start's seeing A's streamed camera view on its screen, A sends the zoom range but before it arrives we pinch on B so the zoome range is never set?"
- [Jaewan, 10-05] "oh so the problem is join screen -> subject screen transition may take long and if the zoom range arrives in between that it is ignored?"
- [Jaewan, 10-05] "then why remove zoomStops and replace with minZoom and maxZoom"
- [Jaewan, 10-07] "Iteration 1 uses local server and same wifi, and there wasn't much delay/latency in live view. If we use a deployed server and the two phones connect through the internet (whether on same wifi or not), will this become slower?"
- [Jaewan, 10-07] "what if with no internet (the QR version) where the two phones connect through one's localonlyhotspot"
- [Jaewan, 10-07] "then should we always use this?"

## Pose generation server (#18)

- [Jaewan, 10-03] "I have to choose which image edit API to use for this project. Can you recommend which to use? Also, write a script that I can use to test these APIs (independent to the current codebase) in `server/scripts/`. Use openrouter. I have $OPENROUTER_API_KEY configurated in this shell env."
- [Jaewan, 10-03] "are there any python packages needed to be downloaded to run this script?"
- [Jaewan, 10-03] "is this better, or should we add pillow to pyproject.toml in a new group?"
- [Jaewan, 10-03] "for the tests you ran, where did you get the initial images from?"
- [Jaewan, 10-03] "For the default prompt, what if we allow changing the person's placing in the image (while keeping the background/angle/etc unchanged)? The person in the input image may be too far or so, and if we fix this for the user it may be good. Also I put some example images in `server/scripts/out/examples/`. Use them if you want."
- [Jaewan, 10-04] "Changed the OPENROUTER_API_KEY in `~/.zshrc`, maybe needs reload. And regarding placement, of course we can give by prompt that 'the person is too far', but I wanted to give the model freedom in changing placing, not by letting it only chagne the pose. Can we test this? Use example images 7, 8, and your far-synthetic image in the `examples` folder. I deleted the previous placement test output folders."
- [Jaewan, 10-04] "Can you make the 'judge' prompt the default'? and using the judge prompt for all four pose types and images `server/scripts/out/examples/example{1-8}.jpg`, can you test the four models (gemini-3.1-flash-image, gemini-3.1-flash-lite-image, bytedance, gpt-image-2.5-flare@low)?"
- [Jaewan, 10-04] "in real usage can we send four generation API requests at once? will we not get rate limited?"
- [Jaewan, 10-04] "Okay. Can you explain how I can use the api comparing script on my own?"
- [Jaewan, 10-04] "Can you add these to the server README? but not like 'put the key in ~/.zshrc` which is specific to my case"
- [Jaewan, 10-04] "Okay, now can we implement the REST APIs for pose generation? It's 2.5.3 and 2.6.3 of the design document. Make sure to use the 'judge' prompt instead of the previous one. Use the bytedance model for now, but make it configurable since we may change it later. Also use openrouter."
- [Jaewan, 10-04] "Is canecllation a specified feature in the design document? And where should I update the current design document?"
- [Jaewan, 10-04] "Is it better to resize the scene photo at the client side? or should we do it? and also on aborting the openrouter call, how can we know when the phone drops the request and how can we abort a running openrouter generation?"
- [Jaewan, 10-04] "Add the abort part. Regarding this, also add to the README what we should take care of when we move to a public host"
- [Jaewan, 10-04] "write the PR message for me. This was my previous PR (https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/17)"
- [Jaewan, 10-04] "for the two deviations from the issue, those are the parts that the design document and R&S should be updated, right?"
- [Jaewan, 10-04] "okay then summarize once more what i should change in the design document and R&S"
- [Jaewan, 10-04] "save these as a md file. and what is the `ImageEditProvider` interface that was previously required? why do we not use this now?"
- [Jaewan, 10-04] "Got two reviews: […]"
  > 1. `server/src/pix_server/poses/router.py` line 48: Bound request bodies before multipart parsing
  >
  > When a client uploads an image larger than 1 MiB, this bounded read happens only after FastAPI has parsed the entire multipart body into an UploadFile. Starlette documents that file parts are not constrained by max_part_size and are spooled to temporary storage, so an arbitrarily large upload can consume disk and write the scene photo there before check_scene rejects it. Enforce a whole-request limit before multipart parsing, while allowing enough overhead for a valid 1 MiB file; Starlette exposes application/route body limits for this purpose. [Starlette request-file documentation](https://github.com/Kludex/starlette/blob/main/docs/requests.md)
  >
  > 2. `server/src/pix_server/poses/generator.py` line 98: Keep upstream error bodies out of logs
  >
  > When OpenRouter returns a non-403 error whose message echoes any submitted input, this logs that upstream-controlled body content; it may include the prompt or the reference image's data URL, exposing the scene photo in server logs and contradicting the privacy handling already applied to 403 responses immediately above. Log only the status and a locally generated reason, not body["error"]["message"].
- [Jaewan, 10-04] "Commit and push this fix"
- [Jaewan, 10-04] "how should we fix the description sayinga photo over 1 MiB gets 400 INVALID_IMAGE"
- [Jaewan, 10-04] "okay. and now the server doesn't resize or fixes wrong image inputs, so the client has to do this, right? is this possible on the android phone? and is this all documented in the design document or is included in the wiki-updates you previously told me?"
- [Jaewan, 10-04] "Can you fix the model to gpt-image-2.5-flare@low and increase the default timeout to 30 seconds?"
- [Jaewan, 10-04] "commit and push this"

## Wiki workflow, documents, and wireframes

- [Jaewan, 10-04] "I want to manage the wiki in this repo too in a `wiki/` folder, and use github actions to update the real wiki on `wiki/` update. Can you do this?"
- [Jaewan, 10-04] "where can I check the repository's Actions token settings?"
- [Jaewan, 10-04] "commit and push this"
- [Jaewan, 10-04] "Can you update the design document and R&S according to the changes we discussed earlier?"
- [Jaewan, 10-04] "do the documents mention that image pre-processing (size, etc.) should be done in the client-side? also, do they specify the rationale for choosing GPT as the model? The main rationale is decent performance and mainly prioritizing speed compared to models with similar latency (gemini, bytedance, ...)"
- [Jaewan, 10-04] "ah sorry i meant cost not speed"
- [Jaewan, 10-04] "commit and push this"
- [Jaewan, 10-06] "The wireframe is maintained in `https://claude.ai/code/artifact/7cdee345-4637-4e89-9641-7e41811df6c6#page-dcb81aee8d83`. Can you update flow 2 as we discussed? follow the design style as is"
- [Jaewan, 10-06] "can you save that flow 2 part as a jpg? or should i just take a screenshot"
- [Jaewan, 10-06] "Put the new image in the repo. Are there any other places to fix regarding this wireframe change? (maybe we should remove the 'Camera, taking the scene photo' row of R&S 6.8)"
- [Jaewan, 10-06] "Can you also add the pose generation consent as implemented to the wireframe, and fix that row in 6.8?"
- [Jaewan, 10-06] "commit and push this"

## Pose generation flow (#7)

- [Jaewan, 10-05] "Now implement the client-side pose generation flow (issue #7). Since we changed the design principle (ex. preprocess image at client side before sending to server), make sure to follow these according to the new design document."
- [Jaewan, 10-05] "Tell me how I should test this on android studio & android device"
- [Jaewan, 10-05] "when clicking 'Guide -> Generate poses here', the app immediately captures the camera screen there and then automatically sends to the server API. we need steps in between here. After we click generate poses here, there should be some step where we show the camera screen to the user and make them press the shutter to shoot the scene image. Also after the image is taken, we should have something like "Use this image" -> yes (use it and generate) / no (shoot again)."
- [Jaewan, 10-05] "What do you recommend for the camera screen? spawn a new one and add zoom to this or build this into the camera screen?"
- [Jaewan, 10-05] "well first commit and push this"
- [Jaewan, 10-05] "and now let's make the fix. build the guide shoot into the Camera screen"
- [Jaewan, 10-05] "one more, can you remove the pose names showing under the generated images? just showing the images is enough I think"
- [Jaewan, 10-05] "I think we can remove the 'up to 30s' message at the bottom when generating poses. And what does 'your photo' and 'same person and place' stand for? are they placeholders that may change in other scenarios? And does the generation flow not support custom text inputs now?"
- [Jaewan, 10-05] "why does generation sometimes stop and just show three results not four? is this a timeout issue? If I want to increase my timeout temporarily then should I just set `PIX_POSE_UPSTREAM_TIMEOUT_SECONDS` in the .env?"
- [Jaewan, 10-05] "Can you stash the current changes and go back to the dev branch"
- [Jaewan, 10-05] "bring it back"
- [Jaewan, 10-05] "Commit and push the fixes"
- [Jaewan, 10-05] "Also write me a PR explanation (not toooo long). Also tell me if we need to update any of the wireframe images or etc. not fixed yet"
- [Jaewan, 10-05] "how should i edit the issue"
- [Jaewan, 10-05] "oh sorry and can we just add the up to 30s text again? but make it different something like 'generation may take up to 30s'"
- [Jaewan, 10-05] "[…] can you update this too"
  > R&S 6.8 "Screens still to be drawn" does not list the scene photo step. It needs one row, like the consent notice already has.
- [Jaewan, 10-05] "commit and push"
- [Jaewan, 10-06] "can you summarize how flow 2 wireframe should be fixed? and what the missing 'Camera, taking the scene photo' wireframe should illustrate?"
- [Jaewan, 10-06] "make this concise. I will write this into the PR comment"
- [Jaewan, 10-06] "can you draw the new flow part as mermaid"
- [Jaewan, 10-06] "PR #31 was just merged to dev, can you update this branch with content in the new dev (don't rebase, just merge) and solve conflicts?"
- [Jaewan, 10-06] "commit and push this"
