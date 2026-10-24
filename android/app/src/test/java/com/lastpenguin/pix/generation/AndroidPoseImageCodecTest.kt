// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The scene photo as the server requires it (Design 2.6.3, step 1): a photo within 1024 px on its long side goes at
 * its size; a wider or a taller one is scaled so its long side is 1024 px, and the caller's bitmap is left alone;
 * the bytes are a JPEG. A candidate's bytes decode to a bitmap; bytes that are no image give null.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPoseImageCodecTest {
    private fun scene(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }

    private fun decoded(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    @Test
    fun aPhotoWithinTheLimit_keepsItsSize_asAJpeg() {
        val bytes = AndroidPoseImageCodec.encodeScene(scene(800, 600))

        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val image = decoded(bytes)
        assertEquals(800, image.width)
        assertEquals(600, image.height)
    }

    @Test
    fun aWidePhoto_isScaledToTheLongSide_andTheOriginalIsKept() {
        val original = scene(2048, 1536)

        val image = decoded(AndroidPoseImageCodec.encodeScene(original))

        assertEquals(1024, image.width)
        assertEquals(768, image.height)
        assertFalse(original.isRecycled)
    }

    @Test
    fun aTallPhoto_isScaledToTheLongSide() {
        val image = decoded(AndroidPoseImageCodec.encodeScene(scene(1500, 3000)))

        assertEquals(512, image.width)
        assertEquals(1024, image.height)
    }

    @Test
    fun aCandidateDecodes_andBytesThatAreNoImageGiveNull() {
        val bytes = AndroidPoseImageCodec.encodeScene(scene(640, 480))

        val candidate = AndroidPoseImageCodec.decodeCandidate(bytes)
        assertNotNull(candidate)
        assertEquals(640, candidate!!.width)
        assertNull(AndroidPoseImageCodec.decodeCandidate(byteArrayOf(1, 2, 3, 4)))
    }
}
