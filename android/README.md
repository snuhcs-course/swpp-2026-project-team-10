# Pix Android app

Kotlin, XML Views with ViewBinding. minSdk 29, targetSdk 36. Open this `android/` folder in Android Studio.

## What is here

- **Shared contracts**: the code-level version of the agreements in the Design Documentation.
- **App shell** (#2): one activity, the navigation graph, every Iteration 1 screen with a basic layout, and every class from Design 2.1–2.2 with its functions declared.
- Owners fill in the bodies in their own issues.

### Camera

The camera screen requests camera permission, shows a rear-camera preview, supports continuous two-finger pinch zoom with an applied-ratio readout, and saves JPEGs to `Pictures/Pix` through MediaStore. Spread two fingers on the preview to zoom in and bring them together to zoom out. The readout also exposes Zoom in / Zoom out accessibility actions. Saved images contain only the camera image. The most recent photo has a thumbnail and opens in the system gallery; failed captures leave the camera and guide state intact.

`Preview`, full-resolution `ImageCapture`, and YUV `ImageAnalysis` share a portrait 3:4 viewport. The preview uses fit-center scaling. `CameraController.status` is an added local contract: `IDLE`, `STARTING`, `READY`, or `UNAVAILABLE`; screens enable capture only when ready and offer a retry on initialization/opening failure. Include this addition in the affected owners' contract review and the Design Documentation update when preparing the PR.

`grabFrame()` snapshots the latest analysis frame before its first suspension and returns an upright, cropped bitmap owned by the caller. Invoke it before navigating away from Camera (for example, at the beginning of `GenerationViewModel.startFromCamera`), then retain the returned scene for generation retries. `setFrameSink()` supplies YUV frames on a serial analysis thread. The receiver must respect crop/rotation metadata, copy what it needs, and close the proxy before returning; it must not block on network work.

Camera UI state observes the camera's applied zoom, rather than assuming requested zoom succeeded, so remote zoom can use the same controller. All ratios are relative to the primary rear camera's 1× field of view. Native logical-camera zoom below 1× is preferred; otherwise the controller can switch to a separately exposed rear ultrawide whose zoom range overlaps the primary camera. The minimum is hardware-dependent (for example 0.5×, 0.6×, or 1×), and unsupported ratios are never advertised. A lens switch may briefly pause the preview, but an ongoing pinch continues to update its target; lens changes requested during a photo save wait until capture finishes. If an ultrawide cannot bind, the controller returns to the primary camera and updates the supported range.

The photographer's viewfinder has a thin 3×3 grid fitted to the actual 3:4 image, excluding letterbox bars. All nine cells have the same width and height. A horizon bar sits at the center of the middle cell: its middle segment counter-rotates with device roll, while two fixed end markers show the horizontal target. Within ±2° it aligns and turns dark yellow (`#D4AC24`); it leaves that state beyond ±3° to avoid flickering. A smoothed gravity sensor (accelerometer fallback) drives the bar only while the screen is resumed. With no usable sensor or when the phone points almost straight up/down, only the grid remains. These aids do not consume pinch/guide gestures and are absent from saved photos and streamed video.

#### Camera verification

Run the lint/format/unit-test commands below and `./gradlew :app:assembleDebug`. On a phone or camera-enabled emulator, check:

1. Fresh launch: grant camera permission; verify the entire 3:4 preview appears. Deny permission on another launch and use Open settings to grant it, then return.
2. Spread and bring together two fingers on the preview; verify continuous zoom and an updated ratio such as 1.7×. Reach each zoom limit, then reverse direction without lifting your fingers; zoom should respond immediately. One-finger swipes should not zoom. With TalkBack, focus the ratio and use its Zoom in / Zoom out actions.
3. Tap the shutter repeatedly while saving: one capture should be in flight, followed by a Pix album image, a new thumbnail, and “Saved without the guide.” Open the thumbnail.
4. Open/close the guide and room-code sheets, open the gallery, background/foreground the app, and return from a full-screen destination; the camera should resume.
5. On the S22 and S23 Ultra, compare preview, saved image, and `grabFrame()` framing/rotation. Verify file-write/camera failures keep the shutter usable for retry.
6. Check that the grid divides only the camera image into equal thirds, including after a save message changes the preview area. Tilt the phone left/right: the center bar should rotate, then align in dark yellow near level. Point it straight down/up: the ambiguous level bar should disappear. Confirm pinch still works through the grid, photos omit the aids, and the bar resumes after returning from the gallery.
7. On phones exposing native logical zoom below 1× and phones exposing a separate rear ultrawide, pinch out to the widest available field of view. Verify the actual minimum: 0.5× only when supported, 0.6× on a 0.6× device, and 1× when no usable ultrawide is exposed. Continue the same gesture across 1× in both directions, including reversing during a lens switch; the latest requested zoom should apply after any brief preview pause. The readout must remain in primary-camera units, rather than resetting to the ultrawide's native 1×.
8. Capture a photo while crossing the lens boundary, then keep pinching. The photo should finish saving before a queued lens change applies, without interrupting the capture. Check preview, saved photo, analysis, and live-stream framing at both lens ranges. If an ultrawide bind fails, verify that the primary preview recovers, its supported range is published, and zoom/capture still work. Recheck zoom restoration after gallery and background return at a sub-1× ratio.

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
| Camera, Camera + guide, Photo saved, live badge | `camera/CameraFragment` | `CameraViewModel`, `SessionViewModel` | #3, #6, #8 |
| Add a pose guide (bottom sheet) | `guide/AddGuideSheet` | `ReferenceViewModel` | #5 |
| Reference confirm, No person found | `guide/ReferenceConfirmFragment`, `guide/NoPersonFoundFragment` | `ReferenceViewModel` | #5 |
| Generating poses, Pick a pose, Couldn't create poses | `generation/GeneratingFragment`, `PickPoseFragment`, `GenerationFailedFragment` | `GenerationViewModel` | #7 |
| Room code (bottom sheet) | `session/RoomCodeSheet` | `SessionViewModel` | #8 |
| Join with code, Session not found | `session/JoinCodeFragment`, `SessionNotFoundFragment` | `SessionViewModel` | #8 |
| Subject view, Connection lost | `session/SubjectFragment`, `ConnectionLostFragment` | `SubjectViewModel`, `SessionViewModel` | #8, #9, #10 |

The failure screens share `fragment_failure.xml`.

### Module classes

| Package | Classes | Issue |
|---|---|---|
| `camera/` | `CameraXController`, `PhotoSaver` | #3 |
| `guide/` | `InMemoryGuideRepository`, `GuideGeometry`, `GuideOverlayView` | #6 |
| `guide/` | `MlKitReferenceGuideMaker`, `SubjectSegmenter`, `OutlineExtractor` | #5 |
| `generation/` | `RemotePoseGenerator` | #7 |
| `session/` | `RtcSessionManager`, `SignalingClient` + `OkHttpSignalingClient`, `PeerConnectionClient` + `WebRtcPeerConnectionClient`, `WebRtcRuntime`, `CameraFrameSource` + `YuvToNv21`, `RemoteVideo`, `SessionIdentity`, `protocol/MessageCodec`, `protocol/ChannelRouter`, `signaling/SignalCodec` | #8 |
| `session/` | `GuideSyncer` | #9 |
| `session/` | `RemoteControlHandler` | #10 |
| `core/` | `AppContainer` (creates every module), `Timings` (Design 2.8) | #3 |

## How to work on it

- **Find your part.** Search for your issue number next to `TODO`, for example `TODO("#3` and `TODO(#3`.
  - Unimplemented module functions throw `TODO(...)`. Camera functions are implemented; the other owners still need to connect their modules.
  - ViewModel functions are empty with a `// TODO` comment, so the app runs while you work.
- **Temporary buttons.** Buttons labeled `(temp)` stand in for events that are not built yet: no person found, poses ready or failed, friend joined, code not found, connection lost. When your ViewModel moves to that screen by itself, remove the button.
- **Fakes.** To work before another owner's module is ready, write a fake of the interface and use it in `core/AppContainer.kt`.
- **Contracts.** Change a contract only through a pull request that the affected owners review, and update the Design Documentation in the same change.
- **Libraries.** Versions are in `gradle/libs.versions.toml`. Add the ones your module needs to `app/build.gradle.kts`.
- **Server address.** `BuildConfig.SERVER_URL` defaults to `http://10.0.2.2:8000/`, the host machine as seen from the emulator. For a real phone, put the laptop's address on the test Wi-Fi in `local.properties` as `pix.serverUrl=http://192.168.0.10:8000/` (the file is not committed), or pass `-Ppix.serverUrl=...` to Gradle. Debug builds allow this plain `http://` and `ws://` traffic (`src/debug/AndroidManifest.xml`); release builds do not.

## Real-time session (#8)

Two phones connect with a room code over the Pix server and then stream phone to phone with WebRTC (Design 1.1.4, 2.5, 2.6.4). The prebuilt library is `io.github.webrtc-sdk:android` (`org.webrtc` package).

- **Run it.** Start the server on the laptop (`server/README.md`), set `pix.serverUrl` as above, install the debug build on both phones, and put the laptop and both phones on one Wi-Fi that allows device-to-device traffic (campus networks usually do not; a phone hotspot does, and the phone that hosts the hotspot can take part too: the peer connection enumerates network interfaces itself instead of asking Android's network monitor, which does not report the hotspot interface). *Shoot together* on one phone shows the code; *Shoot together › Join with a code instead* on the other joins it.
- **One phone is enough to test the photographer's side.** `server/tools/fake_subject.py` plays the subject: it connects over WebRTC, receives the video, and sends a zoom request (see `server/README.md`).
- **Watch it.** `adb logcat -s PixTimings PixSession PixSignaling PixPeer` shows the session steps (`room.created`, `peer.joined`, `offer.sent`, `ice.connected`, `session.connected`), the ping round-trip time (`rtt`), a `stats` line every 2 s with the codec, frame size, fps, bitrate, and ICE round-trip time from WebRTC's statistics (NFR-5, 6, 12), and why a session ended.
- **States.** `SessionManager.state` follows Design 2.4 with one addition: when the subject leaves or drops, the photographer closes the peer connection and goes back to `Waiting(code)` with the same code, so the subject's *Reconnect* can join again. The subject sees `Ended(CONNECTION_LOST)` and the Connection lost screen.
- **Screens.** Screens draw from `SessionViewModel.state` and navigate on `SessionViewModel.transitions`, a one-shot stream of state changes, so returning to a screen never replays an old navigation. "Junhyeong left" notices come from `SessionViewModel.notices`.
- **Background.** The camera is bound to the Camera screen's view, so it is released whenever another screen or app is in front, and the subject's view shows "The photographer's camera is paused" after 2 s without frames. If the whole app stays in the background for 60 s during a live session, the photographer's phone ends the session (Design 2.8, Lifecycle).
- **Remote zoom (#10).** While the photographer is connected, `SessionViewModel` runs `RemoteControlHandler`: a `camera.zoom.set` from the subject is clamped and applied, and every *applied* zoom (the subject's request or the photographer's own chip or pinch) is echoed as `camera.state`, so both phones end on the same value (FR-7.6). Subject view shows the tapped chip at once and snaps to the echo; the photographer sees "<name> set zoom to 2×". Timing: `zoom.sent` and `zoom.echo` on the subject, `zoom.received` and `zoom.applied` on the photographer (NFR-7).
- **Hooks for #9.** Send with `SessionManager.send` and read `SessionManager.incoming`; both data channels are open while the state is `Connected`, `hello` has been exchanged, and `camera.capabilities` is sent by `SessionViewModel`. The reliable channel queues messages while its buffer is above 256 KiB, so image chunks can be sent without checking. Stale realtime values are already dropped by `seq`. The subject's renderer attaches through `RemoteVideo`.
- **Camera frames.** `SessionViewModel` sets `CameraFrameSource` as the frame sink from the moment a subject starts connecting, and `CameraFrameSource` honors `cropRect` and `rotationDegrees`. The `frames` line in the `PixTimings` log counts frames handed to WebRTC.
- **Tests.** `app/src/test/.../session/` covers the envelope codec, the channel table, the signaling JSON against the server's examples, the NV21 packing for every plane layout, and the session state machine with fake signaling and peer clients (`SessionFakes.kt`). A manager test that ends while connected must call `leave`, or the virtual-time ping loop keeps `runTest` from finishing.

## Lint, format, and test

Run these from `android/` before you push. On every pull request, the `android-lint` workflow runs the two checks and the `android-test` workflow runs the unit tests.

| Command | What it does |
|---|---|
| `./gradlew spotlessApply` | Formats Kotlin and Gradle files with ktlint. |
| `./gradlew spotlessCheck` | Fails if a file is not formatted or breaks a ktlint rule. |
| `./gradlew :app:lintDebug` | Android Lint. Errors fail; warnings are only listed in `app/build/reports/lint-results-debug.html`. |
| `./gradlew :app:testDebugUnitTest` | Unit tests in `app/src/test/`. They run on the JVM, without a phone or an emulator. |

- **Rules.** ktlint rules are in `.editorconfig`, which Android Studio's formatter also reads. After you change that file, run `./gradlew clean` once, or Spotless keeps using the old rules.
- **Suppressing.** For one place, use `@Suppress("ktlint:standard:<rule>")` or `@SuppressLint("<LintId>")` with a comment that says why. To turn off an Android Lint check for the whole app, add it to `lint { disable += ... }` in `app/build.gradle.kts`.
- **Tests.** Put a test next to its class, in the same package under `app/src/test/java/`. Add tests in the pull request that writes the code. Unit tests cannot call the Android framework, so keep logic you want to test in plain Kotlin.
