// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.PoseKey
import com.lastpenguin.pix.ui.generation.PoseSaveViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * A pose's guide on the camera as the scene photo was held (#206, Design 2.6.2): Reference confirm shows it on the
 * candidate as it is, and *Use this guide* turns it back into the camera's portrait frame.
 * Partitions (equivalence): a pose from a scene held sideways (90) and upright (0); a picked photo after a sideways
 * pose, and a saved guide, which go on as they are. A pose's ♥ (FR-4.14) from a scene held sideways: saved as it goes
 * on the camera, so the library opens it there, and *Use this pose* with it turns it once, not twice. History (#297):
 * a photo guide kept once used, not before; a pose not kept again at Use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PoseGuideTurnTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val maker = TestAppContainer.ScriptedGuideMaker()
    private val guides = InMemoryGuideRepository()
    private val saved = SavedGuideStore.InMemory()
    private lateinit var model: ReferenceViewModel

    /** The person's box in a 4 × 3 landscape candidate. */
    private val box = NormalizedRect(0.5f, 0.1f, 0.75f, 0.9f)

    private fun landscapeGuide(id: String = "pose") = ReferenceGuide(
        id = id,
        mask = Bitmap.createBitmap(40, 30, Bitmap.Config.ALPHA_8),
        outline = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888),
        aspect = 4f / 3f,
        source = GuideSource.GENERATED,
        anchor = box,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = ReferenceViewModel(maker, guides, saved)
        store.put("reference", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun aPoseFromASceneHeldSideways_isShownAsCut_andGoesOnTheCameraTurnedBack() = runTest {
        val cut = landscapeGuide()
        model.onCandidatePicked(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), turn = 90)
        runCurrent()
        maker.finish(Result.success(cut))
        runCurrent()
        assertEquals(ReferenceUiState.Ready(cut), model.uiState.value)

        model.onUseGuide()

        val onCamera = guides.guide.value ?: error("No guide on the camera")
        assertEquals("pose", onCamera.id)
        assertEquals(30 to 40, onCamera.mask.width to onCamera.mask.height)
        assertEquals(3f / 4f, onCamera.aspect, 1e-6f)
        // Turned a quarter counterclockwise: the right half of the landscape photo is the top half of the frame.
        assertEquals(NormalizedRect(0.1f, 0.25f, 0.9f, 0.5f), onCamera.anchor)
    }

    @Test
    fun aPoseFromASceneHeldUpright_goesOnAsCut() = runTest {
        val cut = landscapeGuide()
        model.onCandidatePicked(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), turn = 0)
        runCurrent()
        maker.finish(Result.success(cut))
        runCurrent()

        model.onUseGuide()

        assertSame(cut, guides.guide.value)
    }

    @Test
    fun aGuideFromAPhoto_isKeptInTheLibraryAsHistoryOnceUsed_andAPoseIsNotKeptAgainHere() = runTest {
        val photo = landscapeGuide("photo").copy(source = GuideSource.GALLERY)
        model.onPhotoPicked(Uri.parse("content://media/picked/1"))
        runCurrent()
        maker.finish(Result.success(photo))
        runCurrent()
        // Shown on Reference confirm only: nothing kept yet.
        assertTrue(saved.saved.value.isEmpty())

        model.onUseGuide()
        runCurrent()

        // History (#297): no tap, not a favorite.
        assertEquals(listOf("photo" to false), saved.saved.value.map { it.id to it.favorite })

        // A pose is kept by the library's own keeping (PoseLibrary), not at Use.
        model.onCandidatePicked(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888))
        runCurrent()
        maker.finish(Result.success(landscapeGuide("pose")))
        runCurrent()
        model.onUseGuide()
        runCurrent()
        assertEquals(listOf("photo"), saved.saved.value.map { it.id })
    }

    @Test
    fun aPickedPhoto_afterASidewaysPose_goesOnAsCut() = runTest {
        model.onCandidatePicked(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), turn = 90)
        runCurrent()
        maker.finish(Result.success(landscapeGuide("pose")))
        runCurrent()
        val photo = landscapeGuide("photo")

        model.onPhotoPicked(Uri.parse("content://media/picked/1"))
        runCurrent()
        maker.finish(Result.success(photo))
        runCurrent()
        model.onUseGuide()

        assertSame(photo, guides.guide.value)
    }

    @Test
    fun aPoseHeartedFromASceneHeldSideways_isSavedAsItGoesOnTheCamera_andTheLibraryOpensItThere() = runTest {
        val hearts = PoseSaveViewModel(PoseLibrary(maker, saved, CoroutineScope(dispatcher)), saved)
        store.put("saves", hearts)
        val key = PoseKey("set-1", "pose-1")
        val cut = landscapeGuide()
        hearts.toggle(key, Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), turn = 90)
        runCurrent()
        maker.finish(Result.success(cut))
        runCurrent()

        // *Use this pose* with the heart's guide: shown as cut, then turned back once.
        val image = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        model.onCandidatePicked(image, kept = { hearts.keptGuide(key, image, turn = 90) }, turn = 90)
        runCurrent()
        assertEquals(ReferenceUiState.Ready(cut), model.uiState.value)
        model.onUseGuide()
        val used = guides.guide.value ?: error("No guide on the camera")
        assertEquals(30 to 40, used.mask.width to used.mask.height)
        assertEquals(NormalizedRect(0.1f, 0.25f, 0.9f, 0.5f), used.anchor)

        // Later, from the library: where it lay on the camera.
        guides.setGuide(null)
        model.onUseSaved("pose")
        runCurrent()
        val opened = guides.guide.value ?: error("No guide from the library")
        assertEquals("pose", opened.id)
        assertEquals(30 to 40, opened.mask.width to opened.mask.height)
        assertEquals(used.anchor, opened.anchor)
    }

    @Test
    fun aSavedGuide_afterASidewaysPose_goesOnAsSaved() = runTest {
        model.onCandidatePicked(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), turn = 90)
        runCurrent()
        val kept = landscapeGuide("kept")
        val id = saved.save(kept, GuideState()).getOrThrow().id

        model.onUseSaved(id)
        runCurrent()

        assertEquals(40 to 30, guides.guide.value?.let { it.mask.width to it.mask.height })
    }
}
