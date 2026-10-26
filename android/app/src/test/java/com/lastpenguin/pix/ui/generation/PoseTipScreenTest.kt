// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Pose tips from Pick a pose (FR-4.10; Testing Documentation 2.1.6), on the real Fragments: Use this pose asks for the
 * tip of the pose taken, from the scene it was made from, once its person is found on Reference confirm, though the
 * set itself is let go at once; Reference confirm's Use this guide brings the tip to Camera.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PoseTipScreenTest {
    private var scenario: FragmentScenario<*>? = null
    private val poses = listOf("Lean on the wall", "Walk toward the camera", "Sit on the step")

    private companion object {
        const val TIP = "Hold the phone at your chest and keep it level."
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** A finished set of three ready candidates (PICKING). */
    private fun picking(modules: TestAppContainer, activity: FragmentActivity) {
        activity.viewModel<GenerationViewModel>().apply {
            beginScene()
            takeScene()
            useScene()
            suggestPoses()
        }
        val run = modules.generator.runs.single()
        run.plan(poses)
        run.send(*Array(poses.size) { CandidateEvent.Ready("pose-${it + 1}", bitmap()) })
        run.finish()
    }

    @Test
    fun useThisPose_asksForTheTipOfThatPose_fromItsScene_andUsingTheGuideBringsItToCamera() {
        val modules = TestAppContainer(camera = FakeCameraController().apply { frame = bitmap(300, 400) }).install()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        var scene: Bitmap? = null
        s.onFragment { fragment ->
            val generation = fragment.requireActivity().viewModel<GenerationViewModel>()
            generation.select("pose-2")
            scene = generation.uiState.value.scene
        }
        idle()

        s.onFragment { FragmentPickPoseBinding.bind(it.requireView()).usePoseButton.performClick() }
        idle()
        // Nothing is asked before Reference confirm has found the person.
        assertEquals(0, modules.tipper.requests.size)
        val guide = ReferenceGuide(
            id = "generated",
            cutout = bitmap(),
            outline = bitmap(),
            aspect = 0.5f,
            source = GuideSource.GENERATED,
            anchor = NormalizedRect(0.3f, 0.1f, 0.7f, 0.9f),
        )
        modules.maker.finish(Result.success(guide))
        idle()

        val request = modules.tipper.requests.single()
        assertEquals("Walk toward the camera", request.pose)
        assertSame(scene, request.scene)
        request.tip(TIP)
        idle()
        assertNull(modules.tips.onCamera.value)

        s.onFragment { it.requireActivity().viewModel<ReferenceViewModel>().onUseGuide() }
        idle()

        assertEquals(TipState.Ready(PoseTip(TIP)), modules.tips.onCamera.value?.state)
    }
}
