package com.lastpenguin.pix.session

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * The process-wide WebRTC objects: native initialization, one EGL context shared by the encoder, the decoder,
 * and the subject's renderer, and the PeerConnectionFactory. All of it is created on first use, so the camera
 * screen does not pay for loading the native library (NFR-1).
 * Owner: Real-time (#8).
 */
class WebRtcRuntime(context: Context, private val frameSource: CameraFrameSource) : PeerConnectionClient.Factory {

    private val appContext = context.applicationContext

    private val eglBase: EglBase by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
        )
        EglBase.create()
    }

    private val factory: PeerConnectionFactory by lazy {
        val options = PeerConnectionFactory.Options().apply {
            // Android's network monitor only reports networks known to ConnectivityManager, which leaves out the
            // phone's own hotspot interface. A phone that hosts the test hotspot then offers only cellular
            // candidates and never connects. Enumerating interfaces directly includes the hotspot (and Wi-Fi)
            // addresses; the cost is no reaction to network switches, which the session's own grace timer covers.
            disableNetworkMonitor = true
        }
        PeerConnectionFactory.builder()
            .setOptions(options)
            // Hardware H.264 (high profile allowed) and VP8 first, software codecs as the fallback (Design 2.6.4).
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    override val eglContext: EglBase.Context
        get() = eglBase.eglBaseContext

    override fun create(config: PeerConfig): PeerConnectionClient = WebRtcPeerConnectionClient(
        factory,
        config,
        frameSource,
    )
}
