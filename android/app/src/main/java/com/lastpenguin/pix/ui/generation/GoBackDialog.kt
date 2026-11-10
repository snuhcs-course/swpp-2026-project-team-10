// AI-generated with Claude Code, 2026-11-10, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.generation

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R

/**
 * Back on Generating poses and Pick a pose asks first (R&S 6.3, #199): the AI service was already asked for the set,
 * so going back is confirmed. [generating] says whether the set is still being made or is made. *Go back* runs
 * [onConfirm]; *Cancel*, a tap outside, or system back keeps the screen. The dialog closes with the screen's view, so a
 * set that ends meanwhile, or a recreated screen, leaves no question behind.
 */
internal fun Fragment.confirmGoingBack(generating: Boolean, onConfirm: () -> Unit): AlertDialog {
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.go_back_title)
        .setMessage(if (generating) R.string.go_back_generating else R.string.go_back_generated)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.go_back) { _, _ -> onConfirm() }
        .show()
    viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dialog.dismiss()
            }
        },
    )
    return dialog
}
