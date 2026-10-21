## Document Revision History

| Version | Date | Key changes |
|---|---|---|
| 1.0 | 2026-10-13 | First version, for Iteration 2 (Iter2-A1). The page format from the course's Testing Documentation Guidelines: one section per testing activity, each with a *Plan* and a *Results* part. Filled in for Iteration 2: Test Responsibility & Schedule (1), and the Unit & Integration Testing plan and first results (2). AI Module Testing (3), Acceptance Testing (4), and Alpha & Beta Testing (5) are headings to be completed in Iterations 3–5. The test design techniques, test-quality rules, and CI and flaky-test policies follow the course lectures on testing (Week 6 and Week 7): oracle and the RIP model, the test pyramid, test doubles, FIRST, equivalence and boundary partitions, state-based and property-based tests, coverage as a map rather than a certificate, mutation analysis, and which tests run at which stage. Versions count up by one minor step per change: 1.1, 1.2, …, 1.9, 1.10, 1.11, and so on. |
| 1.1 | 2026-10-14 | The server's session store (Iter2-A2, #73; Design 2.7). Its tests run on fakeredis, an in-process Redis that runs the store's real Lua scripts, and CI runs them again on a real Redis (1.2, 2.1.1, 2.1.3, 2.1.4). The store gets a row of its own in 1.1 and joins the signaling row of the coverage targets and results (2.1.2, 2.2.1). The `server-image` check is listed (1.2); two workers sharing one Redis are an automated integration test (2.1.5); store outages and restarts are fault-injection cases (2.1.6). 2.1.7 notes that the room transitions are now Lua, which mutmut cannot mutate. No goal or target changed. |
| 1.2 | 2026-10-14 | Pinch zoom and the guide-edit mode (Iter2-P6, #48). Its tests: two-finger MotionEvents through the real `ScaleGestureDetector` (`PinchZoomControllerTest`), one pinch that zooms or edits the guide by the mode alone (`GuideEditModeGestureTest`), the overlay's touch rule (`GuideOverlayGestureTest`), and the mode's rules in `CameraViewModelTest` and `SubjectViewModelTest`; the guide-edit mode partitions are spelled out (2.1.6), and the run is in the Test Log (6). No goal or target changed. |
| 1.3 | 2026-10-15 | The server's accounts and friends (Iter2-P8, #50; Design 2.5.3, 2.7). Their tests: the REST endpoints through the test client (`test_accounts.py`, `test_friends.py`), the Pix ID and password rules at their edges (`test_account_rules.py`), the real Google token check against keys made in the test (`test_google.py`), the migrations and the database's constraints (`test_database.py`), and presence across two workers (`test_registry.py`). Access tokens are JWTs, tested with forged, altered, and expired tokens, and refresh tokens with rotation, reuse, expiry, and sign-out; an unreachable database answers `503`. Google is replaced by the `google` fake, argon2id runs at its lowest cost, and each test gets a new SQLite file; CI's second run adds a PostgreSQL service (1.2, 2.1.1, 2.1.3, 2.1.4). `server-image` also signs up through nginx and checks that the access logs leave out the Pix ID (1.2). The partitions are in 2.1.6. Coverage of this code must trace greenlets (2.1.4). The abuse limits on the endpoints that need no account, a global pose window and a limit on wrong room codes per address, are tested at the socket, the registry across two workers, and the REST endpoint (2.1.6). `test_config.py` keeps `.env.example` in step with the settings, which deploy.sh now checks `/opt/pix/.env` against. No goal or target changed. |
| 1.4 | 2026-10-15 | The kept room code on *Join with code* (Iter2-A3, #81). Its tests, in `SessionViewModelTest`, are the state-based partitions of what each end of a joined session does with the code: kept on Session not found and Connection lost, forgotten on Leave, the photographer's End session, a room gone while reconnecting, and Start as the photographer (2.1.6). The screen's own rule, the code offered only when Session not found opens it, is checked on the phone with the room-code flow of 2.1.5. No goal or target changed. |
| 1.5 | 2026-10-15 | Camera controls (Iter2-P10, #51; Design 2.5.1, 2.6.2, 2.8). Their tests: the tap's mapping from the frame to the camera buffer, the exposure clamp, and the frame shapes (`CameraControlsTest`); the flash and ratio cycles, the saved ratio, the exposure and focus requests, and the guide clamped to a new frame (`CameraControlsViewModelTest`, `InMemoryGuideRepositoryTest`, `GuideGeometryTest`); a tap reported by the pinch controller (`PinchZoomControllerTest`); the frame fitted in each ratio (`ViewfinderFrameTest`); the new fields of `camera.capabilities` and `camera.state` through the real codec, including an older phone without them and a malformed `exposureRange` (`CameraMessagesTest`); the whole camera state sent by the photographer (`RemoteCameraStateTest`); and the ratio on Subject view (`SubjectViewModelAspectTest`). The partitions are in 2.1.6. IT-8 adds 1:1 and the subject's view in each ratio (2.1.5). No goal or target changed. |
| 1.6 | 2026-10-16 | Guide controls (Iter2-P11, #52; Design 2.3, 2.6.1, 2.6.2). Their tests: the outline drawn without any pixel of the reference (`GuideOverlayViewTest`); *Hide* and *Show* with a final change and guide-edit mode ended, and the reference thumbnail's enlarged state and corner (`GuideControlsViewModelTest`); the replace prompt only while a guide is on the camera (`ReferenceViewModelTest`); the starting position from the anchor in each frame ratio, a person below the minimum size, and an anchor that is not finite (`GuideStartTest`, `InMemoryGuideRepositoryTest`); the crop as fractions of the photo (`GuideSizesTest`); the thumbnail's size and corner (`ReferenceThumbnailTest`); its tap and drag with single-finger MotionEvents (`ReferenceDragControllerTest`); and `visible: false` reaching the subject's mirror (`GuideSyncerTest`). The guide has no style any more, so the style cases left the geometry, repository, overlay, Camera, *Reference confirm*, sync, and codec tests, and `MessageCodecTest` checks that an old phone's `style` field is ignored. The partitions are in 2.1.6. IT-5 adds the starting position, *Hide* / *Show*, and a replacement on the subject's phone (2.1.5). No goal or target changed. |
| 1.7 | 2026-10-16 | Remote brightness, flash, and guide moves (Iter2-P17, #57). Their tests: the new messages against the Design's JSON examples and round trips (`RemoteControlMessagesTest`), their channels (`ChannelRouterTest`), the handler's partitions per request (`RemoteControlHandlerTest`, with the real `InMemoryGuideRepository` for the guide's limits), the `by` attribution of the echoed `guide.state` (`GuideSyncerTest`), Subject view's gestures, echoes, and hidden controls (`SubjectViewModelTest`), the step-then-final sender (`StepSenderTest`), and the brightness scale (`ExposureScaleTest`); partitions in 2.1.6, and IT-6 now covers guide moves and names the round-trip measurement for NFR-7 (2.1.5). No goal or target changed. |
| 1.8 | 2026-10-18 | Subject view's video fitted to the frame (Iter2-A4, #86; Design 2.6.4). `SubjectVideoFitTest` inflates Subject view's layout under Robolectric and checks that the video covers exactly the rectangle the grid and the guide fit in each ratio, covers the whole area before the first frame, and that a pinch starting in the margin around a square video still zooms; on the old layout the first test fails. The partitions are in 2.1.6. IT-8 adds the subject's grid ending at the video's edges (2.1.5). No goal or target changed. |
| 1.9 | 2026-10-21 | Pose descriptions and suggested poses (Iter2-P12, #53; Design 2.5.3, 2.6.3). Server, with the fake `image_api` transport, which now also answers OpenRouter's chat completions: `test_poses.py` sends descriptions through `POST /poses` and checks the description reaching the one pose prompt as written, Korean included, on one line; a blank one, and one of 100 and 101 characters counted after trimming; a template and a description together, neither, and an empty description next to a template; a template sent with the same prompt as its default pose; a refusal of a description answering "Try a different description"; requests refused for their description not counted by the rate limit; and the description never in the log. `test_pose_suggestions.py` covers `POST /pose-suggestions`: the request to the suggestion model; answers in other shapes (a code fence, five poses, line breaks); unusable poses (a duplicate, over 100 characters, not a string) replaced by default poses in order; no usable pose, a failing status, an unreadable body, no answer within the limit, no connection, and no key, each giving the four default poses; a refused scene not counted; the suggestion windows apart from the pose windows; the session store down; and neither a pose nor an upstream body in the log. With a `description` it checks the description coming back first with three variations from the variation prompt; the description folded like a pose request; unusable variations (the description repeated first, a duplicate, one too long, more than three, none) and a failing or slow model or no key giving the description in their place; a blank or too long description refused before any call and not counted; an empty one asking for suggestions; and neither the description nor a variation in the log. `test_prompts.py` loads the shipped `prompts.toml` and refuses broken ones (the pose prompt without `{pose}` or with it twice; the variation prompt without `{description}` or with it twice; a prompt missing, empty, or unknown; a default pose without a field, too long, or not a table; an id used twice; not exactly four; not TOML). App: `RemotePoseGeneratorTest` (a description with three variations, the scene encoded once and both texts sent as UTF-8; the description's own request sent before the variations arrive and reported only after the poses; variations failing, late, fewer, blank, or more than three, the description filling in; suggestions first, more or fewer than four, blank, failing, or late; the seeds; arrival order; error mapping; timeout; cancel), `GenerationViewModelTest` (*Generate poses* between the photo and the requests, a blank description asking for suggestions, the slots appearing with the poses, a description rejected in full or in part, *Try other poses* and *Edit description* going back to *Generate poses* with the photo and the text kept, *Try again* for either request, one limit over the suggestions and the requests), and `DescriptionFormTest` under Robolectric on the real layout (the counter, 100 and 101 characters, the chips, the rejection message, the two buttons). The partitions are at the top of each file and in 2.1.6; IT-7 adds *Skip, suggest poses for me* (2.1.5). No goal or target changed. |

Audience: the development team and the course staff. This page makes our testing explicit and reproducible: anyone on the team can run the same commands, get the same numbers, and compare them with the goals set here in advance. Requirement IDs (FR-, NFR-, US-) refer to the [Requirements and Specifications](Requirements-and-Specifications) page, and section numbers in the form "Design 2.x" to the [Design Documentation](Design-Documentation). **TBD** marks a value the team still has to set; **TODO** marks a part that is filled in when its iteration comes.

**Rules for this page**

- **Plan, then Results.** Each activity below has a *Plan* (what is tested, how, by whom, and to what standard) and *Results* (what was measured, against the plan). The iteration in which each section is first required is in its heading; from then on both parts are present and kept up to date.
- **Plans are commitments.** A submitted plan item is never rewritten silently. If a goal has to change, the original stays, the revised goal is added next to it, and the reason is given in the *Results* part of that iteration.
- **Sections not yet due** stay as a heading with a short "to be completed in Iteration N" note.
- **Evidence.** Results name the test date, the commit hash, and the exact command, and link a screenshot of the test or coverage report under `wiki/images/`. Where something is documented elsewhere (a README, a PR, another wiki page), it is linked, with a one-line summary of the outcome here so this page reads on its own.
- **Submission.** This page is exported to PDF and submitted to eTL at the close of each iteration.

**Schedule of sections**

| Iteration | Newly required | Kept up to date |
|---|---|---|
| 2 | 1 Test Responsibility & Schedule; 2 Unit & Integration Testing (plan and first results) | — |
| 3 | 3 AI Module Testing (evaluation set, thresholds, first results); 4 Acceptance Testing (plan) | 2 |
| 4 | 4 Acceptance Testing (results) | 2, 3 |
| 5 | 5 Alpha & Beta Testing (plan, execution, results) | 2, 3, 4 |

**What each part of this page gives evidence of.** Testing answers two questions (Week 6, *Verification vs. Validation*). Sections 1–3 are **verification**, are we building the software right: unit and integration tests, the AI evaluation set, static checks (Spotless, Android Lint, Ruff), and code review check the implementation against the R&S and the Design Documentation. Sections 4 and 5 are **validation**, are we building the right software: the UAT, the heuristic evaluation, and alpha and beta testing check it against what users need. Each test type of the course's list has a home here:

| Type of test | Question it answers | Where on this page |
|---|---|---|
| Functional | Does Pix do what the R&S says? | 2 (unit, integration), 4 (UAT) |
| Performance | Is it fast enough, and does the live view hold up? | 2.2.3 (NFR measurements: NFR-1 to NFR-12, NFR-19) |
| Usability | Can two people get the photo without help? | NFR-16 hallway test, the heuristic evaluation, 4, 5 |
| Acceptance | Does it satisfy the agreed stories? | 4 |
| Security and access control | Can it be misused? | NFR-14 and NFR-15 checks in 2 (server tests for non-friend and blocked cases) |
| Compatibility | Does it work on every supported phone? | NFR-17 device pass in 2.2.2, the pairwise device and network table in 2.1.6 |
| AI behavior | Does pose generation follow the input? | 3 |

**Contents**
1. [Test Responsibility & Schedule](#1-test-responsibility--schedule-iteration-2)
2. [Unit & Integration Testing](#2-unit--integration-testing-from-iteration-2)
3. [AI Module Testing](#3-ai-module-testing-from-iteration-3)
4. [Acceptance Testing](#4-acceptance-testing-iterations-3-and-4)
5. [Alpha & Beta Testing](#5-alpha--beta-testing-iteration-5)
6. [Test Log](#6-test-log)

---

## 1. Test Responsibility & Schedule (Iteration 2)

### 1.1 Who

Each component has one member responsible for writing and maintaining its tests. The components follow the module ownership in Design 2.9.

| Component | Layer (Design 1.1.5, 2.1) | Where the tests live | Responsible |
|---|---|---|---|
| Camera (`camera/`) | Managers and platform: `CameraController`, `CameraXController`, zoom plan, photo saving, camera controls (focus point, exposure, flash, aspect ratio) and their setting | `android/app/src/test/.../camera/` | 박동제 |
| Reference guide and overlay (`guide/`) | Managers and Views: segmentation, mask smoothing, outline, `GuideGeometry` (with the starting position), `GuideRepository`, `GuideOverlayView` | `android/app/src/test/.../guide/` | 박동제 |
| Pose generation, app side (`generation/`) | Managers: `RemotePoseGenerator`, image codec | `android/app/src/test/.../generation/` | 박재완 |
| Account, friends, invitations, app side (`account/`, `ui/account/`) | ViewModels and repositories | `android/app/src/test/.../account/`, `.../ui/` | 박재완 |
| Session, guide sync, remote control (`session/`) | Managers: `RtcSessionManager`, codec, router, `GuideSyncer`, `RemoteControlHandler` | `android/app/src/test/.../session/` | 조성민 |
| Screens and ViewModels (`ui/`) | ViewModels and Views: one test class per screen's ViewModel; View tests under Robolectric | `android/app/src/test/.../ui/` | The owner of the screen's feature |
| Server: signaling and sessions (`signaling/`) | Server module | `server/tests/test_signaling.py`, `test_registry.py` | 조성민 |
| Server: session store (`store/`) | Server module: the Redis scripts for room transitions, delivery between workers, heartbeats, the rate limit | `server/tests/test_registry.py` (several registries as workers), `test_signaling.py`, `test_poses.py` | 박재완 |
| Server: accounts, friends, invitations, TURN credentials (`accounts/`, `invitations/`, `turn/`, `db/`) | Server modules and database | `server/tests/test_accounts.py`, `test_friends.py`, `test_account_rules.py`, `test_passwords.py`, `test_google.py`, `test_database.py`, presence in `test_registry.py`; `test_invitations.py` (to be added in Iter2-P15) | 박재완 |
| Server: poses (`poses/`) | Server module | `server/tests/test_poses.py`, `test_pose_suggestions.py`, `test_prompts.py` (the prompts file) | 박재완 |
| Integration tests for the Iteration 2 flows | Across components, on two phones and the public server | Design 2.9 (Iter2-P21) | 한준형 |
| This page, CI, and coverage reports | — | `wiki/Testing-Documentation.md`, `.github/workflows/` | 한준형 |

**Policy for new code.** The author of a feature writes its tests and submits them in the same pull request as the code; a PR without tests for new logic is not merged (PR template, *Checklist*). Tests are written with the code, in short test–code cycles, and test-first for logic whose expected result is clear in advance (boundaries, protocol messages, state transitions): write the failing test, watch it fail for the right reason (an assertion, not a compile error), make it pass, then refactor with the suite green (Week 7, TDD). **Every fixed bug becomes a test**: the PR that fixes a bug first adds a test that reproduces it and fails, then the fix, and the test stays in CI (Week 6, regression). Shared contracts (Design 2.9) change only with the tests of every affected owner updated in the same PR. AI-generated tests follow the same marker and review rule as other source files (`CLAUDE.md`), and a reviewer checks that their oracle is independent of the code they test, because a test that restates the implementation passes on any bug (Week 6, *The Test Oracle*; Week 7 Part III for AI-written tests).

### 1.2 When

The stages follow the course's rule of fast feedback early and high fidelity before release (Week 7, *Which Tests Run When?*): a failing test should stop the pipeline as early and as cheaply as possible.

| Stage | When | What runs | Must pass? |
|---|---|---|---|
| Local, before pushing (seconds) | Each developer, after a change | The unit tests of the changed module (`./gradlew :app:testDebugUnitTest --tests 'com.lastpenguin.pix.guide.*'`, `uv run pytest tests/test_signaling.py`) and the formatter (`spotlessApply`, `ruff format`) | Expected; not enforced |
| Pull request (minutes) | Every push and pull request | GitHub Actions: `android-lint` (Spotless and Android Lint), `android-test` (`./gradlew :app:testDebugUnitTest`, all JVM and Robolectric tests), `server-lint` (Ruff lint and format), `server-test` (`uv run pytest`, once on fakeredis and a SQLite file, and once on a real Redis and PostgreSQL service). `server-image` also runs: it builds the server's Docker image, whose build runs the server tests, starts it with nginx, Redis, and PostgreSQL, and checks the routes, a sign-up, an access log without the Pix ID, and eight rooms through nginx | Yes: all four are required status checks on PRs to `dev`, and branch protection blocks the merge while any is red. CI passing is necessary, not sufficient: the reviewer also checks the tests themselves (1.3) and the PR's *How to verify* steps on a phone when UI or camera code changed |
| `dev` after a merge | Every push to `dev` | The same four checks; the wiki sync | A red `dev` is fixed or reverted before anyone builds on it |
| End of iteration, before `dev` → `main` (hours) | Once per iteration | The four checks, the coverage reports (2.1.4), the integration tests on two phones and the public server (2.1.5), the NFR measurements of R&S 5 that the iteration's features touch, with `Timings` logs (Design 2.8), and the mutation run on the critical modules once it exists (2.1.7) | Yes; results are recorded in 2.2, and a miss is explained there |
| Nightly | None | The suites run on the JVM and in one Python process in a few minutes, so everything automated runs on every push. Emulator-based UI tests (Espresso), if added in Iteration 3 or later, run on `dev` or nightly, not on every PR (**TBD**, 2.1.6) | — |

Tests that need a phone or an emulator (camera permission, real CameraX capture, MediaStore, two-phone sessions, push delivery) are not in CI; they are run by hand before merging to `dev` and at the end of the iteration, and their results go in 2.2.2 and 2.2.3.

### 1.3 Test quality rules

A test is only as good as its oracle, and a bad test costs more than no test (Week 6, *Writing Good Tests*). Reviewers check new tests against these rules; a test that breaks one is fixed in the same PR.

- **One behavior per test, in Given / When / Then.** The name says the subject, the input or action, and the expected result: `clamp_guideAtRightEdge_keepsTwentyPercentVisible`, `join_unknownCode_answersNotFound`. A failing test's name must tell what broke. Partitions are written as a comment at the top of the test class (2.1.6).
- **Assert observable results, not implementation details.** Tests check what a method returns, what state a `StateFlow` reaches, or what message a fake received, never private fields or the order of internal calls. This keeps tests from breaking on harmless refactoring (the *brittle* smell).
- **Every test asserts something that would fail on a plausible bug.** A test that only runs code inflates coverage without evidence (the *weak* smell; Week 7, *Coverage Is Not Correctness*). In the RIP model, coverage only shows that a line was reached; the chosen input must infect the state, and the assertion must check the output it propagates to.
- **FIRST.** Fast (milliseconds on the JVM; no real camera, network, or server), Independent (no shared mutable state or order dependence; a fresh fixture per test), Repeatable (virtual time through `runTest` and the server's `clock` fixture, fixed seeds, no real clock or randomness), Self-validating (pass or fail without reading a log), Timely (written with the code).
- **Prefer real objects and fakes over mocks.** Our module interfaces (Design 2.1) are replaced by hand-written fakes that behave like the real thing, including its errors (`InMemoryGuideRepository`, `FakeSessionManager`, the server's fake `image_api` transport, and fakeredis for the session store, which runs the same Lua scripts as Redis). A stub is used to put code into a state, a spy to record calls (for example the fake FCM sender that keeps the messages it was given), and a mock only to verify an interaction that has no observable result. Heavy mocking is a smell, not a goal.
- **Cover success, failure, and edge cases.** For every feature, at least one test for the normal path, one for each failure the R&S names (offline, rejected, not found, storage full, expired), and the boundaries in 2.1.6. Corrupted input is a standard case for every codec and endpoint: malformed JSON, a missing field, a wrong type, an unknown message type.
- **Test our code, not the framework.** No tests of CameraX, ML Kit, WebRTC, or FastAPI behavior; Activities and Fragments stay thin and are covered by Robolectric rendering and touch tests and by the manual integration tests.
- **Flaky tests are treated as bugs.** A test that passes and fails on the same commit destroys the CI signal. When one appears: rerun to confirm, quarantine it the same day (`@Ignore` with the issue number), find the cause (timing, shared state, order, a real clock or network, an unfinished coroutine such as the 2 s ping loop that keeps `runTest` from draining), fix it, and remove the quarantine. Waiting is done on conditions (`advanceUntilIdle`, `first { }`), never on `sleep`.

---

## 2. Unit & Integration Testing (from Iteration 2)

### 2.1 Plan

#### 2.1.1 Frameworks

| Component | Test framework | Mocking and isolation | Coverage tool | Notes |
|---|---|---|---|---|
| Android app, pure logic (geometry, codec, router, zoom plan, ViewModels) | JUnit 4, kotlinx-coroutines-test (`runTest`, `StandardTestDispatcher`) | Hand-written fakes behind the module interfaces of Design 2.1 (`FakeSessionManager`, fake `CameraController`, `InMemoryGuideRepository`); no mocking library | JaCoCo (`./gradlew :app:jacocoTestReport`, **TODO** Iter2-A1) | Plain JVM tests with `isReturnDefaultValues`, so `Log` and `SystemClock` are safe (android/CLAUDE.md) |
| Android app, Views and touch (`GuideOverlayView`, sliders, composition view, the reference thumbnail's drag, Subject view's video fit, screens) | JUnit 4 with Robolectric (`AndroidJUnit4`, SDK 36, native graphics) | Views inflated through a started `AppCompatActivity`; fakes as above | JaCoCo | Needs JDK 21+ |
| Server | pytest, pytest-asyncio, pytest-timeout (15 s per test), fakeredis with `lupa` | The `client`, `settings`, and `clock` fixtures in `tests/conftest.py`; the fake `image_api` transport replaces OpenRouter; the session store runs on a fresh fakeredis per test, which `open_store` connects to, `make_registry` shares between several registries standing for workers, and `outage` and `restart_store` switch off or empty; the store's scripts take the time from the `clock` fixture, so room expiry is tested without waiting; the `google` fixture (`FakeGoogle`) stands in for Google's token check in the API tests, and the real verifier is tested against a fake of Google's certificate endpoint with a key made in the test; `password_hasher` runs real argon2id at its lowest cost; `settings` pins the JWT secret, and token expiry follows the `clock` fixture; each test gets a new SQLite file (`database_url`), migrated by the app as it starts; `sign_up` makes an account and its headers; the one disconnect test runs a real Uvicorn server | pytest-cov (`uv run pytest --cov=pix_server`, **TODO** Iter2-A1) | Tests never reach OpenRouter, Google, or FCM. They use a real Redis and PostgreSQL only when `PIX_TEST_REDIS_URL` and `PIX_TEST_DATABASE_URL` name them (2.1.4) |
| Integration (two phones + server) | Manual scripts in 2.1.5, plus `Timings` logs (Design 2.8) | Real devices and the public server | — | Not in CI |

#### 2.1.2 Coverage goals by architectural layer

Our app follows MVVM with unidirectional data flow, and the system is client–server plus peer-to-peer (Design 1.1.5). Targets are **line (statement) coverage** per layer, measured by JaCoCo (app) and pytest-cov (server); **branch coverage** is reported next to it from the same run, because a suite can reach every line while never taking the `else` of a decision (Week 7, *Test Coverage Example*). The targets follow the course's reference points of 60 % acceptable, 75 % commendable, and 90 % exemplary (Google's guideline), set higher where the code is pure logic with fakes and lower where it wraps the platform. Coverage is used to find untested regions, not to certify quality: a component at its target still needs the assertion and partition rules of 1.3 and 2.1.6, and 2.1.7 checks whether the tests would notice a change.

| Layer | Target | Why this target |
|---|---|---|
| App · managers and repositories (`camera/` logic, `guide/`, `generation/`, `session/`, `account/` repositories) | **TBD** (proposal 80 %) | This is the business logic that makes Pix different (geometry, sync, remote control, protocol); it has no Android UI and is cheap to test with fakes |
| App · ViewModels (`ui/*/…ViewModel`) | **TBD** (proposal 75 %) | State and navigation logic, testable with fakes; lower than managers because some branches only matter with a real camera |
| App · Views and Fragments (`ui/`, overlay and composition views) | **TBD** (proposal 40 %) | UI layers are tested through Robolectric for rendering and touch; layout wiring and animations are checked by hand on a phone |
| App · platform adapters (`CameraXController`, `MlKit…`, `Rtc…`, `Android…Codec`) | **TBD** (proposal 30 %) | Wrap CameraX, ML Kit, and WebRTC, which need a device; covered by the integration tests instead |
| Server · `signaling`, `store`, `accounts`, `invitations`, `turn` | **TBD** (proposal 90 %) | Pure Python with fakes for every external service; every message and endpoint has a test. The store's Lua scripts run in fakeredis and are counted through these tests (2.1.3) |
| Server · `poses` | **TBD** (proposal 85 %) | The upstream call is faked; the cancellation path needs a real server and is covered by one test |
| Server · `main`, `config`, `db` migrations | **TBD** (proposal 60 %) | Startup and configuration glue |

The targets are set once in Iteration 2 and then kept; a changed target is added next to the original with the reason in that iteration's Results (rules above).

#### 2.1.3 Scope and exclusions

- **Excluded from coverage:** generated code (`BuildConfig`, ViewBinding classes, `R`), the `debug` source set, test utilities and fakes (`*Fake*`, `tests/conftest.py`, `server/tools/`, `server/scripts/`), Gradle and workflow files, and the WebRTC, CameraX, ML Kit, OkHttp, and FastAPI libraries themselves. The Lua in `server/src/pix_server/store/scripts.py` runs inside Redis, so pytest-cov sees each script as one string; the room transitions are covered by the behavior tests of `test_registry.py` and `test_signaling.py` instead.
- **External dependencies are isolated as follows.** CameraX, ML Kit, and WebRTC sit behind the module interfaces of Design 2.1 and are replaced by fakes in tests; the server's OpenRouter client is replaced by the `image_api` fake transport; Google sign-in is verified through a fake verifier in the API tests, and the real check runs against keys made in the test, served by a fake of Google's certificate endpoint; FCM is sent through a fake sender that records messages; the database is a new SQLite file per test, and in CI's second run a PostgreSQL service that each test empties first; the session store is a fakeredis instance created per test, and a Redis service only in CI's second run; the TURN server is never contacted, only its credential minting is tested.
- **Out of scope for automated tests:** real camera capture quality, hardware zoom ranges, push delivery by Firebase, and connection rates on real networks. These are covered by the manual integration tests (2.1.5) and the NFR measurements (R&S 5).

#### 2.1.4 Reproducible commands

Another developer must be able to copy these and get the same report.

**Android** (from `android/`; JDK 21 or later, for example Android Studio's bundled JBR with `JAVA_HOME` set to it; no phone or emulator needed):

```bash
./gradlew spotlessCheck :app:lintDebug :app:testDebugUnitTest
```

```bash
./gradlew :app:jacocoTestReport
```

Test report: `app/build/reports/tests/testDebugUnitTest/index.html`. Coverage report: `app/build/reports/jacoco/jacocoTestReport/html/index.html` (**TODO** Iter2-A1: the task and the exclusion list in `app/build.gradle.kts`).

**Server** (from `server/`; the Python version in `pyproject.toml`, `requires-python >= 3.11`; `uv` installed; no `.env` needed, the `settings` fixture pins the key and model):

```bash
uv sync --locked
```

```bash
uv run ruff check . && uv run ruff format --check . && uv run pytest
```

```bash
uv run pytest --cov=pix_server --cov-branch --cov-report=term-missing --cov-report=html
```

Coverage report: `server/htmlcov/index.html` (**TODO** Iter2-A1: `pytest-cov` in the dev dependencies and the `[tool.coverage]` omit list). SQLAlchemy's asyncio layer runs on greenlets, so coverage has to trace them: `[tool.coverage.run]` needs `concurrency = ["greenlet", "thread"]`. Without it, lines after a database call show as missed, and `accounts/friends.py` reads 71 % instead of 99 % (measured 2026-10-15).

The same tests on a real Redis and PostgreSQL, which is what runs the session store's scripts and the account queries on the servers. The tests empty database 0 of that Redis and the `public` schema of that PostgreSQL first. The tests that simulate a store outage are skipped, because only fakeredis can switch itself off:

```bash
docker run -d --rm -p 6379:6379 redis:8-alpine
docker run -d --rm -p 5432:5432 -e POSTGRES_USER=pix -e POSTGRES_PASSWORD=pix -e POSTGRES_DB=pix postgres:18-alpine
PIX_TEST_REDIS_URL=redis://localhost:6379/0 PIX_TEST_DATABASE_URL=postgresql+asyncpg://pix:pix@localhost:5432/pix uv run pytest
```

The tests that send two racing requests at once (two refreshes with one token, an accept and a withdrawal, two device registrations, a Pix ID change and a new password, and the like) can only fail on PostgreSQL. On SQLite every transaction holds the write lock from its start, so the requests never overlap there and these tests pass with or without the locks they check; CI's second run is what checks them.

**CI** runs the same commands in `.github/workflows/android-test.yml` and `server-test.yml`, which runs pytest twice: on fakeredis, then on a Redis service. The workflow names `android-test` and `server-test` are the required status checks.

#### 2.1.5 Integration tests (two phones and the server)

Unit tests are not enough: interface, configuration, serialization, and protocol bugs appear only when parts combine (Week 6, *Why Integration Testing?*). Two kinds of integration test cover that in Pix.

- **Automated, horizontal (in CI).** Tests that join several modules at one level with the real codec in between: `GuideSyncer` with a `FakeSessionManager` and two `InMemoryGuideRepository` instances (photographer and mirror), `RemoteControlHandler` with a fake `CameraController`, the server's signaling hub with two test sockets in one room, two registries sharing one Redis as two workers (a room created through one is joined and signaled through the other, in order, and a user's presence is seen from both), and the REST endpoints through the FastAPI test client, with the real migrations and database: a request is sent and accepted between two accounts, and a signed-in signaling socket makes its user online in the other's friend list. The codec and message tests use the JSON examples of Design 2.5 verbatim, so a field renamed on one side fails a test instead of a demo, and the shared contracts in `android/` keep both sides on the same names.
- **Manual, vertical (end to end).** The scripts below slice one user-visible flow through every layer, UI to WebRTC to server, on real phones against the public server (Iter2-P21). They are the top of our test pyramid: few, slow, and reserved for the critical journeys. Each is a numbered script with the expected result, so two testers get the same verdict.

| ID | Flow | Devices and network | Steps (summary) | Expected result | Requirement |
|---|---|---|---|---|---|
| IT-1 | Room-code session on the same Wi-Fi | S22 photographer, S23 Ultra subject, same Wi-Fi | Start, Join with the code, move the phone | Live view within 10 s; delay ≤ 0.5 s | US-13, NFR-5 |
| IT-2 | Invitation over different networks | One phone on Wi-Fi, the other on cellular | Sign in on both, add each other, Invite, Accept from the push | Push within 5 s; live view within 10 s; relay candidate pair in the `Timings` log | US-9, NFR-9, NFR-10 |
| IT-3 | Invite link fallback | As IT-2, with notifications turned off on the subject's phone | Share invite link through a messenger; open it | The *Invitation* screen opens from the link and Accept connects | FR-6.1 |
| IT-4 | Reconnection | As IT-2 | Airplane mode on the subject's phone for 5 s | The session recovers without a new invitation within 10 s | NFR-11 |
| IT-5 | Guide sync and moves from both phones | As IT-1 | Add a guide; drag it on each phone in turn. (Iter2-P11) Hide and show it on the photographer's phone, then replace it through *Guide* | Same position on both phones within 0.3 s; offset ≤ 3 % of the frame width. Added 2026-10-16 (Iter2-P11): the subject's guide starts where the photographer's does, disappears and comes back at the same place, and a replacement starts at its own reference's place on both phones | US-11, NFR-7, NFR-8, FR-3.1, FR-3.6, FR-6.7 |
| IT-6 | Remote zoom, brightness, flash, guide moves | As IT-1 | On the subject's phone: pinch, the brightness slider, the flash button, and, with the guide-edit toggle on, a drag and a pinch on the guide; 20 of each | Applied on the photographer's phone within 0.5 s and the notice shown once per gesture; both phones agree on release. The NFR-7 number is the round trip in the subject's `PixTimings` log, from `zoom.sent`, `exposure.sent`, or `move.sent` with `final` to the echo (`zoom.echo`, `guide.applied`), so no clock alignment is needed | US-12, NFR-7 |
| IT-7 | Pose description | One phone, Wi-Fi | Scene photo, type a description, Generate 4 poses. (Iter2-P12, added 2026-10-21) Then the same photo with *Skip, suggest poses for me* | Four candidates follow the description; first within 20 s, all within 30 s. With *Skip*: four different poses that suit the scene, in the same times counted from the tap, the suggestions included | US-5, FR-4.4, NFR-4 |
| IT-8 | Aspect ratios and saved photo | One phone; two for the subject's view | Choose 16:9, take a photo; repeat with 1:1. With a subject connected, change the ratio | The saved photo is 16:9 (then 1:1) and matches the preview. Added 2026-10-15 (Iter2-P10): the subject's live view, grid, and guide change with each ratio and stay on the same part of the scene as the photographer's. Added 2026-10-18 (#86): in each ratio the subject's grid lines end exactly at the video's edges | FR-1.13 |
| IT-9 | Saved guides | One phone | Save a guide, kill Pix, reopen, use the saved guide | The guide is listed and loads at its saved position | FR-3.8 |
| IT-10 | Zoom without stutter | S21 and S22 | Zoom from 0.6× to 3× and back | No visible stutter or blank preview; `Timings` numbers in the PR | NFR-19 |

#### 2.1.6 Test design techniques and where they apply

Example-based tests alone miss what nobody thought of. Each test class states its partitions in a comment at the top and names one test per partition, so a reviewer can spot a missing class without reading every test (Week 7, *Write Down Your Partitions*). The techniques of the course and where they apply in Pix:

| Technique | Rule | Where it applies in Pix (examples of partitions and cases) |
|---|---|---|
| Equivalence partitioning | Partition inputs (and outputs) into classes treated the same way; at least one test per class, not every combination | Pix ID (`PUT /me`): too short, 4–20 valid chars, too long, an invalid character, taken; a password account's change without its password, with a wrong one, or to the password itself in any case · password at sign-up: valid, too short, too long, no digit, no letter, the Pix ID itself · log-in: right password, wrong password, unknown Pix ID, an account without a password · Google sign-in: registered, `NOT_REGISTERED`, invalid token, Google unavailable; adding Google: the same one again, one another account uses, a second one; adding a password: valid, before a Pix ID, breaking a rule, already set · lookup relation: `NONE`, `SELF`, `FRIEND`, `REQUESTED`, `INCOMING` · `room.join` outcomes against the wrong-code limit: unknown and expired codes count, `FULL` does not, a new socket from the same address inherits the count, another address and an unknown address do not · pose windows: refused by the client's window, by the global one, and counted in neither when refused; suggestion windows counted apart from them · access token: valid, not a JWT, signed with another secret, `alg: none`, payload changed after signing, another issuer, a claim missing, an unknown user · refresh token: current, unknown, empty, already exchanged, exchanged several rotations ago, two refreshes with one token at once, spent longer ago than a session lasts, expired, signed out · friend requests saved at the same moment: to each other, the same one twice · room code: 6 digits valid, fewer, letters, unknown, expired · `POST /poses`: description only (the user's or a suggested pose), template only (an app from before Iteration 2, sent with the same prompt), both, neither, and an empty description next to a template (asks for the template); a description in Korean, with extra whitespace and line breaks (one line in the prompt), and blank; the image service refusing a template and a description (only the description is asked to change); the description never written to the log · `POST /pose-suggestions`: four usable poses; JSON in a code fence; more than four; a duplicate in another case, one too long, or one not a string (replaced by default poses in order); none usable (an empty list, no `poses`, not a list, not JSON, an empty message); a refused scene, not counted; with a description: three variations, more, the description repeated first, a duplicate, one too long, none (the description fills in), blank and too long (refused before a call), empty (suggestions) · `prompts.toml`: as shipped, a prompt wrapped over lines, the pose prompt without `{pose}` or with it twice, a prompt missing, empty, or unknown, a default pose without a field, too long, or not a table, an id used twice, three or five default poses, not TOML; braces in a pose sent as written · the app's request: a description with three variations, or repeated when they cannot be had, suggestions first, more or fewer than four suggestions, none; a blank or padded field (suggestions, or one line) · *Generate poses*: an empty field, typed text, a kept text, a chip on an empty and on a filled field, the last description rejected or not · zoom request: below `minZoom`, inside, above `maxZoom` · brightness request: below the range, inside, above, and a camera without a range · flash request with and without a flash · invitation status: each of the five states on accept/decline/cancel · flash: off, auto, on, and a phone without a flash · aspect ratio: 4:3, 16:9, 1:1 · exposure index inside the range, beyond it, and a camera without compensation · `camera.capabilities` and `camera.state` from a phone without the Iteration 2 fields, and a malformed `exposureRange` (one value, min above max, not an array) · a new guide's anchor: none (the subject's copy), inside the photo, smaller than the minimum size, not finite; a reference taller or wider than the frame, in each ratio · the reference thumbnail: a tall, wide, or square photo, small or enlarged; a tap, a move within the touch slop, a drag released in each quarter of its area, a drag the system cancels · Subject view's video: in each ratio (4:3, 16:9, 1:1), before its first frame; a pinch in the video and in the margin around it |
| Boundary testing | Test at, just inside, and just outside each edge: zero, min and max, empty, null, off-by-one, `>` vs `>=` | Guide `height` 0.21 and 2.1 (30 % and 300 %), and one step beyond · the 20 % visibility rule at the exact clamp value · opacity 0.1 and 0.9 · description of 100 and 101 characters, counted after trimming, so 100 with spaces around them is accepted; a suggested pose and a default pose of 100 and 101; the field cutting input at 100 · the suggestion model answering after its 8 s limit, so the default poses, or the description for its variations, still come in time; the app's 10 s wait for variations · invitation at 29 s, 30 s, 31 s on the server's `clock` fixture · room code at 10 min · reconnection at 10 s · chunk size 12 KiB and `bufferedAmount` 256 KiB · upload of exactly 1 MiB and 1 MiB + 1 byte (`413`) · the last wrong room code allowed and the one after, and one second before and at the end of the 10-minute window · the global pose window full and one window later · an empty friend list, one friend, many · Pix ID of 3, 4, 20, and 21 characters; password of 7, 8, 64, and 65; display name of 30 and 31 · an access token one second before and at its 15 minutes; a refresh token one second before and at 30 days without use · Google's keys one second before and at their max-age · an exposure index at each end of the range and one past it · a tap at the frame's corners, just outside the frame, and not finite · the tap's mapping at each quarter turn of the camera buffer and with the 16:9 crop's offset · a reference thumbnail dropped exactly in the middle of its area (right and bottom win) · an enlarged thumbnail limited by half the preview's width, and by its area's height · an area not laid out yet (the thumbnail stays small) |
| Pairwise (combinatorial) | Most failures involve one or two parameters; cover every pair of values at least once instead of every combination | Manual device and network matrix for 2.1.5 and NFR-10: phone (S21, S22, S23 Ultra) × network (same Wi-Fi, different Wi-Fi, Wi-Fi + cellular, cellular + cellular) × aspect ratio (4:3, 16:9, 1:1); a pairwise table instead of 36 runs, recorded in 2.2.2 |
| Control-flow (white box) | Every branch and condition takes both outcomes; read the branch coverage report for the untaken side | `GuideGeometry.clamp` (each limit on and off), `CameraZoomPlan` (ultrawide routing: native sub-1×, separate ultrawide with and without range overlap, none), `ChannelRouter` (realtime vs reliable, `final`), `RequestBodyLimitMiddleware`, the subject's request handlers (`RemoteControlHandlerTest`): zoom, brightness, flash, and a guide move each applied, clamped, confirmed at once when already applied, or answered with the unchanged state when the camera lacks the capability or the guide is hidden; permission on and off once the switch exists |
| State-based | Test every valid transition and every event that must be rejected, comparing the state after each event | `SessionState` (Design 2.4, Figure 9): each arrow, and rejected events such as `join` while `Connected`, `peer.left` while `Idle`, a stale `seq`; the invitation lifecycle (Figure 9b) on the server; a friend request: sent, accepted, declined, withdrawn, a reverse request that accepts it, and only the receiver answering or the sender withdrawing; an accept at the same moment as a withdrawal or a decline, of which exactly one takes effect; a request sent while the reverse one is accepted, after which only the friendship is left; two device registrations at once for one session, and one token registered by two sessions at once; a Pix ID change and a new password sent at once from two phones, which never end up the same; two Google accounts added at once, of which the first stays; `GenerationUiState.phase` (*Generate poses* between the photo and the requests; a description rejected in full to *Couldn't create poses* and back with *Edit description*, in part to *Pick a pose*; *Try again* asking for new suggestions); the guide-edit mode toggle on both screens (on and off × guide added, hidden, removed; the shutter; a tap without a guide), and one pinch zooming or editing the guide by the mode alone; the subject's kept room code (`SessionViewModelTest`): kept on Session not found and Connection lost and for another try after a failed reconnect, forgotten on Leave, the photographer's End session, a room gone while reconnecting, and Start as the photographer · the flash cycle (off → auto → on → off) and the ratio cycle (4:3 → 16:9 → 1:1 → 4:3), a ratio tap while a photo is saved, a ratio restored at start, and a guide clamped again when the ratio narrows the frame · *Hide* and *Show* (a final change; the guide comes back where it was; guide-edit mode ends; without a guide nothing changes) · the reference thumbnail small or enlarged: a new or removed guide makes it small, moving or hiding the same guide does not, and a guide without a reference never enlarges it; its corner is kept for the next guide · *Add a pose guide* asking to replace only while a guide is on the camera; a gesture's sender (`StepSenderTest`): the first step, queued steps with the newest winning, the final value, a cancel; Subject view's own guide, brightness, and flash values held against echoes until the gesture ends and settled by the first echo after it (`SubjectViewModelTest`) |
| Property-based and metamorphic | When the exact output is hard to state, check a relation that must always hold | `GuideGeometry`: `viewToFrame(frameToView(p)) == p` for any frame and view size; `clamp(clamp(s)) == clamp(s)`; the guide covers the same part of the image on two view sizes within 3 % of the frame width (NFR-8); the codec: `decode(encode(m)) == m` for every message type with random field values; the mask smoother keeps the person's bounding box within a tolerance and never enlarges the mask beyond the margin; an outline is a subset of the dilated mask; a guide clamped for any aspect ratio is not moved again by the subject's mirror, which clamps for the narrowest frame (`CameraControlsTest`). Tools: hand-written loops over generated values now; a property-testing library (Kotest property module, Hypothesis) is **TBD** for Iteration 3 |
| Fault injection | Simulate failures and check that the app survives them | The fake transport answers `502`, `504`, `429`, a truncated body, and a disconnect; the fake session drops the channel mid-transfer and replays a stale `seq`; the fake camera reports `UNAVAILABLE`; storage full on save (FR-1.7); `NoPersonFound`; the session store switched off (`outage`) or emptied as by a restart (`restart_store`), after which every socket of the worker closes with 1013 and pose requests answer `503 UNAVAILABLE`; a worker whose heartbeat ran out, whose signed-in users go offline when it is swept; Google's certificate endpoint answering `500` or unreachable; an expired or invalid access token on the signaling socket, which leaves the socket anonymous; the account database unreachable or dropping its connection, which answers `503 UNAVAILABLE`; the session store down during an accept, which answers `503` and saves nothing; an `OSError` that does not come from the database driver, which stays a 500; a sign-in waiting for its password check and adding Google while Google answers, during which another account request still goes through; a burst of password hashes and checks beyond the two a worker runs at once, which wait their turn; the suggestion model failing, answering late, unreachable, or answering nothing usable, after which the four default poses are used, or the description in place of its variations |
| Fuzz | Many random or malformed inputs | Random bytes and random JSON to `MessageCodec.decode` and to the signaling socket: the receiver ignores or rejects, never crashes |

Cyclomatic complexity stays under 10 per method; a method that needs more than ten tests for its branches is split before it is tested (Week 7, *Cyclomatic Complexity*).

#### 2.1.7 Test adequacy beyond coverage: mutation analysis

Coverage shows which lines ran, not whether the tests would notice a change. From Iteration 3 (**TBD**), a mutation run scores the tests of the critical, pure-logic modules once per iteration, not on every commit: PIT (Kotlin) for `guide/GuideGeometry`, `guide/MaskMath`, `session/protocol/`, `camera/CameraZoomPlan`, and mutmut (Python) for `signaling/registry` and the invitation state logic. (Added in 1.1: the room transitions moved into the Lua scripts of `store/scripts.py` (Design 2.7), which mutmut cannot mutate; how to score their tests is **TBD** for Iteration 3, and mutmut still covers the Python in `signaling/registry`.) Mutants that survive are read as missing cases (typically a boundary or an untested branch) and turned into tests; equivalent mutants are listed with a reason. Proposed threshold: mutation score ≥ 70 % on these modules, declared here before the first run and reported in 2.2 with the date and commit. Until the tooling exists, the boundary pairs of 2.1.6 serve the same purpose by hand: each decision has a test on both sides of its edge.

### 2.2 Results

Results are added per iteration and never overwritten; each iteration gets its own subsection.

#### 2.2.1 Iteration 2 (**TODO**: filled in at the close of Iteration 2, by 10/24)

**Run.** Date: 2026-10-DD · Commit: `<hash>` on `dev` · Tester: <name> · Commands: those in 2.1.4.

**Summary table.**

| Component | Frameworks | Target (2.1.2, line) | Actual line | Actual branch | Date | Command / Report |
|---|---|---|---|---|---|---|
| App · managers and repositories | JUnit 4, coroutines-test, JaCoCo | TBD % | — % | — % | 10/DD | `./gradlew :app:jacocoTestReport` · [report](images/testing_it2_jacoco.png) |
| App · ViewModels | JUnit 4, coroutines-test, JaCoCo | TBD % | — % | — % | 10/DD | same |
| App · Views and Fragments | JUnit 4, Robolectric, JaCoCo | TBD % | — % | — % | 10/DD | same |
| App · platform adapters | — | TBD % | — % | — % | 10/DD | same |
| Server · signaling, store, accounts, invitations, turn | pytest, pytest-cov | TBD % | — % | — % | 10/DD | `uv run pytest --cov=pix_server --cov-branch` · [report](images/testing_it2_pytest_cov.png) |
| Server · poses | pytest, pytest-cov | TBD % | — % | — % | 10/DD | same |
| Server · main, config, db | pytest, pytest-cov | TBD % | — % | — % | 10/DD | same |

**Test counts.**

| Suite | Tests | Passed | Failed | Skipped | Evidence |
|---|---|---|---|---|---|
| Android unit and Robolectric (`:app:testDebugUnitTest`) | — | — | — | — | [screenshot](images/testing_it2_android_tests.png) |
| Server (`uv run pytest`) | — | — | — | — | [screenshot](images/testing_it2_server_tests.png) |
| Server on Redis (`PIX_TEST_REDIS_URL=… uv run pytest`) | — | — | — | — | the `server-test` log |

**Known failing or skipped tests.** None, or one line each: the test, why, and the issue that tracks it. Quarantined flaky tests (1.3) are listed here with the date they were quarantined.

**Untested regions found by the coverage report.** The branches and files the report shows as untaken, with the case that is missing and the issue that adds it, or the reason it stays untested (2.1.3).

**Shortfalls against the targets.** For each component below its target: the reason and what will be done, with the issue number.

#### 2.2.2 Iteration 2 · integration tests (2.1.5)

Device and network pairs follow the pairwise table of 2.1.6; the pair actually used is in the *Devices* column.

| ID | Date | Devices | Result | Measured | Notes |
|---|---|---|---|---|---|
| IT-1 | 10/DD | S22 + S23 Ultra | pass / fail | delay — s | |
| IT-2 | 10/DD | | | push — s, live — s, pair `relay` | |
| IT-3 | 10/DD | | | | |
| IT-4 | 10/DD | | | recovered in — s | |
| IT-5 | 10/DD | | | — ms, — % | |
| IT-6 | 10/DD | | | — ms | |
| IT-7 | 10/DD | | | first — s, all — s | |
| IT-8 | 10/DD | | | | |
| IT-9 | 10/DD | | | | |
| IT-10 | 10/DD | S21 + S22 | | before — / after — | |

#### 2.2.3 Iteration 2 · NFR measurements

One row per NFR that the iteration's features touch (R&S 5, "How we check it"), with the method named there.

| NFR | Target | Measured | Device, network | Date | Evidence |
|---|---|---|---|---|---|
| NFR-5 | ≤ 0.5 s Wi-Fi, ≤ 0.8 s relay | — | | 10/DD | `Timings` log excerpt |
| NFR-7 | ≤ 0.3 s guide, ≤ 0.5 s remote action | — | | | |
| NFR-9 | push ≤ 5 s (95 %), live ≤ 10 s | — | | | |
| NFR-10 | ≥ 90 % per network pair | — / 10 per pair | | | |
| NFR-11 | recover ≤ 10 s | — | | | |
| NFR-19 | no stutter, no blank preview | — | S21, S22 | | |

**Mutation analysis (2.1.7).** Not run in Iteration 2; the tooling is set up in Iteration 3.

#### 2.2.4 Iteration 3

To be completed in Iteration 3.

---

## 3. AI Module Testing (from Iteration 3)

To be completed in Iteration 3. The AI module under test is pose generation (Design 2.6.3): the prompt pipeline and the image-editing model behind `POST /poses`, with a description or a template as input. *Added 2026-10-21:* without a description the input is now a pose that a vision model suggests for the scene (`POST /pose-suggestions`), so the evaluation also covers the suggestion model, with the scene photo as its only input. With a description, three of the four candidates follow variations of it written by the same model; the variation test of Design 2.6.3 had a second vision model judge each set for how many different poses it holds and whether each candidate follows the description, which the evaluation can reuse.

### 3.1 Plan

- **Frozen evaluation set.** A file committed to the repository with at least 30 cases: a scene photo, a template or a description, and the expected behavior (the same person and place, the described pose, the whole person in the frame). Path: **TODO** (proposal `server/eval/pose_cases.json` with the photos under `server/eval/scenes/`). Once submitted, cases may be added but not removed or edited; additions are listed here.
- **Ground truth and evaluation design.** A good pose image has no single correct answer, so the cases are judged by human rating on a fixed scale by at least two team members, with the rubric below, plus automatic checks where they apply. The plan is discussed with the TA before the first run. **TODO**: how the ratings are collected and who rates.
- **Assertion method per case.** The oracle problem is the hard part here: there is no reference image to compare against, so the verdict combines automatic properties and human judgement (Week 6, *The Test Oracle*; Week 7, metamorphic relations). Automatic: schema validity of the response (a decodable JPEG of the expected size), latency within 30 s, a pose or person detector that confirms one person in the frame (**TBD**), and metamorphic checks (the same scene with two seeds gives two different poses; a description and its Korean translation give the same pose class). Human: a 1–5 rating on identity kept, place kept, pose followed, and framing kept, with 4 or more counting as a pass, by two raters with the disagreement rate reported. Exact image matching is not used.
- **Thresholds declared in advance.** **TODO** (declared and committed before the first run; proposal: schema validity = 100 %, latency ≤ 30 s for ≥ 95 % of cases, human pass rate ≥ 80 % overall and ≥ 75 % for described poses).
- **Test settings and command.** Model and version (`PIX_POSE_MODEL`), quality, seed handling, prompt version (Design 2.6.3), and the command that runs the set and prints the metrics: **TODO** (proposal `uv run scripts/eval_poses.py --cases eval/pose_cases.json`).

### 3.2 Results

To be completed in Iteration 3: each declared metric with its value and pass/fail, the date and commit, a short failure analysis (which cases failed, why, what changed), and an explicit note if the settings changed between iterations.

---

## 4. Acceptance Testing (Iterations 3 and 4)

### 4.1 Plan (Iteration 3)

To be completed in Iteration 3. The UAT is validation (are we building the right software?), so its stories are black-box: written from the R&S, run by a tester who follows the procedure without knowing the code, and judged on what the user sees. At least five user stories from R&S 4.4 are chosen for the UAT and not changed afterwards; the current candidates are US-2, US-4, US-9, US-10, US-11, and US-12 (marked "UAT candidate" in the R&S). For each story: the acceptance criteria (the observable conditions under which it passes, from its Given–When–Then table) and the procedure a tester follows, step by step, with the devices and the network.

| Story | Acceptance criteria | Procedure | Devices |
|---|---|---|---|
| US-? | | | |

### 4.2 Results (Iteration 4)

To be completed in Iteration 4: for each story, pass/fail, the test date, the tester, and notes on any deviation from the criteria.

| Story | Result | Date | Tester | Deviations |
|---|---|---|---|---|
| US-? | | | | |

---

## 5. Alpha & Beta Testing (Iteration 5)

To be completed in Iteration 5. Alpha testing is done by people in the course but outside the team (another team or the TAs) in a setting where we observe them; beta testing by external users on their own phones without us present. Alpha runs first, serious issues are fixed, then the beta build is released.

### 5.1 Plan

- **Alpha · participants and setting.** Who, how many, where and how the sessions run (in person with two phones, screen recording).
- **Alpha · scenarios.** The UAT stories of section 4 plus free exploration; what is recorded (task completion, observed confusion, errors, time per task).
- **Beta · release.** How the build is distributed (a GitHub release tag with the APK), the version and commit, and the release date.
- **Beta · participants and schedule.** Target number of external testers, how they are recruited, and the testing period.
- **Feedback and bug handling (both).** How feedback is collected (form, interview, GitHub issues with the `bug` label), how bugs are triaged by severity, and how fixes are verified (a test that fails before the fix, then a re-run of the scenario).

### 5.2 Results

- **Alpha.** Dates, number of testers, issues found with severity, and which were fixed before the beta release, with links to issues and PRs.
- **Beta.** The release actually used (tag), number of participants, period; quantitative results (task completion, ratings) and the main qualitative feedback; bugs found with severity and which were fixed before the final release, with links.

---

## 6. Test Log

One line per recorded run, newest first, so the history of the numbers is visible without reading the sections above.

| Date | Iteration | What ran | Commit | Outcome | Section |
|---|---|---|---|---|---|
| 2026-10-DD | 2 | Unit, Robolectric, server, coverage | `<hash>` | — tests, — failed; coverage app — % / server — % | 2.2.1 |
| 2026-10-DD | 2 | Integration IT-1 … IT-10 | `<hash>` | — of 10 passed | 2.2.2 |
