Back to [[AI Collaboration Report – Iteration 3|AI-Collaboration-Report-–-Iteration-3]]

## Iteration 3 kickoff ([#130])

- [Jaewan, 10-25] "We are now going to start iteration 3. First I'm going to cleanup the design document and R&S's revision history. They are toooo long currently. Can you make each content shorter and concise? Without changing the numbers or order"
- [Jaewan, 10-25] "can you also check for any unnecessary content that contains old app behavior? so we don't have to record all of our rationales on the decision changes we made, the design document and R&S only need to record the current, final behavior"
- [Jaewan, 10-25] "add a 2.0 revision, and commit and push this"
- [Jaewan, 10-25] "also do the same for the tesing documentation and fix the mismatch"
- [Jaewan, 10-25] "Regarding the revision history 2.0, it should also be kept concise like what we did for the previous ones"
- [Jaewan, 10-25] "commit and push this"
- [Jaewan, 10-25] "Okay and now this is a plan of what we are going to do this in iteration 3, from our weekly meeting today: […]"
  > ## 기능 개발
  >
  > 1. UI/UX 개선
  >     - 한글 번역 (영어/한글 선택 가능), 기본은 핸드폰 언어 >> 설정창
  >     - 포즈 선택 시 스와이프로 하나씩 보여주기 (#108)
  > 2. 신규 기능
  >     - 포즈 생성 후 How(Tip) 간단하게 알려주기(각 화면 하단 공간에)
  >         - 현재 찍는 사람 위치에서 앞으로 뒤로
  >         - 줌 인, 줌 아웃
  >         - 카메라 위치 위아래
  >         - 카메라 각도 눕히기, 들기
  >         - 텍스트로 → 원본 사진 + 생성된 사진 제미나이에 API 요청 보내서 tip 받기
  >         - 사진은 4개 생성, 각각 생성되자마자 보여주고 tip 생성 요청 보내놓음. 사용자가 고르는 동안 아마 완성될것임 → 카메라 화면 넘어가서 보여주기 (열고닫을 수 있게 해서)
  >         - 레퍼런스 업로드해서 가이드 생성한 경우는 tip 없음
  >     - Live preview 관련
  >         - 다른 네트워크에서 연결 (원래 Iter2 계획, #56)
  >         - 오프라인에서 QR코드 + 핫스팟으로 연결
  >         - 끊겼을 시 (몇초이내) 재연결
  > 3. 기타 (간단한 버그 or 개발 미완료 기능)
  >     - 친구 초대/맺기 + 푸시알림 (원래 Iter2 계획, #55)
  >     - 포즈 생성 시 3:4 비율 고정 해제>>보내는 사진으로 기준으로
  >
  > ### 논의사항
  >
  > - 개인화 어떻게 할 건지 → Subject vs Photographer 폰에서 각각 어떤 기능을 제공할 것인지?
  >     - 가이드 선택 or 포즈 생성 화면 자체 진입을 찍히는 사람 폰에서 가능하게
  >     - 포즈 생성 및 선택: 현재는 카메라 화면
  >         - 배경 사진 찍을때: 찍는사람(카메라), 찍히는사람(카메라) 같은 화면 보여줌
  >         - 포즈 생성 창: 같은 화면 보여줌, 그러나 화면 내에서의 변화를 스트리밍 하지는 않음, 둘중 먼저 결정이 된 쪽이 발생하면 다음 화면으로 넘어감
  >         - 포즈 선택 창: 위와 동일
  >     - 포즈 (가이드?) 저장: 양쪽에 다 옵션 버튼 주기 / 찍히는 사람 폰에 저장된걸 불러오고 싶으면 화면 진입을 찍히는 사람 폰에서 시작하면 됨
  >     - 찍은 사진 저장: 기본으로 양쪽에 다 저장
  > - 입력 프롬프트 history
  >     - 기존에 사용한 입력 history를 보여줌
  >     - 전체 history or (찍는 사람, 찍히는 사람) pair의 history 중 선택 가능하게
  >     - 전체 history의 경우, 찍는 사람 → 내가 찍었던 history / 찍히는 사람 → 내가 찍혔던 history
  >     - UI: 전체 vs 상대방과 toggle / x버튼 (히스토리 삭제 가능하게)
  > - 셀피모드
  >     - 라이브 프리뷰 안할때만 허용
  >     - 포즈추천 → 프롬프트 지금거가 괜찮은지 확인하고, 별도로 구성해야할듯
  > - 구도 및 배경 관련
  >     - 4개 생성 다르게
  >         - 입력 받을 시: 현재는 paraphrase 3개 → 템플릿을 4종류로 준비해두고 {pose}는 하나로
  >         - 현재 기본 템플릿 발전시키기: 충성 사진 2개 만세 사진 2개 << 이런거 어떻게 할거임?
  >             - {pose}에 들어오는건 유저가 입력한 요청이니 알잘딱으로 해석해라 + 얼굴 바꾸지 마라. 옷 바꾸지 마라. 한 사진에 여러개를 넣지마라. 등등
  > - 설정창에 overlay 색깔/두께 설정 가능하게 / latency? / how to use 도움말?
  >     - 튜토리얼 >> 첫번째 사용에서만 해주고
  >     - 만약 한번더 보고싶으면 설정창에서 누르면 켤 수 있게
  >
  >
  > I believe the documents should be updated while we implement these features. Is there anything we should update now, before the start of this iteration?
- [Jaewan, 10-25] "okay I merged the previous branch. so let's delete this branch and start a new branch from the updated dev."
- [Jaewan, 10-26] "Okay let's first write the kickoff meeting log. We had a few more content in the meeting. […]"
  > ## 테스트
  >
  > - 이번 Iter에 테스트를 중점적으로 해야함
  > - 테스트해야 할 기능: 카메라 촬영 자체 / 포즈 추천 및 가이드 / live sharing
  > - LLM(VLM) judge 테스트: 생성 시 절대 지켜야 할 규칙 정해서 (이는 프롬프트와 일관되어야 함) failure rate 측정 → Claude API 사용
  > - 유저 테스트: ?
  >     - 동제 지인 5명
  >     - 질문지 (구글 설문지) or 직접 물어봐서 녹음 후 testing doc에 정리
  > - Latency 테스트 → 어디어디에 할건지? 몇초? 추후 결정
  >
  >
  > ## 정리
  >
  > 월요일 전까지 할 일
  >
  > - AWS 배포 (임시, 유저 테스트용) @박재완
  > - PM 업무 (문서 내용 추가/수정) @박재완
  > - 한글 번역 @박재완
  >
  > 금요일 전까지 할 일
  > - 포즈 선택 시 스와이프로 하나씩 보여주기 @박동제
  > - 포즈 생성 후 How(Tip) 간단하게 알려주기 @박동제
  > - 다른 네트워크에서 연결 @조성민
  > - 끊겼을 시 (몇초이내) 재연결 @조성민
  > - 친구 초대/맺기 + 푸시알림 @조성민
  > - 포즈 생성 시 3:4 비율 고정 해제>>보내는 사진으로 기준으로 @박재완
  > - 셀피모드 + 프롬프트 괜찮은지 확인좀 @박동제
  > - 개인화 @한준형
  >
  > 나머지는 다음주에 진행
- [Jaewan, 10-26] "commit and push this. And then let's update the docs with what should be settled at kickoff. FYI the '논의사항' are all what we decided"
- [Jaewan, 10-26] "wait, Heuristic Evaluation is 11/4 and the UAT plan changed to 11/16&18 (Iteration 4). There are also further tests in iteration 5, In-class testing is 11/30 and real user test is 12/2"
- [Jaewan, 10-26] "hosting beyond the course servers is done (I already deployed this to AWS, though this is temporary and the course servers will be open soon). also merge the revision history with 2.0"
- [Jaewan, 10-26] "Yes."
- [Jaewan, 10-26] "and we also have to split our tasks and put them into our schedule file (in google sheets, i brought a copy here at `[SWPP-2026] Project Schedule - Team 10.xlsx`) and create issues for this iteration. Including what's also planned for next week, can you give me the list of tasks for this iteration? (Don't edit files or create issues yet)"
- [Jaewan, 10-26] "[…] can you re-schedule?"
  > We don't need an issue for P3 and P4.
  > Add mask only guide transfer as my job up to 10/30. but will this change if we do the personalization thing? 
  > Split P15 into steps & saving photos
  > We don't need tags like (FR-4.8) in the task name
  > For P16 and P17, let's assign all
  > Give P20's description slightly more detailed
  > Split P21 to settings screen and tutorial
  > assign the next week jobs as good as you can
  > testing and integration goes to me
- [Jaewan, 10-26] "changed some, and updated the xlsx file. Do we need any further change to the wiki docs?"
- [Jaewan, 10-26] "Those testing plan items need to be done in this iter?"
- [Jaewan, 10-26] "Okay, move them to Iteration 4 and apply the wiki changes"
- [Jaewan, 10-26] "commit and push this, and then create the issues. there are two remaining issues from iter2, so close them."
- [Jaewan, 10-26] "update the wiki links and close the Iteration 2 milestone"
- [Jaewan, 10-26] "commit and push this"
- [Jaewan, 10-26] "I have one more question. We are currently managing the wireframes in https://claude.ai/artifact/GRLQMzirhDrBui9WWTQ1vm , but can we also manage this in code? in this repo?"
- [Jaewan, 10-26] "I think it would be better to manage this in code, and make the person who made a UI fix also edit the wireframe in the same PR. but can we or should we push this to the canvas after each change in our code?"
- [Jaewan, 10-26] "yes, set it up on a new branch from dev"
- [Jaewan, 10-26] "I think we should put `design/` into `wiki/` and put `design_architecture.html` into it"
- [Jaewan, 10-26] "update CLAUDE.md and include instructions to update the wireframes too. and maybe we should have a render script for `design_architecture.html` too? or just one universal render script?"
