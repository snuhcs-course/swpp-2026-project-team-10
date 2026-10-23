Back to [[AI Collaboration Report – Iteration 2|AI-Collaboration-Report-–-Iteration-2]]

One Claude Code session (Claude Fable 5.1) for the Iteration 2 documents, 10/11–10/23. Each prompt is quoted as it was sent; attachments are named in a note. Prompts are given in the order they were sent; follow-ups that only confirmed a result are left out. The session was told, through `CLAUDE.md`, to commit or push only when asked, so each "commit" below is an explicit instruction.

## AI rules in CLAUDE.md and the PR template ([#44], [#62])

- [Joonhyung, 10-11] "Add the AI-generated marker rule to CLAUDE.md and an AI usage section to the PR template 이 부분에 대해서 현재 작성된 파일을 바탕으로 추가해 줘."
- [Joonhyung, 10-11] "https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/44 이 내용이 모두 포함된 것이 맞지?" (The agent compared the diff with the issue, found two missing items, and added them: one marker line per file and source files only; the 'what we had to fix' and 'prompts' lines.)
- [Joonhyung, 10-11] "해당 내용에 대한 PR에 대해서 작성해 줘. 새로운 템플릿을 바탕으로."

## Requirements and Specifications v1.0 ([#66])

- [Joonhyung, 10-12] "Update requirements for Iteration 2 (R&S v0.9: kickoff decisions, new requirements, dates)를 진행하려고 한다. 회의록(https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Team-Meetings#2026-10-09-fri-iteration-2-kickoff)과 기존 R&S(https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications)를 바탕으로 Iter2를 위한 R&S에 대해서 작성하려고 한다. 어느 부분을 작성 혹은 변경해야 할지 자세히 설명해 줘." (The agent read the live wiki page of the 2026-10-09 meeting, the R&S v0.8, and the Iteration 2 issues, and listed the changes section by section.)
- [Joonhyung, 10-12] "다시 시도" (The first attempt had failed to read the meeting notes; the agent ran it again.)
- [Joonhyung, 10-12] "그러면 현재 https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Requirements-and-Specifications 바탕으로 Iter2 R&S를 작성해 줘. 가이드 스타일은 reference image의 뒤 배경은 안 보여 주는 것으로 하고, 오로지 outline만 보이는 정도로만 결정하고 구체적인 스타일은 P23에서 결정할 예정임을 작성해 줘. 그리고 If possible 항목은 Could로 설정해서 작성해 줘."
- [Joonhyung, 10-12] "이 PR 본문도 새 템플릿으로 작성해 줘. 특히 변경된 내용에 대해서 자세히 설명해 줘."

## Design Documentation v1.0 ([#67])

- [Joonhyung, 10-12] "그렇다면 이제 현재 R&S와 팀 미팅을 바탕으로 Design Document도 작성하려고 한다. 무엇을 작성해야 하는가?"
- [Joonhyung, 10-12] "decided 다섯 항목에 대해서 자세히 설명해 줘. 그리고 각 부분에 대해서 무엇을 확정해야 하는지 설명해 줘."
- [Joonhyung, 10-12] "3번에서 정확히 무엇을 의미하는지 잘 이해를 못 했는데 자세히 쉽게 설명해 줘."
- [Joonhyung, 10-12] "우선 이 모든 내용들을 Still open 혹은 TODO에 정리하고, 추후 해당 담당자들이 task를 수행하면서 design doc을 version update하면서 수정할 예정이다. 그렇기 때문에 우선 현재 결정된 부분들을 바탕으로 design doc을 작성해 줘. (스위치는 가이드 편집 모드 토글로 진행 예정, 슬라이더는 폐기, subject에게 보내는 데이터는 스무딩한 마스크만)"
- [Joonhyung, 10-12] "슬라이더 폐기가 맞는 것 같은데 세부 설정 같은 거 누르면 나오게 하는 것도 고려 중 / subject 폰에도 스위치 있는 게 맞는 거 같음"
- [Joonhyung, 10-13] "이제 해당 PR 본문을 작성해 줘."

## Issue numbering

- [Joonhyung, 10-13] "지금 Issue numbering 관련해서 현재 open issue와 closed issue들에 대해서는 P21부터 [Iter2-P1]로 다시 넘버링을 하고자 하고, 나머지 Iter1 milestone의 issue들은 [Iter1-P1], [Iter1-A1]와 같이 바꾸고자 한다. 그리고 https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/14 이건 그냥 놔둬 줘." (The agent mapped P21–P44 to Iter2-P1–P24 by subtracting 20, so the schedule's numbers still match, and renamed 31 titles.)
- [Joonhyung, 10-13] "이슈 본문에서의 넘버링도 바꿔 줘."
- [Joonhyung, 10-13] "로컬 문서도 바꿔 줘. 근데 각 wiki/iteration-2-design과 wiki/iteration-2-requirements에 각각 할 건데, 우선 R&S부터 해 줘. 근데 지금 해당 브랜치가 접근이 안 되는데 일단 wiki/iteration-2-requirements 브랜치로 접근해서 새 커밋을 날려 줘. 그리고 그다음으로 wiki/iteration-2-design에도 커밋해 줘. 그리고 0.9v가 아니라 iter2부터는 새롭게 1.0부터 시작하고 이후 v1.10, v1.11 이런 식으로 업데이트할 예정이다." (The branch was held by a worktree the agent had created; it committed, pushed, removed the worktree, and retitled the two PRs to v1.0.)
- [Joonhyung, 10-13] "앞으로 바뀌는 번호는 1.0, 1.1, 1.2, … 1.10, 1.11 이런 식인데 맞나?" (The agent had written "continue as 1.10, 1.11"; it corrected both pages, found that [#66] and [#67] had been merged meanwhile, and opened [#68] from `dev` for the fix.)
- [Joonhyung, 10-13] "지금 PR을 그냥 머지했다. 지금 각 브랜치에 대해서 push한 거 취소해 줘." (The two feature branches were reset to the commits their merged PRs used, with `--force-with-lease`.)

## Testing Documentation v1.0 ([#69], [#70])

- [Joonhyung, 10-13] "이제 Issue에 [Iter2-A1]에서 Testing Documentation을 위키에 새로 만드는 것을 추가하려고 한다. Create a new Testing Documentation page on your team's GitHub repository Wiki. Document how your team tested your implementation and the results of your testing. Please follow the Testing Documentation Guidelines for detailed requirements. 그리고 해당 guideline을 기반으로 format을 만들어 줘. 그리고 이것의 기본 format 버전을 1.0부터 시작할 예정이다. 우선 issue 제목과 본문부터 작성해 줘."
- [Joonhyung, 10-13] "wiki/Testing-Documentation.md 포맷을 작성해 줘."
- [Joonhyung, 10-14] "해당 강의록을 기반으로 Testing에서 중요한 부분들을 Testing documentation에 반영해 줘." (The agent extracted the PDFs to text and added the verification-vs-validation map, the test quality rules, the test design techniques with Pix's partitions, mutation analysis, and the CI and flaky-test policies.)
- [Joonhyung, 10-14] "해당 포맷에 대한 PR 본문 작성해 줘."

## CLAUDE.md: follow and update the Testing Documentation ([#76])

- [Joonhyung, 10-14] "지금 테스트 문서 포맷을 만들었기에 앞으로 CLAUDE.md에 새로운 피처를 개발하거나 테스트하는 태스크가 있는 경우에는 해당 테스트를 어떻게 진행했고, 테스트 결과는 어떤지에 대해서 Testing Documentation에 마찬가지로 버전 업데이트를 하면서 작성하도록 반영해 줘. 그리고 테스트가 있는 경우 혹은 항상 해당 문서를 참조하도록 설정해 줘."
- [Joonhyung, 10-14] "해당 PR을 작성해 줘." (The branch was already pushed, so the agent opened [#76] with the template.)
- [Joonhyung, 10-14] "지금까지 wiki에 Claude.md history에 10/12 revised 버전을 기준으로 지금 바뀐 내용을 Iteration2로 새로 작성해서 해당 PR에 커밋해 줘." (The diff blocks were generated from `git show` for each PR.)
- [Joonhyung, 10-14] "일단 그러면 AI Collaboration Report - Iteration 2를 위키에 포맷만 우선 생성해 주고, 지금까지 Document에 대해서 지금까지의 나와의 대화를 바탕으로 Prompt History(Prompt Log)를 작성해서 커밋해 줘."

## Daily Standup Meeting Log, 10/12–10/16 ([#87])

- [Joonhyung, 10-16] "Daily Standup meeting을 작성하려 한다. 나는 지금까지 대화를 바탕으로 10/13부터 10/16까지 작성한다. (그 이전에 한 일들에 대해서 10/13일에 Done에 작성하고, 나머지 부분들은 여러 날에 나눠서 작성하면 된다.) 10/15은 조성민과 데모 영상 촬영 및 10/16 데모 영상 편집 및 발표 자료 준비로 해 줘. 그리고 다른 팀원들의 할 일이다." (The notes of 박동제, 박재완, and 조성민 were pasted after the prompt; the agent wrote the five days from them and from the conversation's own history.)
- [Joonhyung, 10-16] "10/12(월)부터 10/16(금) 까지이다. 날짜 수정해줘. (커밋할 필요 없음)"

## CLAUDE.md: never force-push or rebase

- [Joonhyung, 10-16] "claude.md에 무슨 일이 있어도 포스푸시나 리베이스 하지 말라고 작성해줘." (Added after the branch resets of 10/13; the rule says to offer a merge, a revert, or a new commit instead, even when a human asks.)

## AI Collaboration Report – Iteration 2, guidelines v1.1 ([#91])

- [Joonhyung, 10-20] "이것은 AI Collaboration Guideline이 바뀐 버전이다. 이전과 달라진 점에 대해서 설명해 주고, 지금 현재 문서에서 어느 부분을 추가하거나 변경해야 할지 설명해 줘." (The v1.0 and v1.1 guideline PDFs were attached; the agent listed the new sections and the items of the current page that had to move or be added.)
- [Joonhyung, 10-21] "지금 바뀐 가이드라인대로 AI Collaboration 부분을 (Iter2 범위만) 수정해 줘. 일단 Prompt log는 건드리지 말고, Wiki에서 지금 page에 prompt log가 너무 많이 되어 있는데, 이 부분을 Page로 하는 것보다 AI Collaboration Report – Iteration X에서 Prompt History에서 연결되게끔 할 순 없나?" (The v1.1 guideline PDF and a screenshot of the wiki's page list were attached; the prompt logs became separate pages linked from section 2, Prompt History.)
- [Joonhyung, 10-21] "wiki/ai-report-edit으로 PR 쏴 줘."

## Iteration 2 status for the report and the presentation

- [Joonhyung, 10-21] "지금 이 이슈가 완료되었는지 확인해 줘. https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/54"
- [Joonhyung, 10-21] "https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/47 이 이슈도 완료되었는가?"
- [Joonhyung, 10-21] "지금까지 dev에 merge된 내용에 대해서 Iteration 2에서 구현된 feature들에 대해 요약해 줘."
- [Joonhyung, 10-21] "간단히 이걸 영어로 큰 주제로 몇 줄로 요약해 줘."
- [Joonhyung, 10-21] "이번 Iteration에서는 Branch 관리 방식 및 merge / code review를 어떻게 진행했는지 설명해 줘. 그리고 몇 줄로 요약된 영어로 반환해 줘."
- [Joonhyung, 10-21] "Document Updates에 대해서도 Diff 위주로 간단히 설명. 중요한 변화들은 추가 설명을 해 줘."
- [Joonhyung, 10-21] "각 Documentation에서의 변화들로 영어 요약을 작성해 줘."
- [Joonhyung, 10-21] "Briefly introduce your progress in feature testing. Show which features you have tested so far and how you tested them. Share any key findings, failures, or issues identified through testing. Explain how the testing results have affected, or will affect, your implementation. Briefly describe what you plan to test next. 이 부분을 작성해야 하는데, 지금까지의 git을 바탕으로 레이턴시 테스트, CI Test(android/server), Image Test(첨부 폴더), unit test 등을 포함해서 설명해 줘. 그리고 간단히 영어로 요약해서 작성해 줘." (The speed-target image and the `20261021-cc0-arcade-test` folder were attached.)

## Testing Documentation 1.10–1.13: coverage tools, screen and adapter tests, results, and targets ([#69], [#94])

- [Joonhyung, 10-22] "이 이슈 https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/69 를 진행하려 한다. 지금 현재 Task 상황에 대해서 정리해 줘."
- [Joonhyung, 10-22] "chore/a1-coverage 브랜치로 JaCoCo랑 pytest-cov 추가해 줘." (Testing Documentation 1.10: the two TODO lines of 2.1.1 and 2.1.4 became the configuration.)
- [Joonhyung, 10-22] "푸시하고 PR 작성해 줘."
- [Joonhyung, 10-22] "테스팅 문서에 우리 커버리지 목표 값을 지금 제안된 값을 한 번 다시 검토하고, 어느 정도 수치가 적절한지 설명해 줘."
- [Joonhyung, 10-22] "View와 Adapter 부분의 커버리지를 올릴 수 있는 부분이 있는가?"
- [Joonhyung, 10-22] "CameraFragment render 테스트부터 FragmentScenario로 추가해 줘." (Revision 1.11.)
- [Joonhyung, 10-22] "일단 이 부분만 push하고, draft로 놔둬 줘. 그리고 View 커버리지를 보고해 줘."
- [Joonhyung, 10-22] "SubjectFragment, JoinCodeFragment, ConnectionLostFragment도 같은 방식으로 추가해 줘. 그리고 지금 다른 브랜치로 했는데, 그렇게 하면 안 되고, chore/a1-coverage 브랜치를 기준으로 지금 [#95]를 여기에 머지해서 PR을 유지해 줘." (Revision 1.12; [#95] was merged into the branch and closed.)
- [Joonhyung, 10-22] "RoomCodeSheet랑 guide, generation 화면도 같은 방식으로 추가해 줘." (Revision 1.13.)
- [Joonhyung, 10-22] "Testing Documentation 2.2에 지금 수치 기록해 줘." (Revision 1.14: the first results of 2.2.1 with the four report screenshots and the Test Log's first row.)
- [Joonhyung, 10-22] "App에서 platform adapters의 커버리지를 늘릴 방안에 대해서 생각해 줘."
- [Joonhyung, 10-22] "1번의 세 가지를 지금 chore/a1-coverage에 추가해 줘." (Revision 1.15; 2.2.1 re-recorded on the new commit.)
- [Joonhyung, 10-23] "PR [#94] 본문 다시 확인해 줘. 그리고 Platform adapters의 커버리지는 어떻게 향상시킬 수 있는지 검토해 줘." (The agent found and fixed four stale parts of the PR body and gave four ways to raise the layer, with the expected coverage of each.)
- [Joonhyung, 10-23] "1번 네 가지를 chore/a1-coverage에 추가해 줘. 그리고 Test document에서 이번 변경 사항들을 버전 업을 각각 하는 게 아니라, 하나로 통합해서 작성해 줘. 그리고 가능하면 지금 dev 브랜치와 컨플릭트 나는 부분에 대해서 resolve해 줘." (`dev` was merged into the branch with a merge commit; the six revision rows 1.10–1.15 became one row, 1.13, after dev's 1.10–1.12.)
- [Joonhyung, 10-23] "계정 Fragment 4개도 FragmentScenario 테스트 추가해 줘."
- [Joonhyung, 10-23] "CameraFragment 남은 경로도 커버해 줘."
- [Joonhyung, 10-23] "일단 지금까지 커버리지가 된 부분을 정리해서 보여 줘."
- [Joonhyung, 10-23] "그러면 지금 이 상태로 목표를 순서대로 (App) 90 / 90 / 90 / 30 (Server) 90 / 90 / 90으로 Testing 문서를 기준으로 바꿔 주고, Adapter 부분에 대해서 커버리지가 낮은 이유와 추후 이터레이션에서 보완할 방안들에 대해서 제안하는 식으로 작성해 줘." (2.1.2: the targets set with the proposals kept, and the adapter layer's reasons and the measures of Iterations 3 and 4.)
- [Joonhyung, 10-23] "지금까지의 내용을 바탕으로 AI Collaboration Report에서 Document 부분의 Prompt 로그를 작성해 줘."

[#44]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/44
[#62]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/62
[#66]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/66
[#67]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/67
[#68]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/68
[#69]: https://github.com/snuhcs-course/swpp-2026-project-team-10/issues/69
[#70]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/70
[#76]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/76
[#87]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/87
[#91]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/91
[#94]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/94
[#95]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/95
