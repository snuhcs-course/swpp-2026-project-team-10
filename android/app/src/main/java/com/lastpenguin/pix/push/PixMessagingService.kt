// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.push

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lastpenguin.pix.PixApplication

/**
 * Receives Pix's pushes from Firebase Cloud Messaging, in every app state (Design 2.6.6). Its callbacks run on a
 * background thread. Owner: Real-time (#119).
 */
class PixMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.show(this, message.data)
    }

    /**
     * A running Pix registers the new token at once; a process started only for this has no modules yet, and the
     * registrar registers the current token when they are made, at the next start.
     */
    override fun onNewToken(token: String) {
        val app = application as PixApplication
        Handler(Looper.getMainLooper()).post { app.createdContainer?.pushTokenRegistrar?.onNewToken(token) }
    }
}
