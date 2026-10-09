// AI-generated with ChatGPT Codex, 2026-10-04, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

/** Copied YUV_420_888 data; row padding and interleaved chroma planes are both supported. */
internal data class YuvPlane(val bytes: ByteArray, val rowStride: Int, val pixelStride: Int) {
    fun sample(x: Int, y: Int): Int = bytes[y * rowStride + x * pixelStride].toInt() and 0xff
}

/** Converts only the common ViewPort crop. Rotation is applied to the resulting bitmap separately. */
internal fun yuvToArgb(planes: List<YuvPlane>, left: Int, top: Int, width: Int, height: Int): IntArray {
    require(planes.size == 3 && left >= 0 && top >= 0 && width > 0 && height > 0)
    val (yPlane, uPlane, vPlane) = planes
    return IntArray(width * height) { index ->
        val x = left + index % width
        val y = top + index / width
        // Camera YUV uses video-range BT.601. Integer conversion avoids allocating per pixel.
        val luminance = (yPlane.sample(x, y) - 16).coerceAtLeast(0) * 298
        val u = uPlane.sample(x / 2, y / 2) - 128
        val v = vPlane.sample(x / 2, y / 2) - 128
        val r = ((luminance + 409 * v + 128) shr 8).coerceIn(0, 255)
        val g = ((luminance - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
        val b = ((luminance + 516 * u + 128) shr 8).coerceIn(0, 255)
        (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}
