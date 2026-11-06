// AI-generated with ChatGPT Codex and Claude Code, 2026-11-05, reviewed by Dongje Park and Sungmin Jo
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
 * How far the top bar's 48 dp touch areas reach below what it draws (NFR-16): the bar draws in its top 44 dp, and the
 * image starts there, under the last 4 dp of its buttons, which are transparent.
 */
internal const val TOP_BAR_OVERHANG_DP = 4f

/**
 * Between the zoom row and the image's bottom edge or the panels: the row's controls are drawn 8 dp above it, and the
 * row's 48 dp touch height (NFR-16) takes 2 dp of that.
 */
internal const val ZOOM_ROW_MARGIN_DP = 6f

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
 * Where one ratio's image is on a camera screen, in the screen's coordinates (R&S 6.2): its [area], which the preview
 * or the video takes; its [frame]; and what of the area [shows], the frame, or for 1:1 the whole 4:3 area with the
 * outside dimmed. A ratio change moves the screen from one place to the next ([lerp], Design 2.6.2).
 */
internal data class FramePlace(val area: PreviewArea, val frame: ViewfinderFrame, val shows: ViewfinderFrame) {
    /** Whether the whole area shows, so that nothing of it is clipped. */
    val showsArea: Boolean get() = shows.top == area.top && shows.bottom == area.bottom && shows.left == 0f
}

/** The place of [aspect] on a screen [width] wide, from previewArea's [topBar] down (R&S 6.2). */
internal fun framePlace(
    width: Float,
    screenHeight: Float,
    topBar: Float,
    controls: Float,
    aspect: AspectRatio,
): FramePlace {
    val area = previewArea(width, screenHeight, topBar, controls, aspect.previewAspect)
    val inArea = fitViewfinderFrame(width, area.bottom - area.top, aspect.frameAspect)
    val frame = inArea.copy(top = inArea.top + area.top)
    val shows = if (aspect.previewAspect == aspect.frameAspect) {
        frame
    } else {
        ViewfinderFrame(0f, area.top, width, area.bottom - area.top)
    }
    return FramePlace(area, frame, shows)
}

/** [fraction] of the way from [from] (0) to [to] (1). */
internal fun lerp(from: FramePlace, to: FramePlace, fraction: Float) = FramePlace(
    PreviewArea(
        lerp(from.area.top, to.area.top, fraction),
        lerp(from.area.bottom, to.area.bottom, fraction),
        lerp(from.area.underControls, to.area.underControls, fraction),
    ),
    lerp(from.frame, to.frame, fraction),
    lerp(from.shows, to.shows, fraction),
)

private fun lerp(from: ViewfinderFrame, to: ViewfinderFrame, fraction: Float) = ViewfinderFrame(
    lerp(from.left, to.left, fraction),
    lerp(from.top, to.top, fraction),
    lerp(from.width, to.width, fraction),
    lerp(from.height, to.height, fraction),
)

private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

/** The sensor's image, upright: every ratio is cut from the middle of its 4:3 (Design 2.6.2). */
private const val SENSOR_ASPECT = 3f / 4f

/**
 * How wide the camera's whole image is on screen around [frame]: 3:4 and 1:1 take the sensor's full width, and 9:16
 * its full height, so a 9:16 frame as wide as a 3:4 one shows the scene a third larger.
 */
internal fun sensorWidth(frame: ViewfinderFrame): Float = maxOf(frame.width, frame.height * SENSOR_ASPECT)

/**
 * Where the scene shown at [from] is with the frame at [to]: [scale] times as large, with the point that was at
 * [from]'s frame center ([fromX], [fromY]) now at [to]'s ([x], [y]). Every ratio shares the sensor's center and
 * differs in how much of it shows, so the scene sits where [to]'s own image will show it, and at a place between two
 * ratios ([lerp]) it is on its way there.
 */
internal data class SceneMove(val scale: Float, val fromX: Float, val fromY: Float, val x: Float, val y: Float)

internal fun sceneMove(from: FramePlace, to: FramePlace): SceneMove {
    val fromWidth = sensorWidth(from.frame)
    val scale = if (fromWidth > 0f) sensorWidth(to.frame) / fromWidth else 1f
    return SceneMove(scale, from.frame.centerX, from.frame.centerY, to.frame.centerX, to.frame.centerY)
}

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
