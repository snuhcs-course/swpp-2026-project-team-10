// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideOutlines
import com.lastpenguin.pix.guide.GuideOverlayView
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/*
 * The guide in the settings' look (FR-3.10) wherever it is drawn: on Camera, on Subject view, and on both phones'
 * Reference confirm. The color applies at once; another thickness shows once the mask is traced again, until then the
 * default line. Each phone uses its own look, also for the guide it received.
 */

/**
 * Keeps [this] overlay drawing the guide of [guides] in [look], with the inside guide-edit mode tints (#271) made off the
 * main thread for each guide; runs until cancelled.
 */
internal suspend fun GuideOverlayView.followLook(
    look: Flow<GuideLook>,
    guides: Flow<ReferenceGuide?>,
    outlines: GuideOutlines = GuideOutlines.shared,
) {
    val each = guides.distinctUntilChanged { old, new -> old === new }
    coroutineScope {
        launch { each.collectLatest { guide -> fill(guide, guide?.let { outlines.inside(it) }) } }
        combine(each, look, ::Pair).collectLatest { (guide, look) ->
            lineColor = look.color.argb
            restyle(guide, guide?.let { outlines.outline(it, look.thickness) })
        }
    }
}

/**
 * Keeps [this] image showing the outline of the guide of [guides] in [look]; nothing while there is none. A dark line
 * gets a light backing (`GuideColor.dark`), since the image sits on Reference confirm's dark card.
 */
internal suspend fun ImageView.showOutline(
    look: Flow<GuideLook>,
    guides: Flow<ReferenceGuide?>,
    outlines: GuideOutlines = GuideOutlines.shared,
) {
    combine(guides.distinctUntilChanged { old, new -> old === new }, look, ::Pair).collectLatest { (guide, look) ->
        val color = look.color.argb
        if (color == Color.WHITE) clearColorFilter() else setColorFilter(color, PorterDuff.Mode.SRC_IN)
        background = if (look.color.dark) ContextCompat.getDrawable(context, R.drawable.bg_outline_light) else null
        if (guide == null) return@collectLatest
        setImageBitmap(guide.outline)
        val outline = outlines.outline(guide, look.thickness)
        if (outline !== guide.outline) setImageBitmap(outline)
    }
}
