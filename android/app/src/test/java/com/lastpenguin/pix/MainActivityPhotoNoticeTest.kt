// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix

import android.Manifest
import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.assertNoticeInTheZoomRowsPlace
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNotice
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNoticeView
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.ScreenTestSupport.windowBox
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.showNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/*
 * The subject's notice about the photographer's photo (FR-6.14), a Snackbar since #298, shown by the activity because Subject view may be under
 * another step of the session. Partitions: a photo that fails to save names the photographer; one that comes while the
 * activity is stopped waits for it. A notice over Camera, shown by the activity, sits in the zoom row's place, 12 dp
 * above the line the row stands on, covering the readout and never the controls under the image (#298); Subject view's
 * is in SubjectFragmentRenderTest.
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
    fun `a notice over Camera takes the zoom row's place, clear of the controls`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // With the camera, so its zoom row shows rather than the camera's message.
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
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
                val controls = FragmentCameraBinding.bind(camera.requireView())
                assertEquals(controls.noticeLine, camera.noticeAnchor)
                val notice = checkNotNull(shownNoticeView())
                assertNoticeInTheZoomRowsPlace(notice, controls.zoomRow, controls.zoomReadout, controls.noticeLine)
                assertTrue("clear of the controls", windowBox(notice).bottom <= windowBox(controls.bottomPanel).top)
            }
        }
    }
}
