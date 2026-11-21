// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.generation.ScreenLanguages
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * How a link opens its screen (Design 2.6.6), on the real activity and navigation graph. Partitions: a link that starts
 * Pix in another app's task, such as a browser's, or in a task of its own, such as a notification's (its screen over
 * Camera either way, so Decline returns to Camera); a link while Pix runs (on top of what is shown); the activity
 * recreated after a link started it (the link not opened again). The screens' language for the server's text taken
 * from the activity (FR-10.2). The screens' room: inside the system bars with the keyboard closed, and above the
 * keyboard while it is open, since Android 15 and later no longer shrink the window for it.
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
    fun theScreens_stayInsideTheBars_andAboveAnOpenKeyboard() {
        val root = (start(Intent(Intent.ACTION_MAIN)).get().findViewById<View>(android.R.id.content) as ViewGroup)
            .getChildAt(0)
        fun apply(keyboard: Int) {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, insets)
        }

        apply(keyboard = 0)
        assertEquals(listOf(96, 48), listOf(root.paddingTop, root.paddingBottom))

        apply(keyboard = 900)
        assertEquals(listOf(96, 900), listOf(root.paddingTop, root.paddingBottom))
    }

    @Test
    @Config(qualifiers = "ko")
    fun theActivitysLanguage_becomesTheScreensLanguage() {
        // As on Android 10–12, where Pix's own language reaches the activity but not the application's resources.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val english = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        val languages = ScreenLanguages(english)
        assertEquals("en", languages.current.value)

        TestAppContainer(screenLanguages = languages).install()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()

        assertEquals("ko", languages.current.value)
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
