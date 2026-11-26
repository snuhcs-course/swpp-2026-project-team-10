// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * The shutter's ring while the timer counts down (FR-1.17). countdownFill, by boundary value analysis on the seconds
 * left: the first second (empty), a middle one, the last (two thirds of a 3 s timer), none (full), and a number above
 * the timer's length (counted from itself). The drawable: the ring filled clockwise from the top (drawn on the right
 * half at a half fill, not on the left), the fill held to 0..1, the square dimmer under a finger, and all of it dim
 * while disabled. CountdownRing, state-based on the test's clock: a new second starts the ring again from its place
 * and fills it to the end in the seconds left, and no further; the same second again changes nothing; the phone's
 * animation speed does not change its pace; with animations off it stands at each second's place; stopping empties it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CountdownShutterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        setAnimatorScale(1f)
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    // ---- countdownFill ----------------------------------------------------------------------------------------------

    @Test
    fun countdownFill_isEmptyAtTheFirstSecond_andFillsAThirdEachSecondOfThree() {
        assertEquals(0f, countdownFill(left = 3, total = 3), 0.001f)
        assertEquals(1f / 3, countdownFill(left = 2, total = 3), 0.001f)
        assertEquals(2f / 3, countdownFill(left = 1, total = 3), 0.001f)
        assertEquals(1f, countdownFill(left = 0, total = 3), 0.001f)
    }

    @Test
    fun countdownFill_ofTenSeconds_isATenthAtNine() {
        assertEquals(0f, countdownFill(left = 10, total = 10), 0.001f)
        assertEquals(0.1f, countdownFill(left = 9, total = 10), 0.001f)
        assertEquals(0.9f, countdownFill(left = 1, total = 10), 0.001f)
    }

    @Test
    fun countdownFill_ofMoreSecondsThanTheTimer_countsFromThem() {
        assertEquals(0f, countdownFill(left = 5, total = 3), 0.001f)
        assertEquals(0f, countdownFill(left = 3, total = 0), 0.001f)
    }

    // ---- The drawable -----------------------------------------------------------------------------------------------

    private fun drawn(drawable: CountdownShutterDrawable, size: Int = SIZE_PX): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
        }

    @Test
    fun halfFilled_theRingIsTheAccentOnTheRight_andTheTrackOnTheLeft() {
        val drawable = CountdownShutterDrawable(context).apply { fillFrom(0.5f) }
        val bitmap = drawn(drawable)
        val stroke = (4 * context.resources.displayMetrics.density / 2).toInt()

        val right = bitmap.getPixel(SIZE_PX - 1 - stroke, SIZE_PX / 2)
        val left = bitmap.getPixel(stroke, SIZE_PX / 2)
        val accent = context.getColor(R.color.pix_accent)
        assertEquals(Color.red(accent).toFloat(), Color.red(right).toFloat(), 8f)
        assertEquals(Color.green(accent).toFloat(), Color.green(right).toFloat(), 8f)
        assertEquals(Color.blue(accent).toFloat(), Color.blue(right).toFloat(), 8f)
        assertTrue("the track is faint", Color.alpha(left) < 0x80)
        // The stop square in the middle, in the shutter's white.
        assertEquals(context.getColor(R.color.camera_shutter), bitmap.getPixel(SIZE_PX / 2, SIZE_PX / 2))
    }

    @Test
    fun empty_theRingIsTrackAllRound() {
        val bitmap = drawn(CountdownShutterDrawable(context))
        val stroke = (4 * context.resources.displayMetrics.density / 2).toInt()

        assertTrue(Color.alpha(bitmap.getPixel(SIZE_PX - 1 - stroke, SIZE_PX / 2)) < 0x80)
    }

    @Test
    fun theFill_isHeldTo0And1() {
        val drawable = CountdownShutterDrawable(context)
        drawable.fillFrom(1.4f)
        assertEquals(1f, drawable.fill)
        drawable.fillFrom(-0.2f)
        assertEquals(0f, drawable.fill)
    }

    @Test
    fun underAFinger_theSquareIsDimmer_andDisabled_allOfItIsDim() {
        val drawable = CountdownShutterDrawable(context)
        assertTrue(drawable.isStateful)

        drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
        assertEquals(context.getColor(R.color.camera_shutter_pressed), drawable.squareColor)
        assertFalse(drawable.dimmed)

        drawable.state = intArrayOf()
        assertEquals(context.getColor(R.color.camera_shutter), drawable.squareColor)
        assertTrue(drawable.dimmed)
        val square = drawn(drawable).getPixel(SIZE_PX / 2, SIZE_PX / 2)
        assertTrue("dimmed as a disabled shutter", Color.alpha(square) < 0x80)
    }

    // ---- CountdownRing ----------------------------------------------------------------------------------------------

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun eachSecond_startsTheRingFromItsPlace_andFillsToTheEndInTheSecondsLeft() {
        val ring = CountdownRing(context)

        ring.show(left = 3, total = 3)
        assertEquals(0f, ring.drawable.fill, 0.001f)
        assertTrue(ring.drawable.filling)
        pass(1_500)
        assertEquals(0.5f, ring.drawable.fill, 0.001f)

        // A second late: the ring starts again from where 2 of 3 stands.
        ring.show(left = 2, total = 3)
        assertEquals(1f / 3, ring.drawable.fill, 0.001f)
        pass(1_000)
        assertEquals(2f / 3, ring.drawable.fill, 0.001f)
        pass(1_500)
        assertEquals("full at the end, and no further", 1f, ring.drawable.fill, 0.001f)
        assertFalse(ring.drawable.filling)
    }

    @Test
    fun theSameSecondAgain_changesNothing() {
        val ring = CountdownRing(context)
        ring.show(left = 3, total = 3)
        pass(1_500)

        ring.show(left = 3, total = 3)

        assertEquals(0.5f, ring.drawable.fill, 0.001f)
    }

    @Test
    fun aFasterOrSlowerAnimationSpeed_keepsTheTimersSeconds() {
        setAnimatorScale(2f)
        val ring = CountdownRing(context)

        ring.show(left = 3, total = 3)
        pass(1_500)

        assertEquals(0.5f, ring.drawable.fill, 0.001f)
    }

    @Test
    fun withAnimationsOff_theRingStandsAtEachSecondsPlace() {
        setAnimatorScale(0f)
        val ring = CountdownRing(context)

        ring.show(left = 2, total = 3)
        pass(1_000)

        assertEquals(1f / 3, ring.drawable.fill, 0.001f)
        assertFalse(ring.drawable.filling)
    }

    @Test
    fun stopping_emptiesTheRing_andTheNextCountdownStartsAfresh() {
        val ring = CountdownRing(context)
        ring.show(left = 1, total = 3)

        ring.stop()

        assertEquals(0f, ring.drawable.fill, 0.001f)
        assertFalse(ring.drawable.filling)
        ring.show(left = 1, total = 3)
        assertTrue("the same second shows again after a stop", ring.drawable.filling)
    }

    private companion object {
        const val SIZE_PX = 200
    }
}
