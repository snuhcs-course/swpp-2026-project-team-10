Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

The prompts of the Iteration 2 testing work, 10/22–10/23, from the same Claude Code session (Claude Fable 5.1) as the [[Documents|Iteration-2-–-Prompt-Log-–-Documents]] log: the coverage tools, the screen and adapter tests, the results and targets ([#69], [#94]), the integration run and the user-story mapping ([#59], [#98]), and the integration of the features ([#60], [#99]). Each prompt is quoted as it was sent; attachments are named in a note. The session was told, through `CLAUDE.md`, to commit or push only when asked, so each "commit" below is an explicit instruction.

## Testing Documentation 1.10–1.13: coverage tools, screen and adapter tests, results, and targets ([#69], [#94])

- [Joonhyung, 10-22] "이 이슈 https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/69 를 진행하려 한다. 지금 현재 Task 상황에 대해서 정리해 줘."
- [Joonhyung, 10-22] "chore/a1-coverage 브랜치로 JaCoCo랑 pytest-cov 추가해 줘." (Testing Documentation 1.10: the two TODO lines of 2.1.1 and 2.1.4 became the configuration.)
- [Joonhyung, 10-22] "푸시하고 PR 작성해 줘."
- [Joonhyung, 10-22] "테스팅 문서에 우리 커버리지 목표 값을 지금 제안된 값을 한 번 다시 검토하고, 어느 정도 수치가 적절한지 설명해 줘."
- [Joonhyung, 10-22] "View와 Adapter 부분의 커버리지를 올릴 수 있는 부분이 있는가?"
- [Joonhyung, 10-22] "CameraFragment render 테스트부터 FragmentScenario로 추가해 줘." (Revision 1.11.)
- [Joonhyung, 10-22] "일단 이 부분만 push하고, draft로 놔둬 줘. 그리고 View 커버리지를 보고해 줘."
- [Joonhyung, 10-22] "SubjectFragment, JoinCodeFragment, ConnectionLostFragment도 같은 방식으로 추가해 줘. 그리고 지금 다른 브랜치로 했는데, 그렇게 하면 안 되고, chore/a1-coverage 브랜치를 기준으로 지금 #95를 여기에 머지해서 PR을 유지해 줘." (Revision 1.12; [#95] was merged into the branch and closed.)
- [Joonhyung, 10-22] "RoomCodeSheet랑 guide, generation 화면도 같은 방식으로 추가해 줘." (Revision 1.13.)
- [Joonhyung, 10-22] "Testing Documentation 2.2에 지금 수치 기록해 줘." (Revision 1.14: the first results of 2.2.1 with the four report screenshots and the Test Log's first row.)
- [Joonhyung, 10-22] "App에서 platform adapters의 커버리지를 늘릴 방안에 대해서 생각해 줘."
- [Joonhyung, 10-22] "1번의 세 가지를 지금 chore/a1-coverage에 추가해 줘." (Revision 1.15; 2.2.1 re-recorded on the new commit.)
- [Joonhyung, 10-23] "PR #94 본문 다시 확인해 줘. 그리고 Platform adapters의 커버리지는 어떻게 향상시킬 수 있는지 검토해 줘." (The agent found and fixed four stale parts of the PR body and gave four ways to raise the layer, with the expected coverage of each.)
- [Joonhyung, 10-23] "1번 네 가지를 chore/a1-coverage에 추가해 줘. 그리고 Test document에서 이번 변경 사항들을 버전 업을 각각 하는 게 아니라, 하나로 통합해서 작성해 줘. 그리고 가능하면 지금 dev 브랜치와 컨플릭트 나는 부분에 대해서 resolve해 줘." (`dev` was merged into the branch with a merge commit; the six revision rows 1.10–1.15 became one row, 1.13, after dev's 1.10–1.12.)
- [Joonhyung, 10-23] "계정 Fragment 4개도 FragmentScenario 테스트 추가해 줘."
- [Joonhyung, 10-23] "CameraFragment 남은 경로도 커버해 줘."
- [Joonhyung, 10-23] "일단 지금까지 커버리지가 된 부분을 정리해서 보여 줘."
- [Joonhyung, 10-23] "그러면 지금 이 상태로 목표를 순서대로 (App) 90 / 90 / 90 / 30 (Server) 90 / 90 / 90으로 Testing 문서를 기준으로 바꿔 주고, Adapter 부분에 대해서 커버리지가 낮은 이유와 추후 이터레이션에서 보완할 방안들에 대해서 제안하는 식으로 작성해 줘." (2.1.2: the targets set with the proposals kept, and the adapter layer's reasons and the measures of Iterations 3 and 4.)
- [Joonhyung, 10-23] "지금까지의 내용을 바탕으로 AI Collaboration Report에서 Document 부분의 Prompt 로그를 작성해 줘."


## Integration tests from the demo recordings, and the user-story mapping ([#59], [#98])

- [Joonhyung, 10-23] "https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/59 이 이슈를 진행하려 한다. 이 이슈 태스크에 대해서 설명해 줘."
- [Joonhyung, 10-23] "이 데모 영상들을 바탕으로 Integration을 진행하려고 한다. Streaming 동영상이 두 기기로 진행한 것이고, WIFI는 학교 서버와 로컬 서버에서 둘 다 진행했었고, 셀룰러는 학교 서버(eduroam)로 하는 경우에는 지금 외부와 연결이 되지 않는 상황이라 이 부분은 추후 Iteration에 가능하면 구현할 예정이다. integration test로 우선 PR을 작성하고, 이 내용을 testing documentation에 작성해 줘." (Four screen recordings were attached: `camera_basic.mp4`, `login.mp4`, `reference_image.mp4`, `Streaming.mov`. The agent read them as frame sheets and matched each to the scripts of 2.1.5.)
- [Joonhyung, 10-23] "subject 폰은 S25 Ultra였고, 테스터는 조성민이다. 그리고 이것은 중간 발표 시에 진행했던 데모 영상인데, 이것도 적용해 줘. 그리고 지금까지 올린 영상(camera_basic.mp4, reference_image.mp4, login.mp4, Streaming.mov)도 PR에 반영해 줘. 그리고 conflict이 나는데, 확인해 줘." (The two recordings of the midterm presentation were attached. `dev` with [#97] merged was merged into the branch.)
- [Joonhyung, 10-23] "영상을 스크린샷 한 image는 삭제해 주고, PR 본문을 내가 작성할 테니 영상에 대해 설명할 글만 작성해 줘."
- [Joonhyung, 10-23] "각 테스트를 사용자 스토리별로 Testing Documentation해 줘." (A subagent mapped the 98 acceptance rows of R&S 4.4 to test methods; every name was checked against the files before the page was edited.)
- [Joonhyung, 10-23] "PR #98 본문도 이 커밋 반영해서 갱신해 줘. 그리고 Iteration 2에서 진행해야 할 Integration Test는 모두 수행한 것인지 확인해 줘."

## Integration of the Iteration 2 features ([#60], [#99])

- [Joonhyung, 10-23] "https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/60 이제 마지막으로 통합을 하려고 한다." (The agent traced the one finding of the integration run, the paused notice before the first video, to the watchdog's reference time, fixed it with a screen test, checked the `Timings` marks, and opened the PR.)

[#59]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/59
[#60]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/60
[#69]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/69
[#94]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/94
[#95]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/95
[#96]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/96
[#97]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/97
[#98]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/98
[#99]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/99
