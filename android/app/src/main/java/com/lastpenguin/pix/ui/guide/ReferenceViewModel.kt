package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reference confirm and No person found (R&S 6.2). */
sealed interface ReferenceUiState {
    data object Idle : ReferenceUiState

    /** Separating the person; at most 2 s (NFR-3). */
    data object Working : ReferenceUiState

    data class Ready(val guide: ReferenceGuide, val style: GuideStyle) : ReferenceUiState

    data object NoPersonFound : ReferenceUiState
}

/**
 * Add a pose guide → Reference confirm. Shared by AddGuideSheet, ReferenceConfirmFragment,
 * NoPersonFoundFragment, and PickPoseFragment (activity scope).
 * Owner: PM (#5).
 */
class ReferenceViewModel(
    private val maker: ReferenceGuideMaker,
    private val guides: GuideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferenceUiState>(ReferenceUiState.Idle)
    val uiState: StateFlow<ReferenceUiState> = _uiState.asStateFlow()

    /** A photo from the system photo picker. */
    fun onPhotoPicked(uri: Uri) {
        // TODO(#5): maker.make(uri) → Ready or NoPersonFound.
    }

    /** The pose chosen on Pick a pose. */
    fun onCandidatePicked(image: Bitmap) {
        // TODO(#5): maker.make(image, GuideSource.GENERATED).
    }

    /** The Cutout/Outline switch on Reference confirm. */
    fun onStylePreview(style: GuideStyle) {
        // TODO(#5)
    }

    /** *Use this guide*. */
    fun onUseGuide() {
        // TODO(#5): guides.setGuide(guide) with the chosen style.
    }
}
