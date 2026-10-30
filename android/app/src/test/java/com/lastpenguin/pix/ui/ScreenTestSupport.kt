// AI-generated with Claude Code, 2026-10-30, reviewed by Joonhyung Han, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.ReferenceGuide
import org.robolectric.Shadows.shadowOf

/** Shared helpers of the screen tests: the main looper, strings, a guide with real bitmaps, and a NavController. */
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
}
