// AI-generated with Claude Code, 2026-11-19, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.RotateDrawable
import android.os.Looper
import android.text.Spanned
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer

/*
 * The controls' icons and labels turning with the phone (FR-1.15, #207, Design 2.6.2).
 * Partitions (equivalence): the phone held at 0, 90, 180, and 270 degrees, and -90 and 450 taken as 270 and 90; a
 * quarter turn either way, a half turn, and no turn; a turn from rest and one during a turn; the phone's animations on
 * and off. The button: its icon turned with its background and the button itself as they are, and a new icon keeping
 * the turn; a label that stays and one that turns as a word, keeping its width, and a new label keeping its turn. A
 * text that turns, as the zoom readout does: each text it is given, keeping its width.
 * Boundary: a half turn, the one way both are as short, goes clockwise; the angle after a turn past 180 is read back
 * between -180 and 180.
 * The frame that turns its content whole, as the photo viewer's (FR-1.16): upright, a quarter either way, and a half;
 * laid out sideways at 45 degrees from upright and past it, not just under it (boundary), at every step of a turn, and
 * told once for each change.
 */
@RunWith(AndroidJUnit4::class)
class TurningIconsTest {
    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Pix)

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun play(ms: Long) {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        ShadowChoreographer.setPaused(true)
    }

    private fun button(): TurningButton = TurningButton(context).apply {
        text = "Shoot together"
        setIconResource(R.drawable.ic_people)
    }

    /** The degrees the button's icon is drawn at, from its level. */
    private fun TurningButton.drawnAt(): Float = (icon as RotateDrawable).level * 360f / 10_000f

    @Test
    fun eachQuarter_standsTheIconsTheOtherWay() {
        assertEquals(0f, iconAngleFor(0), 0f)
        assertEquals(-90f, iconAngleFor(90), 0f)
        assertEquals(180f, iconAngleFor(180), 0f)
        assertEquals(90f, iconAngleFor(270), 0f)
        assertEquals(90f, iconAngleFor(-90), 0f)
        assertEquals(-90f, iconAngleFor(450), 0f)
    }

    @Test
    fun aTurn_goesTheShortestWay() {
        assertEquals(-90f, iconAngleTowards(0f, 90), 0f)
        assertEquals(90f, iconAngleTowards(0f, 270), 0f)
        assertEquals(0f, iconAngleTowards(-90f, 0), 0f)
        assertEquals(0f, iconAngleTowards(0f, 0), 0f)
        // Past 180: 180 to the left side up is a quarter clockwise, not three back.
        assertEquals(270f, iconAngleTowards(180f, 90), 0f)
    }

    @Test
    fun aHalfTurn_goesClockwise() {
        assertEquals(180f, iconAngleTowards(0f, 180), 0f)
        assertEquals(90f, iconAngleTowards(-90f, 270), 0f)
        assertEquals(270f, iconAngleTowards(90f, 90), 0f)
    }

    @Test
    fun aTurnDuringATurn_goesOnFromWhereTheIconsAre() {
        assertEquals(0f, iconAngleTowards(-45f, 0), 0f)
        assertEquals(90f, iconAngleTowards(-45f, 270), 0f)
        assertEquals(-180f, iconAngleTowards(-45f, 180), 0f)
    }

    @Test
    fun aTurnedButton_turnsItsIcon_notItsLabelNorItself() {
        val button = button()
        val icon = button.plainIcon

        button.turn = -90f

        assertEquals(270f, button.drawnAt(), 0.05f)
        assertSame(icon, button.plainIcon)
        assertEquals(0f, button.rotation, 0f)
        assertEquals("Shoot together", button.text.toString())
    }

    @Test
    fun aNewIcon_keepsTheTurn() {
        val button = button()
        button.turn = 90f

        button.setIconResource(R.drawable.ic_flash_on)

        assertEquals(R.drawable.ic_flash_on, shadowOf(button.plainIcon).createdFromResId)
        assertEquals(90f, button.drawnAt(), 0.05f)
    }

    @Test
    fun theFirstTurn_isThereAtOnce_andTheNextOneTurnsTheIconsTogether() {
        val button = button()
        val view = View(context)
        val icons = TurningIcons(listOf(button), listOf(view))
        ShadowChoreographer.setPaused(true)

        icons.turnTo(90)
        assertEquals(-90f, view.rotation, 0f)
        assertEquals(270f, button.drawnAt(), 0.05f)

        icons.turnTo(0)
        assertEquals(-90f, icons.angle, 0f)
        checkNotNull(icons.turning).setCurrentFraction(0.5f)
        val halfway = icons.angle
        assertTrue("$halfway", halfway > -90f && halfway < 0f)
        assertEquals(halfway, view.rotation, 0f)
        assertEquals(Math.floorMod(halfway.toInt(), 360).toFloat(), button.drawnAt(), 1f)

        play(Motion.TURN_MS)
        assertEquals(0f, view.rotation, 0f)
        assertEquals(0f, button.drawnAt(), 0.05f)
    }

    @Test
    fun aTurnDuringATurn_startsFromWhereItIs_andEndsUpright() {
        val view = View(context)
        val icons = TurningIcons(emptyList(), listOf(view))
        icons.turnTo(0)
        ShadowChoreographer.setPaused(true)
        icons.turnTo(90)
        checkNotNull(icons.turning).setCurrentFraction(0.5f)
        val there = view.rotation
        assertTrue("$there", there > -90f && there < 0f)

        icons.turnTo(270)

        assertEquals(there, view.rotation, 0f)
        checkNotNull(icons.turning).setCurrentFraction(0.5f)
        assertTrue("${view.rotation}", view.rotation > there && view.rotation < 90f)
        play(Motion.TURN_MS)
        assertEquals(90f, view.rotation, 0f)
    }

    @Test
    fun aTurnPastAHalfTurn_isReadBackBetweenMinus180And180() {
        val view = View(context)
        val icons = TurningIcons(emptyList(), listOf(view))
        icons.turnTo(180)

        icons.turnTo(90)
        play(Motion.TURN_MS * 2)

        assertEquals(-90f, view.rotation, 0f)
        assertEquals(-90f, icons.angle, 0f)
    }

    @Test
    fun withAnimationsOff_eachTurnIsThereAtOnce() {
        val view = View(context)
        val icons = TurningIcons(emptyList(), listOf(view))
        icons.turnTo(0)
        setAnimatorScale(0f)
        ShadowChoreographer.setPaused(true)

        icons.turnTo(90)
        assertEquals(-90f, view.rotation, 0f)
        icons.turnTo(180)
        assertEquals(180f, view.rotation, 0f)
    }

    @Test
    fun stop_leavesTheIconsWhereTheyAre() {
        val view = View(context)
        val icons = TurningIcons(emptyList(), listOf(view))
        icons.turnTo(0)
        ShadowChoreographer.setPaused(true)
        icons.turnTo(270)
        checkNotNull(icons.turning).setCurrentFraction(0.5f)
        val there = view.rotation
        assertTrue("$there", there > 0f && there < 90f)

        icons.stop()
        play(Motion.TURN_MS)

        assertEquals(there, view.rotation, 0f)
    }

    /** A frame of 300 x 500 px, a phone's screen, around a content view that fills it, laid out again after [turn]. */
    private class Screen(context: Context) {
        val frame = TurningFrame(context)
        val content = View(context)
        val told = mutableListOf<Boolean>()

        init {
            frame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
            frame.onSideways = { told += it }
            layOut()
        }

        fun turn(degrees: Float) {
            frame.turn = degrees
            layOut()
        }

        fun layOut() {
            frame.measure(
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            )
            frame.layout(0, 0, 300, 500)
        }

        val placed get() = listOf(content.left, content.top, content.right, content.bottom)

        private companion object {
            const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        }
    }

    @Test
    fun aFrame_turnsItsContentWhole_laidOutSidewaysForAQuarter() {
        val screen = Screen(context)
        assertEquals(listOf(0, 0, 300, 500), screen.placed)

        // A quarter either way: 500 x 300 in the middle, turned, so it covers the screen.
        for (degrees in listOf(-90f, 90f)) {
            screen.turn(degrees)
            assertEquals("$degrees", degrees, screen.content.rotation, 0f)
            assertEquals("$degrees", listOf(-100, 100, 400, 400), screen.placed)
            assertTrue(screen.frame.sideways)
        }

        screen.turn(180f)
        assertEquals(180f, screen.content.rotation, 0f)
        assertEquals(listOf(0, 0, 300, 500), screen.placed)
        assertEquals(listOf(true, false), screen.told)
    }

    @Test
    fun aFrame_isLaidOutSideways_fromHalfwayThroughAQuarterTurn() {
        val screen = Screen(context)
        for ((degrees, sideways) in listOf(44.9f to false, 45f to true, -45f to true, 135f to true, -135.1f to false)) {
            screen.turn(degrees)
            assertEquals("$degrees", sideways, screen.frame.sideways)
            assertEquals("$degrees", if (sideways) 500 else 300, screen.content.width)
        }
        assertEquals(listOf(true, false), screen.told.distinct())
    }

    @Test
    fun aFrame_turnedByTheIcons_isLaidOutSidewaysAtEveryStepPastHalfway() {
        val screen = Screen(context)
        val icons = TurningIcons(listOf(screen.frame), emptyList())
        icons.turnTo(0)
        ShadowChoreographer.setPaused(true)
        icons.turnTo(90)
        val turning = checkNotNull(icons.turning)

        for (step in 0..10) {
            turning.setCurrentFraction(step / 10f)
            val angle = screen.frame.turn
            assertEquals("$angle", abs(angle) >= 45f, screen.frame.sideways)
            assertEquals("$angle", angle, screen.content.rotation, 0f)
        }
        assertEquals(listOf(true), screen.told)
    }

    /** The angles a [TurnedLabelSpan] turns the canvas by before each text it draws. */
    private class Recorder : Canvas(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)) {
        val drawn = mutableListOf<Float>()
        private var turned = 0f

        override fun rotate(degrees: Float) {
            turned = degrees
            super.rotate(degrees)
        }

        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            drawn += turned
            turned = 0f
        }
    }

    @Test
    fun aLabel_staysUnlessItsButtonTurnsIt() {
        val button = button()

        assertFalse(button.turnsLabel)
        assertTrue(button.text !is Spanned)
    }

    @Test
    fun aTurningLabel_keepsItsWidth_andANewLabelKeepsTheTurn() {
        val upright = TurningButton(context).apply { text = "16:9" }
        val turned = TurningButton(context).apply {
            turnsLabel = true
            text = "3:4"
        }

        turned.text = "16:9"

        val span = (turned.text as Spanned).getSpans(0, 4, TurnedLabelSpan::class.java).single()
        assertEquals(
            upright.paint.measureText("16:9").roundToInt(),
            span.getSize(turned.paint, turned.text, 0, 4, null),
        )
        assertEquals("16:9", turned.text.toString())
    }

    @Test
    fun aWord_turnsAsOne_aboutItsMiddle() {
        val recorder = Recorder()

        TurnedLabelSpan { -90f }.draw(recorder, "3:4", 0, 3, 10f, 0, 60, 80, Paint().apply { textSize = 40f })

        assertEquals(listOf(-90f), recorder.drawn)
    }

    @Test
    fun aTurningText_turnsEachTextItIsGiven_andKeepsItsWidth() {
        val label = TurningLabel(context).apply { text = "1.0×" }
        label.turn = 90f

        label.text = "2.0×"

        val text = label.text as Spanned
        val span = text.getSpans(0, text.length, TurnedLabelSpan::class.java).single()
        assertEquals(
            label.paint.measureText("2.0×").roundToInt(),
            span.getSize(label.paint, text, 0, text.length, null),
        )
        val recorder = Recorder()
        span.draw(recorder, text, 0, text.length, 0f, 0, 20, 30, label.paint)
        assertEquals(listOf(90f), recorder.drawn)
    }
}
