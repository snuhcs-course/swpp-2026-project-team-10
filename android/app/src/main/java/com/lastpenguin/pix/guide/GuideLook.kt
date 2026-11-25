// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The guide line's color in the settings (FR-3.10). White is the line of FR-2.2; black stands out on bright scenes, and
 * is [dark]: on the app's dark surfaces, such as Reference confirm's card, it needs a light backing to be seen.
 */
enum class GuideColor(val argb: Int, val dark: Boolean = false) {
    WHITE(0xFFFFFFFF.toInt()),
    BLACK(0xFF141414.toInt(), dark = true),
    YELLOW(0xFFF2C14E.toInt()),
    MINT(0xFF5EEAD4.toInt()),
    PINK(0xFFFF8FB1.toInt()),
}

/** The guide line's thickness in the settings (FR-3.10), as a factor of the default 2 dp line (Design 2.6.1). */
enum class GuideThickness(val scale: Float) {
    THIN(0.75f),
    NORMAL(1f),
    THICK(1.5f),
}

/** How this phone draws every guide: its own choice, never sent to the other phone, which draws in its own. */
data class GuideLook(val color: GuideColor = GuideColor.WHITE, val thickness: GuideThickness = GuideThickness.NORMAL)

/**
 * A guide's outline at a thickness from the settings. [ReferenceGuide.outline] is the default line; another thickness
 * is traced again from the mask, off the main thread, as the other phone traces a received mask (Design 2.6.1). The last
 * few results are kept, so a screen that shows the same guide again does not trace it again. The inside that
 * guide-edit mode tints is made here too, the same way ([inside]).
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

    private val insides = ArrayDeque<Pair<ReferenceGuide, Bitmap>>()

    /**
     * The inside of [guide]'s mask, which guide-edit mode tints (#271): a pass over the whole mask, so it is made off the
     * main thread, never while the guide is drawn, and kept like the outlines. Null when the mask is gone.
     */
    suspend fun inside(guide: ReferenceGuide): Bitmap? {
        synchronized(insides) { insides.firstOrNull { it.first === guide } }?.let { return it.second }
        val inside = withContext(dispatcher) {
            guide.mask.takeUnless { it.isRecycled }?.let { guideInside(it.maskPixels()).toMaskBitmap() }
        } ?: return null
        synchronized(insides) {
            insides.addFirst(guide to inside)
            while (insides.size > KEPT) insides.removeLast()
        }
        return inside
    }

    companion object {
        private const val KEPT = 4

        /** The one every screen uses, so a guide traced for Camera is not traced again for Reference confirm. */
        val shared = GuideOutlines()
    }
}
