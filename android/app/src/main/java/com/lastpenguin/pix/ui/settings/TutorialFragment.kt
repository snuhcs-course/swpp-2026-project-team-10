// AI-generated with Claude Code, 2026-11-04, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.GuideOverlayView
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * One control a screen's tips point at: what it does, where the caption goes, whether its arrow may pass over the
 * screen's other controls, and where the control is on screen in the screen's view, or null while it is not shown.
 */
internal class TutorialTip(
    @param:StringRes val caption: Int,
    val side: CaptionSide,
    val align: CaptionAlign = CaptionAlign.CENTER,
    val overControls: Boolean = false,
    val locate: (root: View) -> Rect?,
)

/** A screen's tips: an optional title, and its controls. */
internal class TutorialTips(@param:StringRes val title: Int?, val tips: List<TutorialTip>)

/** Where [view] is on screen, if it shows. */
internal fun boundsOnScreen(view: View): Rect? {
    if (!view.isShown || view.width <= 0 || view.height <= 0) return null
    val at = IntArray(2).also(view::getLocationOnScreen)
    return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
}

/** The control [id] of the screen, while it shows. */
private fun control(@IdRes id: Int): (View) -> Rect? = { root -> root.findViewById<View>(id)?.let(::boundsOnScreen) }

/** The controls [ids] of the screen together, those it shows. */
private fun controls(@IdRes vararg ids: Int): (View) -> Rect? = { root ->
    ids.toList().mapNotNull { id -> root.findViewById<View>(id)?.let(::boundsOnScreen) }
        .reduceOrNull { all, next -> Rect(all).apply { union(next) } }
}

/** A touch point on the middle of the guide as drawn on the camera image, while one is: where to drag it. */
private fun guideOn(@IdRes overlay: Int): (View) -> Rect? = { root ->
    val view = root.findViewById<GuideOverlayView>(overlay)
    val bounds = view?.takeIf { it.isShown }?.drawnGuideBounds()
    if (view == null || bounds == null) {
        null
    } else {
        val at = IntArray(2).also(view::getLocationOnScreen)
        val half = (TOUCH_POINT_DP * view.resources.displayMetrics.density / 2).toInt()
        val x = at[0] + bounds.centerX().toInt()
        val y = at[1] + bounds.centerY().toInt()
        Rect(x - half, y - half, x + half, y + half)
    }
}

/** The touch point's size on the guide. */
private const val TOUCH_POINT_DP = 56

/** The tips of each screen (FR-10.4), in the order they are placed: a caption placed first keeps the nearest place. */
internal val TUTORIAL_TIPS: Map<TutorialScreen, TutorialTips> = mapOf(
    TutorialScreen.CAMERA to TutorialTips(
        title = null,
        tips = listOf(
            TutorialTip(R.string.tutorial_settings, CaptionSide.BELOW, locate = control(R.id.settingsButton)),
            TutorialTip(R.string.tutorial_together, CaptionSide.BELOW, locate = control(R.id.shootTogetherButton)),
            TutorialTip(R.string.tutorial_guide, CaptionSide.ABOVE, locate = control(R.id.guideButton)),
            TutorialTip(R.string.tutorial_switch, CaptionSide.ABOVE, locate = control(R.id.switchCameraButton)),
            TutorialTip(R.string.tutorial_shutter, CaptionSide.ABOVE, locate = control(R.id.shutterButton)),
        ),
    ),
    TutorialScreen.ADD_GUIDE to TutorialTips(
        title = null,
        tips = listOf(
            TutorialTip(R.string.tutorial_upload, CaptionSide.ABOVE, locate = control(R.id.uploadButton)),
            TutorialTip(
                R.string.tutorial_generate,
                CaptionSide.ABOVE,
                CaptionAlign.END,
                overControls = true,
                locate = control(R.id.generateButton),
            ),
            TutorialTip(
                R.string.tutorial_saved,
                CaptionSide.ABOVE,
                CaptionAlign.START,
                overControls = true,
                locate = controls(R.id.savedGuidesTitle, R.id.savedGuidesScroll),
            ),
        ),
    ),
    TutorialScreen.GUIDE_ON_CAMERA to TutorialTips(
        title = null,
        tips = listOf(
            TutorialTip(R.string.tutorial_move, CaptionSide.ABOVE, locate = guideOn(R.id.guideOverlay)),
            TutorialTip(R.string.tutorial_edit, CaptionSide.ABOVE, locate = control(R.id.editGuideButton)),
            TutorialTip(R.string.tutorial_save, CaptionSide.ABOVE, locate = control(R.id.saveGuideButton)),
            TutorialTip(
                R.string.tutorial_fade,
                CaptionSide.ABOVE,
                locate = controls(R.id.hideGuideButton, R.id.opacitySlider, R.id.opacityValue),
            ),
        ),
    ),
    TutorialScreen.GENERATE_POSES to TutorialTips(
        title = null,
        tips = listOf(
            TutorialTip(R.string.tutorial_scene, CaptionSide.BELOW, locate = control(R.id.sceneImage)),
            TutorialTip(R.string.tutorial_describe, CaptionSide.ABOVE, locate = control(R.id.descriptionInput)),
            TutorialTip(R.string.tutorial_suggest, CaptionSide.ABOVE, locate = control(R.id.suggestButton)),
        ),
    ),
    TutorialScreen.SUBJECT_VIEW to TutorialTips(
        title = R.string.tutorial_friend_title,
        tips = listOf(
            TutorialTip(R.string.tutorial_friend_guide, CaptionSide.START, locate = control(R.id.guideButton)),
            TutorialTip(R.string.tutorial_friend_zoom, CaptionSide.ABOVE, locate = control(R.id.zoomReadout)),
            TutorialTip(
                R.string.tutorial_friend_brightness,
                CaptionSide.ABOVE,
                locate = control(R.id.brightnessButton),
            ),
            TutorialTip(R.string.tutorial_friend_move, CaptionSide.ABOVE, locate = control(R.id.editGuideButton)),
        ),
    ),
)

/**
 * The first-use tutorial (FR-10.4, Design 2.6.8): over a screen, the first time it opens, a dimming with a ring and a
 * caption for each control of it the screen shows ([TUTORIAL_TIPS]), drawn by [CoachMarkLayout] over the live screen,
 * so it lines up on every phone and *Got it* leaves exactly what was under it. It is a full-screen dialog among the
 * screen's child fragments, not a destination of its own, so the camera and a session's video run on under it, a sheet
 * stays open, and the screen still navigates. *Got it* and Back close it, and the screen's tips count as seen.
 * Owner: Settings (#123).
 */
class TutorialFragment : AppCompatDialogFragment(R.layout.fragment_tutorial) {

    private val settings: SettingsViewModel by activityViewModels { PixViewModels.Factory }

    /** The screen these tips are of. */
    internal val screen by lazy { TutorialScreen.valueOf(requireArguments().getString(ARG_SCREEN)!!) }

    private var watch: Pair<View, ViewTreeObserver.OnPreDrawListener>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Pix_Tutorial)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = super.onCreateDialog(savedInstanceState).apply {
        // Over the whole screen, the system bars included, so the dimming covers what the screen draws there.
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layout = view as CoachMarkLayout
        val tips = TUTORIAL_TIPS.getValue(screen)
        val screenView = requireParentFragment().requireView()
        layout.show(
            tips.title?.let(::getString),
            tips.tips.map { CoachMarkLayout.Mark(getString(it.caption), it.side, it.align, it.overControls) },
        )
        layout.locate = {
            val at = IntArray(2).also(layout::getLocationOnScreen)
            tips.tips.map { tip ->
                tip.locate(screenView)?.let { RectF(it).apply { offset(-at[0].toFloat(), -at[1].toFloat()) } }
            }
        }
        // The screen's controls move as it lays out, animates, or changes; the rings follow them.
        val follow = ViewTreeObserver.OnPreDrawListener {
            layout.refresh()
            true
        }
        screenView.viewTreeObserver.addOnPreDrawListener(follow)
        watch = screenView to follow
        layout.findViewById<View>(R.id.gotItButton).setOnClickListener {
            seen()
            dismiss()
        }
    }

    override fun onDestroyView() {
        watch?.let { (screenView, follow) -> screenView.viewTreeObserver.removeOnPreDrawListener(follow) }
        watch = null
        super.onDestroyView()
    }

    /** Back, on the dialog. */
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        seen()
    }

    private fun seen() = settings.tutorialSeen(setOf(screen))

    companion object {
        /** A moment of the camera's picture, or the friend's video, before the tips cover it (FR-1.1). */
        const val AFTER_PICTURE_MS = 800L

        private const val TAG = "tutorial"
        private const val ARG_SCREEN = "screen"

        /** Shows [screen]'s tips over [host], unless tips are already there. */
        fun show(host: Fragment, screen: TutorialScreen) {
            val fragments = host.childFragmentManager
            if (fragments.findFragmentByTag(TAG) != null || fragments.isStateSaved) return
            TutorialFragment().apply { arguments = argumentsFor(screen) }.show(fragments, TAG)
        }

        /** The arguments of the tips of [screen]. */
        internal fun argumentsFor(screen: TutorialScreen) = bundleOf(ARG_SCREEN to screen.name)

        /** The tips over [host], if they are there. */
        internal fun over(host: Fragment): TutorialFragment? =
            host.childFragmentManager.findFragmentByTag(TAG) as? TutorialFragment
    }
}

/** How often [showTutorialWhenDue] checks again whether tips that are due may show. */
internal const val RECHECK_MS = 250L

/** [screen]'s tips while they are due and [ready], for [showTutorialWhenDue]. */
internal fun SettingsViewModel.tutorialFor(
    screen: TutorialScreen,
    ready: Flow<Boolean> = flowOf(true),
): Flow<TutorialScreen?> = combine(tutorialDue(screen), ready) { due, now -> screen.takeIf { due && now } }

/**
 * Shows the tips [due] names over this screen (FR-10.4), [afterMs] after it named them, so the screen shows first, and
 * once [canShow] holds for them: while it does not, such as under a sheet the screen stays started below, they wait
 * for it, checked every [RECHECK_MS], since nothing in [due] changes when that screen closes. Collect it while the
 * screen is started: tips still up when the screen goes close with it, unseen.
 */
internal suspend fun Fragment.showTutorialWhenDue(
    due: Flow<TutorialScreen?>,
    afterMs: Long = 0L,
    canShow: (TutorialScreen) -> Boolean = { true },
) {
    due.distinctUntilChanged().collectLatest { screen ->
        if (screen != null) {
            delay(afterMs)
            while (!canShow(screen)) delay(RECHECK_MS)
            TutorialFragment.show(this, screen)
        }
    }
}
