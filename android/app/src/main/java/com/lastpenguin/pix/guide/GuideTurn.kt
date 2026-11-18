// AI-generated with Claude Code, 2026-11-17, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Matrix

/*
 * Quarter turns of a photo and of the guide made from it (#206). A scene photo taken with the phone held sideways is
 * turned upright as held, so the server makes landscape candidates (FR-4.3). The camera's frame stays portrait, so the
 * guide cut from such a candidate is turned back into the frame (Design 2.6.2): it then lies on the preview held
 * sideways, on both phones (NFR-8).
 */

/** This image turned [degrees] clockwise, a multiple of 90; itself for a whole turn. The config is kept. */
fun Bitmap.turnedClockwise(degrees: Int): Bitmap {
    val turn = quarter(degrees)
    if (turn == 0) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(turn.toFloat()) }, false)
}

/** This box in an image turned [degrees] clockwise, as a box of the turned image. */
fun NormalizedRect.turnedClockwise(degrees: Int): NormalizedRect = when (quarter(degrees)) {
    90 -> NormalizedRect(1f - bottom, left, 1f - top, right)
    180 -> NormalizedRect(1f - right, 1f - bottom, 1f - left, 1f - top)
    270 -> NormalizedRect(top, 1f - right, bottom, 1f - left)
    else -> this
}

/**
 * This guide turned [degrees] clockwise: its mask, outline, box in the photo, and corner thumbnail, so it starts where
 * the person stands in the turned photo (FR-3.1). Its id stays, so its tip and its *Reference confirm* still name it.
 */
fun ReferenceGuide.turnedClockwise(degrees: Int): ReferenceGuide {
    val turn = quarter(degrees)
    if (turn == 0) return this
    val turnedMask = mask.turnedClockwise(turn)
    return copy(
        mask = turnedMask,
        outline = outline.turnedClockwise(turn),
        aspect = turnedMask.width.toFloat() / turnedMask.height,
        anchor = anchor?.turnedClockwise(turn),
        reference = reference?.turnedClockwise(turn),
    )
}

/**
 * The guide cut from a candidate of a scene photo held at [turn] (`PhoneTurns`), turned back into the camera's portrait
 * frame, where it lines up with the preview held the same way.
 */
fun ReferenceGuide.intoFrameHeldAt(turn: Int): ReferenceGuide = turnedClockwise(FULL_TURN - quarter(turn))

private fun quarter(degrees: Int): Int {
    require(degrees % QUARTER_TURN == 0) { "Not a quarter turn: $degrees" }
    return Math.floorMod(degrees, FULL_TURN)
}

private const val QUARTER_TURN = 90
private const val FULL_TURN = 360
