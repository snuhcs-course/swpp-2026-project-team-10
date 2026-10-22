// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraState
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.EglBase
import org.webrtc.VideoSink

/** A session that records what is sent and lets a test deliver incoming messages. */
class FakeSessionManager : SessionManager {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SessionMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<SessionMessage> = _incoming.asSharedFlow()

    private val _peerCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val peerCapabilities: StateFlow<CameraCapabilities?> = _peerCapabilities.asStateFlow()

    private val _peerCamera = MutableStateFlow<CameraState?>(null)
    override val peerCamera: StateFlow<CameraState?> = _peerCamera.asStateFlow()

    val sent = mutableListOf<SessionMessage>()
    val frameSink = CameraFrameSource()

    /** Every `start`, so a test can see what was joined or opened, and as which role. */
    val starts = mutableListOf<Pair<SessionEntry, Role>>()

    override suspend fun start(entry: SessionEntry, role: Role) {
        starts += entry to role
    }

    override fun send(message: SessionMessage) {
        sent += message
    }

    override fun videoSink(): FrameSink = frameSink

    /** Every `leave`, with its reason, so a screen test can see a cancelled room or a left session. */
    val leaves = mutableListOf<EndReason>()

    override suspend fun leave(reason: EndReason) {
        leaves += reason
    }

    fun setState(state: SessionState) {
        _state.value = state
    }

    /** The session kept a `camera.state` whose `incoming` emission this screen did not see. */
    fun setPeerCamera(state: CameraState?) {
        _peerCamera.value = state
    }

    fun deliver(message: SessionMessage) {
        if (message is SessionMessage.Capabilities) _peerCapabilities.value = message.capabilities
        if (message is SessionMessage.CameraStateUpdate) {
            _peerCamera.value = CameraState(message.zoom, message.exposure, message.flash, message.aspect)
        }
        check(_incoming.tryEmit(message))
    }
}

/**
 * A camera whose zoom, exposure, and flash apply at once (or not at all when [applies] is false), within the
 * capabilities it was given: no exposure range and no flash unless a test asks for them.
 */
class FakeCameraController(
    minZoom: Float = 0.6f,
    maxZoom: Float = 10f,
    exposureRange: IntRange = 0..0,
    hasFlash: Boolean = false,
) : CameraController {
    private val _status = MutableStateFlow(CameraStatus.READY)
    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(
        CameraCapabilities(
            minZoom,
            maxZoom,
            listOf(0.6f, 1f, 2f, 3f).filter { it in minZoom..maxZoom },
            exposureRange = exposureRange,
            exposureStep = if (exposureRange.first < exposureRange.last) 0.5f else 0f,
            hasFlash = hasFlash,
        ),
    )
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoom: StateFlow<Float> = _zoom.asStateFlow()

    /** Set by a test to stand for the photographer changing exposure, flash, or the aspect ratio. */
    override val state = MutableStateFlow(CameraState(zoom = 1f))

    val requests = mutableListOf<Float>()
    var applies = true
    var sink: FrameSink? = null

    override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit

    override fun setZoom(ratio: Float) {
        requests += ratio
        if (applies) _zoom.value = ratio
    }

    /** The photographer changed the zoom on their own phone, or a request the camera held was applied. */
    fun applyLocally(ratio: Float) {
        _zoom.value = ratio
    }

    fun setStatus(status: CameraStatus) {
        _status.value = status
    }

    override fun setFocusPoint(x: Float, y: Float) = Unit

    val exposureRequests = mutableListOf<Int>()
    val flashRequests = mutableListOf<FlashMode>()

    override fun setExposure(index: Int) {
        exposureRequests += index
        val range = _capabilities.value?.exposureRange ?: 0..0
        if (applies) state.value = state.value.copy(exposure = index.coerceIn(range))
    }

    override fun setFlash(mode: FlashMode) {
        flashRequests += mode
        if (applies && _capabilities.value?.hasFlash == true) state.value = state.value.copy(flash = mode)
    }

    override fun setAspectRatio(ratio: AspectRatio) = Unit

    /** When set, [takePhoto] saves to it; otherwise saving fails, as it does without a camera. */
    var photo: Uri? = null

    /** When set, [grabFrame] returns it, so the scene photo step can be driven in a screen test. */
    var frame: Bitmap? = null

    override suspend fun takePhoto(): Result<Uri> =
        photo?.let { Result.success(it) } ?: Result.failure(UnsupportedOperationException())

    override suspend fun grabFrame(): Result<Bitmap> =
        frame?.let { Result.success(it) } ?: Result.failure(UnsupportedOperationException())

    override fun setFrameSink(sink: FrameSink?) {
        this.sink = sink
    }
}

/** A guide store that only remembers what it was given. */
class FakeGuideRepository : GuideRepository {
    private val _guide = MutableStateFlow<ReferenceGuide?>(null)
    override val guide: StateFlow<ReferenceGuide?> = _guide.asStateFlow()

    private val _state = MutableStateFlow(GuideState())
    override val state: StateFlow<GuideState> = _state.asStateFlow()

    private val _changes = MutableSharedFlow<GuideChange>(extraBufferCapacity = 64)
    override val changes: SharedFlow<GuideChange> = _changes.asSharedFlow()

    override fun setGuide(guide: ReferenceGuide?) {
        _guide.value = guide
    }

    override fun update(final: Boolean, change: (GuideState) -> GuideState) {
        _state.value = change(_state.value)
        _changes.tryEmit(GuideChange(_state.value, final))
    }
}

/** Video that goes nowhere. */
class FakeRemoteVideo : RemoteVideo {
    override val eglContext: EglBase.Context = object : EglBase.Context {
        override fun getNativeEglContext(): Long = 0L
    }

    val sinks = mutableListOf<VideoSink>()

    override fun attach(sink: VideoSink) {
        sinks += sink
    }

    override fun detach(sink: VideoSink) {
        sinks -= sink
    }
}

/** A Bitmap for tests that never touch pixels: Android's JVM stubs cannot create one, so allocate it raw. */
fun fakeBitmap(): Bitmap {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Bitmap::class.java) as Bitmap
}

fun fakeGuide(id: String, aspect: Float = 0.5f): ReferenceGuide =
    ReferenceGuide(id, fakeBitmap(), fakeBitmap(), aspect, GuideSource.GALLERY)

/** Encodes a guide as its id's bytes and decodes any bytes into a guide, with no graphics. */
class FakeGuideImageCodec : GuideImageCodec {
    val encoded = mutableListOf<String>()
    val decoded = mutableListOf<String>()
    var decodes = true

    override fun encode(guide: ReferenceGuide): EncodedGuideImage {
        encoded += guide.id
        return EncodedGuideImage(
            guide.id,
            "fake",
            10,
            20,
            "image:${guide.id}".toByteArray().let {
                it +
                    ByteArray(20_000)
            },
        )
    }

    override fun decode(guideId: String, format: String, bytes: ByteArray): ReferenceGuide? {
        decoded += guideId
        return if (decodes && format == "fake") fakeGuide(guideId) else null
    }
}
