// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.camera.ZoomablePhotoView
import com.lastpenguin.pix.ui.fadeTo

/**
 * The scene photo of Generate poses, large (FR-4.15; built with [#274], and a step of [#296]): a tap on its thumbnail shows the whole photo over the
 * screen, on either phone, so the user sees what the poses are made from. It is handled as a photo of the photo viewer
 * (ZoomablePhotoView): a pinch or a double tap zooms it, and a tap, a drag down, Back at the top left, or system back
 * closes it. A dialog among the screen's child fragments, like the tutorial's tips, never a navigation destination,
 * which the other phone's steps would close under; it goes with the screen when a set starts. The photo comes from the
 * screen under it ([Host]) and is kept nowhere else; without one, as after the app's process was restarted, it closes.
 * Owner: Iter5-P10.
 */
class ScenePhotoFragment : DialogFragment(R.layout.fragment_scene_photo) {

    /** The screen whose scene photo this shows. */
    interface Host {
        val scenePhoto: Bitmap?
    }

    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Pix_ScenePhoto)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = super.onCreateDialog(savedInstanceState).apply {
        // Over the whole screen, the system bars included; the layout keeps Back clear of them.
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val photo = (parentFragment as? Host)?.scenePhoto
        if (photo == null) {
            dismissAllowingStateLoss()
            return
        }
        val large = view.findViewById<ZoomablePhotoView>(R.id.scenePhotoLarge)
        large.setImageBitmap(photo)
        large.listener = object : ZoomablePhotoView.Listener {
            override fun onTap() = close()

            // The screen shows through as the photo is dragged away.
            override fun onDrag(offset: Float) {
                val away = (offset / (view.height * DRAG_CLEAR)).coerceIn(0f, 1f)
                view.background.alpha = (OPAQUE * (1 - away)).toInt()
            }

            override fun onDismiss() = close()
        }
        view.findViewById<View>(R.id.scenePhotoBack).setOnClickListener { close() }
        (requireDialog() as ComponentDialog).onBackPressedDispatcher.addCallback(viewLifecycleOwner) { close() }
        // Recreated with the screen, it comes at once, as it was.
        if (savedInstanceState == null) {
            view.alpha = 0f
            view.fadeTo(1f, Motion.FADE_MS)
        }
    }

    /** Fades out, then closes; once. */
    private fun close() {
        if (closing) return
        closing = true
        requireView().fadeTo(0f, Motion.FADE_MS) { if (isAdded) dismissAllowingStateLoss() }
    }

    companion object {
        const val TAG = "scenePhoto"

        /** The part of the screen's height a drag takes to clear the background. */
        private const val DRAG_CLEAR = 0.5f
        private const val OPAQUE = 255
    }
}

/**
 * Makes this view, the scene photo's thumbnail on [screen], show the photo large at a tap (FR-4.15), once however
 * often it is tapped; [before] ends what the screen was doing, such as typing. [screen] is the photo's
 * [ScenePhotoFragment.Host].
 */
internal fun View.opensScenePhoto(screen: Fragment, before: () -> Unit = {}) {
    ViewCompat.replaceAccessibilityAction(
        this,
        AccessibilityActionCompat.ACTION_CLICK,
        context.getString(R.string.scene_photo_enlarge),
        null,
    )
    setOnClickListener {
        val children = screen.childFragmentManager
        if (children.isStateSaved ||
            children.findFragmentByTag(ScenePhotoFragment.TAG) != null
        ) {
            return@setOnClickListener
        }
        before()
        ScenePhotoFragment().showNow(children, ScenePhotoFragment.TAG)
    }
}
