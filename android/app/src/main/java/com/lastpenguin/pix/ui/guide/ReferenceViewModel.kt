// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han and Dongje Park
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
import com.lastpenguin.pix.session.ConfirmShare
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SubjectGuideTarget
import com.lastpenguin.pix.session.SubjectUse
import java.util.UUID
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
 * The Reference confirm of a pose chosen on Pick a pose is on the subject's phone too, through [confirms], and the
 * first *Use this guide* to reach this phone decides (Design 2.6.5, *Reference confirm on both phones*).
 * Owner: PM (#5); saved guides #58; pose tips; the guide from the subject Iter3-P9; the subject's pick and use
 * Iter3-P16.
 */
class ReferenceViewModel(
    private val maker: ReferenceGuideMaker,
    private val guides: GuideRepository,
    private val savedGuideStore: SavedGuideStore = SavedGuideStore.InMemory(),
    private val tips: PoseTips? = null,
    private val subject: SubjectGuideTarget? = null,
    private val confirms: ConfirmShare = ConfirmShare.None,
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

    /** The Reference confirm the subject's phone shows too, from a pose chosen on Pick a pose until it ends. */
    private var sharedId: String? = null

    private val _sharing = MutableStateFlow(false)

    /** Whether the subject's phone shows this Reference confirm too, for its hint (FR-6.15). */
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    private val _usedBySubject = Channel<Unit>(Channel.BUFFERED)

    /**
     * Each *Use this guide* of the subject's that set the guide on this camera: Reference confirm then goes back to
     * the camera, as after this phone's own tap. Kept until the screen reads it.
     */
    val usedBySubject: Flow<Unit> = _usedBySubject.receiveAsFlow()

    /** Newest first, for Add a pose guide. */
    val savedGuides: StateFlow<List<SavedGuide>> = savedGuideStore.saved

    private val _savedGuideResults = Channel<SavedGuideResult>(Channel.BUFFERED)

    /**
     * One result per tap on a saved guide: the sheet closes when it is used. Kept until the sheet reads it, so a
     * result that comes while the screen is stopped is not lost.
     */
    val savedGuideResults: Flow<SavedGuideResult> = _savedGuideResults.receiveAsFlow()

    private var opening: Job? = null

    init {
        viewModelScope.launch { confirms.uses.collect(::onSubjectUse) }
    }

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

    /** A photo from the system photo picker; on a Reference confirm the subject shares, it stays shared. */
    fun onPhotoPicked(uri: Uri) {
        _chosenBy.value = null
        sharedId?.let(confirms::finding)
        make { maker.make(uri) }
    }

    /**
     * The pose chosen on Pick a pose. With [tipFrom], the scene and the pose it was made from, its tip is asked for
     * as soon as its person is found, so it is usually ready when Camera opens (FR-4.10). [chosenBy] names the subject
     * when it was their pick in a session (FR-6.15). Its Reference confirm goes to the subject's phone too, which
     * shows it while a session is connected.
     */
    fun onCandidatePicked(image: Bitmap, tipFrom: TipSource? = null, chosenBy: String? = null) {
        _chosenBy.value = chosenBy
        val id = UUID.randomUUID().toString()
        sharedId = id
        _sharing.value = true
        confirms.open(id)
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
        useReady(Role.PHOTOGRAPHER)
    }

    /**
     * The subject's *Use this guide* (FR-6.15): taken only while the outline it names is on screen and nobody has used
     * it, judged here in the order things happen on this phone (AD-7); then it is as this phone's own tap.
     */
    private fun onSubjectUse(use: SubjectUse) {
        val ready = _uiState.value as? ReferenceUiState.Ready ?: return
        if (use.confirmId != sharedId || use.guideId != ready.guide.id) return
        if (useReady(Role.SUBJECT)) _usedBySubject.trySend(Unit)
    }

    private fun useReady(by: Role): Boolean {
        val ready = _uiState.value as? ReferenceUiState.Ready ?: return false
        // Its tip goes with it to the camera; leaving the screen now must not drop it.
        if (ready.guide.id == tipFor) tipFor = null
        use(ready.guide)
        _uiState.value = ReferenceUiState.Idle
        endShare { confirms.used(it, ready.guide.id, by) }
        return true
    }

    /** The shared Reference confirm ends once, with [end]. */
    private fun endShare(end: (confirmId: String) -> Unit) {
        val id = sharedId ?: return
        sharedId = null
        _sharing.value = false
        end(id)
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
        endShare(confirms::close)
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
                    sharedId?.let { id -> confirms.ready(id, it) }
                    ReferenceUiState.Ready(it)
                },
                onFailure = {
                    // No person found and a photo that cannot be used leave Reference confirm, shared or not.
                    endShare(confirms::close)
                    if (it is NoPersonFoundException) ReferenceUiState.NoPersonFound else ReferenceUiState.Failed
                },
            )
        }
    }
}
