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
        PeerConnectionFactory.builder()
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
