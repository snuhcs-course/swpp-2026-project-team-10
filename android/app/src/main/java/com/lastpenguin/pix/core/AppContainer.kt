package com.lastpenguin.pix.core

import android.content.Context
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraXController
import com.lastpenguin.pix.camera.PhotoSaver
import com.lastpenguin.pix.core.network.PixApi
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
import com.lastpenguin.pix.session.PeerConnectionClient
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.RtcSessionManager
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SignalingClient
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.MessageCodec
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
    val guideRepository: GuideRepository = InMemoryGuideRepository()

    /** The subject's copy of the photographer's guide. A separate instance (GuideRepository.kt). */
    val mirrorGuideRepository: GuideRepository = InMemoryGuideRepository()

    val referenceGuideMaker: ReferenceGuideMaker =
        MlKitReferenceGuideMaker(appContext, SubjectSegmenter(appContext), OutlineExtractor())

    // ---- Pose generation (#7) ---------------------------------------------------
    private val pixApi: PixApi = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_URL)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PixApi::class.java)

    val poseGenerator: PoseGenerator = RemotePoseGenerator(pixApi)

    // ---- Session, guide sync, remote zoom (#8, #9, #10) -------------------------
    val sessionManager: SessionManager = RtcSessionManager(
        signaling = SignalingClient(),
        peer = PeerConnectionClient(appContext),
        frameSource = CameraFrameSource(),
        codec = MessageCodec(),
        router = ChannelRouter(),
    )

    val guideSyncer = GuideSyncer(sessionManager, guideRepository, mirrorGuideRepository)

    val remoteControlHandler = RemoteControlHandler(sessionManager, cameraController)
}
