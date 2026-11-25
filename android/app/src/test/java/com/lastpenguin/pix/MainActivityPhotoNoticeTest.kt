// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.R as materialR
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNotice
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.showNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The subject's notice about the photographer's photo (FR-6.14), a Snackbar since #298, shown by the activity because Subject view may be under
 * another step of the session. Partitions: a photo that fails to save names the photographer; one that comes while the
 * activity is stopped waits for it; the notice over Camera sits above its controls, Camera's notice anchor (#298).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityPhotoNoticeTest {

    @Test
    fun `a photo that cannot be saved is a notice with the photographer's name`() {
        val modules = TestAppContainer().install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.viewModel<SessionViewModel>().joinRoom("123456") }
            modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
            idle()

            modules.session.deliver(SessionMessage.PhotoBegin("p1", "webp", 10, 1, 1))
            idle()

            assertEquals(string(R.string.session_photo_save_failed, "Dongje"), shownNotice())
        }
    }

    @Test
    fun `a photo that comes while the activity is stopped is a notice when it starts again`() {
        val modules = TestAppContainer().install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.viewModel<SessionViewModel>().joinRoom("123456") }
            modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
            idle()
            scenario.moveToState(Lifecycle.State.CREATED)

            modules.session.deliver(SessionMessage.PhotoBegin("p1", "webp", 10, 1, 1))
            idle()
            assertNull(shownNotice())

            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()
            assertEquals(string(R.string.session_photo_save_failed, "Dongje"), shownNotice())
        }
    }

    @Test
    fun `a notice over Camera sits above its controls`() {
        val modules = TestAppContainer().install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.viewModel<SessionViewModel>().joinRoom("123456") }
            modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.PHOTOGRAPHER))
            idle()

            scenario.onActivity { it.showNotice("A notice") }
            idle()

            assertEquals("A notice", shownNotice())
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.primaryNavigationFragment!!
                val camera = host.childFragmentManager.primaryNavigationFragment as CameraFragment
                val anchor = checkNotNull(camera.noticeAnchor)
                val notice = checkNotNull(activity.window.decorView.findViewById<View>(materialR.id.snackbar_text))
                    .parent.parent as View
                val noticeAt = IntArray(2).also { notice.getLocationInWindow(it) }
                val anchorAt = IntArray(2).also { anchor.getLocationInWindow(it) }
                assertTrue("the notice above Camera's controls", noticeAt[1] + notice.height <= anchorAt[1])
            }
        }
    }
}
