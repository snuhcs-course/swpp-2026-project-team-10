# Pix Android app

Kotlin, XML Views with ViewBinding. minSdk 29, targetSdk 36. Open this `android/` folder in Android Studio.

## What is here

- **Shared contracts**: the code-level version of the agreements in the Design Documentation.
- **App shell** (#2): one activity, the navigation graph, every Iteration 1 screen, and every class from Design 2.1–2.2 with its functions declared.
- Owners fill in the bodies in their own issues.

### Camera

The camera screen requests camera permission, shows a rear-camera preview, supports continuous zoom through a single-value Material slider with an applied-ratio readout, and saves JPEGs to `Pictures/Pix` through MediaStore. The slider labels show the camera's actual minimum and maximum ratios. It supports native keyboard and accessibility input; the readout also exposes Zoom in / Zoom out accessibility actions. Saved images contain only the camera image. The most recent photo has a thumbnail and opens in the system gallery; failed captures leave the camera and guide state intact.

`Preview`, full-resolution `ImageCapture`, and YUV `ImageAnalysis` share a portrait 3:4 viewport. The preview uses fit-center scaling. `CameraController.status` is an added local contract: `IDLE`, `STARTING`, `READY`, or `UNAVAILABLE`; screens enable capture only when ready and offer a retry on initialization/opening failure. Include this addition in the affected owners' contract review and the Design Documentation update when preparing the PR.

With a guide, start a touch on its visible bounding rectangle to drag it with one finger or resize it with two. A two-finger gesture also moves the guide with its midpoint. The overlay retains that touch sequence until all fingers lift; the preview container keeps split touch dispatch disabled so later fingers stay in the same sequence. Starting outside the guide does not edit it or zoom the camera. Neither the Camera preview nor Subject live video has pinch-to-camera-zoom behavior. Read-only Subject overlays let touches reach the live video. Guide size stays between 30% and 300% of its starting height and at least 20% of each dimension stays in frame (see Design 2.6.2 for unusually wide guides).

`grabFrame()` snapshots the latest analysis frame before its first suspension and returns an upright, cropped bitmap owned by the caller. Invoke it while a screen has the camera bound (the Camera screen's shutter does while the scene photo is taken, in `GenerationViewModel.takeScene`), then retain the returned scene for generation retries. `setFrameSink()` supplies YUV frames on a serial analysis thread. The receiver must respect crop/rotation metadata, copy what it needs, and close the proxy before returning; it must not block on network work.

Camera UI state observes the camera's applied zoom, rather than assuming requested zoom succeeded, so remote zoom can use the same controller. During a slider drag, the thumb follows the user's target while the Camera readout continues to show the applied ratio; asynchronous camera updates do not pull the thumb away from the finger. After release or cancellation, the thumb follows observed zoom again. All ratios are relative to the primary rear camera's 1× field of view. Native logical-camera zoom below 1× is preferred; otherwise the controller can switch to a separately exposed rear ultrawide whose zoom range overlaps the primary camera. The minimum is hardware-dependent (for example 0.5×, 0.6×, or 1×), and unsupported ratios are never advertised. A lens switch may briefly pause the preview, but an ongoing slider drag continues to update its target through `STARTING`; lens changes requested during a photo save wait until capture finishes. If an ultrawide cannot bind, the controller returns to the primary camera and updates the supported range.

The photographer's viewfinder has a thin 3×3 grid fitted to the actual 3:4 image, excluding letterbox bars. All nine cells have the same width and height. A horizon bar sits at the center of the middle cell: its middle segment counter-rotates with device roll, while two fixed end markers show the horizontal target. Within ±2° it aligns and turns dark yellow (`#D4AC24`); it leaves that state beyond ±3° to avoid flickering. A smoothed gravity sensor (accelerometer fallback) drives the bar only while the screen is resumed. With no usable sensor or when the phone points almost straight up/down, only the grid remains. These aids do not consume guide gestures and are absent from saved photos and streamed video.

#### Camera verification

Run the lint/format/unit-test commands below and `./gradlew :app:assembleDebug`. On a phone or camera-enabled emulator, check:

1. Fresh launch: grant camera permission; verify the entire 3:4 preview appears. Deny permission on another launch and use Open settings to grant it, then return.
2. Drag the zoom slider across its labeled hardware range; verify continuous zoom and an applied ratio such as 1.7×. Reach either limit and reverse without lifting: the thumb should follow immediately without jumping back as camera observations arrive. Pinching the preview, including outside a visible guide, must not zoom the camera; pinching a guide must still resize it. Check native slider keyboard/TalkBack adjustment and the readout's Zoom in / Zoom out actions.
3. Tap the shutter repeatedly while saving: one capture should be in flight, followed by a Pix album image, a new thumbnail, and “Saved without the guide.” Open the thumbnail.
4. Open/close the guide and room-code sheets, open the gallery, background/foreground the app, and return from a full-screen destination; the camera should resume.
5. On the S22 and S23 Ultra, compare preview, saved image, and `grabFrame()` framing/rotation. Verify file-write/camera failures keep the shutter usable for retry.
6. Check that the grid divides only the camera image into equal thirds, including after a save message changes the preview area. Tilt the phone left/right: the center bar should rotate, then align in dark yellow near level. Point it straight down/up: the ambiguous level bar should disappear. Confirm guide drag/pinch still works through the grid, photos omit the aids, and the bar resumes after returning from the gallery.
7. On phones exposing native logical zoom below 1× and phones exposing a separate rear ultrawide, move the slider to its widest available field of view. Verify the actual minimum: 0.5× only when supported, 0.6× on a 0.6× device, and 1× when no usable ultrawide is exposed. Continue the same drag across 1× in both directions, including reversing during a lens switch's `STARTING` state; the latest requested zoom should apply after any brief preview pause. The readout must remain in primary-camera units, rather than resetting to the ultrawide's native 1×.
8. Capture a photo while crossing the lens boundary, then keep dragging the zoom slider. The photo should finish saving before a queued lens change applies, without interrupting the capture. Check preview, saved photo, analysis, and live-stream framing at both lens ranges. If an ultrawide bind fails, verify that the primary preview recovers, its supported range and slider endpoints are updated, and zoom/capture still work. Cancel a drag or pause the screen and verify the interaction finishes cleanly; recheck zoom restoration after gallery and background return at a sub-1× ratio.

`PixTimings` logcat events include `app.start`, `camera.preview`, and camera capture events. Emulator smoke checks do not establish real-device resolution, startup latency, or frame rate.

### Shared contracts

| File | What it fixes | Design |
|---|---|---|
| `camera/CameraController.kt` | Camera interface, `CameraCapabilities`, `FrameSink` | 2.1 |
| `guide/GuideModels.kt` | `ReferenceGuide`, `GuideState`, `GuideStyle`, `GuideSource` | 2.3 |
| `guide/GuideRepository.kt`, `guide/ReferenceGuideMaker.kt` | Guide store and reference-to-guide interfaces | 2.1 |
| `generation/GenerationModels.kt`, `generation/PoseGenerator.kt` | Pose templates, candidate events, generator interface | 2.1, 2.3 |
| `session/SessionModels.kt`, `session/SessionManager.kt` | Roles, entries, session states, session interface | 2.1, 2.3, 2.4 |
| `session/protocol/SessionMessage.kt` | Data channel messages between the two phones | 2.5.1 |
| `session/signaling/SignalMessage.kt` | WebSocket signaling messages | 2.5.2 |
| `core/network/PixApi.kt`, `core/network/ApiModels.kt` | REST API used by the app | 2.5.3 |

### Screens

All screens are in `res/navigation/nav_graph.xml`, and the app starts on Camera. Screens of one flow share a ViewModel (activity scope).

| Screen (R&S 6) | Class in `ui/` | ViewModel | Issue |
|---|---|---|---|
| Camera, Camera + guide, Photo saved, live badge, Scene photo, Scene photo · Review | `camera/CameraFragment` | `CameraViewModel`, `SessionViewModel`, `GenerationViewModel` | #3, #6, #7, #8 |
| Add a pose guide (bottom sheet), Consent notice | `guide/AddGuideSheet` | `ReferenceViewModel`, `GenerationViewModel` | #5, #7 |
| Reference confirm, No person found | `guide/ReferenceConfirmFragment`, `guide/NoPersonFoundFragment` | `ReferenceViewModel` | #5 |
| Generating poses, Pick a pose, Couldn't create poses | `generation/GeneratingFragment`, `PickPoseFragment`, `GenerationFailedFragment` | `GenerationViewModel` | #7 |
| Room code (bottom sheet) | `session/RoomCodeSheet` | `SessionViewModel` | #8 |
| Join with code, Session not found | `session/JoinCodeFragment`, `SessionNotFoundFragment` | `SessionViewModel` | #8 |
| Subject view, Connection lost | `session/SubjectFragment`, `ConnectionLostFragment` | `SubjectViewModel`, `SessionViewModel` | #8, #9, #10 |

The failure screens share `fragment_failure.xml`.

**Look.** Every screen uses the dark-only `Theme.Pix` (`res/values/themes.xml`). The design tokens are in `colors.xml`: the ground and surfaces, glass for controls over the preview, one accent (`#F2C14E`), the live color (`#36D399`), the guide color (`#5EEAD4`), and the danger color. Type and component styles are in `styles.xml`: `TextAppearance.Pix.*`, and `Widget.Pix.*` for primary, secondary, text, glass, icon, and segment buttons, chips, sliders, cards, sheets, and dialogs. Fonts are Manrope and DM Mono in `res/font` (SIL OFL 1.1; licenses in `assets/licenses`). Icons are vector drawables in `res/drawable` (`ic_*`, with 16–20 dp `_small` versions for use beside text), and the app icon is the adaptive icon in `res/mipmap-anydpi`. The team's UI mockups are the visual reference; where they differ from built behavior, the behavior wins, for example the zoom slider instead of zoom chips and the system number keyboard instead of an on-screen keypad.

### Module classes

| Package | Classes | Issue |
|---|---|---|
| `camera/` | `CameraXController`, `PhotoSaver` | #3 |
| `guide/` | `InMemoryGuideRepository`, `GuideGeometry`, `GuideOverlayView` | #6 |
| `guide/` | `MlKitReferenceGuideMaker`, `SubjectSegmenter`, `OutlineExtractor` | #5 |
| `generation/` | `RemotePoseGenerator`, `PoseImageCodec` + `AndroidPoseImageCodec`, `GenerationConsent` + `DataStoreGenerationConsent` | #7 |
| `session/` | `RtcSessionManager`, `SignalingClient` + `OkHttpSignalingClient`, `PeerConnectionClient` + `WebRtcPeerConnectionClient`, `WebRtcRuntime`, `CameraFrameSource` + `YuvToNv21`, `RemoteVideo`, `SessionIdentity`, `protocol/MessageCodec`, `protocol/ChannelRouter`, `signaling/SignalCodec` | #8 |
| `session/` | `GuideSyncer` | #9 |
| `session/` | `RemoteControlHandler` | #10 |
| `core/` | `AppContainer` (creates every module), `Timings` (Design 2.8) | #3 |

## How to work on it

- **Find your part.** Search for your issue number next to `TODO`, for example `TODO("#3` and `TODO(#3`.
  - Unimplemented module functions throw `TODO(...)`. Camera functions are implemented; the other owners still need to connect their modules.
  - ViewModel functions are empty with a `// TODO` comment, so the app runs while you work.
- **Fakes.** To work before another owner's module is ready, write a fake of the interface and use it in `core/AppContainer.kt`.
- **Contracts.** Change a contract only through a pull request that the affected owners review, and update the Design Documentation in the same change.
- **Libraries.** Versions are in `gradle/libs.versions.toml`. Add the ones your module needs to `app/build.gradle.kts`.
- **Server address.** `BuildConfig.SERVER_URL` defaults to `http://10.0.2.2:8000/`, the host machine as seen from the emulator. For a real phone, put the laptop's address on the test Wi-Fi in `local.properties` as `pix.serverUrl=http://192.168.0.10:8000/` (the file is not committed), or pass `-Ppix.serverUrl=...` to Gradle. Debug builds allow this plain `http://` and `ws://` traffic (`src/debug/AndroidManifest.xml`); release builds do not.

## Real-time session (#8)

Two phones connect with a room code over the Pix server and then stream phone to phone with WebRTC (Design 1.1.4, 2.5, 2.6.4). The prebuilt library is `io.github.webrtc-sdk:android` (`org.webrtc` package).

- **Run it.** Start the server on the laptop (`server/README.md`), set `pix.serverUrl` as above, install the debug build on both phones, and put the laptop and both phones on one Wi-Fi that allows device-to-device traffic (campus networks usually do not; a phone hotspot does, and the phone that hosts the hotspot can take part too: the peer connection enumerates network interfaces itself instead of asking Android's network monitor, which does not report the hotspot interface). *Shoot together* on one phone shows the code; *Shoot together › Join with a code instead* on the other joins it.
- **One phone is enough to test either side.** `server/tools/fake_photographer.py` plays the photographer for Subject view: it sends a test-pattern video, a guide image, guide moves and style changes, and echoes zoom requests. `server/tools/fake_subject.py` plays the subject: it connects over WebRTC, receives the video and the guide image (checked against its CRC, `--save-guide` keeps it), prints every `guide.state`, and sends a zoom request (see `server/README.md`).
- **Watch it.** `adb logcat -s PixTimings PixSession PixSignaling PixPeer` shows the session steps (`room.created`, `peer.joined`, `offer.sent`, `ice.connected`, `session.connected`), the ping round-trip time (`rtt`) and each ping received from the other phone (`ping.received`), both with the ping's `ts` so the two phones' logs can be aligned (Design 2.5.1), a `stats` line every 2 s with the codec, frame size, fps, bitrate, and ICE round-trip time from WebRTC's statistics (NFR-5, 6, 12), and why a session ended.
- **States.** `SessionManager.state` follows Design 2.4 with one addition: when the subject leaves or drops, the photographer closes the peer connection and goes back to `Waiting(code)` with the same code, so the subject's *Reconnect* can join again. The subject sees `Ended(CONNECTION_LOST)` and the Connection lost screen.
- **Screens.** Screens draw from `SessionViewModel.state` and navigate on `SessionViewModel.transitions`, a one-shot stream of state changes, so returning to a screen never replays an old navigation. "Junhyeong left" notices come from `SessionViewModel.notices`.
- **Background.** The camera is bound to the Camera screen's view, so it is released whenever another screen or app is in front, and the subject's view shows "The photographer's camera is paused" after 2 s without frames. If the whole app stays in the background for 60 s during a live session, the photographer's phone ends the session (Design 2.8, Lifecycle).
- **Remote zoom (#10).** While the photographer is connected, `SessionViewModel` runs `RemoteControlHandler`: a `camera.zoom.set` from the subject is clamped and applied, and every *applied* zoom (the subject's request or the photographer's slider/accessibility input) is echoed as `camera.state`, so both phones end on the same value (FR-7.6). Subject view has a single-value Material zoom slider with the photographer's actual minimum/maximum labels. A drag updates its thumb and readout optimistically; echoes do not pull the thumb away during the drag. `SubjectViewModel.onZoomGesture` still sends each step as a non-final `camera.zoom.set` at most every 50 ms (newest wins). Release, cancellation, or screen pause sends the last value reliably as `final`; only a final request makes the photographer's "<name> set zoom to 2×" notice, and subsequent echoes settle the readout and thumb on the applied ratio. Native slider keyboard/accessibility changes and the readout's Zoom in / Zoom out actions send final requests directly. Pinching the live video does not change camera zoom. Timing: `zoom.sent` and `zoom.echo` on the subject, `zoom.received` and `zoom.applied` on the photographer (NFR-7).
- **Remote zoom verification.** On two connected phones, drag the Subject slider in both directions and across a lens boundary, then release: verify the immediate local readout, continuous photographer zoom, and eventual agreement with the applied echo. Cancel a drag or background the subject and check that the final value is delivered without leaving an active drag. Check keyboard/TalkBack changes, hardware endpoint labels, photographer-side slider changes appearing on the subject, and that pinching either preview never zooms the camera while photographer guide drag/pinch still works.
- **Guide sync (#9).** `GuideSyncer` keeps the subject's guide the same as the photographer's (Design 2.5.1, 2.6.1 step 6, 2.6.2). Photographer: once connected, `SessionViewModel` runs it as sender; it sends the cutout as a lossy WebP with alpha, at most 720 px tall, in `guide.image.begin`/`chunk`/`end` messages (Base64 chunks of at most 12 KiB, CRC32 at the end), then the current `guide.state`; the image is skipped when the subject's `hello` already named that guide id (`PeerInfo.haveGuideId`), and encoded images are cached per guide id. Afterwards every change from `GuideRepository.changes` goes out: gesture steps on the realtime channel at most every 50 ms (newest wins), the final value of a gesture at once on the reliable channel, a new guide as image plus state, and a removed guide as `guide.clear`. Subject: `SessionViewModel` runs the receiver from the moment the subject joins (the image can arrive before this phone's own `Connected`); it rebuilds and checks the image, derives the outline from the cutout's alpha (`WebpGuideImageCodec`), and writes image and state into the mirror `GuideRepository`, which Subject view draws read-only with `GuideOverlayView`. A state that arrives before its image waits for it; stale `seq` is dropped by the session. Timing: `guide.sent` and `guide.applied` in `PixTimings`; `PixGuideSync` logs a dropped image and why.
- **Camera frames.** `SessionViewModel` sets `CameraFrameSource` as the frame sink from the moment a subject starts connecting, and `CameraFrameSource` honors `cropRect` and `rotationDegrees`. The `frames` line in the `PixTimings` log counts frames handed to WebRTC.
- **Tests.** `app/src/test/.../session/` covers the envelope codec, the channel table, the signaling JSON against the server's examples, the NV21 packing for every plane layout, the session state machine with fake signaling and peer clients (`SessionFakes.kt`), the guide image chunks and the real WebP round trip (Robolectric), and `GuideSyncer` on both sides with a fake codec (`ModuleFakes.kt`). A manager test that ends while connected must call `leave`, or the virtual-time ping loop keeps `runTest` from finishing.

## Pose generation

*Guide › Generate poses here* lets the user take a scene photo, sends it through the Pix server, and shows up to four pose candidates; the chosen one goes to Reference confirm like an uploaded photo (R&S F4, Design 2.5.3, 2.6.3).

- **Run it.** Start the server with an OpenRouter key (`server/README.md`) and set `pix.serverUrl` as above; on the emulator the default address already reaches the laptop. Every set is billed as up to four images.
- **Scene photo.** The photo is taken on the Camera screen, which keeps the camera running, so zoom and the grid work as usual. While `GenerationUiState.phase` is `FRAMING`, `CameraFragment` shows "Take the scene photo" with *Cancel* and hides *Guide*, *Shoot together*, the thumbnail, and any earlier guide; the shutter takes the frame with `grabFrame()` and saves nothing. In `REVIEWING` the photo is shown over the preview with *Use this photo* and *Shoot again*; nothing is sent before *Use this photo* (FR-4.1). `CameraFragment.renderScenePhoto` holds this and runs last in `render`. `AndroidPoseImageCodec` prepares the confirmed photo on the phone: long side at most 1024 px, JPEG quality 85, no EXIF. The server checks the photo and rejects any other; it does not resize it (Design 2.6.3, step 1).
- **Requests.** `RemotePoseGenerator` sends one `POST /poses` per template in parallel, each with its own seed, and emits a `CandidateEvent` as each one ends. A request without an answer after 30 s becomes `TIMEOUT`; the HTTP client in `AppContainer` waits 35 s so that this limit decides (OkHttp's default of 10 s would end every generation). `GenerationViewModel` puts the same 30 s limit on a whole run, fetching the templates included, and counts the poses still unanswered then as timed out, so Generating poses never lasts longer. Cancelling the collector closes the connections, and the server then stops its own calls to the image service.
- **Consent.** The notice appears once, before the first scene photo is taken (FR-4.2), and the answer is kept in DataStore (`pose_generation`). *Not now* sends nothing and stays on the sheet. Clear the app's data to see the notice again.
- **Screens.** While the scene photo is taken, system back is *Shoot again* when the photo is shown and *Cancel* before that. Generating poses moves to Pick a pose when at least one candidate arrived and to Couldn't create poses otherwise; a pose that failed is left out of Pick a pose. *Use this pose* hands the chosen image to Reference confirm and then discards the scene photo and the other candidates. System back does what *Cancel* and *Back to camera* do: it stops the requests and discards the scene photo and the candidates.
- **Watch it.** `adb logcat -s PixTimings PixPoses` shows `pose.start`, `pose.first`, and `pose.done 3 of 4 ready` (NFR-4), and one `PixPoses` line for each failed request with the server's error code and message.
- **Tests.** `generation/RemotePoseGeneratorTest` covers the parallel requests, the seeds, the error mapping, the timeout, and cancellation with a fake `PixApi`; `ui/generation/GenerationViewModelTest` covers taking and confirming the photo, the phases, partial failure, retry, cancel, selection, and consent.

## Lint, format, and test

Run these from `android/` before you push. On every pull request, the `android-lint` workflow runs the two checks and the `android-test` workflow runs the unit tests.

| Command | What it does |
|---|---|
| `./gradlew spotlessApply` | Formats Kotlin and Gradle files with ktlint. |
| `./gradlew spotlessCheck` | Fails if a file is not formatted or breaks a ktlint rule. |
| `./gradlew :app:lintDebug` | Android Lint. Errors fail; warnings are only listed in `app/build/reports/lint-results-debug.html`. |
| `./gradlew :app:testDebugUnitTest` | All tests, in `app/src/test/`. They run on the JVM, without a phone or an emulator, and need JDK 21 or later (Robolectric's Android 36 sandbox). Guide overlay tests check Canvas rendering and touch dispatch, including drag/pinch transitions and hit testing outside the guide. Slider tests cover touch ordering, late observed updates, finalization, invalid ranges, accessibility, and keyboard input. |

- **Rules.** ktlint rules are in `.editorconfig`, which Android Studio's formatter also reads. After you change that file, run `./gradlew clean` once, or Spotless keeps using the old rules.
- **Suppressing.** For one place, use `@Suppress("ktlint:standard:<rule>")` or `@SuppressLint("<LintId>")` with a comment that says why. To turn off an Android Lint check for the whole app, add it to `lint { disable += ... }` in `app/build.gradle.kts`.
- **Tests.** Put a test next to its class, in the same package under `app/src/test/java/`; there is no `androidTest` source set. Add tests in the pull request that writes the code. Plain JVM tests see only Android stubs (`isReturnDefaultValues`), so keep coordinate and state logic in plain Kotlin. When a test needs real Views, Canvas, touch events, or Material widgets, run it with `@RunWith(AndroidJUnit4::class)` (Robolectric) and add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` if it draws or reads pixels. These tests use synthetic guide bitmaps and do not require camera permission, segmentation, or a server.
