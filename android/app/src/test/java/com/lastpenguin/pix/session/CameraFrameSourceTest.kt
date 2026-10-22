// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.ImageFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.camera.FakeImageProxy
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
 * array back in the pool when the frame is released). The crop-and-scale path needs libwebrtc and is left to the
 * two-phone check (IT-8). Under Robolectric, because the crop test needs a real Rect; the JVM stubs answer 0.
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

        source.onFrame(image)

        assertTrue(image.closed)
    }

    @Test
    fun aFormatOtherThanYuv420_isDroppedAndGivenBack() {
        val observer = Observer()
        val source = CameraFrameSource().apply { this.observer = observer }
        val image = FakeImageProxy(2, 2, ByteArray(4), ByteArray(1), ByteArray(1), format = ImageFormat.JPEG)

        source.onFrame(image)

        assertTrue(image.closed)
        assertTrue(observer.frames.isEmpty())
    }

    @Test
    fun aFullFrame_reachesTheSessionAsNv21WithItsRotationAndTime_andIsGivenBack() {
        val observer = Observer()
        var now = 1_000L
        val source = CameraFrameSource(clock = { now }).apply { this.observer = observer }
        val image = FakeImageProxy.flat(4, 2, luma = 235, rotation = 90)

        source.onFrame(image)
        now = 2_000L
        source.onFrame(FakeImageProxy.flat(4, 2, luma = 16, rotation = 270))

        assertEquals(listOf(Triple(4, 2, 90), Triple(4, 2, 270)), observer.frames)
        assertEquals(listOf(1_000L, 2_000L), observer.timestamps)
        assertTrue(observer.buffers.all { it is NV21Buffer })
        assertTrue(image.closed)
        observer.buffers.forEach { it.release() }
    }
}
