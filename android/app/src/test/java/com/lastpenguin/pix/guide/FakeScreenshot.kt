// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

/**
 * A phone's screenshot drawn in ARGB pixels for the screenshot cut (FR-2.10): the app's flat [background] with lines of
 * text and icons inside its 16 px margins, and photos of a detailed scene. Half a phone's size by default (540 × 1170,
 * 19.5:9). Real screenshots are never committed: they show other people's photos.
 */
internal class FakeScreenshot(
    val width: Int = 540,
    val height: Int = 1170,
    private val background: Int = WHITE,
) {
    val pixels = IntArray(width * height) { background }

    private val ink = if (background == WHITE) rgb(20, 20, 20) else rgb(240, 240, 240)

    /** An avatar, a name, and an icon at the right, [TEXT_ROWS] tall from [top], as a post's header or its buttons. */
    fun text(top: Int) {
        fill(16, top, 56, top + TEXT_ROWS, ink)
        fill(70, top + 6, 220, top + TEXT_ROWS - 6, ink)
        fill(width - 56, top, width - 16, top + TEXT_ROWS, ink)
    }

    /** A photo from [top], [rows] tall, across [left] to [right]: the whole width, as a feed shows it. */
    fun photo(top: Int, rows: Int, left: Int = 0, right: Int = width) {
        for (y in top until top + rows) {
            for (x in left until right) pixels[y * width + x] = scene(x, y)
        }
    }

    /**
     * The screen of a feed post: the status bar, the app bar, the post's header, its photo [rows] tall from [top],
     * its buttons, its caption, and the navigation bar. The photo's bottom row is `top + rows`, exclusive.
     */
    fun post(rows: Int, top: Int = POST_TOP) {
        text(10)
        text(56)
        text(top - 48)
        photo(top, rows)
        text(top + rows + 16)
        text(top + rows + 60)
        if (height - 40 > top + rows + 60 + TEXT_ROWS) text(height - 40)
    }

    /**
     * Google Images in Chrome, dark: the page's header right above the photo, the photo [rows] tall from [top] centered
     * on a grey panel with [margin] on each side, Google Lens' icon over the panel, the title under it, and Chrome's
     * scrollbar down the right edge. Make it with [VIEWER_PAGE] as the background.
     */
    fun viewer(rows: Int, margin: Int, top: Int = VIEWER_TOP) {
        text(10)
        text(56)
        text(top - 50)
        fill(0, top, width, top + rows, VIEWER_PANEL)
        photo(top, rows, margin, width - margin)
        fill(margin / 3, top + rows - 50, margin * 2 / 3, top + rows - 20, rgb(240, 240, 240))
        text(top + rows + 30)
        scrollbar()
    }

    /** A browser's scrollbar, 8 px wide (1.5 %, as Chrome's) inside the right edge's 2 %, from top to bottom. */
    fun scrollbar() = fill(width - 9, 0, width - 1, height, rgb(81, 82, 86))

    /** A blurred background from [top], [rows] tall, across the width: it changes by at most 1 from pixel to pixel. */
    fun blur(top: Int, rows: Int) {
        for (y in top until top + rows) {
            for (x in 0 until width) pixels[y * width + x] = rgb(60 + y / 24, 70 + x / 24, 90 + (x + y) / 48)
        }
    }

    fun fill(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top until bottom) {
            for (x in left until right) pixels[y * width + x] = color
        }
    }

    fun find(): PixelRect? = findScreenshotPhoto(pixels, width, height)

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()

        /** Instagram's dark background. */
        val DARK = rgb(13, 16, 21)

        /** Google Images' dark page, and the panel behind a photo, more than the tolerance apart. */
        val VIEWER_PAGE = rgb(29, 29, 31)
        val VIEWER_PANEL = rgb(43, 46, 53)

        const val POST_TOP = 170
        const val VIEWER_TOP = 200
        const val TEXT_ROWS = 24

        fun rgb(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

        /**
         * A scene's pixel, as detailed as a photo's: it changes from pixel to pixel, and every channel is 30–229, so
         * never within the tolerance of white or of a dark background.
         */
        fun scene(x: Int, y: Int): Int {
            val hash = (x * 73_856_093) xor (y * 19_349_663)
            return rgb(30 + (hash ushr 4) % 200, 40 + (hash ushr 12) % 180, 50 + (hash ushr 20) % 160)
        }
    }
}
