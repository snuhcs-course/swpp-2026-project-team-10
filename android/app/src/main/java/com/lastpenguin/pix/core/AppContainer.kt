// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.core

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthTokens
import com.lastpenguin.pix.account.BearerTokens
import com.lastpenguin.pix.account.CredentialManagerGoogleIdTokens
import com.lastpenguin.pix.account.DataStoreSignInStore
import com.lastpenguin.pix.account.FriendsRepository
import com.lastpenguin.pix.account.GoogleIdTokens
import com.lastpenguin.pix.account.OkHttpPresenceSockets
import com.lastpenguin.pix.account.PresenceConnection
import com.lastpenguin.pix.account.RemoteAuthRepository
import com.lastpenguin.pix.account.RemoteFriendsRepository
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.CameraXController
import com.lastpenguin.pix.camera.DataStoreCameraSettings
import com.lastpenguin.pix.camera.NARROWEST_FRAME_ASPECT
import com.lastpenguin.pix.camera.PhotoSaver
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.network.PixAccountApi
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.core.network.PixAuthApi
import com.lastpenguin.pix.generation.DataStoreGenerationConsent
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseTipper
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.generation.RemotePoseGenerator
import com.lastpenguin.pix.generation.RemotePoseTipper
import com.lastpenguin.pix.guide.FileSavedGuideStore
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.MlKitReferenceGuideMaker
import com.lastpenguin.pix.guide.OutlineExtractor
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.guide.SubjectSegmenter
import com.lastpenguin.pix.session.CameraFrameSource
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.OkHttpSignalingClient
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.RemoteVideo
import com.lastpenguin.pix.session.RtcSessionManager
import com.lastpenguin.pix.session.SessionIdentity
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.WebRtcRuntime
import com.lastpenguin.pix.session.WebpGuideImageCodec
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.signalingUrl
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The modules behind the contract interfaces, created in one place (Design 2.8): [create] builds the real ones for
 * the app, and a test builds a container from fakes and installs it with [com.lastpenguin.pix.PixApplication.installContainer], so a
 * screen can run under Robolectric without the camera, ML Kit, WebRTC, or the network. To work before another
 * owner's module is ready, replace it in [create] with your own fake, for example `sessionManager = FakeSessionManager()`.
 */
class AppContainer(
    // ---- Camera (#3) ----------------------------------------------------------
    val cameraController: CameraController,
    val cameraSettings: CameraSettings,
    // ---- Guide (#5, #6) -------------------------------------------------------
    /** The photographer's guide, clamped to the frame of the chosen aspect ratio (AD-10). */
    val guideRepository: GuideRepository,
    /**
     * The subject's copy of the photographer's guide. A separate instance (GuideRepository.kt). The photographer
     * clamps every state for its own frame (AD-7), so the copy only clamps with the limits of the narrowest frame,
     * which contain every ratio's: a `guide.state` that arrives before the `camera.state` of a new ratio, or before
     * the first one, is never moved.
     */
    val mirrorGuideRepository: GuideRepository,
    val referenceGuideMaker: ReferenceGuideMaker,
    /** Guides kept to use again, in the app's private files only (FR-3.8, AD-16, NFR-13). */
    val savedGuideStore: SavedGuideStore,
    // ---- Pose generation (#7) ---------------------------------------------------
    val poseGenerator: PoseGenerator,
    /** How to hold the phone for a generated pose (FR-4.10). */
    val poseTipper: PoseTipper,
    /** The tip of the guide on Camera, asked for with [poseTipper]. */
    val poseTips: PoseTips,
    val generationConsent: GenerationConsent,
    // ---- Accounts and friends (#54) ---------------------------------------------
    val authRepository: AuthRepository,
    val friendsRepository: FriendsRepository,
    val googleIdTokens: GoogleIdTokens,
    // ---- Session, guide sync, remote zoom (#8, #9, #10) -------------------------
    val sessionManager: SessionManager,
    /** The photographer's live video for the Subject view. */
    val remoteVideo: RemoteVideo,
    val guideSyncer: GuideSyncer,
    val remoteControlHandler: RemoteControlHandler,
) {
    companion object {
        /** Like the session's socket, so the course's proxy never sees it idle. */
        private const val PRESENCE_PING_SECONDS = 15L

        /** The real modules, created once per process by [PixApplication]; presence starts with them. */
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val cameraController: CameraController = CameraXController(appContext, PhotoSaver(appContext))
            val guideRepository: GuideRepository =
                InMemoryGuideRepository { cameraController.state.value.aspect.frameAspect }
            val mirrorGuideRepository: GuideRepository = InMemoryGuideRepository { NARROWEST_FRAME_ASPECT }

            /** Work that outlives every screen: loading the sign-in, presence, reading the saved guides. */
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val json = Json { ignoreUnknownKeys = true }
            fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.SERVER_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            // A pose arrives in one piece after 10–25 s. OkHttp's default gives up after 10 s without data, so wait
            // past the generator's own 60 s limit and let that limit decide.
            val pixApi: PixApi = retrofit(
                OkHttpClient.Builder().readTimeout(
                    RemotePoseGenerator.TIMEOUT_MS + 5_000,
                    TimeUnit.MILLISECONDS,
                ).build(),
            ).create(PixApi::class.java)

            // Three clients, each with its own dispatcher: the refresh in AuthTokens must never queue behind the
            // account calls that wait for it, or behind four pose requests (Design 2.5.3, Session tokens).
            val authApi: PixAuthApi = retrofit(OkHttpClient()).create(PixAuthApi::class.java)
            val authTokens = AuthTokens(DataStoreSignInStore(appContext), authApi, appScope)
            val accountApi: PixAccountApi = BearerTokens(authTokens).let { bearer ->
                retrofit(OkHttpClient.Builder().addInterceptor(bearer).authenticator(bearer).build())
                    .create(PixAccountApi::class.java)
            }
            val authRepository: AuthRepository = RemoteAuthRepository(authApi, accountApi, authTokens)

            // Whether any of Pix is in front. The container is created on the main thread, where observers are added.
            val foreground = MutableStateFlow(false).also { flow ->
                val lifecycle = ProcessLifecycleOwner.get().lifecycle
                lifecycle.addObserver(
                    LifecycleEventObserver { _, _ ->
                        flow.value = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    },
                )
            }
            val presenceClient = OkHttpClient.Builder().pingInterval(PRESENCE_PING_SECONDS, TimeUnit.SECONDS).build()
            PresenceConnection(
                authRepository,
                foreground,
                OkHttpPresenceSockets(presenceClient, signalingUrl(BuildConfig.SERVER_URL)),
                appScope,
            ).start()

            val frameSource = CameraFrameSource()
            val rtcSessionManager = RtcSessionManager(
                signaling = OkHttpSignalingClient(),
                // Loads the native WebRTC library on the first session, not at app start.
                peers = WebRtcRuntime(appContext, frameSource),
                frameSource = frameSource,
                codec = MessageCodec(),
                router = ChannelRouter(),
                serverUrl = BuildConfig.SERVER_URL,
                identity = SessionIdentity.of(appContext, BuildConfig.VERSION_NAME),
                haveGuideId = { mirrorGuideRepository.guide.value?.id },
            )
            val guideSyncer =
                GuideSyncer(rtcSessionManager, guideRepository, mirrorGuideRepository, WebpGuideImageCodec())
            val referenceGuideMaker: ReferenceGuideMaker =
                MlKitReferenceGuideMaker(appContext, SubjectSegmenter(appContext), OutlineExtractor())
            val poseTipper: PoseTipper = RemotePoseTipper(pixApi)
            return AppContainer(
                cameraController = cameraController,
                cameraSettings = DataStoreCameraSettings(appContext),
                guideRepository = guideRepository,
                mirrorGuideRepository = mirrorGuideRepository,
                referenceGuideMaker = referenceGuideMaker,
                savedGuideStore = FileSavedGuideStore(File(appContext.filesDir, "guides"), appScope),
                poseGenerator = RemotePoseGenerator(pixApi),
                poseTipper = poseTipper,
                poseTips = PoseTips(poseTipper, guideRepository.guide, appScope),
                generationConsent = DataStoreGenerationConsent(appContext),
                authRepository = authRepository,
                friendsRepository = RemoteFriendsRepository(accountApi, authRepository.state, appScope),
                googleIdTokens = CredentialManagerGoogleIdTokens(BuildConfig.GOOGLE_SERVER_CLIENT_ID),
                sessionManager = rtcSessionManager,
                remoteVideo = rtcSessionManager,
                guideSyncer = guideSyncer,
                remoteControlHandler = RemoteControlHandler(
                    rtcSessionManager,
                    cameraController,
                    guideRepository,
                    attributeGuideChanges = guideSyncer::attribute,
                ),
            )
        }
    }
}
