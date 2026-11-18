// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Partitions for the reference thumbnail's size and place (FR-3.9).
 * Size, small: a tall, a wide, and a square photo. Enlarged: limited by half the preview's width, limited by the
 * area's height, and an area not laid out yet (stays small). Invalid aspect: zero, negative, non-finite (a square).
 * Place: each of the four corners, small and enlarged. Nearest corner: a drop in each quarter of the area, and one
 * exactly in its middle (a boundary: right and bottom win).
 * Hidden (#237). Place: at the side of each corner, with only the peek inside the preview; hidden while enlarged (the
 * small one's strip), and a thumbnail narrower than the peek (just under half of it stays, a boundary at twice the
 * peek). A drop: inside the preview (its corner, shown); its center at the preview's side and just past
 * it, left and right (a boundary: hidden only past it); flung toward the nearer side at the fling speed and just under
 * it, toward the far side, and more down than sideways (hidden only for the first). How see-through: inside, sliding
 * out, and hidden.
 */
class ReferenceThumbnailTest {
    // A 1080 px wide phone: 72 dp is 189 px, and the area runs from the 31 px margins to above the guide's controls.
    private val area = ReferenceArea(left = 31, top = 31, right = 1049, bottom = 1667)

    @Test
    fun placeReference_smallTallWideAndSquarePhotos_fitTheirLongSideToTheSmallSize() {
        assertEquals(31 to 31, place(aspect = 2f / 3f).let { it.x to it.y })
        assertEquals(126 to 189, place(aspect = 2f / 3f).let { it.width to it.height })
        assertEquals(189 to 142, place(aspect = 4f / 3f).let { it.width to it.height })
        assertEquals(189 to 189, place(aspect = 1f).let { it.width to it.height })
    }

    @Test
    fun placeReference_enlargedPhotoThatFits_isHalfThePreviewWide() {
        val result = place(aspect = 1280f / 1900f, expanded = true)

        assertEquals(540 to 802, result.width to result.height)
    }

    @Test
    fun placeReference_enlargedPhotoTallerThanTheArea_isNarrowedToTheAreasHeight() {
        val result = place(aspect = 0.25f, expanded = true)

        assertEquals(ReferencePlacement(31, 31, 409, 1636), result)
    }

    @Test
    fun placeReference_areaNotLaidOut_staysSmall() {
        val notLaidOut = place(aspect = 2f / 3f, expanded = true, previewWidth = 0)
        assertEquals(126 to 189, notLaidOut.width to notLaidOut.height)
        val collapsed = ReferenceArea(31, 31, 1049, 31)
        assertEquals(126, placeReference(2f / 3f, true, ReferenceCorner.TOP_LEFT, 189, 1080, collapsed).width)
    }

    @Test
    fun placeReference_invalidAspect_showsASquare() {
        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(189 to 189, place(aspect).let { it.width to it.height })
            assertEquals(540 to 540, place(aspect, expanded = true).let { it.width to it.height })
        }
    }

    @Test
    fun placeReference_eachCorner_touchesThoseEdgesOfTheArea() {
        val small = mapOf(
            ReferenceCorner.TOP_LEFT to (31 to 31),
            ReferenceCorner.TOP_RIGHT to (923 to 31),
            ReferenceCorner.BOTTOM_LEFT to (31 to 1478),
            ReferenceCorner.BOTTOM_RIGHT to (923 to 1478),
        )
        for ((corner, expected) in small) {
            assertEquals(expected, place(aspect = 2f / 3f, corner = corner).let { it.x to it.y })
        }
        // Enlarged in a bottom corner, it grows up and in from that corner.
        val enlarged = place(aspect = 1280f / 1900f, expanded = true, corner = ReferenceCorner.BOTTOM_RIGHT)
        assertEquals(509 to 865, enlarged.x to enlarged.y)
    }

    @Test
    fun nearestCorner_dropInEachQuarter_isThatQuartersCorner() {
        assertEquals(ReferenceCorner.TOP_LEFT, nearestCorner(200f, 300f, area))
        assertEquals(ReferenceCorner.TOP_RIGHT, nearestCorner(900f, 300f, area))
        assertEquals(ReferenceCorner.BOTTOM_LEFT, nearestCorner(200f, 1500f, area))
        assertEquals(ReferenceCorner.BOTTOM_RIGHT, nearestCorner(900f, 1500f, area))
    }

    @Test
    fun nearestCorner_dropExactlyInTheMiddle_goesToTheBottomRight() {
        assertEquals(ReferenceCorner.BOTTOM_RIGHT, nearestCorner(540f, 849f, area))
    }

    @Test
    fun placeReference_hidden_leavesOnlyThePeekInsideThePreviewAtItsCornersSide() {
        // 16 dp is 42 px: of a 126 px wide thumbnail, 84 px are out at the left, and all but 42 px at the right.
        val hidden = mapOf(
            ReferenceCorner.TOP_LEFT to (-84 to 31),
            ReferenceCorner.TOP_RIGHT to (1038 to 31),
            ReferenceCorner.BOTTOM_LEFT to (-84 to 1478),
            ReferenceCorner.BOTTOM_RIGHT to (1038 to 1478),
        )
        for ((corner, expected) in hidden) {
            val result = place(aspect = 2f / 3f, corner = corner, hidden = true)
            assertEquals(corner.name, expected, result.x to result.y)
            assertEquals(126 to 189, result.width to result.height)
        }
    }

    @Test
    fun placeReference_hiddenWhileEnlarged_isTheSmallThumbnailsStrip() {
        val small =
            place(aspect = 1280f / 1900f, expanded = false, corner = ReferenceCorner.BOTTOM_RIGHT, hidden = true)
        val enlarged =
            place(aspect = 1280f / 1900f, expanded = true, corner = ReferenceCorner.BOTTOM_RIGHT, hidden = true)

        // A hidden thumbnail is never the enlarged one: a tall strip would cover the side of the preview.
        assertEquals(small, enlarged)
        assertEquals(189, enlarged.height)
    }

    @Test
    fun referencePeek_isThePeek_orJustUnderHalfOfAThumbnailTooNarrowForIt() {
        assertEquals(42, referencePeek(width = 126, peek = 42))
        // 85 px is the narrowest that keeps the whole peek with more than half of it out: a boundary.
        assertEquals(42, referencePeek(width = 85, peek = 42))
        assertEquals(41, referencePeek(width = 84, peek = 42))
        assertEquals(11, referencePeek(width = 24, peek = 42))
        assertEquals(0, referencePeek(width = 1, peek = 42))
    }

    @Test
    fun placeReference_hiddenVeryTallPhoto_leavesJustUnderHalfOfItsNarrowThumbnail() {
        // 1:8, so the small thumbnail is 24 px wide, narrower than the 42 px peek.
        val left = place(aspect = 1f / 8f, corner = ReferenceCorner.TOP_LEFT, hidden = true)
        val right = place(aspect = 1f / 8f, corner = ReferenceCorner.TOP_RIGHT, hidden = true)

        assertEquals(24, left.width)
        assertEquals(-13, left.x)
        assertEquals(1069, right.x)
    }

    @Test
    fun referenceAlpha_narrowThumbnail_fadesOverTheWayItCanGo() {
        assertEquals(1f, referenceAlpha(x = 0f, width = 24, previewWidth = 1080, peek = 42), 0.001f)
        assertEquals(0.775f, referenceAlpha(x = -6.5f, width = 24, previewWidth = 1080, peek = 42), 0.001f)
        assertEquals(
            REFERENCE_HIDDEN_ALPHA,
            referenceAlpha(x = -13f, width = 24, previewWidth = 1080, peek = 42),
            0.001f,
        )
    }

    @Test
    fun dropReference_insideThePreview_goesToItsCornerAndStaysShown() {
        assertEquals(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false), drop(centerX = 200f, centerY = 300f))
        assertEquals(ReferenceDrop(ReferenceCorner.BOTTOM_RIGHT, hidden = false), drop(centerX = 900f, centerY = 1500f))
    }

    @Test
    fun dropReference_centerPastThePreviewsSide_isHiddenAtThatSide() {
        // More than half of it is out. Exactly at the side, half is still in: a boundary.
        assertEquals(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false), drop(centerX = 0f, centerY = 300f))
        assertEquals(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true), drop(centerX = -1f, centerY = 300f))
        assertEquals(
            ReferenceDrop(ReferenceCorner.BOTTOM_RIGHT, hidden = false),
            drop(centerX = 1080f, centerY = 1500f),
        )
        assertEquals(ReferenceDrop(ReferenceCorner.BOTTOM_RIGHT, hidden = true), drop(centerX = 1081f, centerY = 1500f))
    }

    @Test
    fun dropReference_flungTowardTheNearerSide_isHiddenFromTheFlingSpeedOn() {
        assertEquals(true, drop(centerX = 200f, centerY = 300f, velocityX = -2_100f).hidden)
        assertEquals(false, drop(centerX = 200f, centerY = 300f, velocityX = -2_099f).hidden)
        assertEquals(true, drop(centerX = 900f, centerY = 300f, velocityX = 2_100f).hidden)
        assertEquals(false, drop(centerX = 900f, centerY = 300f, velocityX = 2_099f).hidden)
    }

    @Test
    fun dropReference_flungTowardTheFarSideOrMoreDownThanSideways_staysShown() {
        // Toward the other side, it is on its way to that corner, not out.
        assertEquals(false, drop(centerX = 200f, centerY = 300f, velocityX = 5_000f).hidden)
        assertEquals(false, drop(centerX = 900f, centerY = 300f, velocityX = -5_000f).hidden)
        // A fast move down along the side is a move to the corner below.
        assertEquals(false, drop(centerX = 200f, centerY = 300f, velocityX = -3_000f, velocityY = 3_001f).hidden)
    }

    @Test
    fun referenceAlpha_fadesAsTheThumbnailSlidesOut_downToTheHiddenStrips() {
        // A 126 px wide thumbnail with a 42 px peek: all of it in, half of what may go out, and only the peek in.
        assertEquals(1f, referenceAlpha(x = 31f, width = 126, previewWidth = 1080, peek = 42), 0.001f)
        assertEquals(1f, referenceAlpha(x = 0f, width = 126, previewWidth = 1080, peek = 42), 0.001f)
        val half = 1f - (1f - REFERENCE_HIDDEN_ALPHA) / 2
        assertEquals(half, referenceAlpha(x = -42f, width = 126, previewWidth = 1080, peek = 42), 0.001f)
        assertEquals(
            REFERENCE_HIDDEN_ALPHA,
            referenceAlpha(x = -84f, width = 126, previewWidth = 1080, peek = 42),
            0.001f,
        )
        // The same at the right side, and never fainter than the hidden strip.
        assertEquals(half, referenceAlpha(x = 996f, width = 126, previewWidth = 1080, peek = 42), 0.001f)
        assertEquals(
            REFERENCE_HIDDEN_ALPHA,
            referenceAlpha(x = 1038f, width = 126, previewWidth = 1080, peek = 42),
            0.001f,
        )
        assertEquals(
            REFERENCE_HIDDEN_ALPHA,
            referenceAlpha(x = 2_000f, width = 126, previewWidth = 1080, peek = 42),
            0.001f,
        )
    }

    private fun place(
        aspect: Float,
        expanded: Boolean = false,
        corner: ReferenceCorner = ReferenceCorner.TOP_LEFT,
        previewWidth: Int = 1080,
        hidden: Boolean = false,
    ) = placeReference(
        aspect,
        expanded,
        corner,
        smallLongSide = 189,
        previewWidth = previewWidth,
        area = area,
        hidden = hidden,
        peek = 42,
    )

    /** A drop on a 1080 px wide preview, where a fling is 800 dp/s: 2100 px/s. */
    private fun drop(centerX: Float, centerY: Float, velocityX: Float = 0f, velocityY: Float = 0f) =
        dropReference(centerX, centerY, velocityX, velocityY, area, previewWidth = 1080, flingSpeed = 2_100f)
}
