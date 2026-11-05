// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.Navigator
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.MainActivity
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.settings.SettingsFragment
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * The transitions between screens (Design 2.1, R&S 6.9, Iter3-A14) on the real activity and navigation graph, with the
 * display's frames paused where a test looks at a transition's start, since idle() plays each to its end (Testing
 * 2.1.8).
 * Every destination (equivalence partitioning): a screen of a live image, any other screen, or a sheet. KINDS names
 * each, so a destination added to the graph fails here until it is named, and then gets the transitions of its kind.
 * Pairs: to or from a live image fades in place; two other screens share the x axis, the next coming from the right
 * and, going back, the last coming from the left; a sheet leaves the screen under it as it is.
 * Back: the system's Back, a screen's own Back, and *Back to camera* from two screens deep, which pops both at once; a
 * move that closes the screen shown (popUpTo) goes back with the screen beneath it, not the one it closed; a link's
 * screen comes and goes as any other. A tap during a transition acts at once. Each transition lasts 300 ms or less,
 * and with the phone's animations off each screen is at its end at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class ScreenTransitionsTest {
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
        controller?.pause()?.stop()?.destroy()
    }

    private fun start(): ActivityController<MainActivity> {
        TestAppContainer().also { it.auth.signInAs(it.auth.account) }.install()
        return Robolectric.buildActivity(MainActivity::class.java).setup().also {
            controller = it
            idle()
        }
    }

    private val MainActivity.navHost get() = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment

    private val MainActivity.nav: NavController get() = navHost.navController

    /** The view of the screen shown, or coming. */
    private fun MainActivity.shown(): View = navHost.childFragmentManager.primaryNavigationFragment!!.requireView()

    private fun MainActivity.go(@IdRes action: Int) {
        nav.navigate(action)
        idle()
    }

    private fun MainActivity.back() {
        onBackPressedDispatcher.onBackPressed()
        idle()
    }

    /** Lets the display's frames go until the transition under way has ended. */
    private fun play() {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TRANSITION_MS + 100))
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun slide() = controller!!.get().resources.getDimension(R.dimen.screen_slide)

    /** [view] at the start of its transition in: not shown yet, [from] its place along x. */
    private fun assertComing(view: View, from: Float) {
        assertEquals("alpha", 0f, view.alpha, 0f)
        assertEquals("x", from, view.translationX, 0.5f)
    }

    /** [view] at the start of its transition out: still drawn, in its place. */
    private fun assertGoing(view: View) {
        assertTrue(view.isAttachedToWindow)
        assertEquals("alpha", 1f, view.alpha, 0f)
        assertEquals("x", 0f, view.translationX, 0f)
    }

    /** [view] at its transition's end. */
    private fun assertIn(view: View) {
        assertEquals("alpha", 1f, view.alpha, 0f)
        assertEquals("x", 0f, view.translationX, 0f)
    }

    @Test
    fun everyDestination_isNamed_andGetsTheTransitionsOfItsKind() {
        val graph = start().get().nav.graph
        assertEquals(KINDS.keys, graph.map { it.id }.toSet())

        val camera = CameraFragment::class.java.name
        val settings = SettingsFragment::class.java.name
        for (destination in graph) {
            val name = destination.label
            when (KINDS.getValue(destination.id)) {
                Kind.SHEET -> assertTrue("$name", destination is DialogFragmentNavigator.Destination)

                Kind.LIVE -> {
                    val screen = (destination as FragmentNavigator.Destination).className
                    assertEquals("$name", ScreenMotion.FADE, ScreenMotion.between(camera, screen))
                    assertEquals("$name", ScreenMotion.FADE, ScreenMotion.between(settings, screen))
                }

                Kind.SCREEN -> {
                    val screen = (destination as FragmentNavigator.Destination).className
                    assertEquals("$name", ScreenMotion.FADE, ScreenMotion.between(camera, screen))
                    assertEquals("$name", ScreenMotion.SHARED_AXIS, ScreenMotion.between(settings, screen))
                    assertEquals("$name", ScreenMotion.SHARED_AXIS, ScreenMotion.between(screen, settings))
                }
            }
        }
    }

    @Test
    fun everyTransition_lastsAtMost300ms() {
        val context = start().get()
        for (motion in ScreenMotion.entries) {
            for (animator in listOf(motion.enter, motion.exit, motion.backEnter, motion.backExit)) {
                val length = AnimatorInflater.loadAnimator(context, animator).totalDuration
                assertTrue("$motion: $length ms", length in 1..300)
            }
        }
    }

    @Test
    fun theActivitysScreens_allGoThroughTheScreenNavigator() {
        val pix = start().get()

        assertTrue(pix.navHost is PixNavHostFragment)
        assertTrue(pix.nav.navigatorProvider.getNavigator<Navigator<*>>("fragment") is ScreenNavigator)
    }

    @Test
    fun cameraAndAnotherScreen_fadeInPlace_bothWays() {
        val pix = start().get()
        val camera = pix.shown()
        ShadowChoreographer.setPaused(true)

        pix.go(R.id.action_camera_to_settings)

        val settings = pix.shown()
        assertComing(settings, from = 0f)
        assertGoing(camera)
        play()
        assertIn(settings)
        assertFalse(camera.isAttachedToWindow)

        ShadowChoreographer.setPaused(true)
        pix.back()

        val cameraAgain = pix.shown()
        assertComing(cameraAgain, from = 0f)
        assertGoing(settings)
        play()
        assertIn(cameraAgain)
        assertFalse(settings.isAttachedToWindow)
    }

    @Test
    fun twoOtherScreens_shareTheXAxis_theNextFromTheRight_andBackFromTheLeft() {
        val pix = start().get()
        pix.go(R.id.action_camera_to_settings)
        val settings = pix.shown()
        ShadowChoreographer.setPaused(true)

        pix.go(R.id.action_settings_to_about)

        val about = pix.shown()
        assertComing(about, from = slide())
        assertGoing(settings)
        play()
        assertIn(about)

        ShadowChoreographer.setPaused(true)
        pix.back()

        val settingsAgain = pix.shown()
        assertComing(settingsAgain, from = -slide())
        assertGoing(about)
        play()
        assertIn(settingsAgain)
        assertFalse(about.isAttachedToWindow)
    }

    @Test
    fun aMoveThatClosesTheScreenShown_goesBackWithTheScreenBeneath() {
        val pix = start().get()
        pix.go(R.id.joinCodeFragment)
        ShadowChoreographer.setPaused(true)

        // See their camera closes itself for Session not found: the two share the x axis.
        pix.go(R.id.action_joinCode_to_sessionNotFound)
        val notFound = pix.shown()
        assertComing(notFound, from = slide())
        play()

        // Its Back goes to Camera, which was beneath See their camera: a fade, not a slide back.
        ShadowChoreographer.setPaused(true)
        FragmentFailureBinding.bind(notFound).secondaryButton.performClick()
        idle()
        assertEquals(R.id.cameraFragment, pix.nav.currentDestination?.id)
        assertComing(pix.shown(), from = 0f)
        assertGoing(notFound)
    }

    @Test
    fun backToCamera_fromTwoScreensDeep_fadesCameraIn() {
        val pix = start().get()
        pix.go(R.id.action_camera_to_settings)
        pix.go(R.id.action_settings_to_about)
        val about = pix.shown()
        ShadowChoreographer.setPaused(true)

        pix.nav.popBackStack(R.id.cameraFragment, false)
        idle()

        assertComing(pix.shown(), from = 0f)
        assertTrue(about.isAttachedToWindow)
        play()
        assertIn(pix.shown())
        assertFalse(about.isAttachedToWindow)
    }

    @Test
    fun aLinksScreen_fadesInOverCamera_andBackFadesCameraIn() {
        val pix = start()
        ShadowChoreographer.setPaused(true)

        pix.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("pix://invite/inv_1")))
        idle()

        val invitation = pix.get().shown()
        assertComing(invitation, from = 0f)
        play()
        assertIn(invitation)

        ShadowChoreographer.setPaused(true)
        pix.get().back()
        assertComing(pix.get().shown(), from = 0f)
        assertGoing(invitation)
    }

    @Test
    fun aSheet_leavesTheScreenUnderItAsItIs() {
        val pix = start().get()
        val camera = pix.shown()
        ShadowChoreographer.setPaused(true)

        pix.go(R.id.action_camera_to_addGuide)

        assertSame(camera, pix.navHost.childFragmentManager.primaryNavigationFragment!!.view)
        assertIn(camera)
    }

    @Test
    fun aTapDuringATransition_actsAtOnce() {
        val pix = start().get()
        pix.go(R.id.action_camera_to_settings)
        ShadowChoreographer.setPaused(true)
        pix.go(R.id.action_settings_to_about)
        assertEquals(0f, pix.shown().alpha, 0f)

        // About Pix's Back, before it has faded in.
        pix.shown().findViewById<View>(R.id.backButton).performClick()
        idle()

        assertEquals(R.id.settingsFragment, pix.nav.currentDestination?.id)
    }

    @Test
    fun withAnimationsOff_eachScreenIsAtItsEndAtOnce() {
        val pix = start().get()
        setAnimatorScale(0f)
        // Nothing may wait for a display frame.
        ShadowChoreographer.setPaused(true)

        pix.go(R.id.action_camera_to_settings)
        assertIn(pix.shown())
        pix.go(R.id.action_settings_to_about)
        assertIn(pix.shown())
        pix.back()
        assertIn(pix.shown())
        pix.back()
        assertIn(pix.shown())
        assertEquals(R.id.cameraFragment, pix.nav.currentDestination?.id)
    }

    private enum class Kind { LIVE, SCREEN, SHEET }

    private companion object {
        /** The longest transition. */
        const val TRANSITION_MS = 300L

        /** Every destination of the graph and its kind (Design 2.1). */
        val KINDS = mapOf(
            R.id.cameraFragment to Kind.LIVE,
            R.id.settingsFragment to Kind.SCREEN,
            R.id.helpFragment to Kind.SCREEN,
            R.id.aboutFragment to Kind.SCREEN,
            R.id.addGuideSheet to Kind.SHEET,
            R.id.libraryFragment to Kind.SCREEN,
            R.id.referenceConfirmFragment to Kind.SCREEN,
            R.id.noPersonFoundFragment to Kind.SCREEN,
            R.id.describePoseFragment to Kind.SCREEN,
            R.id.generatingFragment to Kind.SCREEN,
            R.id.pickPoseFragment to Kind.SCREEN,
            R.id.generationFailedFragment to Kind.SCREEN,
            R.id.friendsFragment to Kind.SCREEN,
            R.id.addFriendFragment to Kind.SCREEN,
            R.id.sentRequestsFragment to Kind.SCREEN,
            R.id.signInFragment to Kind.SCREEN,
            R.id.signUpFragment to Kind.SCREEN,
            R.id.pixIdSetupFragment to Kind.SCREEN,
            R.id.accountSheet to Kind.SHEET,
            R.id.shootTogetherSheet to Kind.SHEET,
            R.id.roomCodeSheet to Kind.SHEET,
            R.id.inviteSentSheet to Kind.SHEET,
            R.id.inviteNotAcceptedFragment to Kind.SCREEN,
            R.id.invitationFragment to Kind.SCREEN,
            R.id.joinCodeFragment to Kind.SCREEN,
            R.id.sessionNotFoundFragment to Kind.SCREEN,
            R.id.subjectFragment to Kind.LIVE,
            R.id.subjectDescribeFragment to Kind.SCREEN,
            R.id.subjectGeneratingFragment to Kind.SCREEN,
            R.id.subjectPickPoseFragment to Kind.SCREEN,
            R.id.subjectConfirmFragment to Kind.SCREEN,
            R.id.connectionLostFragment to Kind.SCREEN,
        )
    }
}
