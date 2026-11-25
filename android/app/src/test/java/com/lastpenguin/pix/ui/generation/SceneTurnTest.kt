// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakePersonDetector
import com.lastpenguin.pix.session.PoseShare
import com.lastpenguin.pix.ui.TestAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The scene photo as the phone was held (FR-4.3, #206): turned upright, so a photo taken sideways is a landscape one
 * and the server makes landscape candidates; its review on Camera shows it as framed.
 * Partitions (equivalence): held at 0, 90, 180, and 270 degrees, with the rear camera and with the front one, whose
 * frame comes mirrored as its preview (FR-1.14) and is turned the same way. What gets the upright photo: the person
 * check, the request, *Select*, which hands the turn on to Reference confirm, and the set's start, which tells the
 * subject's phone. Back from a set to the review shows the photo as framed again. The turn is read with the frame, not
 * at the press.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneTurnTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val camera = FakeCameraController()
    private val persons = FakePersonDetector()
    private val generator = TestAppContainer.ScriptedPoseGenerator()
    private val turn = MutableStateFlow(0)
    private lateinit var model: GenerationViewModel

    /** The turn each set started with, as the subject's phone hears it. */
    private val opened = mutableListOf<Int>()
    private val share = object : PoseShare by PoseShare.None {
        override fun open(setId: String, sceneTurn: Int, situation: Situation?) {
            opened += sceneTurn
        }
    }

    /** A 3 × 4 portrait frame, as the camera gives it, black but for its top-left pixel. */
    private val framed: Bitmap = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLACK)
        setPixel(0, 0, Color.WHITE)
    }

    private val state get() = model.uiState.value

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        camera.frame = framed
        model = GenerationViewModel(
            generator,
            camera,
            TestAppContainer.IdleConsent(),
            persons,
            share = share,
            phoneTurn = turn,
            turning = dispatcher,
        )
        store.put("generation", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    /** Where the marked pixel is in [this]. */
    private fun Bitmap.mark(): Pair<Int, Int> {
        for (y in 0 until height) for (x in 0 until width) if (getPixel(x, y) == Color.WHITE) return x to y
        error("No mark")
    }

    private fun TestScope.shootAt(degrees: Int) {
        model.beginScene()
        turn.value = degrees
        model.takeScene()
        runCurrent()
    }

    /** The upright photo of the 3 × 4 frame, and where its top-left corner went, for each turn. */
    private val expected = mapOf(
        0 to ((3 to 4) to (0 to 0)),
        90 to ((4 to 3) to (3 to 0)),
        180 to ((3 to 4) to (2 to 3)),
        270 to ((4 to 3) to (0 to 2)),
    )

    @Test
    fun eachTurn_withTheRearCamera_turnsTheSceneUpright_andKeepsTheFrameForTheReview() = runTest {
        for ((degrees, shape) in expected) {
            shootAt(degrees)

            val scene = state.scene ?: error("No scene at $degrees")
            assertEquals("$degrees", shape.first, scene.width to scene.height)
            assertEquals("$degrees", shape.second, scene.mark())
            assertSame("$degrees", framed, state.framed)
            assertEquals("$degrees", degrees, state.sceneTurn)
        }
    }

    @Test
    fun eachTurn_withTheFrontCamera_turnsItsMirroredFrameTheSameWay() = runTest {
        model.chooseSituation(Situation.SELFIE)
        for ((degrees, shape) in expected) {
            shootAt(degrees)

            val scene = state.scene ?: error("No scene at $degrees")
            assertEquals(CameraFacing.FRONT, camera.facing.value)
            assertTrue("$degrees", state.selfie)
            assertEquals("$degrees", shape.second, scene.mark())
            assertSame("$degrees", framed, state.framed)
        }
    }

    @Test
    fun aSceneHeldSideways_isCheckedAndSentUpright_andSelectHandsTheTurnOn() = runTest {
        shootAt(90)
        val upright = state.scene

        model.suggestPoses()
        runCurrent()
        val run = generator.runs.single()
        run.plan(listOf("Lean on the wall"))
        run.send(CandidateEvent.Ready("pose-1", Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)))
        run.finish()
        runCurrent()
        model.select("pose-1")

        assertSame(upright, persons.checked.single())
        assertSame(upright, run.scene)
        assertEquals(listOf(90), opened)
        assertEquals(90, model.usePose()?.turn)
    }

    @Test
    fun backFromASet_toGeneratePoses_keepsThePhotoAsFramed_andItsTurn() = runTest {
        shootAt(270)
        model.suggestPoses()
        runCurrent()

        model.stopGenerating()

        assertSame(framed, state.framed)
        assertEquals(270, state.sceneTurn)
    }

    @Test
    fun theTurn_isTheOneWhenTheFrameIsTaken_notAtThePress() = runTest {
        model.beginScene()
        turn.value = 0
        model.takeScene()
        turn.value = 90
        runCurrent()

        assertEquals(90, state.sceneTurn)
        assertEquals(4 to 3, state.scene?.let { it.width to it.height })
    }
}
