// AI-generated with Claude Code, 2026-11-17, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Quarter turns of a photo and of the guide made from it (#206, Design 2.6.2).
 * Partitions (equivalence): a turn of 0, 90, 180, and 270 degrees clockwise, and one of -90 and 450 taken as 270 and
 * 90; a turn that is not a quarter, refused. Bitmaps: the size and where a marked corner goes, an ALPHA_8 mask kept
 * ALPHA_8, and a whole turn returning the same bitmap. Boxes: each quarter by its corners. Guides: the mask, outline,
 * thumbnail, aspect, and box turned together, the id kept, and no box or thumbnail staying none.
 * Metamorphic: a box turned by a turn and back is itself, also four quarters in a row; a pose's guide cut from a
 * scene photo held at any turn goes on the camera with the person's box where it was in the framed photo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideTurnTest {
    /** A 4 × 3 photo, black but for its top-left pixel. */
    private fun marked(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap =
        Bitmap.createBitmap(4, 3, config).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(0, 0, Color.WHITE)
        }

    /** Where the marked pixel is now. */
    private fun Bitmap.mark(): Pair<Int, Int> {
        for (y in 0 until height) for (x in 0 until width) if (Color.alpha(getPixel(x, y)) > 0) return x to y
        error("No mark")
    }

    private val box = NormalizedRect(0.1f, 0.2f, 0.4f, 0.9f)

    private fun guide(anchor: NormalizedRect? = box, reference: Bitmap? = marked()) = ReferenceGuide(
        id = "pose",
        mask = marked(Bitmap.Config.ALPHA_8),
        outline = marked(),
        aspect = 4f / 3f,
        source = GuideSource.GENERATED,
        anchor = anchor,
        reference = reference,
    )

    private fun assertBox(expected: NormalizedRect, actual: NormalizedRect?) {
        val turned = actual ?: error("No box")
        for ((name, pair) in listOf(
            "left" to (expected.left to turned.left),
            "top" to (expected.top to turned.top),
            "right" to (expected.right to turned.right),
            "bottom" to (expected.bottom to turned.bottom),
        )) {
            assertEquals(name, pair.first, pair.second, 1e-6f)
        }
    }

    @Test
    fun aPhoto_turnedEachQuarter_movesItsCornerClockwise_andTurnsItsShape() {
        val expected = mapOf(90 to (2 to 0), 180 to (3 to 2), 270 to (0 to 3))
        for ((turn, corner) in expected) {
            val turned = marked().turnedClockwise(turn)
            val wide = turn == 180
            assertEquals("$turn", if (wide) 4 else 3, turned.width)
            assertEquals("$turn", if (wide) 3 else 4, turned.height)
            assertEquals("$turn", corner, turned.mark())
        }
    }

    @Test
    fun aWholeTurn_returnsTheSamePhoto() {
        val photo = marked()

        assertSame(photo, photo.turnedClockwise(0))
        assertSame(photo, photo.turnedClockwise(360))
    }

    @Test
    fun aNegativeTurn_orOneOverAWholeTurn_isItsQuarter() {
        assertEquals(marked().turnedClockwise(270).mark(), marked().turnedClockwise(-90).mark())
        assertEquals(marked().turnedClockwise(90).mark(), marked().turnedClockwise(450).mark())
    }

    @Test
    fun aMask_staysAlpha8() {
        assertEquals(Bitmap.Config.ALPHA_8, marked(Bitmap.Config.ALPHA_8).turnedClockwise(90).config)
    }

    @Test
    fun aTurnThatIsNotAQuarter_isRefused() {
        assertThrows(IllegalArgumentException::class.java) { marked().turnedClockwise(45) }
    }

    @Test
    fun aBox_turnedEachQuarter_keepsItsCornersOnThePhoto() {
        assertBox(NormalizedRect(0.1f, 0.1f, 0.8f, 0.4f), box.turnedClockwise(90))
        assertBox(NormalizedRect(0.6f, 0.1f, 0.9f, 0.8f), box.turnedClockwise(180))
        assertBox(NormalizedRect(0.2f, 0.6f, 0.9f, 0.9f), box.turnedClockwise(270))
        assertBox(box, box.turnedClockwise(0))
    }

    @Test
    fun aBox_turnedAndBack_orFourQuarters_isItself() {
        for (turn in listOf(0, 90, 180, 270)) {
            assertBox(box, box.turnedClockwise(turn).turnedClockwise(360 - turn))
        }
        assertBox(box, box.turnedClockwise(90).turnedClockwise(90).turnedClockwise(90).turnedClockwise(90))
    }

    @Test
    fun aGuide_turnsItsMaskOutlineThumbnailAndBox_together_andKeepsItsId() {
        val turned = guide().turnedClockwise(90)

        assertEquals("pose", turned.id)
        assertEquals(3 to 4, turned.mask.width to turned.mask.height)
        assertEquals(Bitmap.Config.ALPHA_8, turned.mask.config)
        assertEquals(2 to 0, turned.outline.mark())
        assertEquals(2 to 0, turned.reference?.mark())
        assertEquals(3f / 4f, turned.aspect, 1e-6f)
        assertBox(box.turnedClockwise(90), turned.anchor)
    }

    @Test
    fun aGuideWithoutABoxOrThumbnail_staysWithoutThem() {
        val turned = guide(anchor = null, reference = null).turnedClockwise(270)

        assertEquals(null, turned.anchor)
        assertEquals(null, turned.reference)
    }

    @Test
    fun aPosesGuide_fromASceneHeldAtAnyTurn_goesOnTheCameraWhereThePersonStoodInTheFrame() {
        // The person's box and shape as the camera framed them; the scene goes to the server turned upright as held, so
        // the candidate's guide is turned the same way.
        for (turn in listOf(0, 90, 180, 270)) {
            val inCandidate = guide().turnedClockwise(turn)

            val onCamera = inCandidate.intoFrameHeldAt(turn)

            assertBox(box, onCamera.anchor)
            assertEquals("$turn", 4 to 3, onCamera.mask.width to onCamera.mask.height)
            assertEquals("$turn", 0 to 0, onCamera.outline.mark())
        }
    }
}
