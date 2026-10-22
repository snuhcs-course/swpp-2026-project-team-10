// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
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

    fun string(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    fun bitmap(width: Int = 40, height: Int = 80): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    fun guide(withReference: Boolean = true) = ReferenceGuide(
        id = "guide-1",
        cutout = bitmap(),
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
}
