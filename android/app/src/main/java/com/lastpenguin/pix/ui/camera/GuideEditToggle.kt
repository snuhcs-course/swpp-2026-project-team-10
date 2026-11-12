// AI-generated with Claude Code, 2026-11-13, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R

/**
 * The guide-edit toggle's icon names the mode it is in (E2, #204), on Camera and on Subject view: a person's outline
 * while a pinch moves the guide, a camera while it zooms. Set only when the mode changes, as each render calls it.
 */
internal fun MaterialButton.showGuideEditing(editing: Boolean) {
    val icon = if (editing) R.drawable.ic_guide_mode else R.drawable.ic_camera
    if (getTag(R.id.editGuideButton) == icon) return
    setTag(R.id.editGuideButton, icon)
    setIconResource(icon)
}
