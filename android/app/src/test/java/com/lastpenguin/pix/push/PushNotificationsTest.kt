// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/*
 * Partitions for the notifications pushes ask for (Design 2.6.6; FR-5.7, FR-6.1). Type: an invitation, a friend
 * request, another type, one without its fields. An invitation: 60 s left, expired on its way (at its expiry). The
 * phone: notifications allowed or not, English or Korean.
 */
@RunWith(AndroidJUnit4::class)
class PushNotificationsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun invitation(expiresAt: Long = NOW + 60_000) = mapOf(
        "type" to "invitation",
        "invitationId" to "inv_1",
        "fromName" to "Dongje",
        "expiresAt" to expiresAt.toString(),
    )

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun anInvitation_dropsDownUntilItExpires_andOpensTheInvitationScreen() {
        assertTrue(PushNotifications.show(context, invitation(), NOW))

        val notification = posted().single()
        assertEquals(PushNotifications.INVITATIONS, notification.channelId)
        assertEquals("Dongje invites you to shoot together", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(60_000L, notification.timeoutAfter)
        val opens = shadowOf(notification.contentIntent).savedIntent
        assertEquals(Uri.parse("pix://invite/inv_1"), opens.data)
        assertEquals(MainActivity::class.java.name, opens.component?.className)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(PushNotifications.INVITATIONS).importance,
        )
    }

    @Test
    fun anInvitationThatExpiredOnItsWay_isNotShown() {
        assertFalse(PushNotifications.show(context, invitation(expiresAt = NOW), NOW))
        assertTrue(posted().isEmpty())
    }

    @Test
    fun aFriendRequest_opensFriends() {
        val data = mapOf("type" to "friend.request", "requestId" to "fr_1", "fromName" to "Junhyeong")
        assertTrue(PushNotifications.show(context, data, NOW))

        val notification = posted().single()
        assertEquals(PushNotifications.FRIEND_REQUESTS, notification.channelId)
        assertEquals("Junhyeong sent you a friend request", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Uri.parse("pix://friends"), shadowOf(notification.contentIntent).savedIntent.data)
    }

    @Test
    fun anotherTypeOrAPushWithoutItsFields_isNotShown() {
        assertFalse(PushNotifications.show(context, mapOf("type" to "check"), NOW))
        assertFalse(PushNotifications.show(context, invitation() - "fromName", NOW))
        assertFalse(PushNotifications.show(context, invitation() - "expiresAt", NOW))
        assertTrue(posted().isEmpty())
    }

    @Test
    fun withoutConsentToNotifications_nothingIsShown() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PushNotifications.show(context, invitation(), NOW))
        assertTrue(posted().isEmpty())
    }

    @Test
    @Config(qualifiers = "ko")
    fun aKoreanPhone_getsTheNotificationInKorean() {
        PushNotifications.show(context, invitation(), NOW)
        assertEquals("Dongje님이 카메라 공유에 초대했어요", posted().single().extras.getString(Notification.EXTRA_TITLE))
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
