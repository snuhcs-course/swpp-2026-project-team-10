package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.signaling.IceCandidate
import com.lastpenguin.pix.session.signaling.IceServer
import com.lastpenguin.pix.session.signaling.SessionDescription
import java.nio.ByteBuffer
import java.util.EnumMap
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.DataChannel
import org.webrtc.IceCandidate as RtcIceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport
import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription as RtcSessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * [PeerConnectionClient] on libwebrtc (Design 2.5.1, 2.6.4). The photographer adds the video track and opens both
 * data channels before the offer; the subject receives them. WebRTC callbacks only publish [PeerEvent]s, so they
 * never block its threads (Design 2.8, Concurrency).
 * Owner: Real-time (#8).
 */
class WebRtcPeerConnectionClient(
    private val factory: PeerConnectionFactory,
    config: PeerConfig,
    private val frameSource: CameraFrameSource,
) : PeerConnectionClient {

    private val _events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<PeerEvent> = _events.asSharedFlow()

    private val lock = Any()
    private val channels = EnumMap<Channel, DataChannel>(Channel::class.java)
    private val sinks = LinkedHashSet<VideoSink>()
    private var remoteTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var closed = false
    private var lastBytes: Long? = null
    private var lastStatsTimestampUs = 0.0

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: RtcIceCandidate) {
            _events.tryEmit(
                PeerEvent.LocalCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)),
            )
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            val state = when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> LinkState.CONNECTED
                PeerConnection.PeerConnectionState.DISCONNECTED -> LinkState.DISCONNECTED
                PeerConnection.PeerConnectionState.FAILED -> LinkState.FAILED
                PeerConnection.PeerConnectionState.CLOSED -> LinkState.CLOSED
                else -> LinkState.CONNECTING
            }
            _events.tryEmit(PeerEvent.Link(state))
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            val channel = when (dataChannel.label()) {
                REALTIME_LABEL -> Channel.REALTIME
                RELIABLE_LABEL -> Channel.RELIABLE
                else -> return
            }
            synchronized(lock) {
                if (closed) return
                channels[channel] = dataChannel
            }
            observe(channel, dataChannel)
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track() as? VideoTrack ?: return
            synchronized(lock) {
                if (closed) return
                remoteTrack = track
                sinks.forEach(track::addSink)
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidatesRemoved(candidates: Array<out RtcIceCandidate>) = Unit

        override fun onAddStream(stream: MediaStream) = Unit

        override fun onRemoveStream(stream: MediaStream) = Unit

        override fun onRenegotiationNeeded() = Unit
    }

    private val peerConnection: PeerConnection

    init {
        val rtcConfig = PeerConnection.RTCConfiguration(config.iceServers.map { it.toWebRtc() })
        peerConnection = factory.createPeerConnection(rtcConfig, observer)
            ?: error("Could not create a peer connection")
        if (config.role == Role.PHOTOGRAPHER) {
            val source = factory.createVideoSource(false)
            // Crops to 4:3 and scales down to 960×720 if the camera gives more (Design 2.6.4).
            source.adaptOutputFormat(STREAM_LONG_SIDE, STREAM_SHORT_SIDE, STREAM_FPS)
            videoSource = source
            frameSource.observer = source.capturerObserver
            source.capturerObserver.onCapturerStarted(true)
            val track = factory.createVideoTrack(VIDEO_TRACK_ID, source)
            val transceiver = peerConnection.addTransceiver(
                track,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf(STREAM_ID)),
            )
            preferCodecs(transceiver)
            limitSender(transceiver.sender)
            val realtime = DataChannel.Init().apply {
                ordered = false
                maxRetransmits = 0
            }
            channels[Channel.REALTIME] = peerConnection.createDataChannel(REALTIME_LABEL, realtime)
                .also { observe(Channel.REALTIME, it) }
            channels[Channel.RELIABLE] = peerConnection.createDataChannel(RELIABLE_LABEL, DataChannel.Init())
                .also { observe(Channel.RELIABLE, it) }
        }
    }

    override suspend fun createOffer(): SessionDescription =
        createLocal { peerConnection.createOffer(it, MediaConstraints()) }

    override suspend fun createAnswer(): SessionDescription =
        createLocal { peerConnection.createAnswer(it, MediaConstraints()) }

    private suspend fun createLocal(create: (SdpObserver) -> Unit): SessionDescription {
        val sdp = suspendCancellableCoroutine<RtcSessionDescription> { continuation ->
            create(SdpCallbacks(onCreated = { continuation.resume(it) }, onFailed = continuation::resumeWithException))
        }
        suspendCancellableCoroutine { continuation ->
            peerConnection.setLocalDescription(
                SdpCallbacks(onSet = { continuation.resume(Unit) }, onFailed = continuation::resumeWithException),
                sdp,
            )
        }
        return SessionDescription(type = sdp.type.canonicalForm(), sdp = sdp.description)
    }

    override suspend fun applyRemote(sdp: SessionDescription) {
        val remote = RtcSessionDescription(RtcSessionDescription.Type.fromCanonicalForm(sdp.type), sdp.sdp)
        suspendCancellableCoroutine { continuation ->
            peerConnection.setRemoteDescription(
                SdpCallbacks(onSet = { continuation.resume(Unit) }, onFailed = continuation::resumeWithException),
                remote,
            )
        }
    }

    override fun addIceCandidate(candidate: IceCandidate) {
        synchronized(lock) { if (closed) return }
        val rtcCandidate = RtcIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
        if (!peerConnection.addIceCandidate(rtcCandidate)) Log.w(TAG, "A remote ICE candidate was rejected")
    }

    override fun send(channel: Channel, text: String): Boolean {
        val dataChannel = synchronized(lock) { if (closed) null else channels[channel] } ?: return false
        if (dataChannel.state() != DataChannel.State.OPEN) return false
        return dataChannel.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false))
    }

    override fun bufferedAmount(channel: Channel): Long {
        val dataChannel = synchronized(lock) { if (closed) null else channels[channel] } ?: return 0L
        return dataChannel.bufferedAmount()
    }

    override suspend fun stats(): LinkStats? {
        synchronized(lock) { if (closed) return null }
        val report = suspendCancellableCoroutine { continuation ->
            peerConnection.getStats { report -> continuation.resume(report) }
        }
        return parse(report)
    }

    /** Reads the video RTP stream, its codec, and the nominated candidate pair; bitrate comes from byte deltas. */
    private fun parse(report: RTCStatsReport): LinkStats? {
        val all = report.statsMap.values
        // A transceiver reports both directions; the one carrying bytes is the live video stream.
        val rtp =
            all.filter { (it.type == "outbound-rtp" || it.type == "inbound-rtp") && it.members["kind"] == "video" }
                .maxByOrNull { it.bytes() ?: -1L } ?: return null
        val members = rtp.members
        val bytes = rtp.bytes()
        val codec = (members["codecId"] as? String)?.let { report.statsMap[it]?.members?.get("mimeType") as? String }
        val pair = all.firstOrNull { it.type == "candidate-pair" && it.members["nominated"] == true }
            ?: all.firstOrNull { it.type == "candidate-pair" && it.members["state"] == "succeeded" }
        val roundTripMs = (pair?.members?.get("currentRoundTripTime") as? Number)?.toDouble()?.times(1000)
        val now: Double = rtp.timestampUs
        val previousBytes = lastBytes
        val bitrateKbps = if (bytes != null && previousBytes != null && now > lastStatsTimestampUs) {
            (bytes - previousBytes) * 8.0 / ((now - lastStatsTimestampUs) / 1_000_000.0) / 1000.0
        } else {
            null
        }
        lastBytes = bytes
        lastStatsTimestampUs = now
        return LinkStats(
            codec = codec,
            width = (members["frameWidth"] as? Number)?.toInt(),
            height = (members["frameHeight"] as? Number)?.toInt(),
            framesPerSecond = (members["framesPerSecond"] as? Number)?.toDouble(),
            bitrateKbps = bitrateKbps,
            roundTripMs = roundTripMs,
        )
    }

    private fun RTCStats.bytes(): Long? = ((members["bytesSent"] ?: members["bytesReceived"]) as? Number)?.toLong()

    override fun addVideoSink(sink: VideoSink) {
        synchronized(lock) {
            if (closed) return
            sinks += sink
            remoteTrack?.addSink(sink)
        }
    }

    override fun removeVideoSink(sink: VideoSink) {
        synchronized(lock) {
            if (closed) return
            sinks -= sink
            remoteTrack?.removeSink(sink)
        }
    }

    override fun close() {
        val dataChannels: List<DataChannel>
        val track: VideoTrack?
        val attached: List<VideoSink>
        synchronized(lock) {
            if (closed) return
            closed = true
            dataChannels = channels.values.toList()
            channels.clear()
            track = remoteTrack
            remoteTrack = null
            attached = sinks.toList()
            sinks.clear()
        }
        videoSource?.let { source ->
            if (frameSource.observer === source.capturerObserver) frameSource.observer = null
            source.capturerObserver.onCapturerStopped()
        }
        track?.let { remote -> attached.forEach(remote::removeSink) }
        dataChannels.forEach {
            it.unregisterObserver()
            it.close()
            it.dispose()
        }
        // Disposing the peer connection also disposes the tracks held by its senders and receivers.
        peerConnection.dispose()
        videoSource?.dispose()
        videoSource = null
    }

    private fun observe(channel: Channel, dataChannel: DataChannel) {
        dataChannel.registerObserver(
            object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {
                    _events.tryEmit(PeerEvent.BufferedAmount(channel, dataChannel.bufferedAmount()))
                }

                override fun onStateChange() {
                    when (dataChannel.state()) {
                        DataChannel.State.OPEN -> _events.tryEmit(PeerEvent.ChannelOpen(channel))
                        DataChannel.State.CLOSED -> _events.tryEmit(PeerEvent.ChannelClosed(channel))
                        else -> Unit
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    if (buffer.binary) return
                    // The buffer is only valid during this callback, so copy it out.
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    _events.tryEmit(PeerEvent.Message(channel, String(bytes, Charsets.UTF_8)))
                }
            },
        )
    }

    /** H.264 first, then VP8, then whatever else the phone offers (Design 2.6.4). */
    private fun preferCodecs(transceiver: RtpTransceiver) {
        val codecs = factory.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
        if (codecs.isEmpty()) return
        val ordered = codecs.sortedBy { codec ->
            when (codec.name.uppercase(Locale.ROOT)) {
                "H264" -> 0
                "VP8" -> 1
                else -> 2
            }
        }
        transceiver.setCodecPreferences(ordered)
    }

    /** 2.5 Mbps and 30 fps at most; a weak network lowers resolution before frame rate (NFR-6, NFR-12). */
    private fun limitSender(sender: RtpSender) {
        val parameters = sender.parameters
        parameters.encodings.forEach {
            it.maxBitrateBps = MAX_BITRATE_BPS
            it.maxFramerate = STREAM_FPS
        }
        parameters.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
        if (!sender.setParameters(parameters)) Log.w(TAG, "Could not apply the sender limits")
    }

    private class SdpCallbacks(
        private val onCreated: (RtcSessionDescription) -> Unit = {},
        private val onSet: () -> Unit = {},
        private val onFailed: (Throwable) -> Unit,
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: RtcSessionDescription) = onCreated(sdp)

        override fun onSetSuccess() = onSet()

        override fun onCreateFailure(error: String?) = onFailed(IllegalStateException(error ?: "SDP creation failed"))

        override fun onSetFailure(error: String?) = onFailed(IllegalStateException(error ?: "SDP was not applied"))
    }

    private fun IceServer.toWebRtc(): PeerConnection.IceServer {
        val builder = PeerConnection.IceServer.builder(urls)
        username?.let(builder::setUsername)
        credential?.let(builder::setPassword)
        return builder.createIceServer()
    }

    private companion object {
        const val TAG = "PixPeer"
        const val REALTIME_LABEL = "realtime"
        const val RELIABLE_LABEL = "reliable"
        const val VIDEO_TRACK_ID = "pix-video"
        const val STREAM_ID = "pix"
        const val STREAM_LONG_SIDE = 960
        const val STREAM_SHORT_SIDE = 720
        const val STREAM_FPS = 30
        const val MAX_BITRATE_BPS = 2_500_000
    }
}
