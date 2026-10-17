// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reference confirm and No person found (R&S 6.2). */
sealed interface ReferenceUiState {
    data object Idle : ReferenceUiState

    /** Separating the person; at most 2 s (NFR-3). */
    data object Working : ReferenceUiState

    data class Ready(val guide: ReferenceGuide) : ReferenceUiState

    data object NoPersonFound : ReferenceUiState

    /** The photo could not be read, or the segmentation model is not available. */
    data object Failed : ReferenceUiState
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

    private var making: Job? = null

    /** Whether a guide is on the camera, so Add a pose guide asks to replace it (FR-3.6). */
    val replacing: Boolean
        get() = guides.guide.value != null

    /** A photo from the system photo picker. */
    fun onPhotoPicked(uri: Uri) {
        make { maker.make(uri) }
    }

    /** The pose chosen on Pick a pose. */
    fun onCandidatePicked(image: Bitmap) {
        make { maker.make(image, GuideSource.GENERATED) }
    }

    /** *Use this guide*. */
    fun onUseGuide() {
        val ready = _uiState.value as? ReferenceUiState.Ready ?: return
        guides.setGuide(ready.guide)
        _uiState.value = ReferenceUiState.Idle
    }

    /** Separates the person; a newer photo (*Choose another photo*) replaces one still in progress. */
    private fun make(block: suspend () -> Result<ReferenceGuide>) {
        making?.cancel()
        _uiState.value = ReferenceUiState.Working
        making = viewModelScope.launch {
            _uiState.value = block().fold(
                onSuccess = { ReferenceUiState.Ready(it) },
                onFailure = {
                    if (it is NoPersonFoundException) ReferenceUiState.NoPersonFound else ReferenceUiState.Failed
                },
            )
        }
    }
}
