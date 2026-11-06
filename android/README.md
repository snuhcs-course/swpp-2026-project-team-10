# Pix Android app

Kotlin, XML Views with ViewBinding. minSdk 29, targetSdk 36, compileSdk 36. Open this `android/` folder in Android Studio.

This README says where the code is, how to run and try each part, and how to lint and test. The [Design Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation) describes the design, and the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation) describes the tests.

## What is here

This folder holds one Android app with these parts:

- One activity with a navigation graph (`res/navigation/nav_graph.xml`). `PixNavHostFragment` in `ui/ScreenTransitions.kt` hosts the graph and gives every move between screens its transition ([Design 2.1](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#21-android-app-structure)).
- The screens of R&S 6, as Fragments and bottom sheets under `ui/` ([Screens](#screens)).
- The modules behind the shared contracts. `core/AppContainer.kt` creates them ([Shared contracts](#shared-contracts)). The package layout in [Design 2.1](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#21-android-app-structure) lists the packages and the module classes.

### Shared contracts

These files fix the contracts between the module owners. Design 2.1 marks them with \*.

| File | What it fixes | Design |
|---|---|---|
| `camera/CameraController.kt` | Camera interface, `CameraStatus`, `CameraCapabilities`, `CameraState`, `CameraFacing`, `AspectRatio`, `FlashMode`, `FrameSink` | 2.1, 2.3 |
| `guide/GuideModels.kt` | `ReferenceGuide`, `GuideState`, `NormalizedRect`, `PhotoCrop`, `GuideSource` | 2.3 |
| `guide/GuideRepository.kt`, `guide/ReferenceGuideMaker.kt` | Guide store and reference-to-guide interfaces | 2.1 |
| `guide/SavedGuideStore.kt` | Saved guides on the phone, the library. `SavedGuideStore` (with `thumbnail`, which a cell reads when it shows), `SavedGuide` (with its source), `GuideSaved`, and `SavedGuideStore.InMemory` for tests. | 2.1, 2.3 |
| `generation/GenerationModels.kt`, `generation/PoseGenerator.kt` | Pose requests, planned poses, generation and candidate events, generator interface | 2.1, 2.3 |
| `generation/PoseTipper.kt` | How to hold the phone for a generated pose: `PoseTipper`, `PoseTip` | 2.1, 2.6.3 |
| `generation/PersonDetector.kt` | The person check on the phone before the app sends a scene photo: `PersonDetector` | 2.1, 2.6.3 |
| `generation/PromptHistory.kt` | The descriptions used before, on this phone: `PromptHistory`, `PromptEntry`, and `PromptHistory.InMemory` for tests | 2.1, 2.6.3 |
| `session/SessionModels.kt`, `session/SessionManager.kt` | Roles, entries, session states, session interface | 2.1, 2.3, 2.4 |
| `session/protocol/SessionMessage.kt` | Data channel messages between the two phones (`PROTOCOL_VERSION` 3) | 2.5.1 |
| `session/signaling/SignalMessage.kt` | WebSocket signaling messages | 2.5.2 |
| `core/network/PixApi.kt`, `core/network/ApiModels.kt` | The REST API that the app uses: `PixApi` (poses and pose tips), `PixAuthApi` (sign-in), `PixAccountApi` (endpoints with a token) | 2.5.3 |
| `account/AccountModels.kt`, `account/AuthRepository.kt`, `account/FriendsRepository.kt` | `Account`, `AuthState`, friends and requests, invitations, `AccountError`, and `AccountException`. The sign-in and friends interfaces. | 2.1, 2.3 |
| `settings/AppSettings.kt`, `settings/AppLanguage.kt`, `guide/GuideLook.kt` | The settings screen's choices. `AppSettings` holds the guide's look, the latency indicator, the base brightness, and the tutorial tips seen. `AppLanguage`, `GuideLook`, `GuideColor`, `GuideThickness`. `AppSettings` and `AppLanguage` each have an `InMemory` for tests. | 2.1, 2.6.8 |

### Screens

Every screen except the tutorial's tips is a destination in `res/navigation/nav_graph.xml`, and the app starts on Camera. The screens of one flow share a ViewModel in the activity scope. The failure screens share `fragment_failure.xml`.

| Screens (R&S 6) | Classes in `ui/` | ViewModels |
|---|---|---|
| Camera, Camera + guide, Photo saved, live light, Scene photo, Scene photo · Review | `camera/CameraFragment` | `CameraViewModel`, `SessionViewModel`, `GenerationViewModel`, `SettingsViewModel` |
| Add a pose guide (bottom sheet) with *My library*, Consent notice | `guide/AddGuideSheet`, `SavedGuideList` | `ReferenceViewModel`, `GenerationViewModel`, `SettingsViewModel` |
| Library | `guide/LibraryFragment`, `LibraryAdapter` | `ReferenceViewModel` |
| Reference confirm (with a screenshot's cut), No person found | `guide/ReferenceConfirmFragment`, `ScreenshotCutView`, `NoPersonFoundFragment` | `ReferenceViewModel`, `SessionViewModel`, `SettingsViewModel` |
| Generate poses | `generation/DescribePoseFragment` | `GenerationViewModel`, `SessionViewModel`, `SettingsViewModel` |
| Generating poses, Pick a pose, Couldn't create poses | `generation/GeneratingFragment`, `PickPoseFragment`, `GenerationFailedFragment` | `GenerationViewModel`, `ReferenceViewModel`, `PoseSaveViewModel`, `SessionViewModel` |
| Who takes the photo? (bottom sheet), See their camera, Session not found | `session/ShootTogetherSheet`, `JoinCodeFragment`, `SessionNotFoundFragment` | `IncomingInvitationsViewModel`, `SessionViewModel` |
| Share my camera | `account/FriendsFragment` | `FriendsViewModel`, `SessionViewModel` |
| Add a friend, No user found, Sent requests | `account/AddFriendFragment`, `SentRequestsFragment` | `FriendsViewModel` |
| Sign in, Sign up, Pix ID setup, Account (bottom sheet) | `account/SignInFragment`, `SignUpFragment`, `PixIdSetupFragment`, `AccountSheet` | `AccountViewModel` |
| Room code and Invite sent (bottom sheets), Invite not accepted | `session/RoomCodeSheet`, `InviteSentSheet`, `InviteNotAcceptedFragment` | `SessionViewModel` |
| Invitation (deep link `pix://invite/{invitationId}`) | `session/InvitationFragment` | `InvitationViewModel`, `SessionViewModel` |
| Subject view, the subject's Generate poses, Generating poses, Pick a pose, and Your reference, Connection lost | `session/SubjectFragment`, `SubjectDescribeFragment`, `SubjectGeneratingFragment`, `SubjectPickPoseFragment`, `SubjectConfirmFragment`, `ConnectionLostFragment` | `SubjectViewModel`, `SubjectDescribeViewModel`, `SubjectGeneratingViewModel`, `SubjectPickViewModel`, `SubjectConfirmViewModel`, `SessionViewModel`, `SettingsViewModel`, `PoseSaveViewModel` |
| Settings, How to use Pix, About Pix | `settings/SettingsFragment`, `HelpFragment`, `AboutFragment` | `SettingsViewModel`, `GenerationViewModel` (the AI agreement) |
| The tutorial's tips (a dialog, not a destination, FR-10.4) | `settings/TutorialFragment` | `SettingsViewModel` |

## How to work on it

- **Fakes.** To work before another owner's module is ready, write a fake of its interface. Use the fake in `core/AppContainer.kt`.
- **Contracts.** Change a contract only through a pull request that the affected owners review. Update the Design Documentation in the same change.
- **Libraries.** Versions are in `gradle/libs.versions.toml`. Add the ones your module needs to `app/build.gradle.kts`.
- **Server address.** `BuildConfig.SERVER_URL` depends on the build type. In a release build, it is Server A, `http://swpp26t10.147-46-242-169.sslip.io/`, which runs `main` and answers only on the campus network. In a debug build, it defaults to the AWS server, `http://3.105.185.251/`. The AWS server is permanent and runs `dev`. Unlike the course servers, it answers from any network, cellular data included. To use another server, put its address in `local.properties` as `pix.serverUrl=...`, or pass `-Ppix.serverUrl=...` to Gradle. Git does not track `local.properties`. Use the laptop's address on the test Wi-Fi, such as `http://192.168.0.10:8000/`. From the emulator, use `http://10.0.2.2:8000/`, the host machine. The servers have no HTTPS, so the app allows plain `http://` and `ws://` traffic. Release builds allow it only to the two course servers (`src/main/res/xml/network_security_config.xml`). Debug builds allow it to any host (`src/debug/res/xml/network_security_config.xml`).

**Look.** Every screen uses the dark-only `Theme.Pix` (`res/values/themes.xml`). The design tokens are in `colors.xml`. They are the ground and the surfaces, glass for controls over the preview, and one accent (`#F2C14E`). They also include the live color (`#36D399`), the guide color (`#5EEAD4`), the danger color, and the red of a saved pose's ♥ (`pix_heart`). The type styles (`TextAppearance.Pix.*`) and the component styles (`Widget.Pix.*`) are in `styles.xml`. The component styles cover primary, secondary, text, glass, icon, and segment buttons, chips, sliders, cards, sheets, and dialogs.

The fonts are Manrope, DM Mono, and Pretendard in `res/font` (SIL OFL 1.1, with the licenses in `assets/licenses`). A text style names the alias of its weight, `@font/pix_medium` to `pix_extrabold`, not a font file (see *Language* below). Icons are vector drawables in `res/drawable` (`ic_*`). Their `_small` versions are 16–20 dp, for use next to text. The app icon is the adaptive icon in `res/mipmap-anydpi`. It shows the shutter P from [`branding/`](../branding/) on a paper tile (`ic_launcher_background`). `ic_launcher_monochrome` serves themed icons. Notifications use `ic_notification`, the small version of the logo.

The team's UI mockups are the visual reference. Where a mockup differs from the built behavior, the behavior wins. Two examples are pinch zoom with a guide-edit mode toggle instead of zoom chips, and the system number keyboard instead of an on-screen keypad.

**Language.** English is in `res/values/strings.xml`, and Korean is in `res/values-ko/strings.xml`. Both files use the same keys in the same order (FR-10.2). A new string gets its Korean in the same PR, or lint fails. [Design 2.8](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#28-implementation-decisions) (*Language*) and [2.6.8](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#268-settings-iteration-3) say how Android and *Settings › Language* choose the language.

Korean is in 해요체 (사진을 저장했어요). A name in a message takes 님 (`%1$s님이 나갔어요`), and a number takes 에. Thus no particle depends on how the name or the number ends. Only strings without words (ratios, counters, `@%1$s`) are `translatable="false"`.

Every text appearance sets `android:lineBreakWordStyle="phrase"`. Thus on Android 13 and later, Korean lines break between words. Android 10–12 break them inside words. Manrope has no Hangul, so on a phone in Korean every screen uses Pretendard, Latin letters included. `res/font` points the `pix_*` aliases to Manrope, and `res/font-ko` points them to Pretendard.

The app asks the server for screen text in the screens' language. So far, the only such text is the pose tip. `screenLanguage` (`generation/ScreenLanguage.kt`) gives `ko` when `values-ko/bools.xml` sets `screens_in_korean`, and `en` otherwise. `ScreenLanguages` follows the language from the application and from `MainActivity`.

## Camera

`camera/CameraXController` implements `CameraController` with CameraX. The camera binds to the Camera screen's view lifecycle. [Design 2.6.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#262-guide-geometry-shared-coordinates) describes the viewport, the aids, the guide-edit mode, and the layout. [Design 2.6.4](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#264-video-pipeline) describes the front camera's mirror, and [Design 2.6.5](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#265-remote-control) describes the zoom and the lenses. [Design 2.8](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#28-implementation-decisions) describes the controls (*Aspect ratio and camera controls*, *Camera labels and brightness*). R&S 6.2 shows the screens. These rules apply to code that uses the camera:

- **Status.** `CameraController.status` is `IDLE`, `STARTING`, `READY`, or `UNAVAILABLE`. Screens enable capture only at `READY`, and they offer a retry when the camera fails to start or open. A failed capture leaves the camera state and the guide state intact.
- **Zoom.** The UI follows the zoom that the camera applied, and it does not assume that a requested zoom succeeded. Thus remote zoom can use the same controller. During a pinch, the requested ratio accumulates from the ratio where the pinch began. Asynchronous camera updates do not move the request under the fingers.
- **Lens switch.** A lens change requested during a photo save waits until the capture ends. If an ultrawide cannot bind, the controller returns to the primary camera and updates the supported range.
- **`grabFrame()`.** It copies the latest analysis frame before its first suspension. It returns an upright, cropped bitmap, and the caller owns that bitmap. Call it while a screen has the camera bound. The Camera screen's shutter calls it for the scene photo, in `GenerationViewModel.takeScene`. Keep the returned scene for generation retries.
- **`setFrameSink()`.** The sink gets YUV frames on a serial analysis thread, with a `mirrored` flag for the front camera. The receiver must obey the crop and rotation metadata and copy what it needs. It must close the proxy before it returns. It must not block on network work.
- **Overlays.** A touch on the letterbox next to the image never edits the guide. At 1:1, the screen does not clip the 4:3 area but darkens the image outside the square (`CameraCompositionView.dimOutside`). The screen starts the horizon bar's sensor on resume and stops it on pause.
- **Timings.** The `PixTimings` log has `app.start`, `camera.preview`, and the camera capture events. Design 2.6.5 lists the zoom measurement's events.

### Camera verification

Run the commands in [Lint, format, and test](#lint-format-and-test) and `./gradlew :app:assembleDebug`. The [integration scripts](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#appendix-a-integration-test-procedures) of the Testing Documentation (Appendix A) cover the ratios (IT-8), the zoom (IT-10), and selfie mode (IT-18). They also cover the changes on screen (IT-30) and the camera's controls (IT-34). No script covers these checks. Do them on a phone or on an emulator with a camera:

1. Start the app fresh and grant the camera permission. On another start, deny it, grant it through *Open settings*, and return to Pix.
2. Tap the shutter many times while a photo saves. Only one capture should be in flight.
3. The camera should resume after the guide sheet, the room-code sheet, the gallery, the background, and a full-screen destination.
4. On the S22 and the S23 Ultra, the preview, the saved image, and `grabFrame()` should have the same framing and rotation. After a file-write failure or a camera failure, the shutter should work for a retry.
5. The grid should divide only the camera image into equal thirds, also after a save message changes the preview area. With the phone pointed straight down or up, the horizon bar should disappear. The bar should resume after a return from the gallery. Photos should not contain the aids.
6. At a zoom limit, reverse the pinch without lifting the fingers. The readout should follow and not jump back. Try the readout's Zoom in and Zoom out TalkBack actions.
7. On a phone with native zoom below 1× and on a phone with a separate rear ultrawide, the minimum should be the real one. The readout should stay in primary-camera units. A photo taken while a pinch crosses the lens boundary should finish saving before the lens changes. If an ultrawide bind fails, the primary preview should recover. Below 1×, the app should restore the zoom after a return from the gallery and from the background.

An emulator smoke check does not establish the resolution, startup latency, or frame rate of a real device.

## Real-time session

Two phones connect over the Pix server, with a room code or an invitation. Then they stream phone to phone with WebRTC. The Design describes the states ([2.4](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#24-state-machines)), the data channel messages ([2.5.1](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#251-data-channel-protocol-phone--phone)), the signaling ([2.5.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#252-signaling-protocol-phone--server-websocket)), and the video ([2.6.4](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#264-video-pipeline)). It also describes remote control and the shared steps ([2.6.5](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#265-remote-control)), invitations ([2.6.6](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#266-invitations-and-push-iteration-3)), and reconnection ([2.6.7](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#267-reconnection-and-sessions-across-networks-iteration-3)).

- **Run it.** Start the server on the laptop (`server/README.md`), and set `pix.serverUrl` as in *Server address*. Install the debug build on both phones. Put the laptop and both phones on one Wi-Fi that allows device-to-device traffic. Campus networks usually do not allow it, but a phone hotspot does, also for the phone that hosts it. The peer connection enumerates the network interfaces itself, because Android's network monitor does not report the hotspot interface. On one phone, *Shoot together › I'll take the photo › Show a code* shows the code. On the other phone, *Shoot together › I'll be in the photo* joins it.
- **Across networks.** Use the default AWS server, which every network reaches. With a TURN key on the server, each phone also gets the relay (`server/README.md`). To try the relay on one Wi-Fi, build with `-Ppix.relayOnly=true`, or set `pix.relayOnly=true` in `local.properties`. The debug build then uses only the relay. The first session on a phone's mobile data shows "The live view uses mobile data." once on that phone (FR-6.13). When the server refuses a join for too many wrong codes from the network, *See their camera* says so.
- **One phone is enough to test either side.** `server/tools/fake_photographer.py` plays the photographer for Subject view. `server/tools/fake_subject.py` plays the subject for the photographer's phone. The [server README](../server/README.md#testing-the-photographers-phone-without-a-second-phone) says what each tool sends and how to run it.
- **Reconnecting.** To try it, put one phone in airplane mode for 5 s during a session (IT-4). The server must support `session.join`.
- **Watch it.** `adb logcat -s PixTimings PixSession PixSignaling PixPeer` shows these lines ([Design 2.8](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#28-implementation-decisions), *Measurement hooks*):
  - The session steps: `room.created`, `peer.joined`, `offer.sent`, `ice.connected`, and `session.connected`. At the end, the log gives the reason that the session ended.
  - `rtt` and `ping.received`. Both carry the ping's `ts`, so you can align the logs of the two phones.
  - A `stats` line every 2 s, an `ice.pair` line for each new candidate pair, and a `frames` line that counts the frames that go to WebRTC.
  - `ice.disconnected` when the link drops. While the link recovers: `session.reconnecting`, `signaling.rejoined`, `ice.restarted`, and `session.reconnected <ms>`.
  - Remote control: `zoom.sent` and `zoom.echo` on the subject, and `zoom.received` and `zoom.applied` on the photographer (NFR-7).
  - Sync: `guide.sent` and `guide.applied`, also for the reference and the confirm. `pose.sent` and `pose.applied`, also for the description.
  - With `PixGuideSync`, `PixDescriptionSync`, `PixPoseSync`, or `PixConfirmSync` in the filter: a dropped or refused transfer and the reason.
- **Screens.** Screens draw from `SessionViewModel.state`. They navigate on `SessionViewModel.transitions`, a one-shot stream of state changes, so a return to a screen never replays an old navigation. The "… left" notices come from `SessionViewModel.notices`.
- **Session modules.** `SessionViewModel` runs `RemoteControlHandler` and the syncers. It sets `CameraFrameSource` as the frame sink from the moment a subject starts to connect.
- **Verification.** IT-5 and IT-6 in the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#appendix-a-integration-test-procedures) are the scripts for two phones. IT-6 says how to read NFR-7 from the subject's `PixTimings` log.

## Pose generation

*Guide › Generate poses here* lets the user take a scene photo. The user then describes a pose or lets the server suggest four, and the app shows up to four pose candidates. [Design 2.6.3](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#263-pose-generation) describes the steps, the requests, the time limits, and the failures. [Design 2.5.3](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#253-rest-api-phone--server) describes the endpoints, and R&S 6.3 shows the screens.

- **Run it.** A server needs an OpenRouter key for pose generation (`server/README.md`). For a laptop server, start it with that key and set `pix.serverUrl` as in *Server address*. The service bills each set as up to four images.
- **Code.** The scene photo is a step of the Camera screen, not a screen. While `GenerationUiState.phase` is `FRAMING` or `REVIEWING`, `CameraFragment.renderScenePhoto` holds the logic of this step and runs last in `render`. `GenerationViewModel` holds the phases. `RemotePoseGenerator` sends the requests, and `DescriptionForm` holds the form of *Generate poses* apart from the fragment.
- **Timeouts.** The HTTP client in `AppContainer` waits 65 s, so a run's 60 s limit decides. OkHttp's default of 10 s would end every generation. The generator waits 10 s for the variations (`VARIATION_TIMEOUT_MS`).
- **Consent.** The app keeps the answer in DataStore (`pose_generation`). To see the notice again, withdraw the agreement in *Settings › AI pose generation*, or clear the app's data.
- **Watch it.** `adb logcat -s PixTimings PixPoses` shows these lines:
  - `pose.person found` or `pose.person none`: the person check of the scene photo (FR-4.13).
  - `pose.start described` or `pose.start suggested`.
  - `pose.planned`: the app knows the poses.
  - `pose.first`, then `pose.done 3 of 4 ready` (NFR-4).
  - One `PixPoses` line for each failed request, with the server's error code and message.

## Accounts and friends

*I'll take the photo* on the *Shoot together* sheet opens *Share my camera* (`FriendsFragment`). Signed out, it offers *Sign in to add friends*. Signed in, it lists the friends with their online status, and the received requests. The Design describes the endpoints and the tokens ([2.5.3](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#253-rest-api-phone--server)), presence ([2.5.2](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#252-signaling-protocol-phone--server-websocket)), and invitations and push ([2.6.6](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Design-Documentation#266-invitations-and-push-iteration-3)). R&S 6.4 and 6.7 show the screens.

- **Run it.** Use a server with the accounts endpoints: the default AWS server, Server B, or a laptop as above. Sign-up and sign-in with a Pix ID and a password work everywhere. *Check* on Sign up and Pix ID setup needs a server with `POST /auth/check-pix-id`. The received invitations on *See their camera* need `GET /invitations/incoming`. Google sign-in needs three more things. The server needs `PIX_GOOGLE_CLIENT_ID`. `app/build.gradle.kts` needs the web client ID. The team's Google Cloud project needs your debug key. To register the key, run `./gradlew :app:signingReport`. Then ask a project owner to add the `SHA1` of `Variant: debug` as an Android OAuth client for `com.lastpenguin.pix`. Without the key, Google's sheet fails and the app says so. An emulator needs a Google Play image signed in to a test user.
- **Push setup.** `app/google-services.json` holds the app's Firebase project (`pix-swpp`), and the `google-services` Gradle plugin reads it. The repository includes this file, because it is not a secret, unlike the server's service account key (`server/README.md`, *Push notifications*). An emulator with a Google APIs image also receives pushes. To open an invitation without a push to a second phone, open its link: `adb shell am start -a android.intent.action.VIEW -d http://<server>/i/<id>`.
- **Watch it.** `adb logcat -s PixAccount PixPresence PixPush` shows a refused refresh, Google sign-in failures, "Online" when the presence socket opens, and a failed push registration. `PixSession` shows an invitation's `DECLINED` or `EXPIRED`.

## Lint, format, and test

Run these commands from `android/` before you push. On every pull request, the `android-lint` workflow runs `spotlessCheck` and `lintDebug`. The `android-test` workflow runs `testDebugUnitTest`.

| Command | What it does |
|---|---|
| `./gradlew spotlessApply` | Formats Kotlin and Gradle files with ktlint. |
| `./gradlew spotlessCheck` | Fails if a file is not formatted or breaks a ktlint rule. |
| `./gradlew :app:lintDebug` | Android Lint. Errors fail the run. Warnings only appear in `app/build/reports/lint-results-debug.html`. |
| `./gradlew :app:testDebugUnitTest` | All unit tests, in `app/src/test/`. They run on the JVM, without a phone or an emulator. They need JDK 21 or later (Robolectric's Android 36 sandbox). |
| `./gradlew :app:jacocoTestReport` | Runs `testDebugUnitTest` with coverage. The line and branch coverage is in `app/build/reports/jacoco/jacocoTestReport/html/index.html`. The exclusions and targets are in the [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation) (2.1.2–2.1.4). |

- **Rules.** ktlint rules are in `.editorconfig`, which Android Studio's formatter also reads. After you change that file, run `./gradlew clean` once, or Spotless keeps the old rules.
- **Suppressing.** For one place, use `@Suppress("ktlint:standard:<rule>")` or `@SuppressLint("<LintId>")` with a comment that says why. To disable an Android Lint check for the whole app, add it to `lint { disable += ... }` in `app/build.gradle.kts`.
- **Tests.** Put a test in the same package as its class, under `app/src/test/java/`. `app/src/androidTest/` holds only `PersonCheckEval`, a measurement of the phone's person check for the AI module tests. It runs on a device and never in CI (Testing Documentation 3.1). Add tests in the pull request that writes the code. Plain JVM tests see only Android stubs (`isReturnDefaultValues`), so keep coordinate and state logic in plain Kotlin. When a test needs real Views, Canvas, touch events, or Material widgets, run it with `@RunWith(AndroidJUnit4::class)` (Robolectric). Also add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` if it draws or reads pixels.
- **Fakes.** Screen tests start every real Fragment and bottom sheet with `FragmentScenario` on a container of fakes (`ui/TestAppContainer`, `ui/ScreenTestSupport`). The session fakes are in `session/SessionFakes.kt` and `session/ModuleFakes.kt`, and the account fakes in `account/AccountFakes.kt`. The [Testing Documentation](https://github.com/snuhcs-course/swpp-2026-project-team-10/wiki/Testing-Documentation#218-writing-a-test-the-fakes-and-the-pitfalls) (2.1.8) says what each fake replaces and lists the pitfalls.
