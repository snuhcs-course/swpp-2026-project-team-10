// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui

import android.content.Context
import androidx.annotation.AnimatorRes
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.session.SubjectFragment

/**
 * The host of every screen (Design 2.1, Iter3-A14): Navigation's own, with [ScreenNavigator] as its fragment
 * navigator, so every screen of `nav_graph.xml` comes and goes with the same transitions without a word about them in
 * the graph. The sheets are dialogs, which keep their own slide.
 */
class PixNavHostFragment : NavHostFragment() {
    override fun onCreateNavHostController(navHostController: NavHostController) {
        super.onCreateNavHostController(navHostController)
        // In place of the fragment navigator just added, before the graph attaches it.
        navHostController.navigatorProvider.addNavigator(ScreenNavigator(requireContext(), childFragmentManager, id))
    }
}

/**
 * Navigation's fragment navigator, which gives each move between screens its [ScreenMotion]: forward, between the
 * screen shown and the next, and back, between the next and the screen it will go back to, which is not the screen
 * shown when the move closes that one (`popUpTo`). Back, the system's back gesture, *Back to camera*, and a screen a
 * link opened all take the back transitions, since they pop the back stack. Options that name their own animations
 * keep them, and the first screen comes without any.
 */
@Navigator.Name("fragment")
internal class ScreenNavigator(
    context: Context,
    private val fragments: FragmentManager,
    @param:IdRes private val containerId: Int,
) : FragmentNavigator(context, fragments, containerId) {

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?,
    ) {
        for (entry in entries) super.navigate(listOf(entry), withTransitions(entry, navOptions), navigatorExtras)
    }

    private fun withTransitions(entry: NavBackStackEntry, options: NavOptions?): NavOptions? {
        val beneath = state.backStack.value.lastOrNull() ?: return options
        if (options != null && options.hasAnimations()) return options
        val next = entry.screen
        val shown = fragments.findFragmentById(containerId)?.javaClass?.name ?: beneath.screen
        val forward = ScreenMotion.between(shown, next)
        val back = ScreenMotion.between(next, beneath.screen)
        return NavOptions.Builder()
            .setLaunchSingleTop(options?.shouldLaunchSingleTop() == true)
            .setRestoreState(options?.shouldRestoreState() == true)
            .setPopUpTo(
                options?.popUpToId ?: -1,
                options?.isPopUpToInclusive() == true,
                options?.shouldPopUpToSaveState() == true,
            )
            .setEnterAnim(forward.enter)
            .setExitAnim(forward.exit)
            .setPopEnterAnim(back.backEnter)
            .setPopExitAnim(back.backExit)
            .build()
    }

    private val NavBackStackEntry.screen get() = (destination as Destination).className

    private fun NavOptions.hasAnimations() =
        enterAnim != -1 || exitAnim != -1 || popEnterAnim != -1 || popExitAnim != -1
}

/**
 * How one screen gives way to another (Design 2.1, R&S 6.9), from Material's motion, in 300 ms with its standard
 * curve: the screen going fades out in the first 35 %, and the screen coming fades in through the rest.
 */
internal enum class ScreenMotion(
    @param:AnimatorRes val enter: Int,
    @param:AnimatorRes val exit: Int,
    @param:AnimatorRes val backEnter: Int,
    @param:AnimatorRes val backExit: Int,
) {
    /** To or from a screen of a live image: a fade only, since that image is the world, which does not slide. */
    FADE(R.animator.screen_fade_in, R.animator.screen_fade_out, R.animator.screen_fade_in, R.animator.screen_fade_out),

    /**
     * Between any other two (Material's shared axis along x): the next screen comes 30 dp in from the right and the
     * last goes 30 dp to the left, and back the other way.
     */
    SHARED_AXIS(
        R.animator.screen_axis_in,
        R.animator.screen_axis_out,
        R.animator.screen_axis_back_in,
        R.animator.screen_axis_back_out,
    ),
    ;

    companion object {
        /** The screens of a live image, Camera's preview and Subject view's video, by their class names. */
        val LIVE = setOf(CameraFragment::class.java.name, SubjectFragment::class.java.name)

        /** The motion between the screens of the classes [one] and [other], in either direction. */
        fun between(one: String, other: String) = if (one in LIVE || other in LIVE) FADE else SHARED_AXIS
    }
}
