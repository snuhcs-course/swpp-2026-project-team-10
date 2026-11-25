// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.R as materialR
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.settings.CoachMarkLayout
import com.lastpenguin.pix.ui.settings.TutorialFragment
import kotlinx.coroutines.launch
import org.robolectric.Shadows.shadowOf

/**
 * Shared helpers of the screen tests: the main looper, strings, a guide with real bitmaps, a NavController, and the
 * tutorial's tips over a screen.
 */
object ScreenTestSupport {
    /** Runs the main looper: ViewModel coroutines on Dispatchers.Main and the posted UI work. */
    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * Runs the main looper until [done], for work that finishes on another thread and posts its result, such as a
     * `ListAdapter` diffing a new list after its first one. Gives up after [timeoutMs]; the caller then asserts.
     */
    fun idleUntil(timeoutMs: Long = 2_000, done: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        idle()
        while (!done() && System.nanoTime() < deadline) {
            Thread.sleep(5)
            idle()
        }
    }

    fun string(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    fun bitmap(width: Int = 40, height: Int = 80): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /**
     * The text of the notice on the screen now (`showNotice`, #298), the newest when one is going as another comes, or
     * null with none: a Snackbar in any window, the activity's or a sheet's. It stays while it shows, so a test that
     * moves the clock past its length sees it go, as on a phone.
     */
    fun shownNotice(): String? = notices().lastOrNull()?.findViewById<TextView>(materialR.id.snackbar_text)
        ?.text?.toString()

    /** The action button of the notice on the screen now, such as Undo, or null with none or one without an action. */
    fun noticeAction(): Button? = notices().lastOrNull()?.findViewById<Button>(materialR.id.snackbar_action)
        ?.takeIf { it.isShown }

    /** Every notice on the screen, oldest first, from every window's views (WindowManagerGlobal keeps them in order). */
    private fun notices(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val roots = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
        val found = mutableListOf<View>()
        fun walk(view: View) {
            if (!view.isShown) return
            if (view is Snackbar.SnackbarLayout) {
                found += view
                return
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        roots.filterIsInstance<View>().forEach(::walk)
        return found
    }

    /** The screen whose tutorial tips are over [host], or null with none there (FR-10.4). */
    fun tutorialOver(host: Fragment): TutorialScreen? = TutorialFragment.over(host)?.screen

    /** The tips' layer over [host], with its rings and captions, or null with none there. */
    fun tipsOver(host: Fragment): CoachMarkLayout? = TutorialFragment.over(host)?.view as? CoachMarkLayout

    /** Taps the tips over [host] anywhere, which closes them. */
    fun closeTips(host: Fragment) {
        checkNotNull(tipsOver(host)).performClick()
    }

    fun guide(withReference: Boolean = true) = ReferenceGuide(
        id = "guide-1",
        mask = bitmap(),
        outline = bitmap(),
        aspect = 0.5f,
        source = GuideSource.GALLERY,
        reference = if (withReference) bitmap(60, 80) else null,
    )

    /**
     * A NavController on the app's graph, standing at [destination] with the Camera screen (the start) below it, so
     * the screen's `navigate` and `popBackStack(R.id.cameraFragment)` can be checked on `currentDestination`.
     */
    fun <F : Fragment> FragmentScenario<F>.attachNav(@IdRes destination: Int): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        onFragment { fragment ->
            nav.setGraph(R.navigation.nav_graph)
            nav.setCurrentDestination(destination)
            Navigation.setViewNavController(fragment.requireView(), nav)
        }
        return nav
    }

    /**
     * Launches [F] stopped at CREATED, where the Fragment has an activity but no view yet: the NavController goes
     * on the activity's content view, which the Fragment's `findNavController` reaches through its view's parents,
     * and [prepare] puts the activity's ViewModels in the state the screen renders from. Then the screen is resumed,
     * so both a screen that reads its ViewModel once in `onViewCreated` and one that collects a flow see that state.
     */
    inline fun <reified F : Fragment> launchPrepared(
        @IdRes destination: Int,
        args: Bundle? = null,
        crossinline prepare: (FragmentActivity) -> Unit,
    ): Pair<FragmentScenario<F>, TestNavHostController> {
        val scenario = launchFragmentInContainer<F>(
            fragmentArgs = args,
            themeResId = R.style.Theme_Pix,
            initialState = Lifecycle.State.CREATED,
        )
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        scenario.onFragment { fragment ->
            val activity = fragment.requireActivity()
            nav.setGraph(R.navigation.nav_graph)
            // The arguments go to the destination too, which refuses to open without a required one.
            nav.setCurrentDestination(destination, args ?: Bundle())
            Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
            prepare(activity)
        }
        idle()
        scenario.moveToState(Lifecycle.State.RESUMED)
        idle()
        return scenario to nav
    }

    /** The activity-scoped ViewModel of [activity], the one its screens share. */
    inline fun <reified VM : androidx.lifecycle.ViewModel> FragmentActivity.viewModel(): VM =
        androidx.lifecycle.ViewModelProvider(this, PixViewModels.Factory)[VM::class.java]

    /**
     * *Invite* on Friends, which takes the result while the invitation is on its way and then goes, as for *Invite
     * sent*: an invitation answered with no such screen open is cancelled (`SessionViewModel.inviteResults`).
     */
    fun FragmentActivity.inviteFromFriends(friend: PixUser) {
        val sessionViewModel = viewModel<SessionViewModel>()
        val friends = lifecycleScope.launch { sessionViewModel.inviteResults.collect {} }
        sessionViewModel.invite(friend)
        idle()
        friends.cancel()
    }
}
