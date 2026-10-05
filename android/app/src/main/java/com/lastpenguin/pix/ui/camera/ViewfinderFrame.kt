package com.lastpenguin.pix.ui.camera

/** The same portrait 3:4, fit-centered image area used by CameraX and PreviewView. */
internal data class ViewfinderFrame(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

internal fun fitViewfinderFrame(viewWidth: Float, viewHeight: Float): ViewfinderFrame {
    if (!viewWidth.isFinite() || !viewHeight.isFinite() || viewWidth <= 0f || viewHeight <= 0f) {
        return ViewfinderFrame(0f, 0f, 0f, 0f)
    }
    val scale = minOf(viewWidth / 3f, viewHeight / 4f)
    val width = 3f * scale
    val height = 4f * scale
    return ViewfinderFrame((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
}
