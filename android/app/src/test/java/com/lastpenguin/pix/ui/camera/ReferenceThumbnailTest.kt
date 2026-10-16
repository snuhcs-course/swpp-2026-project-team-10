// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Partitions for the reference thumbnail's size and place (FR-3.9).
 * Size, small: a tall, a wide, and a square photo. Enlarged: limited by half the preview's width, limited by the
 * area's height, and an area not laid out yet (stays small). Invalid aspect: zero, negative, non-finite (a square).
 * Place: each of the four corners, small and enlarged. Nearest corner: a drop in each quarter of the area, and one
 * exactly in its middle (a boundary: right and bottom win).
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

    private fun place(
        aspect: Float,
        expanded: Boolean = false,
        corner: ReferenceCorner = ReferenceCorner.TOP_LEFT,
        previewWidth: Int = 1080,
    ) = placeReference(aspect, expanded, corner, smallLongSide = 189, previewWidth = previewWidth, area = area)
}
