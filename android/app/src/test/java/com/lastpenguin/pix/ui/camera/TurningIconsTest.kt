// AI-generated with Claude Code, 2026-11-18, reviewed by Joonhyung Han
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
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
 * the turn; a label that stays, one that turns as a word, and one that turns letter by letter, each keeping its width,
 * and a new label keeping its turn. Letters: drawn from the start at 0 and 90 degrees the other way (a quarter
 * counterclockwise on the screen), and from the end at 90 and 180.
 * Boundary: a half turn, the one way both are as short, goes clockwise; the angle after a turn past 180 is read back
 * between -180 and 180; the letters' order changes past 45 degrees clockwise and at 135 counterclockwise.
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

    /** What a [TurnedLabelSpan] draws: each drawText's first index, its x, and the angle the canvas was turned by. */
    private class Recorder : Canvas(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)) {
        val drawn = mutableListOf<Triple<Int, Float, Float>>()
        private var turned = 0f

        override fun rotate(degrees: Float) {
            turned = degrees
            super.rotate(degrees)
        }

        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            drawn += Triple(start, x, turned)
            turned = 0f
        }
    }

    private fun drawn(word: String, letters: Boolean, degrees: Float): List<Triple<Int, Float, Float>> {
        val paint = Paint().apply { textSize = 40f }
        val recorder = Recorder()
        TurnedLabelSpan(letters) { degrees }.draw(recorder, word, 0, word.length, 10f, 0, 60, 80, paint)
        return recorder.drawn
    }

    @Test
    fun aLabel_staysUnlessItsButtonTurnsIt() {
        val button = button()

        assertEquals(LabelTurn.NONE, button.labelTurn)
        assertTrue(button.text !is Spanned)
    }

    @Test
    fun aTurningLabel_keepsItsWidth_andANewLabelKeepsTheTurn() {
        val upright = TurningButton(context).apply { text = "16:9" }
        val turned = TurningButton(context).apply {
            labelTurn = LabelTurn.WHOLE
            text = "3:4"
        }

        turned.text = "16:9"
        val paint = turned.paint

        val span = (turned.text as Spanned).getSpans(0, 4, TurnedLabelSpan::class.java).single()
        assertEquals(upright.paint.measureText("16:9").roundToInt(), span.getSize(paint, turned.text, 0, 4, null))
        assertEquals("16:9", turned.text.toString())
    }

    @Test
    fun aWord_turnsAsOne_aboutItsMiddle() {
        val drawn = drawn("3:4", letters = false, degrees = -90f)

        assertEquals(listOf(0), drawn.map { it.first })
        assertEquals(-90f, drawn.single().third, 0.01f)
    }

    @Test
    fun letters_eachTurn_andReadFromTheStart_atTheEdgeOfEachOrder() {
        val width = Paint().apply { textSize = 40f }.measureText("공")
        for ((degrees, order) in listOf(
            0f to listOf(0, 1),
            -90f to listOf(0, 1),
            90f to listOf(1, 0),
            180f to listOf(1, 0),
        )) {
            val drawn = drawn("공유", letters = true, degrees = degrees)

            assertEquals("$degrees", order, drawn.map { it.first })
            assertEquals("$degrees", listOf(10f, 10f + width), drawn.map { it.second })
            for (letter in drawn) assertEquals("$degrees", degrees, letter.third, 0.01f)
        }
    }

    @Test
    fun theLettersOrder_changesPast45Clockwise_andAt135Counterclockwise() {
        assertEquals(false, readsBackward(45f))
        assertEquals(true, readsBackward(45.5f))
        assertEquals(false, readsBackward(-134.5f))
        assertEquals(true, readsBackward(-135f))
        assertEquals(true, readsBackward(-270f))
        assertEquals(false, readsBackward(270f))
        assertEquals(false, readsBackward(-360f))
    }
}
