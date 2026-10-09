// AI-generated with Claude Code, 2026-10-05, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.fragment.app.Fragment

/**
 * Registers the system photo picker for one image (FR-2.1): Pix gets only the picked photo, with no access to the
 * whole gallery. [onResult] receives null when the picker is closed without a choice. Call it while the fragment is
 * created, as with any activity result; the returned function opens the picker.
 */
internal fun Fragment.registerPhotoPicker(onResult: (Uri?) -> Unit): () -> Unit {
    val launcher = registerForActivityResult(PickVisualMedia(), onResult)
    return { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
}
