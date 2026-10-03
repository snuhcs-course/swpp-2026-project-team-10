# Android context

- Open `android/` in Android Studio. Commands for formatting, lint, unit tests, and the build are in [README.md](README.md); run them from `android/` before pushing. If `java` is not on the PATH, set `JAVA_HOME` to Android Studio's bundled JBR.
- The shared contracts listed in [README.md](README.md) change only through a reviewed pull request together with the Design Documentation. Module classes behind them belong to the issue owner named in each file's `Owner:` comment; keep other owners' `TODO(#n)` markers in place.
- `core/AppContainer.kt` is the only place modules are created, and `ui/PixViewModels.kt` the only place ViewModels get them. Replace a module with a fake there, never in a screen.
- `BuildConfig.SERVER_URL` comes from `pix.serverUrl` in `local.properties` or a Gradle property and defaults to the emulator's host address; only debug builds allow plain `http://` and `ws://` (`src/debug/AndroidManifest.xml`).
- Session code (`session/`) touches its state only on `RtcSessionManager`'s serial scope; WebRTC and OkHttp callbacks only publish events. Never call `dispose()` from a WebRTC callback thread, and dispose the peer connection before the video source; it disposes the tracks itself.
- Screens render from `SessionViewModel.state` and navigate from `SessionViewModel.transitions` (one-shot). After the subject leaves, the photographer is back in `Waiting(code)`, not `Ended`; the subject gets `Ended(CONNECTION_LOST)` and reconnects with the same code.
- JVM unit tests use `isReturnDefaultValues`, so `Log` and `SystemClock` are safe to call. A session test that ends while connected must call `leave`, because the 2 s ping loop never lets `runTest` drain virtual time.
