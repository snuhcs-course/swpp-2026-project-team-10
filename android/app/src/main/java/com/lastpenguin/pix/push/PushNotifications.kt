// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lastpenguin.pix.MainActivity
import com.lastpenguin.pix.R

/**
 * The notifications that pushes ask for (Design 2.6.6): an invitation, which opens the *Invitation* screen, and a friend
 * request, which opens *Friends* (FR-5.7). Pushes are FCM data messages, so Pix writes every notification itself, in
 * the phone's language, whether Pix is closed, in the background, or in front. Owner: Real-time (#119).
 */
object PushNotifications {
    const val INVITATIONS = "invitations"
    const val FRIEND_REQUESTS = "friend_requests"

    /** What a notification opens, through the navigation graph's deep links. */
    fun invitationUri(invitationId: String): Uri = Uri.parse("pix://invite/$invitationId")

    val FRIENDS_URI: Uri = Uri.parse("pix://friends")

    /**
     * Shows the notification [data] asks for, and returns whether it did: not for a push of another type, an
     * invitation that expired on its way, or a phone where Pix may not show notifications.
     */
    fun show(context: Context, data: Map<String, String>, now: Long = System.currentTimeMillis()): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val (id, notification) = when (data["type"]) {
            "invitation" -> invitation(context, data, now)
            "friend.request" -> friendRequest(context, data)
            else -> null
        } ?: return false
        createChannels(context)
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    private fun invitation(context: Context, data: Map<String, String>, now: Long): Pair<Int, Notification>? {
        val invitationId = data["invitationId"] ?: return null
        val name = data["fromName"] ?: return null
        val left = (data["expiresAt"]?.toLongOrNull() ?: return null) - now
        if (left <= 0) return null
        val notification = NotificationCompat.Builder(context, INVITATIONS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.invitation_notification, name))
            .setContentText(context.getString(R.string.invitation_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            // Gone once the invitation can no longer be accepted (FR-6.3).
            .setTimeoutAfter(left)
            .setContentIntent(open(context, invitationUri(invitationId)))
            .build()
        return invitationId.hashCode() to notification
    }

    private fun friendRequest(context: Context, data: Map<String, String>): Pair<Int, Notification>? {
        val requestId = data["requestId"] ?: return null
        val name = data["fromName"] ?: return null
        val notification = NotificationCompat.Builder(context, FRIEND_REQUESTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.friend_request_notification, name))
            .setContentText(context.getString(R.string.friend_request_notification_body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(open(context, FRIENDS_URI))
            .build()
        return requestId.hashCode() to notification
    }

    /** Opens [uri] in the running Pix, on top of what it shows, or starts Pix with it (MainActivity is single-top). */
    private fun open(context: Context, uri: Uri): PendingIntent = PendingIntent.getActivity(
        context,
        uri.hashCode(),
        Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                // High, so an invitation drops down over whatever is on screen: it lasts only a minute.
                NotificationChannel(
                    INVITATIONS,
                    context.getString(R.string.invitations_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    FRIEND_REQUESTS,
                    context.getString(R.string.friend_requests_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }
}
