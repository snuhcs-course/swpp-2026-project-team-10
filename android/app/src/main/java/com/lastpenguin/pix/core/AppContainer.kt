// AI-generated with ChatGPT Codex and Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.core

import android.content.Context
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.CameraXController
import com.lastpenguin.pix.camera.DataStoreCameraSettings
import com.lastpenguin.pix.camera.NARROWEST_FRAME_ASPECT
import com.lastpenguin.pix.camera.PhotoSaver
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.generation.DataStoreGenerationConsent
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.RemotePoseGenerator
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.MlKitReferenceGuideMaker
import com.lastpenguin.pix.guide.OutlineExtractor
import com.lastpenguin.pix.guide.ReferenceGuideMaker
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
import java.util.concurrent.TimeUnit
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
    // ---- Pose generation (#7) ---------------------------------------------------
    val poseGenerator: PoseGenerator,
    val generationConsent: GenerationConsent,
    // ---- Session, guide sync, remote zoom (#8, #9, #10) -------------------------
    val sessionManager: SessionManager,
    /** The photographer's live video for the Subject view. */
    val remoteVideo: RemoteVideo,
    val guideSyncer: GuideSyncer,
    val remoteControlHandler: RemoteControlHandler,
) {
    companion object {
        /** The real modules, created once per process by [PixApplication]. */
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val cameraController: CameraController = CameraXController(appContext, PhotoSaver(appContext))
            val guideRepository: GuideRepository =
                InMemoryGuideRepository { cameraController.state.value.aspect.frameAspect }
            val mirrorGuideRepository: GuideRepository = InMemoryGuideRepository { NARROWEST_FRAME_ASPECT }

            val pixApi: PixApi = Retrofit.Builder()
                .baseUrl(BuildConfig.SERVER_URL)
                // A pose arrives in one piece after 10–25 s. OkHttp's default gives up after 10 s without data, so
                // wait past the generator's own 60 s limit and let that limit decide.
                .client(
                    OkHttpClient.Builder()
                        .readTimeout(RemotePoseGenerator.TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS)
                        .build(),
                )
                .addConverterFactory(
                    Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()),
                )
                .build()
                .create(PixApi::class.java)

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
            return AppContainer(
                cameraController = cameraController,
                cameraSettings = DataStoreCameraSettings(appContext),
                guideRepository = guideRepository,
                mirrorGuideRepository = mirrorGuideRepository,
                referenceGuideMaker =
                    MlKitReferenceGuideMaker(appContext, SubjectSegmenter(appContext), OutlineExtractor()),
                poseGenerator = RemotePoseGenerator(pixApi),
                generationConsent = DataStoreGenerationConsent(appContext),
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
