# Android context

- CameraX is the sole camera owner. Bind it to the Camera screen's view lifecycle, not the activity. Preview, capture, and analysis share a portrait 3:4 viewport; PreviewView uses FIT_CENTER so it shows that entire crop. Saved photos never include View overlays.
- CameraController.status is a local contract extension for asynchronous startup/failure handling. READY requires an open camera and an analysis frame. Include this extension in contract review and the wiki design update when preparing its PR.
- Call grabFrame before navigation stops the camera: it snapshots cached YUV data before suspending, then converts the caller-owned image off the main thread. FrameSink runs on the serial analysis executor and must copy/crop/rotate as needed and close ImageProxy before returning; never retain camera buffers or wait on the network.
- Camera controls render observed zoom, not the requested value. This is also the state remote zoom must echo. Camera status is separate from whether the preview surface has displayed a frame; use camera.preview timing for visible startup.
- Run ./gradlew spotlessCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug from android/. Pure coordinate/YUV/zoom logic uses JVM tests; permissions, real CameraX capture, MediaStore, and hardware resolution still require an emulator or phone.
