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

## Wireframes and Korean translation ([#138], [#140])

- [Jaewan, 10-26] "Now we need to do Korean translation (issue #111). How are we going to do this?"
- [Jaewan, 10-27] "yes, yes, yes. One more question. how is the default language chosen in Android? should the user change language settings in the app or in the phone settings?"
- [Jaewan, 10-27] "go for it"
- [Jaewan, 10-27] "can you search for other korean camera apps and see what vocabulary they use often?"
- [Jaewan, 10-27] "fix them"
- [Jaewan, 10-27] "can we draw the wireframe images in two versions? both korean and english"
- [Jaewan, 10-27] "Can you commit and push this, and create a draft PR? make the PR description concise."
- [Jaewan, 10-27] "what do you mean by 'with 日本語, every screen is in English.'"
- [Jaewan, 10-27] "in the wireframes, only the phone screen text should be korean. Other description text should be still in english"
- [Jaewan, 10-27] "oh yeah and fix the PR script and testing document for that part"
- [Jaewan, 10-27] "later if we have the settings screen and users change language there, then is the normal flow to prioritize app settings and then phone settings? is there no phone-level app settings? (i think i've seen ios work like this)"
- [Jaewan, 10-27] "#137 is merged into dev, so we need to do this again. But before this Korean thing, I think we should redraw the wireframes. can you draw a new branch from the new dev and redraw the wireframes so that they match the real ui? currently the wireframes are not exactly the same design as the real app. Can we do this first?"
- [Jaewan, 10-27] "is it still running?"
- [Jaewan, 10-27] "something new's merged into dev"
- [Jaewan, 10-27] "commit and push, and create a PR"
- [Jaewan, 10-27] "some wireframes are cut off at the bottom (flow2, flow3, etc.) i think we need some space below the button at the bottom"
- [Jaewan, 10-27] "the testing images in the `images` folder is slightly weird. can we move these to a different folder? or move the wireframe images"
- [Jaewan, 10-27] "Yes add this. Also, let's create an issue for this wireframe fix, with task number Iter3-A2, and link it to the PR"
- [Jaewan, 10-27] "the camera screen's background color is black, so it's kinda hard to separate that from the surrounding screen. can we draw the camera screen in slightly different color?"
- [Jaewan, 10-27] "yes"
- [Jaewan, 10-27] "in flow1, the guide is cut from the original image exactly in its position, and if we press 'use this guide', then it should be placed in the camera screen exactly at its position. can we make this shown?"
- [Jaewan, 10-27] "no so the guide's position in the 'your reference' screen and the camera screen should be the same"
- [Jaewan, 10-27] "so in the reference confirm screen the guide is always placed in the center, and when in the camera screen it is placed at its real position in the pose image?"
- [Jaewan, 10-27] "okay then let's just discard this and come back to this later"
- [Jaewan, 10-27] "now I merged #140. now let's go back to #138 and apply the changes that were made to dev"
- [Jaewan, 10-27] "commit and push when the tests pass"
- [Jaewan, 10-27] "there are some places where the banner width doesn't match the text length (like flow2-ko)"
- [Jaewan, 10-27] "yes"
- [Jaewan, 10-27] "what fonts are we using for the app now? and what does the wireframes use?"
- [Jaewan, 10-27] "can we use Pretendard?"
- [Jaewan, 10-27] "Commit, push, and update the PR body"
- [Jaewan, 10-27] ""2 of 4 ready" in flow2 has some blank space at its left. also the positioning of the "Agree and continue" vs "not now" buttons in flow2 is kinda weird. same for their counterparts in flow2-ko. does the real app look like this too? or can you fix the wireframes?"
- [Jaewan, 10-27] "For the agree and continue / not now, it seems like there is too much space of the right side? we can move it slightly more to the right"
- [Jaewan, 10-27] "Commit, push, and update the PR body"
- [Jaewan, 10-27] "I want to fix some korean: […]"
  > ## 함께 찍기 -> 카메라 공유, 방 코드 -> 참여 코드, 세션 -> 카메라 공유
  > also fix places that use these vocab other than the below list
  >
  > shoot_together: (함께 찍기) -> (카메라 공유)
  > signed_out_title: (친구와 함께 찍기) -> (친구와 카메라 공유)
  > signed_out_body: (로그인하면 Pix ID로 친구를 추가하고 함께 찍자고 초대할 수 있어요.) -> (로그인하면 Pix ID로 친구를 추가하고 카메라 공유에 초대할 수 있어요.)
  > sign_in_body: (로그인하고 친구를 추가해 함께 찍어요.) -> (로그인하고 친구를 추가해 카메라를 공유해요.)
  > end_session: (세션 종료) -> (공유 종료)
  > leave_session_title: (세션에서 나갈까요?) -> (카메라 공유에서 나갈까요?)
  > peer_ended_session: (%1$s님이 세션을 끝냈어요) -> (%1$s님이 카메라 공유를 끝냈어요)
  > session_ended: (세션이 끝났어요.) -> (카메라 공유가 끝났어요.)
  > use_room_code: (방 코드 사용) -> (참여 코드 사용)
  > use_room_code_instead: (대신 방 코드 사용) -> (로그인 없이 참여 코드 사용)
  > room_code_title: (내 방 코드) -> (참여 코드)
  > room_code_label: (방 코드) -> (참여 코드)
  > room_code_body: (친구에게 Pix를 열고 함께 찍기 › 방 코드 사용을 눌러 이 코드로 참여해 달라고 하세요.) -> (친구가 Pix에서 카메라 공유 › 참여 코드 사용을 누르고 이 코드를 입력하면 돼요.)
  > code_expired: (코드가 만료됐어요. 방 코드 사용을 다시 눌러 새 코드를 받으세요.) -> (코드가 만료됐어요. 참여 코드 사용을 다시 눌러 새 코드를 받으세요.)
  > join_instead: (대신 코드로 참여) -> (받은 코드로 참여하기)
  > session_not_found_title: (세션을 찾지 못했어요) -> (참여할 수 없는 코드예요)
  > session_not_found_message: (친구에게 코드를 확인하고 다시 시도하세요.) -> (코드가 맞는지 친구에게 확인해 보세요.)
  >
  > ## 사람 -> 인물
  > person_found: (사람을 찾았어요) -> (인물을 인식했어요)
  > finding_person: (사람을 찾는 중…) -> (인물을 인식하는 중…)
  > no_person_title: (사람을 찾지 못했어요) -> (인물을 인식하지 못했어요)
  > no_person_message: (한 사람이 또렷하게 나온 사진을 고르세요.) -> (인물 한 명이 또렷하게 나온 사진을 골라 주세요.)
  > upload_reference_body: (갤러리에서 한 사람이 나온 사진) -> (갤러리에서 인물 한 명이 나온 사진 선택)
  > reference_preview_description: (참고 사진 속 사람) -> (참고 사진 속 인물)
  >
  > ## 장면 -> 배경/장소
  > scene_title: (장면 사진 찍기) -> (배경 사진 찍기)
  > scene_photo_description: (포즈를 만들 장면 사진) -> (포즈를 만들 배경 사진)
  > generate_poses_body: (AI가 이 장면에 맞는 포즈 아이디어 4개를 만들어요) -> (AI가 지금 장소에 어울리는 포즈 4개를 추천해요)
  > consent_message: (포즈를 만들려고, 다음에 찍는 장면 사진을 Pix 서버를 거쳐 외부 AI 이미지 서비스로 보내요. Pix 서버는 사진을 보관하지 않아요.) -> (포즈를 만들기 위해 이어서 찍을 배경 사진을 Pix 서버를 거쳐 외부 AI 이미지 서비스로 보내요. Pix 서버에는 사진이 저장되지 않아요.)
  >
  > ## Google 추가 -> 연결
  > add_google: (Google 추가) -> (Google 계정 연결)
  > link_google_body: (Pix 계정에 로그인하면 Google 계정이 추가돼요.) -> (Pix 계정에 로그인하면 Google 계정이 연결돼요.)
  > google_added: (Google을 추가했어요. 이제 Google로 로그인할 수 있어요.) -> (Google 계정을 연결했어요. 이제 Google로도 로그인할 수 있어요.)
  > existing_account_message: (이 Google 계정을 쓰는 Pix 계정이 아직 없어요. 계정이 있다면 로그인하세요. Google이 그 계정에 추가돼요. 없다면 Pix가 새 계정을 만들어요.) -> (이 Google 계정과 연결된 Pix 계정이 없어요. Pix 계정이 있다면 로그인해서 연결하고, 없다면 새로 만들 수 있어요.)
  > existing_account_yes: (네, 로그인) -> (기존 계정으로 로그인)
  > existing_account_no: (아니요, 새로 만들기) -> (새 계정 만들기)
  >
  > ## remove "내 ~"
  > reference_title: (내 참고 사진) -> (참고 사진)
  > your_photo: (내 사진) -> (찍은 사진)
  >
  > ## misc
  > add_guide_body: (사진 속 사람을 오려 내 카메라에 보여 줘요. 같은 포즈로 구도를 잡을 수 있어요.) -> (사진 속 인물만 따서 카메라 화면에 띄워 줘요. 보면서 같은 포즈로 찍어 보세요.)
  > generate_poses: (여기서 포즈 만들기) -> (AI로 포즈 만들기)
  > photo_saved: (가이드 없이 저장했어요) -> (가이드는 빼고 사진만 저장했어요)
  > scene_review_body: (이 사진을 보내 포즈를 만들어요.) -> (이 사진으로 포즈를 만들어요.)
  > describe_body: (원하는 포즈를 설명하거나, 이 장소에 맞는 포즈 4개를 추천받으세요) -> (원하는 포즈를 적거나, 이 장소에 어울리는 포즈 4개를 추천받으세요)
  > describe_label: (포즈 설명) -> (원하는 포즈)
  > generating_scene_caption: (같은 사람과 장소 · 최대 60초) -> (인물과 배경은 그대로 · 최대 60초)
  > ideas_label: (아이디어) -> (추천 포즈)
  > 포즈 예시 string-array item: (주머니에 손 넣고 위 올려다보기) -> (주머니에 손 넣고 하늘 보기)
  > 포즈 예시 string-array item: (팔짱 끼고 어딘가에 기대기) -> (팔짱 끼고 기대 서기)
  > generation_failed_message: (요청이 실패했거나 60초 넘게 걸렸어요. 사진은 그대로 있으니 다시 시도할 수 있어요.) -> (문제가 생겼거나 60초 넘게 걸렸어요. 사진은 그대로 있으니 다시 시도해 보세요.)
  > pix_id_setup_body: (친구들이 이 ID로 나를 찾아요. 그래서 대문자와 공백은 쓸 수 없어요.) -> (친구가 이 ID로 나를 검색해요. 대문자와 띄어쓰기는 쓸 수 없어요.)
  > pix_id_taken: (이미 쓰고 있는 Pix ID예요) -> (이미 사용 중인 Pix ID예요)
  > result_label: (결과) -> (검색 결과)
  > no_user_found_message: (친구에게 Pix ID를 확인하고 다시 시도하세요.) -> (Pix ID가 맞는지 친구에게 확인해 보세요.)
  > subject_caption: (영상을 두 손가락으로 벌리거나 오므려 촬영자의 카메라를 확대·축소하세요) -> (화면을 두 손가락으로 벌리거나 오므려 상대 카메라를 확대·축소할 수 있어요)
  > join_body: (친구의 카메라에 보이는 6자리 코드를 입력하세요.) -> (친구 화면에 표시된 6자리 코드를 입력하세요.)
  >
  > ## wiki/design/wireframes/ko.json
  > flow1_pose_guide: (휴대폰을 허리 높이에 두고 살짝 위로 기울이세요 - 다리가 길어 보여요.) -> (휴대폰을 허리 높이로 내리고 살짝 올려다보듯 찍으면 다리가 길어 보여요.)
  > flow4_photographer: (Test Account 2님에게 알림이 가요. 수락하면 실시간 화면이 시작돼요.) -> (Test Account 2님에게 알림이 가요. 수락하면 카메라 공유가 시작돼요.)
  > flow4_photographer: (Test Account 2님이 카메라와 가이드를 조정하도록 허용) -> (Test Account 2님의 카메라·가이드 조절 허용)
  > flow4_photographer: (초대를 수락하지 않았어요) -> (초대가 수락되지 않았어요)
  > flow5_friend: (Test Account 1님이 함께 찍자고 초대했어요) -> (Test Account 1님이 카메라 공유에 초대했어요)
  > flow5_friend: (Test Account 1님이 카메라를 보여 주려고 해요) -> (Test Account 1님이 카메라를 공유하려고 해요)
- [Jaewan, 10-27] "a few more […] and the "Pix ID로 친구를 추가하세요" text in flow3 is not center-aligned"
  > 포즈 '만들다'(만들기, 만드는 중, ...) -> '생성'
  > 포즈 '고르기' -> '선택'
  > 옆으로 넘겨 포즈를 하나씩' 보세요' -> '살펴보세요'
  > '이메일을 받지 않아서 비밀번호를 잊으면 재설정할 수 없어요.' -> 'Pix는 현재 가입 시 이메일을 받지 않아요. 비밀번호를 잊으면 재설정할 수 없어요.'
- [Jaewan, 10-27] "a few more […]"
  > in_session -> 카메라 공유 중
  > subject_title_placeholder -> 상대 카메라
  > camera_paused -> 상대 카메라가 잠시 중지됐어요.
  > google_in_use -> 이 Google 계정은 이미 다른 Pix 계정에 연결되어 있어요.
  > google_already_added -> 이 계정은 이미 Google 계정에 연결되어 있어요.
- [Jaewan, 10-27] "yes, but you don't need to write this to the PR body"
- [Jaewan, 10-27] "fix the toast fallback when the name is unknown"
- [Jaewan, 10-27] "Fix these too […]"
  > - The English "Expires in 10 min" that wraps onto two lines on the room-code sheet.
  > - Making Android break Korean lines between words instead of in the middle of them (lineBreakWordStyle="phrase").
- [Jaewan, 10-27] "in the wireframes, text outside the phone screens that mark the exact text in the phone screen should be written in korean. fix these"
- [Jaewan, 10-27] "commit and push this. then, there are new changes to dev so apply this"
- [Jaewan, 10-27] "this is merged into dev. can you run the emulator and give me some screenshots of the korean screens?"
- [Jaewan, 10-27] "in 14_room_code.png, it says "두 휴대폰을 같은 WiFi에 연결하세요". is this shown always? I think we've merged #139 (connecting from different networks)"
- [Jaewan, 10-27] "on different networks, do we always go to TURN?"
- [Jaewan, 10-27] "so what's the current flow. the two phones need to be on the same network? or is that just a ui mismatch and we need to fix that ui?"
- [Jaewan, 10-27] "yes"
- [Jaewan, 10-27] "I want one more fix for korean. for '<id>, 쓸수 있어요', i want '사용할 수 있는 ID예요'. And for all '쓸/쓰다', use '사용/사용하다'"
- [Jaewan, 10-27] "yes"
- [Jaewan, 10-27] "and can you take and save relevant screenshots for this?"
- [Jaewan, 10-27] "can you also give me screenshots for before room code en and before connection lost en and before connection lost ko?"
- [Jaewan, 10-27] "also give me before join en ?"
- [Jaewan, 10-27] "and I want to check for places where we use '찍다' instead of '촬영하다'"
- [Jaewan, 10-27] "what do you recommend? to fix and not fix"
- [Jaewan, 10-27] "when is photo_save_failed used? shouldn't this only mention save failure?"
- [Jaewan, 10-27] "okay. then fix 찍다 to 촬영하다 as you proposed"
- [Jaewan, 10-27] "and give me screenshots of before and after, for two usage cases"
- [Jaewan, 10-27] "yes"
- [Jaewan, 10-27] "in the room code screen, the 'ask your friend to open~' part kinda looks weird because it's multiline and maybe left align should look better? or should be put a box over that with a question mark at the left"
- [Jaewan, 10-27] "try it and give me a screenshot"
- [Jaewan, 10-27] "yes i'll keep it"
- [Jaewan, 10-27] "yeah and check for that bug"
- [Jaewan, 10-27] "yeah fix that pattern there too. and just fix the test name, we don't need to fix the testing document"

[#130]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/130
[#138]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/138
[#140]: https://github.com/snuhcs-course/swpp-2026-project-team-10/pull/140
