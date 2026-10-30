// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo
package com.lastpenguin.pix

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.GraphicsMode

/*
 * How a link opens its screen (Design 2.6.6), on the real activity and navigation graph. Partitions: a link that starts
 * Pix in another app's task, such as a browser's, or in a task of its own, such as a notification's (its screen over
 * Camera either way, so Decline returns to Camera); a link while Pix runs (on top of what is shown); the activity
 * recreated after a link started it (the link not opened again).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    private var controller: ActivityController<MainActivity>? = null
    private lateinit var modules: TestAppContainer

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun start(intent: Intent): ActivityController<MainActivity> {
        modules = TestAppContainer().also { it.auth.signInAs(it.auth.account) }.install()
        return Robolectric.buildActivity(MainActivity::class.java, intent).setup().also {
            controller = it
            idle()
        }
    }

    private fun link() = Intent(Intent.ACTION_VIEW, Uri.parse("pix://invite/inv_1"))

    private val ActivityController<MainActivity>.nav: NavController
        get() = (get().supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController

    /** The screens on the back stack, bottom first, without the graph itself. */
    private fun ActivityController<MainActivity>.screens() =
        nav.currentBackStack.value.map { it.destination.id }.filter { it != R.id.nav_graph }

    private fun ActivityController<MainActivity>.shown(): FragmentFailureBinding {
        val navHost = get().supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        return FragmentFailureBinding.bind(navHost.childFragmentManager.primaryNavigationFragment!!.requireView())
    }

    @Test
    fun aLinkThatStartsPix_opensItsScreenOverCamera_inAnotherAppsTaskOrItsOwn() {
        for (flags in listOf(0, Intent.FLAG_ACTIVITY_NEW_TASK)) {
            val pix = start(link().addFlags(flags))

            assertEquals("flags $flags", listOf(R.id.cameraFragment, R.id.invitationFragment), pix.screens())
            pix.shown().secondaryButton.performClick() // Decline
            idle()
            assertEquals("flags $flags", listOf(R.id.cameraFragment), pix.screens())
            assertEquals("decline:inv_1", modules.invitations.calls.last())
            tearDown()
        }
        controller = null
    }

    @Test
    fun aLinkWhilePixRuns_opensItsScreenOnTop() {
        val pix = start(Intent(Intent.ACTION_MAIN))
        assertEquals(listOf(R.id.cameraFragment), pix.screens())

        pix.newIntent(link())
        idle()

        assertEquals(listOf(R.id.cameraFragment, R.id.invitationFragment), pix.screens())
    }

    @Test
    fun theActivityRecreated_afterALinkStartedPix_doesNotOpenItAgain() {
        val pix = start(link())

        pix.recreate()
        idle()

        assertEquals(listOf(R.id.cameraFragment, R.id.invitationFragment), pix.screens())
    }
}
