// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

/**
 * One line of the outline: x, y pairs at pixel centers (pixel (0, 0) is at 0, 0). A closed line does not repeat its
 * first point; an open one ends at the photo's edge.
 */
internal class Contour(val points: FloatArray, val closed: Boolean) {
    val size: Int get() = points.size / 2
}

/**
 * The lines where a row-major [field] crosses [level] (marching squares, Design 2.6.1, step 5), with each crossing
 * placed between the two pixels by linear interpolation, so a blurred field gives a smooth line instead of pixel steps.
 * Only cells inside the photo are traced: a person cut off by the photo's edge stays open there instead of getting a
 * line along the border.
 */
internal fun traceContours(field: FloatArray, width: Int, height: Int, level: Float): List<Contour> {
    require(field.size >= width * height) { "${field.size} values for ${width}x$height" }
    val links = HashMap<Int, IntArray>()
    fun link(a: Int, b: Int) {
        links.getOrPut(a) { intArrayOf(NONE, NONE) }.let { if (it[0] == NONE) it[0] = b else it[1] = b }
        links.getOrPut(b) { intArrayOf(NONE, NONE) }.let { if (it[0] == NONE) it[0] = a else it[1] = a }
    }
    for (y in 0 until height - 1) {
        for (x in 0 until width - 1) {
            val i = y * width + x
            val topLeft = field[i] >= level
            val topRight = field[i + 1] >= level
            val bottomRight = field[i + width + 1] >= level
            val bottomLeft = field[i + width] >= level
            val case = (if (topLeft) 8 else 0) or (if (topRight) 4 else 0) or
                (if (bottomRight) 2 else 0) or (if (bottomLeft) 1 else 0)
            if (case == 0 || case == 15) continue
            // Each crossing is identified by the cell edge it lies on.
            val top = horizontalEdge(x, y, width)
            val bottom = horizontalEdge(x, y + 1, width)
            val left = verticalEdge(x, y, width)
            val right = verticalEdge(x + 1, y, width)
            when (case) {
                1, 14 -> link(left, bottom)

                2, 13 -> link(bottom, right)

                3, 12 -> link(left, right)

                4, 11 -> link(top, right)

                6, 9 -> link(top, bottom)

                7, 8 -> link(left, top)

                5, 10 -> {
                    // Two opposite corners inside: the cell's center decides whether they are joined.
                    val center = (field[i] + field[i + 1] + field[i + width] + field[i + width + 1]) / 4f >= level
                    if ((case == 5) == center) {
                        link(left, top)
                        link(bottom, right)
                    } else {
                        link(left, bottom)
                        link(top, right)
                    }
                }
            }
        }
    }
    fun point(edge: Int, out: FloatArray, at: Int) {
        val cell = edge shr 1
        val x0 = cell % width
        val y0 = cell / width
        val x1 = if (edge and 1 == 0) x0 + 1 else x0
        val y1 = if (edge and 1 == 0) y0 else y0 + 1
        val v0 = field[y0 * width + x0]
        val v1 = field[y1 * width + x1]
        val t = if (v1 == v0) 0.5f else ((level - v0) / (v1 - v0)).coerceIn(0f, 1f)
        out[at] = x0 + t * (x1 - x0)
        out[at + 1] = y0 + t * (y1 - y0)
    }

    val visited = HashSet<Int>(links.size * 2)
    val contours = ArrayList<Contour>()
    // Open lines first, from an end at the photo's edge; whatever is left is a closed loop.
    val starts = links.keys.filter { links.getValue(it)[1] == NONE } + links.keys
    val chain = ArrayList<Int>()
    for (start in starts) {
        if (!visited.add(start)) continue
        chain.clear()
        chain.add(start)
        var previous = NONE
        var current = start
        while (true) {
            val next = links.getValue(current).firstOrNull { it != NONE && it != previous && it !in visited } ?: break
            visited.add(next)
            chain.add(next)
            previous = current
            current = next
        }
        val closed = chain.size > 2 && links.getValue(start)[1] != NONE && start in links.getValue(current)
        val points = FloatArray(chain.size * 2)
        chain.forEachIndexed { k, edge -> point(edge, points, k * 2) }
        contours.add(Contour(points, closed))
    }
    return contours
}

/**
 * Chaikin corner cutting: every pass replaces each segment by points at a quarter and three quarters of it, which
 * rounds the corners left between pixels. An open line keeps its two ends where they are.
 */
internal fun smoothContour(contour: Contour, passes: Int = 2): Contour {
    var points = contour.points
    repeat(passes) {
        val n = points.size / 2
        if (n < 3) return Contour(points, contour.closed)
        val segments = if (contour.closed) n else n - 1
        val out = FloatArray((segments * 2 + if (contour.closed) 0 else 2) * 2)
        var k = 0
        if (!contour.closed) {
            out[k++] = points[0]
            out[k++] = points[1]
        }
        for (s in 0 until segments) {
            val a = s * 2
            val b = (s + 1) % n * 2
            out[k++] = 0.75f * points[a] + 0.25f * points[b]
            out[k++] = 0.75f * points[a + 1] + 0.25f * points[b + 1]
            out[k++] = 0.25f * points[a] + 0.75f * points[b]
            out[k++] = 0.25f * points[a + 1] + 0.75f * points[b + 1]
        }
        if (!contour.closed) {
            out[k++] = points[points.size - 2]
            out[k] = points[points.size - 1]
        }
        points = out
    }
    return Contour(points, contour.closed)
}

private const val NONE = -1

/** The edge from pixel (x, y) to (x + 1, y). */
private fun horizontalEdge(x: Int, y: Int, width: Int) = (y * width + x) shl 1

/** The edge from pixel (x, y) to (x, y + 1). */
private fun verticalEdge(x: Int, y: Int, width: Int) = ((y * width + x) shl 1) or 1
