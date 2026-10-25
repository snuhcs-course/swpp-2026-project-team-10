// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.fakeGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the pose tip on the Camera screen (FR-4.10), state-based. The guide on the camera: none, one with a
 * tip, one without, a newer one with its own tip, removed. The tip: preparing, arriving while the banner is open or
 * closed, failing while it is open or closed. The user: a touch anywhere (with the banner open or closed, or no tip),
 * the tip button (with a tip, or after a failure).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraTipViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val guides = InMemoryGuideRepository()
    private val tips = MutableStateFlow<TipState?>(null)
    private val ready = TipState.Ready(PoseTip("Hold the phone at your chest and keep it level."))

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model() = CameraViewModel(FakeCameraController(), guides, tips = tips).also { store.put("camera", it) }

    private val CameraViewModel.tip get() = uiState.value.tip

    @Test
    fun aGuideWithATipOpensTheBanner_preparingUntilTheTipArrivesInPlace() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        tips.value = TipState.Preparing
        runCurrent()
        assertEquals(TipBanner("g1", TipState.Preparing, open = true), model.tip)

        tips.value = ready
        runCurrent()

        assertEquals(TipBanner("g1", ready, open = true), model.tip)
    }

    @Test
    fun noGuide_orAGuideWithoutATip_hasNoBanner() = runTest(dispatcher) {
        val model = model()
        tips.value = ready
        runCurrent()
        assertNull(model.tip)

        guides.setGuide(fakeGuide("gallery"))
        tips.value = null
        runCurrent()
        assertNull(model.tip)
    }

    @Test
    fun aTouchAnywhereClosesTheBanner_andTheTipButtonOpensItAgain() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        tips.value = ready
        runCurrent()

        model.onAnyTouch()
        assertFalse(model.tip!!.open)
        model.onAnyTouch()
        assertFalse(model.tip!!.open)

        model.onTipButton()
        assertTrue(model.tip!!.open)
    }

    @Test
    fun aTipThatArrivesWhileClosedIsUnseenUntilTheButtonOpensIt() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        tips.value = TipState.Preparing
        runCurrent()
        model.onAnyTouch()

        tips.value = ready
        runCurrent()
        assertEquals(TipBanner("g1", ready, open = false, unseen = true), model.tip)

        model.onTipButton()
        assertEquals(TipBanner("g1", ready, open = true, unseen = false), model.tip)
    }

    @Test
    fun aFailureIsSaidForANoticeWhileOpen_thenTheBannerClosesForGood() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        tips.value = TipState.Preparing
        runCurrent()

        tips.value = TipState.Failed
        runCurrent()
        assertEquals(TipBanner("g1", TipState.Failed, open = true), model.tip)
        advanceTimeBy(CameraViewModel.NOTICE_MS + 1)

        assertFalse(model.tip!!.open)
        model.onTipButton()
        assertFalse(model.tip!!.open)
    }

    @Test
    fun aFailureWhileClosedStaysClosed() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        tips.value = TipState.Preparing
        runCurrent()
        model.onAnyTouch()

        tips.value = TipState.Failed
        runCurrent()

        assertEquals(TipBanner("g1", TipState.Failed, open = false), model.tip)
    }

    @Test
    fun aNewerGuideOpensItsOwnTip_andARemovedGuideTakesItsTipAway() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        tips.value = ready
        runCurrent()
        model.onAnyTouch()

        guides.setGuide(fakeGuide("g2"))
        tips.value = TipState.Preparing
        runCurrent()
        assertEquals(TipBanner("g2", TipState.Preparing, open = true), model.tip)

        model.onRemoveGuide()
        tips.value = null
        runCurrent()
        assertNull(model.tip)
    }

    @Test
    fun aTouchWithoutATipChangesNothing() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        val before = model.uiState.value

        model.onAnyTouch()
        model.onTipButton()

        assertEquals(before, model.uiState.value)
    }
}
