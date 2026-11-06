// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.session

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.core.Timings
import java.util.concurrent.ArrayBlockingQueue
import org.webrtc.CapturerObserver
import org.webrtc.NV21Buffer
import org.webrtc.VideoFrame

/**
 * Feeds camera frames into the WebRTC video source (Design 2.6.4). The photographer passes it to
 * CameraController.setFrameSink while connected. Each frame is packed to NV21, cropped to the viewport of the chosen aspect ratio, and
 * handed over with its rotation, so the encoder sends the sensor's landscape buffer and the subject's renderer
 * turns it upright. The front camera's frames are flipped first, so the subject sees the photographer's mirrored
 * preview and the guide lines up on both phones (FR-1.14). A small pool reuses the NV21 arrays.
 * Owner: Real-time (#8).
 */
class CameraFrameSource(private val clock: () -> Long = System::nanoTime) : FrameSink {

    /** The current peer connection's video source; null while no session is streaming, which drops frames. */
    @Volatile
    var observer: CapturerObserver? = null

    private val pool = ArrayBlockingQueue<ByteArray>(POOL_SIZE)
    private var frames = 0L

    override fun onFrame(image: ImageProxy, mirrored: Boolean) {
        try {
            val observer = observer ?: return
            if (image.format != ImageFormat.YUV_420_888) return
            val width = image.width
            val height = image.height
            val size = YuvToNv21.sizeFor(width, height)
            val nv21 = pool.poll()?.takeIf { it.size == size } ?: ByteArray(size)
            val planes = image.planes
            YuvToNv21.convert(
                width,
                height,
                planes[0].toYuvPlane(),
                planes[1].toYuvPlane(),
                planes[2].toYuvPlane(),
                nv21,
            )
            val rotation = image.imageInfo.rotationDegrees
            // Turned by a quarter, the upright frame's left and right are the buffer's rows.
            val rows = rotation % 180 != 0
            if (mirrored) YuvToNv21.mirror(nv21, width, height, rows)
            val full = NV21Buffer(nv21, width, height) { pool.offer(nv21) }
            val crop = image.cropRect.let { crop ->
                when {
                    !mirrored -> crop
                    rows -> Rect(crop.left, height - crop.bottom, crop.right, height - crop.top)
                    else -> Rect(width - crop.right, crop.top, width - crop.left, crop.bottom)
                }
            }
            val buffer = if (crop.left == 0 && crop.top == 0 && crop.width() == width && crop.height() == height) {
                full
            } else {
                full.cropAndScale(crop.left, crop.top, crop.width(), crop.height(), crop.width(), crop.height())
                    .also { full.release() }
            }
            val frame = VideoFrame(buffer, rotation, clock())
            observer.onFrameCaptured(frame)
            frame.release()
            if (++frames == 1L || frames % LOG_EVERY == 0L) {
                Timings.mark(
                    "frames",
                    "n=$frames ${width}x$height crop=${crop.width()}x${crop.height()} rotation=$rotation",
                )
            }
        } finally {
            image.close()
        }
    }

    private fun ImageProxy.PlaneProxy.toYuvPlane() = YuvPlane(buffer, rowStride, pixelStride)

    private companion object {
        const val POOL_SIZE = 3
        const val LOG_EVERY = 300L
    }
}
