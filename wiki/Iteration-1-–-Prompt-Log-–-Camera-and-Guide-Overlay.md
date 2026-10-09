Back to [[AI Collaboration Report – Iteration 1|AI-Collaboration-Report-–-Iteration-1]]

Prompts were given in Korean; they are translated here. Claude Code dates are KST; the Codex sessions did not record dates. Git and branch questions are left out.

## Camera (P7, [#3]): ChatGPT Codex (GPT-6 Astra)

- [Joonhyung] (Attached the Design Documentation, R&S, and wireframe PDFs and the project schedule) "Based on the `dev` branch on GitHub, explain in detail what is implemented so far and what I need to implement, following the Project Schedule."
- [Joonhyung] "Then start implementing the P7 camera first."
- [Joonhyung] "Write the camera feature PR, referring to PR #17."
- [Joonhyung] "Does the current implementation meet every task that issue #3 asks for?"

## Camera zoom and composition guides (A2, [#26]): ChatGPT Codex (GPT-6 Astra)

- [Joonhyung] "Instead of 1×, 2×, 3× buttons, can it zoom in and out naturally with two fingers?"
- [Joonhyung] "Make a 3×3 grid of equal cells at the current ratio, put a horizon bar at the center of the middle cell, and show it in dark yellow when level. Refer to the Figma material."
- [Joonhyung] "I checked the existing implementation on a real phone. As `fix: low magnification pinch zoom`, support zooming out to 0.5× where possible."
- [Joonhyung] "Write the PR for what is implemented so far, in our format. I finished the real-device tests except for zooming below 1×."

## Guide overlay groundwork (#6): ChatGPT Codex (GPT-6 Astra)

- [Joonhyung] "Explain in detail what is implemented so far (`dev` branch), and explain issue #6, the scope I need to implement."
- [Joonhyung] "Then first complete the geometry."
- [Joonhyung] "Next, do the 'complete the store' step."
- [Joonhyung] "Next, implement static rendering."
- [Joonhyung] "Next, implement the gestures."
- [Joonhyung] "Everything works through the guide. But resizing the guide seems to conflict with camera zoom for the user, so make zoom a slider instead."

## Guide editing (#6): Claude Code (Claude Opus 5.5)

- [Joonhyung, 10-06] (Attached the Design Documentation, R&S, and wireframe PDFs) "Look at issue #6, check how much is implemented on the current branch, and explain what to implement next."
- [Joonhyung, 10-06] "Then implement showing the guide edit control bar first."
- [Joonhyung, 10-06] "Now implement the opacity slider and the style toggle too."
- [Joonhyung, 10-06] "There's already a test directory, and I don't see a reason to keep androidTest separate. Merge it into the existing test directory." (Chose "Move to Robolectric".)
- [Joonhyung, 10-06] "So which of the issue #6 tasks does what's built so far cover, and what is left?"
- [Joonhyung, 10-06] "Don't create the PR yourself; just return the PR text here."
- [Joonhyung, 10-06] "The PR example is https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/30. Write it like this."
- [Joonhyung, 10-06] "Explain everything implemented so far in detail."

## Server and two-phone setup

- [Joonhyung, 10-06] "I want to run the server on my laptop and test on real phones."
- [Joonhyung, 10-06] "So I need a Python virtual environment, and then I install the dependencies from pyproject.toml in it?"
- [Joonhyung, 10-06] "Running `uvicorn pix_server.main:app --host 0.0.0.0 --port 8000 --workers 1 --ws-max-size 65536` gives `ModuleNotFoundError: No module named 'starlette.middleware.body_limit'`."

## Integration (#11)

- [Joonhyung, 10-06] "https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/11 I'm going to work on this issue now; explain in detail what I need to do."
- [Joonhyung, 10-06] "First implement task 4, the Timings improvement."
- [Joonhyung, 10-06] (Pasted S23 `ping.received` logs) "It comes out like this. Is that okay?"
- [Joonhyung, 10-06] (Pasted Note 9 `stats`/`rtt` and S23 logs) "I copied only part of it. Is it working?"
- [Joonhyung, 10-06] "So can I conclude that there is no delay in streaming that users would find meaningfully uncomfortable?"
- [Joonhyung, 10-06] "Let's treat the Timings work as done. Functional testing on real phones is complete, but I want the UI to look clean rather than like a demo. Use the /design plugin and improve the current UI with this artifact as the reference: [mockup link]. Where the UI and the current features differ (for example camera zoom slider vs. buttons), keep the implemented features. Use [wireframe link] for the wireframe. Also create and apply an app icon that fits the app's features and purpose." (Sent again after an interruption; chose "download and bundle the font files".)
- [Joonhyung, 10-07] "After using the guide, the 'Saved without the guide' message keeps showing at the top. It should show for 3 s and then disappear, but it doesn't. Please fix it."
- [Joonhyung, 10-07] "Write the PR text for everything done so far and return it here in the chat."
- [Joonhyung, 10-07] "Now I think only the documents' revision history is left. Add it."

[#3]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/3
[#26]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/26
