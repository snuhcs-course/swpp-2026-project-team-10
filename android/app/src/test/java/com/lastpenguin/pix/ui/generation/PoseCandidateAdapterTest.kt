// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.generation

import android.animation.ValueAnimator
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * A slot of Generating poses as its candidate or failure arrives (Iter3-A13), on the real item layout, with the
 * display's frames paused to see a fade's start. The adapter binds a slot directly, as RecyclerView does after a
 * layout: with the display's frames paused, the screen's own list would not take its change until a frame let it
 * through, which would play the fade to its end at once (Testing 2.1.8).
 * What arrives (equivalence partitioning): a candidate (its image fades in), a failure ("Couldn't create" fades in),
 * and a slot bound anew, as when the screen is made again (shown at once). What counts as arriving: a slot that had
 * nothing and now has an event, not one that had its event already. With the phone's animations off, at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PoseCandidateAdapterTest {

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private val waiting = PoseSlot(PlannedPose("pose-1", "Wave"), event = null)
    private val ready = waiting.copy(event = CandidateEvent.Ready("pose-1", bitmap()))
    private val failed = waiting.copy(event = CandidateEvent.Failed("pose-1", GenerationError.SERVER))

    /**
     * The adapter with [slot] as its only slot, and that slot's holder, bound as the list shows it. The adapter's first
     * list is taken at once; a later one is compared off the main thread.
     */
    private fun bound(slot: PoseSlot): Pair<PoseCandidateAdapter, PoseCandidateAdapter.Holder> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Pix)
        val parent = FrameLayout(activity)
        val adapter = PoseCandidateAdapter()
        adapter.submitList(listOf(slot))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        return adapter to holder
    }

    private fun play() {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FADE_END_MS))
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    @Test
    fun onlyASlotThatHadNothing_arrives() {
        assertEquals(PoseCandidateAdapter.ARRIVED, PoseCandidateAdapter.Diff.getChangePayload(waiting, ready))
        assertEquals(PoseCandidateAdapter.ARRIVED, PoseCandidateAdapter.Diff.getChangePayload(waiting, failed))
        assertNull(PoseCandidateAdapter.Diff.getChangePayload(ready, ready.copy(selected = true)))
        assertNull(PoseCandidateAdapter.Diff.getChangePayload(waiting, waiting.copy(selected = true)))
    }

    @Test
    fun aCandidateThatArrives_fadesInWhereItsSpinnerWas() {
        val (adapter, holder) = bound(ready)
        ShadowChoreographer.setPaused(true)

        adapter.onBindViewHolder(holder, 0, mutableListOf(PoseCandidateAdapter.ARRIVED))

        assertEquals(0f, holder.binding.poseImage.alpha, 0f)
        play()
        assertEquals(1f, holder.binding.poseImage.alpha, 0f)
    }

    @Test
    fun aFailureThatArrives_fadesInItsMessage() {
        val (adapter, holder) = bound(failed)
        ShadowChoreographer.setPaused(true)

        adapter.onBindViewHolder(holder, 0, mutableListOf(PoseCandidateAdapter.ARRIVED))

        assertEquals("Couldn't create", holder.binding.poseStatus.text.toString())
        assertEquals(0f, holder.binding.poseStatus.alpha, 0f)
        play()
        assertEquals(1f, holder.binding.poseStatus.alpha, 0f)
    }

    @Test
    fun aSlotBoundAnew_showsWhatItHasAtOnce_alsoOverAFadeUnderWay() {
        val (adapter, holder) = bound(ready)
        ShadowChoreographer.setPaused(true)
        adapter.onBindViewHolder(holder, 0, mutableListOf(PoseCandidateAdapter.ARRIVED))

        adapter.onBindViewHolder(holder, 0)

        assertEquals(1f, holder.binding.poseImage.alpha, 0f)
    }

    @Test
    fun withAnimationsOff_anArrivalIsThereAtOnce() {
        val (adapter, holder) = bound(ready)
        setAnimatorScale(0f)
        ShadowChoreographer.setPaused(true)

        adapter.onBindViewHolder(holder, 0, mutableListOf(PoseCandidateAdapter.ARRIVED))

        assertEquals(1f, holder.binding.poseImage.alpha, 0f)
    }

    private companion object {
        /** The fade, 200 ms, and a little more. */
        const val FADE_END_MS = 300L
    }
}
