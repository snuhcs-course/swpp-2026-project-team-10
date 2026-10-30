// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.lastpenguin.pix.account.PushTokens
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * This phone's FCM token from Firebase. The manifest turns Firebase's own start-up registration off, so a phone gets a
 * token only once someone signs in and the registrar asks for one (Design 2.6.6). Owner: Real-time (#119).
 */
class FirebasePushTokens : PushTokens {
    override suspend fun current(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) Log.w("PixPush", "No FCM token", task.exception)
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
}
