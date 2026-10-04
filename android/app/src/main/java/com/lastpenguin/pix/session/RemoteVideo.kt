package com.lastpenguin.pix.session

import org.webrtc.EglBase
import org.webrtc.VideoSink

/**
 * The photographer's live video on the subject's phone (FR-6.6). The Subject view attaches its renderer here
 * and detaches it when the view goes away; the session keeps the renderer across reconnects.
 * Owner: Real-time (#8).
 */
interface RemoteVideo {
    /** For `SurfaceViewRenderer.init`. */
    val eglContext: EglBase.Context

    fun attach(sink: VideoSink)

    fun detach(sink: VideoSink)
}
