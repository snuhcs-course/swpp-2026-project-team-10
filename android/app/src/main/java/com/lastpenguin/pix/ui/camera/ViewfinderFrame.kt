// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.frameAspect

/** The same fit-centered image area as CameraX and PreviewView, in the chosen aspect ratio (AD-10). */
internal data class ViewfinderFrame(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** [frameAspect] is the frame's width / height: 3:4 by default, 9:16, or 1:1. */
internal fun fitViewfinderFrame(viewWidth: Float, viewHeight: Float, frameAspect: Float = 3f / 4f): ViewfinderFrame {
    if (!viewWidth.isFinite() || !viewHeight.isFinite() || viewWidth <= 0f || viewHeight <= 0f ||
        !frameAspect.isFinite() || frameAspect <= 0f
    ) {
        return ViewfinderFrame(0f, 0f, 0f, 0f)
    }
    val scale = minOf(viewWidth / frameAspect, viewHeight)
    val width = frameAspect * scale
    val height = scale
    return ViewfinderFrame((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
}

/**
 * The part of a screen that the camera's image takes (R&S 6.2), from [top] to [bottom], and how much of it lies under
 * the controls below ([underControls]).
 */
internal data class PreviewArea(val top: Float, val bottom: Float, val underControls: Float)

/**
 * The image takes the screen's [width] at [areaAspect] (width / height) right below the top bar ([topBar] tall). One
 * taller than the space above the controls ([controls] tall) reaches down under them; one taller than the rest of
 * the screen ([screenHeight]) stops at its bottom and narrows to fit.
 */
internal fun previewArea(
    width: Float,
    screenHeight: Float,
    topBar: Float,
    controls: Float,
    areaAspect: Float,
): PreviewArea {
    val height = if (width.isFinite() && width > 0f && areaAspect.isFinite() && areaAspect > 0f) {
        width / areaAspect
    } else {
        screenHeight - topBar - controls
    }
    val bottom = minOf(topBar + height, screenHeight)
    return PreviewArea(topBar, bottom, maxOf(0f, bottom - (screenHeight - controls)))
}

/** The area the preview takes for this ratio: 1:1 is shown inside the 4:3 area, the rest of it dimmed (R&S 6.2). */
internal val AspectRatio.previewAspect: Float
    get() = if (this == AspectRatio.R1_1) AspectRatio.R4_3.frameAspect else frameAspect

/**
 * Puts [row], the zoom row, [margin] above whichever comes first: the image's visible bottom ([imageBottom]) or the
 * first panel shown in [panels], the column of panels above the controls. Both are laid out with their bottoms on
 * the controls' top, in the coordinates of their parent; only the row's translation moves (R&S 6.2).
 */
internal fun placeZoomRow(row: View, panels: ViewGroup, imageBottom: Float, margin: Float) {
    val panel = panels.children.firstOrNull { it.isVisible }
    val panelsTop = if (panel == null) panels.bottom - panels.paddingBottom else panels.top + panel.top
    row.translationY = minOf(imageBottom, panelsTop.toFloat()) - margin - row.bottom
}
