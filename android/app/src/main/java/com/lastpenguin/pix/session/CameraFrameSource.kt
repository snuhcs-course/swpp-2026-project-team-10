// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.session

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.core.Timings
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.abs
import org.webrtc.CapturerObserver
import org.webrtc.NV21Buffer
import org.webrtc.VideoFrame

/**
 * Feeds camera frames into the WebRTC video source (Design 2.6.4). The photographer passes it to
 * CameraController.setFrameSink while connected. Each frame is packed to NV21, cropped to what the photographer's
 * preview shows ([streamCrop]: the viewport of the chosen aspect ratio, or for 1:1 the 4:3 around it), and
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
            val viewport = image.cropRect
            val shown = streamCrop(CropBox(viewport.left, viewport.top, viewport.right, viewport.bottom), width, height)
            val crop = when {
                !mirrored -> Rect(shown.left, shown.top, shown.right, shown.bottom)
                rows -> Rect(shown.left, height - shown.bottom, shown.right, height - shown.top)
                else -> Rect(width - shown.right, shown.top, width - shown.left, shown.bottom)
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

/** A crop of a frame's buffer, in its pixels: [left] and [top] in, [right] and [bottom] out. */
internal data class CropBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * What of a [width] × [height] buffer the subject's phone gets for the viewport's [crop] (Design 2.6.4): the crop,
 * except a square one, 1:1, which widens to 4:3 around it along the buffer's long side, within the buffer. Subject view
 * then shows the whole 4:3 area with the outside of the square dimmed, as the photographer's preview does (R&S 6.2,
 * 6.6). Edges stay even, as NV21's chroma is half the size.
 */
internal fun streamCrop(crop: CropBox, width: Int, height: Int): CropBox {
    val side = minOf(crop.width, crop.height)
    if (side <= 0 || abs(crop.width - crop.height) * SQUARE_TOLERANCE > side) return crop
    val long = side * SENSOR_LONG / SENSOR_SHORT
    return if (width >= height) {
        val wide = minOf(long, width) and EVEN
        val left = ((crop.left + crop.right - wide) / 2).coerceIn(0, width - wide) and EVEN
        CropBox(left, crop.top, left + wide, crop.bottom)
    } else {
        val tall = minOf(long, height) and EVEN
        val top = ((crop.top + crop.bottom - tall) / 2).coerceIn(0, height - tall) and EVEN
        CropBox(crop.left, top, crop.right, top + tall)
    }
}

// A viewport's square may be a pixel off on a buffer whose sides do not divide evenly: within 1 % of its side.
private const val SQUARE_TOLERANCE = 100
private const val SENSOR_LONG = 4
private const val SENSOR_SHORT = 3
private const val EVEN = -2
