// AI-generated with ChatGPT Codex and Claude Code, 2026-11-26, reviewed by Jaewan Park, Dongje Park, and Joonhyung Han
package com.lastpenguin.pix.core

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.AuthTokens
import com.lastpenguin.pix.account.BearerTokens
import com.lastpenguin.pix.account.CredentialManagerGoogleIdTokens
import com.lastpenguin.pix.account.DataStoreSignInStore
import com.lastpenguin.pix.account.FriendsRepository
import com.lastpenguin.pix.account.GoogleIdTokens
import com.lastpenguin.pix.account.InvitationManager
import com.lastpenguin.pix.account.OkHttpPresenceSockets
import com.lastpenguin.pix.account.PresenceConnection
import com.lastpenguin.pix.account.PushTokenRegistrar
import com.lastpenguin.pix.account.RemoteAuthRepository
import com.lastpenguin.pix.account.RemoteFriendsRepository
import com.lastpenguin.pix.account.RemoteInvitationManager
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.CameraXController
import com.lastpenguin.pix.camera.DataStoreCameraSettings
import com.lastpenguin.pix.camera.NARROWEST_FRAME_ASPECT
import com.lastpenguin.pix.camera.PhonePhotoSource
import com.lastpenguin.pix.camera.PhonePhotos
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.camera.PhotoDeletion
import com.lastpenguin.pix.camera.PhotoSaver
import com.lastpenguin.pix.camera.SensorPhoneTurns
import com.lastpenguin.pix.camera.canManageMedia
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.camera.photoAccess
import com.lastpenguin.pix.core.network.PixAccountApi
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.core.network.PixAuthApi
import com.lastpenguin.pix.generation.DataStoreGenerationConsent
import com.lastpenguin.pix.generation.DataStorePromptHistory
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.MlKitPersonSignals
import com.lastpenguin.pix.generation.PersonDetector
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.generation.PoseTipper
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.generation.PromptHistory
import com.lastpenguin.pix.generation.RemotePoseGenerator
import com.lastpenguin.pix.generation.RemotePoseTipper
import com.lastpenguin.pix.generation.RuleBasedPersonDetector
import com.lastpenguin.pix.generation.ScreenLanguages
import com.lastpenguin.pix.generation.followSubjectSets
import com.lastpenguin.pix.guide.FileSavedGuideStore
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.MlKitReferenceGuideMaker
import com.lastpenguin.pix.guide.OutlineExtractor
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.guide.SubjectSegmenter
import com.lastpenguin.pix.push.FirebasePushTokens
import com.lastpenguin.pix.session.CameraFrameSource
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.ContentPhotoReader
import com.lastpenguin.pix.session.DataStoreMobileDataNotice
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.HeldGuide
import com.lastpenguin.pix.session.JpegGuideReferenceCodec
import com.lastpenguin.pix.session.JpegPoseCandidateCodec
import com.lastpenguin.pix.session.MaskGuideImageCodec
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.MobileDataNotice
import com.lastpenguin.pix.session.OkHttpSignalingClient
import com.lastpenguin.pix.session.PhotoSyncer
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.RemoteVideo
import com.lastpenguin.pix.session.RtcSessionManager
import com.lastpenguin.pix.session.SessionGuideKeeper
import com.lastpenguin.pix.session.SessionIdentity
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.WebRtcRuntime
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.signalingUrl
import com.lastpenguin.pix.settings.AndroidAppLanguage
import com.lastpenguin.pix.settings.AppLanguage
import com.lastpenguin.pix.settings.AppSettings
import com.lastpenguin.pix.settings.DataStoreAppSettings
import com.lastpenguin.pix.telemetry.FirebaseTelemetry
import com.lastpenguin.pix.telemetry.GatedTelemetry
import com.lastpenguin.pix.telemetry.Telemetry
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
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
    /** The phone's photos Pix may read, for Camera's thumbnail, the newest, and its viewer (FR-1.6). */
    val phonePhotos: PhonePhotoSource,
    /** Deletes a photo from the viewer: from the phone, and from the session's photos too (FR-1.6). */
    val photoDeleter: PhotoDeleter,
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
    /** The subject's copy of the photographer's reference photo, for Subject view's thumbnail (FR-3.9). */
    val mirrorReference: MirrorReference,
    val referenceGuideMaker: ReferenceGuideMaker,
    /** Guides kept to use again, in the app's private files only (FR-3.8, AD-16, NFR-13). */
    val savedGuideStore: SavedGuideStore,
    /** Each candidate kept in [savedGuideStore] as history, and what the ♥ marks (FR-4.14, #297). */
    val poseLibrary: PoseLibrary,
    // ---- Pose generation (#7) ---------------------------------------------------
    val poseGenerator: PoseGenerator,
    /** Looks for a person in the scene photo before anything is sent (FR-4.13). */
    val personDetector: PersonDetector,
    /** How to hold the phone for a generated pose (FR-4.10). */
    val poseTipper: PoseTipper,
    /** The tip of the guide on Camera, asked for with [poseTipper]. */
    val poseTips: PoseTips,
    val generationConsent: GenerationConsent,
    /** The descriptions used before, on this phone only (FR-4.11, NFR-13). */
    val promptHistory: PromptHistory,
    // ---- Accounts and friends (#54) ---------------------------------------------
    val authRepository: AuthRepository,
    val friendsRepository: FriendsRepository,
    val googleIdTokens: GoogleIdTokens,
    // ---- Invitations and push (#119) --------------------------------------------
    val invitationManager: InvitationManager,
    // ---- Session, guide sync, remote zoom (#8, #9, #10) -------------------------
    val sessionManager: SessionManager,
    /** The photographer's live video for the Subject view. */
    val remoteVideo: RemoteVideo,
    val guideSyncer: GuideSyncer,
    /** The photographer's candidates on the subject's *Pick a pose* (FR-6.15, Design 2.6.5). */
    val poseSyncer: PoseSyncer,
    /** The outline on the photographer's Reference confirm on the subject's *Your reference* (FR-6.15, Design 2.6.5). */
    val confirmSyncer: ConfirmSyncer,
    /** The photographer's *Generate poses* on the subject's, where both write the description (FR-6.15, Design 2.6.5). */
    val descriptionSyncer: DescriptionSyncer,
    /** Each photo taken in a session, for the subject's "Pix" album (FR-6.14, Design 2.6.5). */
    val photoSyncer: PhotoSyncer,
    /** The tip of the guide on the photographer's camera, on Subject view at a tap (FR-4.10, #213). */
    val tipSyncer: TipSyncer,
    val remoteControlHandler: RemoteControlHandler,
    /** Whether this phone has said once that a session uses mobile data (FR-6.13). */
    val mobileDataNotice: MobileDataNotice,
    // ---- Settings (#123) ---------------------------------------------------------
    /** The guide's look and the latency indicator from the settings screen (FR-10.1). */
    val appSettings: AppSettings,
    /** Pix's own language, which Android keeps (FR-10.2). */
    val appLanguage: AppLanguage,
    /** The screens' language for the text the server writes (FR-10.2); none in tests, whose tips stay in English. */
    val screenLanguages: ScreenLanguages? = null,
    /** Registers this phone's push token; none in tests, which have no Firebase. */
    val pushTokenRegistrar: PushTokenRegistrar? = null,
    /** How the phone is held, for photos saved as taken (FR-1.5) and the controls that turn (FR-1.15). */
    val phoneTurns: PhoneTurns = PhoneTurns.Fixed(),
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
            val mirrorReference = MirrorReference()
            // What the subject holds past a session, for `hello`: not the mirror, which is emptied at its end (#268).
            val heldGuide = HeldGuide()

            /** Work that outlives every screen: loading the sign-in, presence, reading the saved guides. */
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val appSettings = DataStoreAppSettings(appContext, appScope)
            // Crash reports and usage events (Design 2.8, Telemetry): only while Settings allows them (FR-10.6), from
            // the stored answer on, and never from a debug build unless pix.telemetry is set.
            val telemetry = GatedTelemetry(FirebaseTelemetry(appContext), buildAllows = BuildConfig.TELEMETRY)
            Telemetry.install(telemetry)
            appScope.launch { appSettings.shareUsage.filterNotNull().collect(telemetry::setAllowed) }

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

            // Pushes for invitations and friend requests reach this phone once someone signs in (Design 2.6.6).
            val pushTokenRegistrar =
                PushTokenRegistrar(accountApi, authRepository.state, FirebasePushTokens(), appScope).also { it.start() }

            val frameSource = CameraFrameSource()
            val rtcSessionManager = RtcSessionManager(
                signaling = OkHttpSignalingClient(),
                // A session's socket counts as signed in, so friends see "In a session" and an invitation to this
                // phone is BUSY meanwhile (FR-6.9).
                accessToken = authRepository::freshAccessToken,
                // The other phone names this one by its account while signed in, else by the phone's name (#271).
                accountName = { (authRepository.state.value as? AuthState.SignedIn)?.account?.displayName },
                // Loads the native WebRTC library on the first session, not at app start.
                peers = WebRtcRuntime(appContext, frameSource, relayOnly = BuildConfig.RELAY_ONLY),
                frameSource = frameSource,
                codec = MessageCodec(),
                router = ChannelRouter(),
                serverUrl = BuildConfig.SERVER_URL,
                identity = SessionIdentity.of(appContext, BuildConfig.VERSION_NAME),
                haveGuideId = { heldGuide.guideId },
                haveReferenceId = { heldGuide.referenceId },
            )
            val guideSyncer = GuideSyncer(
                rtcSessionManager,
                guideRepository,
                mirrorGuideRepository,
                MaskGuideImageCodec(),
                JpegGuideReferenceCodec(),
                mirrorReference,
                held = heldGuide,
            )
            val referenceGuideMaker: ReferenceGuideMaker =
                MlKitReferenceGuideMaker(appContext, SubjectSegmenter(appContext), OutlineExtractor())
            // The tip is written in the screens' language, and asked again when it changes while Pix runs (FR-10.2).
            val screenLanguages = ScreenLanguages(appContext)
            val screenLanguage = screenLanguages.current
            val poseTipper: PoseTipper = RemotePoseTipper(pixApi, language = { screenLanguage.value })
            val descriptionSyncer = DescriptionSyncer(rtcSessionManager, JpegPoseCandidateCodec())
            val poseSyncer = PoseSyncer(rtcSessionManager, JpegPoseCandidateCodec())
            // As the subject, each set the photographer starts is this phone's history too (FR-4.11), whichever
            // screen is in front, once the server accepted its description.
            val savedGuideStore = FileSavedGuideStore(File(appContext.filesDir, "guides"), appScope)
            val promptHistory = DataStorePromptHistory(appContext, appScope)
            promptHistory.followSubjectSets(descriptionSyncer.started, poseSyncer.mirror, poseSyncer.verdicts, appScope)
            val poseTips = PoseTips(poseTipper, guideRepository.guide, appScope, screenLanguage)
            val poseLibrary = PoseLibrary(referenceGuideMaker, savedGuideStore, appScope)
            // In a session, each phone keeps what the other keeps by itself: the guides that go on the camera and every
            // pose candidate (#297), whichever screen is in front.
            SessionGuideKeeper(
                savedGuideStore,
                poseLibrary,
                guideRepository,
                guideSyncer.adopted,
                mirrorGuideRepository,
                mirrorReference.reference,
                poseSyncer.mirror,
            ).start(appScope)
            val phonePhotos = PhonePhotos(
                appContext.contentResolver,
                appScope,
                appContext.packageName,
                appContext::photoAccess,
                canManageMedia = appContext::canManageMedia,
            )
            val photoSyncer = PhotoSyncer(
                rtcSessionManager,
                ContentPhotoReader(appContext.contentResolver),
                PhotoSaver(appContext),
                File(appContext.cacheDir, "session-photos"),
            )
            return AppContainer(
                cameraController = cameraController,
                phonePhotos = phonePhotos,
                photoDeleter = object : PhotoDeleter {
                    override suspend fun delete(
                        uri: Uri,
                    ): PhotoDeletion = photoSyncer.delete(uri, phonePhotos, appScope)

                    override fun asksFirst(uri: Uri) = phonePhotos.asksFirst(uri)

                    override fun needsAccess(uri: Uri) = phonePhotos.needsAccess(uri)
                },
                cameraSettings = DataStoreCameraSettings(appContext),
                guideRepository = guideRepository,
                mirrorGuideRepository = mirrorGuideRepository,
                mirrorReference = mirrorReference,
                referenceGuideMaker = referenceGuideMaker,
                savedGuideStore = savedGuideStore,
                poseLibrary = poseLibrary,
                poseGenerator = RemotePoseGenerator(pixApi),
                personDetector = RuleBasedPersonDetector(MlKitPersonSignals()),
                poseTipper = poseTipper,
                poseTips = poseTips,
                generationConsent = DataStoreGenerationConsent(appContext),
                promptHistory = promptHistory,
                authRepository = authRepository,
                friendsRepository = RemoteFriendsRepository(accountApi, authRepository.state, appScope),
                googleIdTokens = CredentialManagerGoogleIdTokens(BuildConfig.GOOGLE_SERVER_CLIENT_ID),
                invitationManager = RemoteInvitationManager(accountApi),
                sessionManager = rtcSessionManager,
                remoteVideo = rtcSessionManager,
                guideSyncer = guideSyncer,
                poseSyncer = poseSyncer,
                confirmSyncer = ConfirmSyncer(rtcSessionManager, MaskGuideImageCodec()),
                descriptionSyncer = descriptionSyncer,
                tipSyncer = TipSyncer(rtcSessionManager, poseTips.onCamera),
                photoSyncer = photoSyncer,
                remoteControlHandler = RemoteControlHandler(
                    rtcSessionManager,
                    cameraController,
                    guideRepository,
                    attributeGuideChanges = guideSyncer::attribute,
                ),
                mobileDataNotice = DataStoreMobileDataNotice(appContext),
                appSettings = appSettings,
                appLanguage = AndroidAppLanguage(),
                screenLanguages = screenLanguages,
                pushTokenRegistrar = pushTokenRegistrar,
                phoneTurns = SensorPhoneTurns(appContext),
            )
        }
    }
}
