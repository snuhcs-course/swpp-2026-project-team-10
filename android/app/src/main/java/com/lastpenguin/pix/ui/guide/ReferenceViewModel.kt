// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
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
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** What a tap on a saved guide led to (FR-3.8): on the camera, or its files could not be read. */
enum class SavedGuideResult { USED, FAILED }

/**
 * Add a pose guide → Reference confirm, and the saved guides on Add a pose guide (FR-3.8). Shared by AddGuideSheet,
 * ReferenceConfirmFragment, NoPersonFoundFragment, and PickPoseFragment (activity scope).
 * Owner: PM (#5); saved guides #58.
 */
class ReferenceViewModel(
    private val maker: ReferenceGuideMaker,
    private val guides: GuideRepository,
    private val savedGuideStore: SavedGuideStore = SavedGuideStore.InMemory(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferenceUiState>(ReferenceUiState.Idle)
    val uiState: StateFlow<ReferenceUiState> = _uiState.asStateFlow()

    private var making: Job? = null

    /** Newest first, for Add a pose guide. */
    val savedGuides: StateFlow<List<SavedGuide>> = savedGuideStore.saved

    private val _savedGuideResults = MutableSharedFlow<SavedGuideResult>(extraBufferCapacity = 1)

    /** One result per tap on a saved guide: the sheet closes when it is used. */
    val savedGuideResults: SharedFlow<SavedGuideResult> = _savedGuideResults.asSharedFlow()

    private var opening: Job? = null

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

    /**
     * *Use* on a saved guide: it becomes the guide on the camera, in place of any other (FR-3.6). It starts where
     * the person stands in its reference, as when it was first made (FR-3.1), not where it was when it was saved.
     * A second tap while one is opening does nothing.
     */
    fun onUseSaved(id: String) {
        if (opening?.isActive == true) return
        opening = viewModelScope.launch {
            val result = savedGuideStore.load(id).fold(
                onSuccess = { (guide, _) ->
                    guides.setGuide(guide)
                    SavedGuideResult.USED
                },
                onFailure = { SavedGuideResult.FAILED },
            )
            _savedGuideResults.emit(result)
        }
    }

    /** *Delete* on a saved guide, after the sheet asked; a guide on the camera stays there. */
    fun onDeleteSaved(id: String) {
        viewModelScope.launch { savedGuideStore.delete(id) }
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
