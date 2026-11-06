// AI-generated with Claude Code, 2026-10-28, reviewed by Dongje Park
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.ReferenceGuide
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Real JPEG encoding and decoding of the reference photo for Subject view (FR-3.9; Robolectric native graphics).
 * Partitions: a photo wider and taller than 512 px, one already small, none, and another format; on receipt, a photo
 * larger than 512 px and bytes that are not an image.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegGuideReferenceCodecTest {

    private val codec = JpegGuideReferenceCodec()

    private fun guide(reference: Bitmap?) = ReferenceGuide(
        "g1",
        Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888),
        0.5f,
        GuideSource.GALLERY,
        reference = reference,
    )

    private fun photo(width: Int, height: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 80)) }

    @Test
    fun `a large photo travels at most 512 px on its long side and comes back the same size`() {
        for ((width, height) in listOf(1200 to 900, 600 to 1000)) {
            val encoded = codec.encode(guide(photo(width, height)))
            assertNotNull(encoded)
            encoded!!
            assertEquals(512, maxOf(encoded.width, encoded.height))
            assertEquals(width.toFloat() / height, encoded.width.toFloat() / encoded.height, 0.01f)
            assertTrue(encoded.chunks.size <= 5)

            val decoded = codec.decode(encoded.format, encoded.bytes)
            assertEquals(encoded.width, decoded?.width)
            assertEquals(encoded.height, decoded?.height)
        }
    }

    @Test
    fun `a small photo keeps its size, no photo sends nothing, and another format is refused`() {
        val small = codec.encode(guide(photo(300, 400)))
        assertEquals(300, small?.width)
        assertEquals(400, small?.height)

        assertNull(codec.encode(guide(null)))
        assertNull(codec.decode("webp", small!!.bytes))
    }

    @Test
    fun `a received photo larger than 512 px is refused before it is decoded`() {
        val out = ByteArrayOutputStream()
        photo(1200, 400).compress(Bitmap.CompressFormat.JPEG, 85, out)

        assertNull(codec.decode(JpegGuideReferenceCodec.FORMAT, out.toByteArray()))
        assertNull(codec.decode(JpegGuideReferenceCodec.FORMAT, byteArrayOf(1, 2, 3)))
    }
}
