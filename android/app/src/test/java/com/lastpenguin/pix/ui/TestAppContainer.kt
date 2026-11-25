// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Dongje Park, and Joonhyung Han
package com.lastpenguin.pix.ui

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.account.FakeFriendsRepository
import com.lastpenguin.pix.account.FakeGoogleIdTokens
import com.lastpenguin.pix.account.FakeInvitationManager
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.PhoneAlbum
import com.lastpenguin.pix.camera.PhonePhotoSource
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoAccess
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.camera.PhotoDeletion
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationEvent
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.PoseTipper
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.generation.PromptHistory
import com.lastpenguin.pix.generation.ScreenLanguages
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.FakeGuideReferenceCodec
import com.lastpenguin.pix.session.FakePersonDetector
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.MobileDataNotice
import com.lastpenguin.pix.session.PhotoSyncer
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.fakeBitmap
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.settings.AppLanguage
import com.lastpenguin.pix.settings.AppSettings
import com.lastpenguin.pix.settings.TutorialScreen
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.zip.CRC32
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.robolectric.RuntimeEnvironment

/**
 * An [AppContainer] of fakes for screen tests under Robolectric (Testing Documentation 2.1.3): the camera, the
 * session, and the guide stores are the fakes of the module tests; the guide maker, pose generation, and pose tips
 * answer when the test says so ([maker], [generator], [tipper]), consent is already given, and the account is signed out ([auth]). [install] puts it where [PixViewModels]
 * looks, so a Fragment launched by FragmentScenario gets its ViewModels with these modules.
 */
class TestAppContainer(
    val camera: FakeCameraController = FakeCameraController(),
    val session: FakeSessionManager = FakeSessionManager(),
    frameAspect: () -> Float = { camera.state.value.aspect.frameAspect },
    val saved: SavedGuideStore = SavedGuideStore.InMemory(),
    /** What the photographer's candidates decode to on the subject's phone; a test that draws one gives a real one. */
    candidateImage: () -> Bitmap = { fakeBitmap() },
    /** The screens' language for the server's text; none unless a test checks it. */
    val screenLanguages: ScreenLanguages? = null,
    /** The tutorial's slides seen; all unless a test is about them, so a screen test has no slide over its screen. */
    tutorialSeen: Set<TutorialScreen>? = TutorialScreen.entries.toSet(),
    /** Whether usage data is shared (FR-10.6), the default; null while the stored answer loads. */
    shareUsage: Boolean? = true,
) {
    val guides: GuideRepository = InMemoryGuideRepository(frameAspect)
    val mirror: GuideRepository = InMemoryGuideRepository()

    /** The subject's reference photo; a test sets it as the syncer would. */
    val reference = MirrorReference()
    val settings = CameraSettings.InMemory()
    val consent = IdleConsent()

    /** The descriptions used before; a test records the ones its screen should list. */
    val history = PromptHistory.InMemory()
    val maker = ScriptedGuideMaker()
    val generator = ScriptedPoseGenerator()

    /** Finds a person in every scene photo unless a test says otherwise (FR-4.13). */
    val persons = FakePersonDetector()
    val tipper = ScriptedPoseTipper()

    val tips = PoseTips(tipper, guides.guide, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    val auth = FakeAuthRepository()
    val video = FakeRemoteVideo()
    val friends = FakeFriendsRepository()
    val invitations = FakeInvitationManager()
    val mobileData = MobileDataNotice.InMemory()
    val appSettings = AppSettings.InMemory(tutorial = tutorialSeen, usage = shareUsage)
    val language = AppLanguage.InMemory()

    /** The guide on both phones, with an image and a reference photo a screen can draw (a guide from the subject). */
    val syncer = GuideSyncer(
        session,
        guides,
        mirror,
        FakeGuideImageCodec { id -> ScreenTestSupport.guide(withReference = false).copy(id = id) },
        FakeGuideReferenceCodec { ScreenTestSupport.bitmap(60, 80) },
        reference,
        Dispatchers.Unconfined,
    )

    /** The photographer's candidates on the subject's phone; on the main thread, as the screens collect it. */
    val poses = PoseSyncer(session, FakePoseCandidateCodec(candidateImage), Dispatchers.Unconfined)

    /** The photographer's Reference confirm on the subject's phone, the same way, with outlines a screen can draw. */
    val confirms = ConfirmSyncer(
        session,
        FakeGuideImageCodec { id -> ScreenTestSupport.guide(withReference = false).copy(id = id) },
        Dispatchers.Unconfined,
    )

    /** How the phone is held; upright unless the test turns it (FR-1.5). */
    val turns = PhoneTurns.Fixed()

    /** The photographer's Generate poses on the subject's phone, the same way, with a scene photo a screen can draw. */
    val descriptions =
        DescriptionSyncer(session, FakePoseCandidateCodec { ScreenTestSupport.bitmap(60, 80) }, Dispatchers.Unconfined)

    /** The photographer's tip of the guide on the subject's phone (#213), from this container's own tips. */
    val tipSync = TipSyncer(session, tips.onCamera)

    /**
     * The session's photos, the same way; a screen test sends none. The subject's album saves each photo as
     * [savedPhoto] of its id unless [albumFails]; nothing is written.
     */
    val photos = PhotoSyncer(
        session,
        { null },
        { _, photoId, _ ->
            if (albumFails) Result.failure(IOException("The disk is full")) else Result.success(savedPhoto(photoId))
        },
        File(System.getProperty("java.io.tmpdir"), "pix-session-photos"),
        Dispatchers.Unconfined,
    )

    /** Whether the subject's album refuses the photographer's photos, as a full disk does (FR-6.14). */
    var albumFails = false

    /**
     * The phone's photos that Pix may read (FR-1.6), oldest first, which a test sets: no MediaStore here tells of a
     * saved photo.
     */
    val album = MutableStateFlow<List<Uri>>(emptyList())

    /** How much of the phone's photos [album] has; all of them unless a test says otherwise. */
    val photoAccess = MutableStateFlow(PhotoAccess.ALL)

    /** The albums of [album], which a test sets with it. */
    val albums = MutableStateFlow<List<PhoneAlbum>>(emptyList())

    /** What reading the photos again brings, as after the permission was asked; [rereads] counts them. */
    var onReread: () -> Unit = {}
    var rereads = 0
        private set

    private val phonePhotos = object : PhonePhotoSource {
        override val photos: StateFlow<List<Uri>> = album
        override val access: StateFlow<PhotoAccess> = photoAccess
        override val albums: StateFlow<List<PhoneAlbum>> = this@TestAppContainer.albums

        override suspend fun refresh() {
            rereads++
            onReread()
        }
    }

    /** The photos the viewer deleted, in order; each leaves [album] and the session's photos unless [deleteFails]. */
    val deleted = mutableListOf<Uri>()

    /** Whether a delete fails, as when MediaStore refuses. */
    var deleteFails = false

    /** Other apps' photos, which only Android's question deletes, [deleteQuestion]; its yes deletes them. */
    var othersPhotos: Set<Uri> = emptySet()

    /** Stands in for Android's question before another app's photo is deleted. */
    val deleteQuestion: IntentSender = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent("com.lastpenguin.pix.test.DELETE_QUESTION"),
        PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    /** The phone's own delete, as `PhonePhotos.delete`. */
    private val albumDeleter = PhotoDeleter { uri ->
        when {
            uri in othersPhotos -> PhotoDeletion.NeedsConsent(deleteQuestion, deletesItself = true)

            deleteFails -> PhotoDeletion.Failed

            else -> {
                deleted += uri
                album.value -= uri
                PhotoDeletion.Deleted
            }
        }
    }

    /**
     * The viewer's delete, through the real `PhotoSyncer` as in the app. A screen test starts no sender, so a photo
     * taken in a session waits to go and is deleted only after its session.
     */
    private val deleter = object : PhotoDeleter {
        override suspend fun delete(uri: Uri) = photos.delete(uri, albumDeleter, CoroutineScope(Dispatchers.Main))

        override fun asksFirst(uri: Uri) = uri !in othersPhotos
    }

    /** The candidates kept as history through [maker], so a test finishes each cut it waits for. */
    val poseLibrary = PoseLibrary(maker, saved, CoroutineScope(Dispatchers.Main))

    val container = AppContainer(
        cameraController = camera,
        cameraSettings = settings,
        phonePhotos = phonePhotos,
        photoDeleter = deleter,
        guideRepository = guides,
        mirrorGuideRepository = mirror,
        mirrorReference = reference,
        referenceGuideMaker = maker,
        savedGuideStore = saved,
        poseLibrary = poseLibrary,
        poseGenerator = generator,
        personDetector = persons,
        poseTipper = tipper,
        poseTips = tips,
        generationConsent = consent,
        promptHistory = history,
        authRepository = auth,
        friendsRepository = friends,
        googleIdTokens = FakeGoogleIdTokens(),
        invitationManager = invitations,
        sessionManager = session,
        remoteVideo = video,
        guideSyncer = syncer,
        poseSyncer = poses,
        confirmSyncer = confirms,
        descriptionSyncer = descriptions,
        photoSyncer = photos,
        tipSyncer = tipSync,
        remoteControlHandler = RemoteControlHandler(
            session,
            camera,
            guides,
            attributeGuideChanges = syncer::attribute,
        ),
        mobileDataNotice = mobileData,
        appSettings = appSettings,
        appLanguage = language,
        screenLanguages = screenLanguages,
        phoneTurns = turns,
    )

    companion object {
        /** Where the subject's album keeps the photographer's photo [photoId] in a screen test. */
        fun savedPhoto(photoId: String): Uri = Uri.parse("content://media/external/images/media/$photoId")
    }

    /**
     * Installs the container in the test's [PixApplication]; call before the screen is launched. The screen is drawn
     * dark, as on the phone: Pix keeps dark through `AppCompatDelegate`, which only an `AppCompatActivity` follows, and
     * `launchFragmentInContainer` hosts a plain `FragmentActivity`, so the test runs in the night configuration.
     */
    fun install(): TestAppContainer {
        RuntimeEnvironment.setQualifiers("+night")
        // Camera's hand-off to Generate poses lives with the process; one an earlier test left is not this test's.
        com.lastpenguin.pix.ui.generation.SceneHandOff.pending = null
        ApplicationProvider.getApplicationContext<PixApplication>().installContainer(container)
        return this
    }

    /**
     * The photographer's photo [photoId] reaching this phone whole, as `photo.begin`, one chunk, and `photo.end`; the
     * subject's [photos] must be receiving. A few bytes that start as a JPEG are enough, since nothing decodes them.
     */
    fun deliverPhoto(photoId: String, shot: String? = null) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        session.deliver(SessionMessage.PhotoBegin(photoId, "jpeg", bytes.size.toLong(), 1, takenAt = 1, shot = shot))
        session.deliver(SessionMessage.PhotoChunk(photoId, 0, Base64.getEncoder().encodeToString(bytes)))
        session.deliver(SessionMessage.PhotoEnd(photoId, CRC32().apply { update(bytes) }.value))
    }

    /** Makes a guide when the test says so: each request waits for [finish]. */
    class ScriptedGuideMaker : ReferenceGuideMaker {
        val pending = ArrayDeque<CompletableDeferred<Result<ReferenceGuide>>>()
        val sources = mutableListOf<GuideSource>()

        /** Each picked photo asked for, and whether as the whole image (FR-2.10). */
        val picked = mutableListOf<Pair<Uri, Boolean>>()

        override suspend fun make(source: Uri, wholeImage: Boolean): Result<ReferenceGuide> {
            picked += source to wholeImage
            return next(GuideSource.GALLERY)
        }

        override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> = next(source)

        fun finish(result: Result<ReferenceGuide>) {
            pending.removeFirst().complete(result)
        }

        private suspend fun next(source: GuideSource): Result<ReferenceGuide> {
            sources += source
            return CompletableDeferred<Result<ReferenceGuide>>().also(pending::addLast).await()
        }
    }

    /** Generates what the test sends: one [Run] per request, whose events the test feeds and closes. */
    class ScriptedPoseGenerator : PoseGenerator {
        val runs = mutableListOf<Run>()

        override fun generate(
            scene: Bitmap,
            request: PoseRequest,
            seed: Long,
            selfie: Boolean,
            situation: Situation,
        ): Flow<GenerationEvent> {
            val run = Run(scene, request, selfie, situation).also(runs::add)
            return flow {
                try {
                    for (event in run.events) emit(event)
                } catch (error: CancellationException) {
                    run.cancelled = true
                    throw error
                }
            }
        }

        class Run(
            val scene: Bitmap,
            val request: PoseRequest,
            val selfie: Boolean = false,
            val situation: Situation = Situation.DEFAULT,
        ) {
            val events = Channel<GenerationEvent>(Channel.UNLIMITED)
            var cancelled = false

            /** The poses of the set are known, `pose-1` … `pose-n`. */
            fun plan(texts: List<String>) = send(
                GenerationEvent.Planned(texts.mapIndexed { index, text -> PlannedPose("pose-${index + 1}", text) }),
            )

            fun send(vararg sent: GenerationEvent) {
                for (event in sent) check(events.trySend(event).isSuccess)
            }

            /** The run ends; the ViewModel then goes to PICKING or FAILED. */
            fun finish() {
                events.close()
            }
        }
    }

    /** Tips what the test says: one [Request] per call, which waits until the test answers it. */
    class ScriptedPoseTipper : PoseTipper {
        val requests = mutableListOf<Request>()

        override suspend fun tip(scene: Bitmap, candidate: Bitmap, pose: String): Result<PoseTip> {
            val request = Request(scene, candidate, pose).also(requests::add)
            try {
                return request.answer.await()
            } catch (error: CancellationException) {
                request.cancelled = true
                throw error
            }
        }

        class Request(val scene: Bitmap, val candidate: Bitmap, val pose: String) {
            val answer = CompletableDeferred<Result<PoseTip>>()
            var cancelled = false

            fun tip(text: String) = check(answer.complete(Result.success(PoseTip(text))))

            fun fail() = check(answer.complete(Result.failure(IOException("No tip"))))
        }
    }

    /** Consent already given, so the scene photo step opens without the notice. */
    class IdleConsent : GenerationConsent {
        var given = true

        /** When set, the stored answer is handed over only after this completes, as a slow DataStore read. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun isGiven(): Boolean {
            gate?.await()
            return given
        }

        override suspend fun give() {
            given = true
        }

        override suspend fun withdraw() {
            given = false
        }
    }
}
