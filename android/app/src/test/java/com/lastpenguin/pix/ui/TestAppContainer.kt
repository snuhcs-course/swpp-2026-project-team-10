// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han, Sungmin Jo, Jaewan Park, and Dongje Park
package com.lastpenguin.pix.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.account.FakeFriendsRepository
import com.lastpenguin.pix.account.FakeGoogleIdTokens
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationEvent
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.PoseTipper
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.FakeGuideReferenceCodec
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.MobileDataNotice
import com.lastpenguin.pix.session.RemoteControlHandler
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
) {
    val guides: GuideRepository = InMemoryGuideRepository(frameAspect)
    val mirror: GuideRepository = InMemoryGuideRepository()

    /** The subject's reference photo; a test sets it as the syncer would. */
    val reference = MirrorReference()
    val settings = CameraSettings.InMemory()
    val consent = IdleConsent()
    val maker = ScriptedGuideMaker()
    val generator = ScriptedPoseGenerator()
    val tipper = ScriptedPoseTipper()

    val tips = PoseTips(tipper, guides.guide, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    val auth = FakeAuthRepository()
    val video = FakeRemoteVideo()
    val friends = FakeFriendsRepository()
    val mobileData = MobileDataNotice.InMemory()
    val syncer =
        GuideSyncer(session, guides, mirror, FakeGuideImageCodec(), FakeGuideReferenceCodec(), reference)

    val container = AppContainer(
        cameraController = camera,
        cameraSettings = settings,
        guideRepository = guides,
        mirrorGuideRepository = mirror,
        mirrorReference = reference,
        referenceGuideMaker = maker,
        savedGuideStore = saved,
        poseGenerator = generator,
        poseTipper = tipper,
        poseTips = tips,
        generationConsent = consent,
        authRepository = auth,
        friendsRepository = friends,
        googleIdTokens = FakeGoogleIdTokens(),
        sessionManager = session,
        remoteVideo = video,
        guideSyncer = syncer,
        remoteControlHandler = RemoteControlHandler(session, camera, guides, attributeGuideChanges = syncer::attribute),
        mobileDataNotice = mobileData,
    )

    /** Installs the container in the test's [PixApplication]; call before the screen is launched. */
    fun install(): TestAppContainer {
        ApplicationProvider.getApplicationContext<PixApplication>().installContainer(container)
        return this
    }

    /** Makes a guide when the test says so: each request waits for [finish]. */
    class ScriptedGuideMaker : ReferenceGuideMaker {
        val pending = ArrayDeque<CompletableDeferred<Result<ReferenceGuide>>>()
        val sources = mutableListOf<GuideSource>()

        override suspend fun make(source: Uri): Result<ReferenceGuide> = next(GuideSource.GALLERY)

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
        ): Flow<GenerationEvent> {
            val run = Run(scene, request, selfie).also(runs::add)
            return flow {
                try {
                    for (event in run.events) emit(event)
                } catch (error: CancellationException) {
                    run.cancelled = true
                    throw error
                }
            }
        }

        class Run(val scene: Bitmap, val request: PoseRequest, val selfie: Boolean = false) {
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

        override suspend fun isGiven(): Boolean = given

        override suspend fun give() {
            given = true
        }
    }
}
