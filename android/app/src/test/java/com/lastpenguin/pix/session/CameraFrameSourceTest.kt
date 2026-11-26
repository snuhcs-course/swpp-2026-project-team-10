// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Joonhyung Han, and Dongje Park
package com.lastpenguin.pix.session

import android.graphics.ImageFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.camera.FakeImageProxy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.CapturerObserver
import org.webrtc.NV21Buffer
import org.webrtc.VideoFrame

/*
 * The frames the photographer streams (Design 2.6.4): no session (dropped, closed), a format the encoder cannot
 * take (dropped, closed), a full frame (packed to NV21, handed over with its rotation and time, closed, and the
 * array back in the pool when the frame is released), and a front camera's full frame, flipped by rows when turned a
 * quarter and by columns when upright (FR-1.14). The crop-and-scale path needs libwebrtc and is left to the two-phone
 * check (IT-8). Under Robolectric, because the crop test needs a real Rect; the JVM stubs answer 0.
 * What of the buffer is streamed (streamCrop, R&S 6.6): a 1:1 viewport on a landscape buffer and on a portrait one
 * (widened to 4:3 along the long side), one a pixel off square (widened too, with even edges), 3:4 and 9:16 viewports
 * and a small 2:1 frame (as they are), and an empty crop (as it is).
 */
@RunWith(AndroidJUnit4::class)
class CameraFrameSourceTest {
    private class Observer : CapturerObserver {
        val frames = mutableListOf<Triple<Int, Int, Int>>()
        var buffers = mutableListOf<VideoFrame.Buffer>()
        var timestamps = mutableListOf<Long>()

        override fun onCapturerStarted(success: Boolean) = Unit

        override fun onCapturerStopped() = Unit

        override fun onFrameCaptured(frame: VideoFrame) {
            frames += Triple(frame.buffer.width, frame.buffer.height, frame.rotation)
            buffers += frame.buffer
            timestamps += frame.timestampNs
            frame.retain()
        }
    }

    @Test
    fun withoutASession_theFrameIsDroppedAndGivenBack() {
        val source = CameraFrameSource()
        val image = FakeImageProxy.flat(4, 4, luma = 128)

        source.onFrame(image, mirrored = false)

        assertTrue(image.closed)
    }

    @Test
    fun aFormatOtherThanYuv420_isDroppedAndGivenBack() {
        val observer = Observer()
        val source = CameraFrameSource().apply { this.observer = observer }
        val image = FakeImageProxy(2, 2, ByteArray(4), ByteArray(1), ByteArray(1), format = ImageFormat.JPEG)

        source.onFrame(image, mirrored = false)

        assertTrue(image.closed)
        assertTrue(observer.frames.isEmpty())
    }

    @Test
    fun aFullFrame_reachesTheSessionAsNv21WithItsRotationAndTime_andIsGivenBack() {
        val observer = Observer()
        var now = 1_000L
        val source = CameraFrameSource(clock = { now }).apply { this.observer = observer }
        val image = FakeImageProxy.flat(4, 2, luma = 235, rotation = 90)

        source.onFrame(image, mirrored = false)
        now = 2_000L
        source.onFrame(FakeImageProxy.flat(4, 2, luma = 16, rotation = 270), mirrored = false)

        assertEquals(listOf(Triple(4, 2, 90), Triple(4, 2, 270)), observer.frames)
        assertEquals(listOf(1_000L, 2_000L), observer.timestamps)
        assertTrue(observer.buffers.all { it is NV21Buffer })
        assertTrue(image.closed)
        observer.buffers.forEach { it.release() }
    }

    @Test
    fun aFrontCameraFrameTurnedAQuarter_reachesTheSessionWithItsRowsInReverse() {
        val sent = sendMirrored(rotation = 270)

        assertArrayEquals(byteArrayOf(5, 6, 7, 8, 1, 2, 3, 4, -1, 101, -2, 102), sent)
    }

    @Test
    fun anUprightFrontCameraFrame_reachesTheSessionWithEachRowInReverse() {
        val sent = sendMirrored(rotation = 0)

        assertArrayEquals(byteArrayOf(4, 3, 2, 1, 8, 7, 6, 5, -2, 102, -1, 101), sent)
    }

    /**
     * Sends a 4×2 front camera frame whose luma counts 1 to 8 and whose chroma is U 101, 102 and V -1, -2, and returns
     * the NV21 bytes the session got. `NV21Buffer` keeps them private; they are what the encoder is given.
     */
    private fun sendMirrored(rotation: Int): ByteArray {
        val observer = Observer()
        val source = CameraFrameSource().apply { this.observer = observer }
        val image = FakeImageProxy(
            4,
            2,
            ByteArray(8) { (it + 1).toByte() },
            byteArrayOf(101, 102),
            byteArrayOf(-1, -2),
            rotation = rotation,
        )

        source.onFrame(image, mirrored = true)

        assertEquals(listOf(Triple(4, 2, rotation)), observer.frames)
        val buffer = observer.buffers.single()
        val data = NV21Buffer::class.java.getDeclaredField("data").apply { isAccessible = true }.get(buffer)
        return (data as ByteArray).copyOf(12).also { buffer.release() }
    }

    @Test
    fun aSquareViewport_isWidenedToTheSensors4By3_alongTheBuffersLongSide() {
        assertEquals(CropBox(0, 0, 960, 720), streamCrop(CropBox(120, 0, 840, 720), 960, 720))
        assertEquals(CropBox(0, 0, 720, 960), streamCrop(CropBox(0, 120, 720, 840), 720, 960))
    }

    @Test
    fun aViewportAPixelOffSquare_isWidenedToo_withEvenEdges() {
        // 721 × 720 in a buffer wider than 4:3: 960 wide around its middle, from an even column.
        val widened = streamCrop(CropBox(151, 0, 872, 720), 1024, 720)

        assertEquals(CropBox(30, 0, 990, 720), widened)
    }

    @Test
    fun otherViewports_andAnEmptyCrop_streamAsTheyAre() {
        val threeByFour = CropBox(0, 0, 960, 720)
        val nineBySixteen = CropBox(0, 120, 1280, 840)
        val empty = CropBox(0, 0, 0, 0)
        val small = CropBox(0, 0, 4, 2)

        assertEquals(small, streamCrop(small, 4, 2))
        assertEquals(threeByFour, streamCrop(threeByFour, 960, 720))
        assertEquals(nineBySixteen, streamCrop(nineBySixteen, 1280, 960))
        assertEquals(empty, streamCrop(empty, 960, 720))
    }
}
