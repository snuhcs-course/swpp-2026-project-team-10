// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The guide line's color in the settings (FR-3.10). White is the line of FR-2.2; black stands out on bright scenes. */
enum class GuideColor(val argb: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    BLACK(0xFF141414.toInt()),
    YELLOW(0xFFF2C14E.toInt()),
    MINT(0xFF5EEAD4.toInt()),
    PINK(0xFFFF8FB1.toInt()),
}

/** The guide line's thickness in the settings (FR-3.10), as a factor of the default 3 dp line (Design 2.6.1). */
enum class GuideThickness(val scale: Float) {
    THIN(2f / 3f),
    NORMAL(1f),
    THICK(5f / 3f),
}

/** How this phone draws every guide: its own choice, never sent to the other phone, which draws in its own. */
data class GuideLook(val color: GuideColor = GuideColor.WHITE, val thickness: GuideThickness = GuideThickness.NORMAL)

/**
 * A guide's outline at a thickness from the settings. [ReferenceGuide.outline] is the default line; another thickness
 * is traced again from the mask, off the main thread, as the other phone traces a received mask (Design 2.6.1). The last
 * few results are kept, so a screen that shows the same guide again does not trace it again.
 */
class GuideOutlines(
    private val extractor: OutlineExtractor = OutlineExtractor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private class Traced(val guide: ReferenceGuide, val thickness: GuideThickness, val outline: Bitmap)

    private val traced = ArrayDeque<Traced>()

    suspend fun outline(guide: ReferenceGuide, thickness: GuideThickness): Bitmap {
        if (thickness == GuideThickness.NORMAL) return guide.outline
        synchronized(traced) { traced.firstOrNull { it.guide === guide && it.thickness == thickness } }
            ?.let { return it.outline }
        val outline = withContext(dispatcher) {
            extractor.extract(guide.mask, outlineStrokePx(guide.mask.height) * thickness.scale)
        }
        synchronized(traced) {
            traced.addFirst(Traced(guide, thickness, outline))
            while (traced.size > KEPT) traced.removeLast()
        }
        return outline
    }

    companion object {
        private const val KEPT = 4

        /** The one every screen uses, so a guide traced for Camera is not traced again for Reference confirm. */
        val shared = GuideOutlines()
    }
}
