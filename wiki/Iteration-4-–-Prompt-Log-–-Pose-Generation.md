Back to [[AI Collaboration Report – Iteration 4|AI-Collaboration-Report-–-Iteration-4]]

## A refused description out of the prompt history ([#200])

- [Dongje, 11-10] "We block wrong photos and prompts with Jev and with ML Kit's pose and face detection, but a blocked prompt stays in the history. We need something that keeps such a prompt from being saved." (The agent found the path in the code and reproduced it in a test: a description goes out as five requests, each judged by itself, and the filter lets a request through when it cannot answer in 2 s, so a refused description could still get a candidate, from a variation or from such a request, and was kept at that first candidate. IT-22's three descriptions asked 20 times each gave the same answer every time, and 1 call of 60 took over 2 s. Now the description's own request decides: when the service refuses the description as written, neither phone keeps it, and an entry kept before is removed. The photographer's phone tells the subject's in `pose.candidate.failed`.)
- [Dongje, 11-10] "Are the image service's refusal and the filter's both kept out of the history now? Then keep blocking both, and fix the wording." (Both arrive as `REJECTED`, so both already counted; the documents had called the image service's part temporary, until [#203] gives it a code of its own. They now say that both count, and that [#203] must keep it so.)

[#200]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/200
[#203]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/203
