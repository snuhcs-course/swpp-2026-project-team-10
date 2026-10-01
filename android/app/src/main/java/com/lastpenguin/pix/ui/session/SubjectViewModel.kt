package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Subject view (R&S 6.6, FR-6.6, FR-7.1). */
data class SubjectUiState(
    /** For "Dongje's camera"; arrives in `hello`. */
    val photographerName: String? = null,
    /** From `camera.capabilities`. */
    val zoomStops: List<Float> = emptyList(),
    /** From `camera.state`. */
    val zoom: Float = 1f,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
)

/**
 * Subject view (Design 2.2, Figure 7).
 * Owner: Real-time (#8 live view, #10 zoom chips), with the guide from #9.
 */
class SubjectViewModel(
    private val session: SessionManager,
    private val mirror: GuideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    fun onZoomChip(ratio: Float) {
        // TODO(#10): show it at once, send camera.zoom.set, then snap to the echoed camera.state (Design 2.6.5).
    }
}
