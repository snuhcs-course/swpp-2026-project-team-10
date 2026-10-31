// AI-generated with Claude Code, 2026-10-31, reviewed by Joonhyung Han
package com.lastpenguin.pix

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.session.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/*
 * The subject's toast about the photographer's photo (FR-6.14), shown by the activity because Subject view may be under
 * another step of the session. Partitions: a photo that fails to save names the photographer.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun `a photo that cannot be saved is a toast with the photographer's name`() {
        val modules = TestAppContainer().install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.viewModel<SessionViewModel>().joinRoom("123456") }
            modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
            idle()

            modules.session.deliver(SessionMessage.PhotoBegin("p1", "webp", 10, 1, 1))
            idle()

            assertEquals(string(R.string.session_photo_save_failed, "Dongje"), ShadowToast.getTextOfLatestToast())
        }
    }
}
