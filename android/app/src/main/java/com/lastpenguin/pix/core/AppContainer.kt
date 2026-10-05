package com.lastpenguin.pix.core

import android.content.Context
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraXController
import com.lastpenguin.pix.camera.PhotoSaver
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.generation.DataStoreGenerationConsent
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.RemotePoseGenerator
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.InterimGuideRepository
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
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.MessageCodec
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Creates the modules behind the contract interfaces in one place (Design 2.8).
 * To work before another owner's module is ready, replace it here with your own fake,
 * for example `val sessionManager: SessionManager = FakeSessionManager()`.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // ---- Camera (#3) ----------------------------------------------------------
    val cameraController: CameraController = CameraXController(appContext, PhotoSaver(appContext))

    // ---- Guide (#5, #6) -------------------------------------------------------

    /** The photographer's guide. */
    // TODO(#6): back to InMemoryGuideRepository() once #6 fills it in; InterimGuideRepository stands in until then.
    val guideRepository: GuideRepository = InterimGuideRepository()

    /** The subject's copy of the photographer's guide. A separate instance (GuideRepository.kt). */
    val mirrorGuideRepository: GuideRepository = InMemoryGuideRepository()

    val referenceGuideMaker: ReferenceGuideMaker =
        MlKitReferenceGuideMaker(appContext, SubjectSegmenter(appContext), OutlineExtractor())

    // ---- Pose generation (#7) ---------------------------------------------------
    private val pixApi: PixApi = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_URL)
        // A pose arrives in one piece after 10–25 s. OkHttp's default gives up after 10 s without data, so wait
        // past the generator's own 30 s limit and let that limit decide.
        .client(
            OkHttpClient.Builder()
                .readTimeout(RemotePoseGenerator.TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS)
                .build(),
        )
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PixApi::class.java)

    val poseGenerator: PoseGenerator = RemotePoseGenerator(pixApi)

    val generationConsent: GenerationConsent = DataStoreGenerationConsent(appContext)

    // ---- Session, guide sync, remote zoom (#8, #9, #10) -------------------------
    private val frameSource = CameraFrameSource()

    private val rtcSessionManager = RtcSessionManager(
        signaling = OkHttpSignalingClient(),
        // Loads the native WebRTC library on the first session, not at app start.
        peers = WebRtcRuntime(appContext, frameSource),
        frameSource = frameSource,
        codec = MessageCodec(),
        router = ChannelRouter(),
        serverUrl = BuildConfig.SERVER_URL,
        identity = SessionIdentity.of(appContext, BuildConfig.VERSION_NAME),
    )

    val sessionManager: SessionManager = rtcSessionManager

    /** The photographer's live video for the Subject view. */
    val remoteVideo: RemoteVideo = rtcSessionManager

    val guideSyncer = GuideSyncer(sessionManager, guideRepository, mirrorGuideRepository)

    val remoteControlHandler = RemoteControlHandler(sessionManager, cameraController)
}
