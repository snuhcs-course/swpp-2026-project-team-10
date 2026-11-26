// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.settings

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.settings.AppLanguage
import com.lastpenguin.pix.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/*
 * Whether the media access for deleting was offered, as the photo viewer asks it (FR-1.16): the stored answer unread
 * (counted as offered, so the offer never comes twice after a start), stored as not offered (not yet), and offered now
 * (at once, before the store has it, and stored).
 */
@RunWith(AndroidJUnit4::class)
class MediaAccessOfferTest {
    private fun model(settings: AppSettings) = SettingsViewModel(settings, AppLanguage.InMemory(), FakeAuthRepository())

    @Test
    fun anUnreadAnswer_countsAsOffered() {
        val unread = object : AppSettings by AppSettings.InMemory() {
            override val mediaAccessOffered: StateFlow<Boolean?> = MutableStateFlow(null)
        }

        assertTrue(model(unread).mediaAccessOffered())
    }

    @Test
    fun notOfferedYet_untilItIs_thenAtOnceAndStored() {
        val settings = AppSettings.InMemory()
        val model = model(settings)
        assertFalse(model.mediaAccessOffered())

        model.markMediaAccessOffered()

        assertTrue(model.mediaAccessOffered())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(settings.mediaAccessOffered.value == true)
    }
}
