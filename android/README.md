# Pix Android app

Kotlin, XML Views with ViewBinding. minSdk 29, targetSdk 36. Open this `android/` folder in Android Studio.

## What is here

The Gradle project builds an empty app, plus the **shared contracts**: the code-level version of the agreements in the Design Documentation.
Owners build everything else in their tasks: screens, navigation, implementations, fakes, and the server.

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

## Rules

- **Program against these interfaces.** Start with a fake implementation of the modules you depend on, so you do not wait for other owners.
- **Change a contract only through a pull request** that the affected owners review, and update the Design Documentation in the same change.
- **Library versions** are in `gradle/libs.versions.toml`. Add the ones your module needs to `app/build.gradle.kts`.
