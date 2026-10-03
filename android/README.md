# Pix Android app

Kotlin, XML Views with ViewBinding. minSdk 29, targetSdk 36. Open this `android/` folder in Android Studio.

## What is here

- **Shared contracts**: the code-level version of the agreements in the Design Documentation.
- **App shell** (#2): one activity, the navigation graph, every Iteration 1 screen with a basic layout, and every class from Design 2.1–2.2 with its functions declared.
- Owners fill in the bodies in their own issues.

### Camera

The camera screen now requests camera permission, shows a rear-camera preview, exposes supported zoom chips and pinch zoom, and saves JPEGs to `Pictures/Pix` through MediaStore. Saved images contain only the camera image. The most recent photo has a thumbnail and opens in the system gallery; failed captures leave the camera and guide state intact.

`Preview`, full-resolution `ImageCapture`, and YUV `ImageAnalysis` share a portrait 3:4 viewport. The preview uses fit-center scaling. `CameraController.status` is an added local contract: `IDLE`, `STARTING`, `READY`, or `UNAVAILABLE`; screens enable capture only when ready and offer a retry on initialization/opening failure. Include this addition in the affected owners' contract review and the Design Documentation update when preparing the PR.

`grabFrame()` snapshots the latest analysis frame before its first suspension and returns an upright, cropped bitmap owned by the caller. Invoke it before navigating away from Camera (for example, at the beginning of `GenerationViewModel.startFromCamera`), then retain the returned scene for generation retries. `setFrameSink()` supplies YUV frames on a serial analysis thread. The receiver must respect crop/rotation metadata, copy what it needs, and close the proxy before returning; it must not block on network work.

Camera UI state observes the camera's applied zoom, rather than assuming requested zoom succeeded, so remote zoom can use the same controller. The guide and session implementations remain in their owners' issues.

#### Camera verification

Run the lint/format/unit-test commands below and `./gradlew :app:assembleDebug`. On a phone or camera-enabled emulator, check:

1. Fresh launch: grant camera permission; verify the entire 3:4 preview appears. Deny permission on another launch and use Open settings to grant it, then return.
2. Tap every supported zoom chip and pinch the preview; check the displayed selection follows the applied zoom.
3. Tap the shutter repeatedly while saving: one capture should be in flight, followed by a Pix album image, a new thumbnail, and “Saved without the guide.” Open the thumbnail.
4. Open/close the guide and room-code sheets, open the gallery, background/foreground the app, and return from a full-screen destination; the camera should resume.
5. On the S22 and S23 Ultra, compare preview, saved image, and `grabFrame()` framing/rotation. Verify file-write/camera failures keep the shutter usable for retry.

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
| `session/` | `RtcSessionManager`, `SignalingClient`, `PeerConnectionClient`, `CameraFrameSource`, `protocol/MessageCodec`, `protocol/ChannelRouter` | #8 |
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
- **Server address.** `BuildConfig.SERVER_URL` in `app/build.gradle.kts`. Set it to the laptop's address on the test Wi-Fi (#4).

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
