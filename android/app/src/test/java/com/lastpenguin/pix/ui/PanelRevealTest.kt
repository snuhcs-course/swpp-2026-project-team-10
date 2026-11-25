// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer

/*
 * A panel under the camera's top bar, the camera menu or the ratios, through `reveal` (#272; Testing Documentation
 * 2.1.6), on a plain View. Partitions: opening with animations on (faded and 8 dp up at its start, then in place),
 * shown again while it opens (the opening plays on), closing (at once, also during the opening), and opening with the
 * phone's animations off (at once).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class PanelRevealTest {
    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Pix)
    private val panel = View(context).apply { visibility = View.GONE }
    private val density = context.resources.displayMetrics.density

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun play(ms: Long) {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun assertAtRest(message: String) {
        assertEquals(message, 1f, panel.alpha, 0.001f)
        assertEquals(message, 0f, panel.translationY, 0.001f)
    }

    @Test
    fun opening_startsFadedAnd8DpUp_andComesIntoPlace() {
        ShadowChoreographer.setPaused(true)

        panel.reveal(true)
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(0f, panel.alpha, 0.001f)
        assertEquals(-8 * density, panel.translationY, 0.5f)

        // The render shows it again while it opens: the opening plays on.
        panel.reveal(true)
        assertEquals(-8 * density, panel.translationY, 0.5f)

        play(Motion.PANEL_MS + 100)
        assertAtRest("opened")
    }

    @Test
    fun closing_isAtOnce_alsoDuringTheOpening() {
        ShadowChoreographer.setPaused(true)
        panel.reveal(true)

        panel.reveal(false)

        assertEquals(View.GONE, panel.visibility)
        assertAtRest("closed during the opening")
        play(Motion.PANEL_MS + 100)
        assertAtRest("closed, after the opening's time")
    }

    @Test
    fun withAnimationsOff_itOpensAtOnce() {
        setAnimatorScale(0f)

        panel.reveal(true)

        assertEquals(View.VISIBLE, panel.visibility)
        assertAtRest("opened at once")
    }
}
