// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.SubjectGuideTarget
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
 * ReferenceConfirmFragment, NoPersonFoundFragment, and PickPoseFragment (activity scope). The guide of a generated
 * pose also gets its tip asked for here, once its person is found (FR-4.10). Opened from Subject view
 * ([openFor]), the guide goes to the photographer through [subject] instead of this phone's camera (Design 2.6.5).
 * Owner: PM (#5); saved guides #58; pose tips; the guide from the subject Iter3-P9; the subject's pick Iter3-P16.
 */
class ReferenceViewModel(
    private val maker: ReferenceGuideMaker,
    private val guides: GuideRepository,
    private val savedGuideStore: SavedGuideStore = SavedGuideStore.InMemory(),
    private val tips: PoseTips? = null,
    private val subject: SubjectGuideTarget? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferenceUiState>(ReferenceUiState.Idle)
    val uiState: StateFlow<ReferenceUiState> = _uiState.asStateFlow()

    private var making: Job? = null

    /** The guide whose tip was asked for and that has not gone on the camera yet. */
    private var tipFor: String? = null

    private val _chosenBy = MutableStateFlow<String?>(null)

    /**
     * The subject whose pick on their *Pick a pose* this candidate is, for Reference confirm's "… chose this pose"
     * (Design 2.6.5); null for this phone's own choice and for an uploaded photo.
     */
    val chosenBy: StateFlow<String?> = _chosenBy.asStateFlow()

    /** Newest first, for Add a pose guide. */
    val savedGuides: StateFlow<List<SavedGuide>> = savedGuideStore.saved

    private val _savedGuideResults = Channel<SavedGuideResult>(Channel.BUFFERED)

    /**
     * One result per tap on a saved guide: the sheet closes when it is used. Kept until the sheet reads it, so a
     * result that comes while the screen is stopped is not lost.
     */
    val savedGuideResults: Flow<SavedGuideResult> = _savedGuideResults.receiveAsFlow()

    private var opening: Job? = null

    /** Whether the guide chosen here goes to the photographer from Subject view rather than to this camera. */
    var forSubject: Boolean = false
        private set

    /** Add a pose guide opened from the Camera screen or, with [fromSubject], from Subject view (FR-6.15). */
    fun openFor(fromSubject: Boolean) {
        forSubject = fromSubject && subject != null
    }

    /** Whether a guide is on the camera or the video, so Add a pose guide asks to replace it (FR-3.6). */
    val replacing: Boolean
        get() = if (forSubject) subject?.current != null else guides.guide.value != null

    private fun use(guide: ReferenceGuide) {
        val target = subject
        if (forSubject && target != null) target.offer(guide) else guides.setGuide(guide)
    }

    /** A photo from the system photo picker. */
    fun onPhotoPicked(uri: Uri) {
        _chosenBy.value = null
        make { maker.make(uri) }
    }

    /**
     * The pose chosen on Pick a pose. With [tipFrom], the scene and the pose it was made from, its tip is asked for
     * as soon as its person is found, so it is usually ready when Camera opens (FR-4.10). [chosenBy] names the subject
     * when it was their pick in a session (FR-6.15).
     */
    fun onCandidatePicked(image: Bitmap, tipFrom: TipSource? = null, chosenBy: String? = null) {
        _chosenBy.value = chosenBy
        make(
            onReady = { guide ->
                if (tipFrom != null && tips != null) {
                    tips.prepare(guide, image, tipFrom)
                    tipFor = guide.id
                }
            },
        ) { maker.make(image, GuideSource.GENERATED) }
    }

    /** *Use this guide*. */
    fun onUseGuide() {
        val ready = _uiState.value as? ReferenceUiState.Ready ?: return
        // Its tip goes with it to the camera; leaving the screen now must not drop it.
        if (ready.guide.id == tipFor) tipFor = null
        use(ready.guide)
        _uiState.value = ReferenceUiState.Idle
    }

    /**
     * Back on Reference confirm, which leaves without *Use this guide*: a person still being found is not, and the tip
     * asked for its guide is dropped, so nothing is sent for a guide that will not be used (FR-4.10).
     */
    fun onConfirmLeft() {
        if (making?.isActive == true) {
            making?.cancel()
            _uiState.value = ReferenceUiState.Idle
        }
        discardTip()
    }

    private fun discardTip() {
        tipFor?.let { tips?.discard(it) }
        tipFor = null
    }

    /**
     * *Use* on a saved guide: it becomes the guide on the camera, in place of any other (FR-3.6). It starts where
     * the person stands in its reference, as when it was first made (FR-3.1), not where it was when it was saved.
     * A second tap while one is opening does nothing, and [cancelOpening] stops one that has not arrived.
     */
    fun onUseSaved(id: String) {
        if (opening?.isActive == true) return
        opening = viewModelScope.launch {
            val result = try {
                val (guide, _) = savedGuideStore.load(id).getOrThrow()
                use(guide)
                SavedGuideResult.USED
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Unreadable files, or values the guide cannot be placed with: a message, not a crash.
                SavedGuideResult.FAILED
            }
            _savedGuideResults.send(result)
        }
    }

    /** The sheet closed: a saved guide still opening must not replace the guide the user chose to keep. */
    fun cancelOpening() {
        opening?.cancel()
    }

    /** *Delete* on a saved guide, after the sheet asked; a guide on the camera stays there. */
    fun onDeleteSaved(id: String) {
        viewModelScope.launch { savedGuideStore.delete(id) }
    }

    /** Separates the person; a newer photo (*Choose another photo*) replaces one still in progress. */
    private fun make(onReady: (ReferenceGuide) -> Unit = {}, block: suspend () -> Result<ReferenceGuide>) {
        making?.cancel()
        // The guide it replaces will not be used, so its tip is not needed.
        discardTip()
        _uiState.value = ReferenceUiState.Working
        making = viewModelScope.launch {
            _uiState.value = block().fold(
                onSuccess = {
                    onReady(it)
                    ReferenceUiState.Ready(it)
                },
                onFailure = {
                    if (it is NoPersonFoundException) ReferenceUiState.NoPersonFound else ReferenceUiState.Failed
                },
            )
        }
    }
}
