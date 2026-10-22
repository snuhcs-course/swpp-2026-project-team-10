// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationEvent
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * An [AppContainer] of fakes for screen tests under Robolectric (Testing Documentation 2.1.3): the camera, the
 * session, and the guide stores are the fakes of the module tests, and the modules a screen test never drives
 * (the guide maker, pose generation, consent) do nothing. [install] puts it where [PixViewModels] looks, so a
 * Fragment launched by FragmentScenario gets its ViewModels with these modules.
 */
class TestAppContainer(
    val camera: FakeCameraController = FakeCameraController(),
    val session: FakeSessionManager = FakeSessionManager(),
    frameAspect: () -> Float = { camera.state.value.aspect.frameAspect },
) {
    val guides: GuideRepository = InMemoryGuideRepository(frameAspect)
    val mirror: GuideRepository = InMemoryGuideRepository()
    val settings = CameraSettings.InMemory()
    val consent = IdleConsent()
    private val syncer = GuideSyncer(session, guides, mirror, FakeGuideImageCodec())

    val container = AppContainer(
        cameraController = camera,
        cameraSettings = settings,
        guideRepository = guides,
        mirrorGuideRepository = mirror,
        referenceGuideMaker = IdleGuideMaker(),
        poseGenerator = IdlePoseGenerator(),
        generationConsent = consent,
        sessionManager = session,
        remoteVideo = FakeRemoteVideo(),
        guideSyncer = syncer,
        remoteControlHandler = RemoteControlHandler(session, camera, guides, attributeGuideChanges = syncer::attribute),
    )

    /** Installs the container in the test's [PixApplication]; call before the screen is launched. */
    fun install(): TestAppContainer {
        ApplicationProvider.getApplicationContext<PixApplication>().installContainer(container)
        return this
    }

    /** Never asked to make a guide in a screen test; fails loudly if it is. */
    class IdleGuideMaker : ReferenceGuideMaker {
        override suspend fun make(source: Uri): Result<ReferenceGuide> =
            Result.failure(UnsupportedOperationException("not in this test"))

        override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> =
            Result.failure(UnsupportedOperationException("not in this test"))
    }

    /** Generates nothing; the screens that would collect it are not launched. */
    class IdlePoseGenerator : PoseGenerator {
        override fun generate(scene: Bitmap, request: PoseRequest, seed: Long): Flow<GenerationEvent> = emptyFlow()
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
