package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A session that records what is sent and lets a test deliver incoming messages. */
class FakeSessionManager : SessionManager {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SessionMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<SessionMessage> = _incoming.asSharedFlow()

    val sent = mutableListOf<SessionMessage>()
    val frameSink = CameraFrameSource()

    override suspend fun start(entry: SessionEntry, role: Role) = Unit

    override fun send(message: SessionMessage) {
        sent += message
    }

    override fun videoSink(): FrameSink = frameSink

    override suspend fun leave(reason: EndReason) = Unit

    fun setState(state: SessionState) {
        _state.value = state
    }

    fun deliver(message: SessionMessage) = check(_incoming.tryEmit(message))
}

/** A camera whose zoom applies at once (or not at all when [applies] is false). */
class FakeCameraController(
    minZoom: Float = 0.6f,
    maxZoom: Float = 10f,
) : CameraController {
    private val _status = MutableStateFlow(CameraStatus.READY)
    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(
        CameraCapabilities(minZoom, maxZoom, listOf(0.6f, 1f, 2f, 3f).filter { it in minZoom..maxZoom }),
    )
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoom: StateFlow<Float> = _zoom.asStateFlow()

    val requests = mutableListOf<Float>()
    var applies = true
    var sink: FrameSink? = null

    override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit

    override fun setZoom(ratio: Float) {
        requests += ratio
        if (applies) _zoom.value = ratio
    }

    /** The photographer changed the zoom on their own phone. */
    fun applyLocally(ratio: Float) {
        _zoom.value = ratio
    }

    override suspend fun takePhoto(): Result<Uri> = Result.failure(UnsupportedOperationException())

    override suspend fun grabFrame(): Result<Bitmap> = Result.failure(UnsupportedOperationException())

    override fun setFrameSink(sink: FrameSink?) {
        this.sink = sink
    }
}
