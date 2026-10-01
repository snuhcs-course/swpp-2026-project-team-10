package com.lastpenguin.pix.session

import android.content.Context
import com.lastpenguin.pix.session.signaling.IceCandidate
import com.lastpenguin.pix.session.signaling.SessionDescription

/**
 * One WebRTC peer connection: the photographer's video track and two data channels,
 * realtime and reliable (Design 2.5.1, 2.6.4). The data channel fields are added with the WebRTC library (#8).
 * Owner: Real-time (#8).
 */
class PeerConnectionClient(private val context: Context) {

    /** Photographer. */
    suspend fun createOffer(): SessionDescription =
        TODO("#8")

    /** Subject, after applying the photographer's offer. */
    suspend fun createAnswer(): SessionDescription =
        TODO("#8")

    suspend fun applyRemote(sdp: SessionDescription) {
        TODO("#8")
    }

    fun addIceCandidate(candidate: IceCandidate) {
        TODO("#8")
    }

    fun close() {
        TODO("#8")
    }
}
