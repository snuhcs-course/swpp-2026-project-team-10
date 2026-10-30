// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.camera

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The analysis frame copy behind grabFrame (FR-4.1): no frame, a frame, a frame with an empty crop, a snapshot that
 * is independent of later frames, clear; the snapshot's upright bitmap at rotation 0 and 90 with the crop applied, and
 * a front camera's, which is also flipped left to right once upright (FR-1.14).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFrameCacheTest {
    @Test
    fun noFrameUntilAnUpdate_thenASnapshotOfTheCrop() {
        val cache = CameraFrameCache()
        assertFalse(cache.hasFrame())
        assertNull(cache.snapshot())

        cache.update(FakeImageProxy.flat(4, 4, luma = 235, crop = Rect(1, 1, 3, 3), rotation = 90))

        assertTrue(cache.hasFrame())
        val snapshot = checkNotNull(cache.snapshot())
        assertEquals(
            listOf(1, 1, 2, 2, 90),
            listOf(snapshot.left, snapshot.top, snapshot.width, snapshot.height, snapshot.rotation),
        )
        assertEquals(3, snapshot.planes.size)
    }

    @Test
    fun anEmptyCrop_countsAsNoFrame_andClearForgetsTheFrame() {
        val cache = CameraFrameCache()
        cache.update(FakeImageProxy.flat(4, 4, luma = 16, crop = Rect(0, 0, 0, 0)))
        assertFalse(cache.hasFrame())

        cache.update(FakeImageProxy.flat(4, 4, luma = 16))
        assertTrue(cache.hasFrame())
        cache.clear()
        assertFalse(cache.hasFrame())
        assertNull(cache.snapshot())
    }

    @Test
    fun aSnapshotKeepsItsBytesWhenTheNextFrameOverwritesTheCache() {
        val cache = CameraFrameCache()
        cache.update(FakeImageProxy.flat(2, 2, luma = 235))
        val white = checkNotNull(cache.snapshot())

        // The cache reuses its arrays for a frame of the same size; the snapshot must not change with it.
        cache.update(FakeImageProxy.flat(2, 2, luma = 16))

        assertEquals(235, white.planes[0].sample(0, 0))
        assertEquals(16, checkNotNull(cache.snapshot()).planes[0].sample(0, 0))
    }

    @Test
    fun uprightBitmap_appliesTheCropAndTheRotation() {
        val cache = CameraFrameCache()
        // 4×2 frame, the right half cropped: a 2×2 image that a 90° rotation keeps 2×2, a 4×2 crop becomes 2×4.
        cache.update(FakeImageProxy.flat(4, 2, luma = 235, crop = Rect(2, 0, 4, 2)))
        val flat = checkNotNull(cache.snapshot()).toUprightBitmap()
        assertEquals(2 to 2, flat.width to flat.height)
        assertEquals(0xffffffff.toInt(), flat.getPixel(0, 0))

        cache.update(FakeImageProxy.flat(4, 2, luma = 16, rotation = 90))
        val rotated = checkNotNull(cache.snapshot()).toUprightBitmap()
        assertEquals(2 to 4, rotated.width to rotated.height)
        assertEquals(0xff000000.toInt(), rotated.getPixel(1, 3))
        assertNotNull(rotated)
    }

    @Test
    fun aFrontCamerasUprightBitmap_isTurnedThenFlippedLeftToRight() {
        // A 4×2 buffer, its top row black and its bottom row white. Turned 90° clockwise, the bottom row is the
        // image's left column; the mirror then puts it on the right, as the front camera's preview shows it.
        val rows = ByteArray(8) { (if (it < 4) 16 else 235).toByte() }
        val chroma = ByteArray(2) { 128.toByte() }
        val frame = { FakeImageProxy(4, 2, rows, chroma, chroma.copyOf(), rotation = 90) }
        val plain = CameraFrameCache().apply { update(frame()) }
        val mirrored = CameraFrameCache(mirrored = true).apply { update(frame()) }

        val seen = checkNotNull(plain.snapshot()).toUprightBitmap()
        val shown = checkNotNull(mirrored.snapshot()).toUprightBitmap()

        assertEquals(2 to 4, shown.width to shown.height)
        assertEquals(listOf(WHITE, BLACK), listOf(seen.getPixel(0, 0), seen.getPixel(1, 0)))
        assertEquals(listOf(BLACK, WHITE), listOf(shown.getPixel(0, 0), shown.getPixel(1, 0)))
    }

    private companion object {
        const val WHITE = 0xffffffff.toInt()
        const val BLACK = 0xff000000.toInt()
    }
}
