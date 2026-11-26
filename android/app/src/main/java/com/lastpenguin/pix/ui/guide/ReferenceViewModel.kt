// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.collection.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.guide.forSituation
import com.lastpenguin.pix.guide.intoFrameHeldAt
import com.lastpenguin.pix.guide.withoutPicked
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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

/** What a tap on a saved guide in the library led to (FR-3.8): on the camera, or its files could not be read. */
enum class SavedGuideResult { USED, FAILED }

/**
 * Add a pose guide → Reference confirm, and the library of saved guides (FR-3.8, FR-3.11). Shared by AddGuideSheet,
 * LibraryFragment, ReferenceConfirmFragment, NoPersonFoundFragment, and PickPoseFragment (activity scope). The guide
 * of a generated pose also gets its tip asked for here, once its person is found (FR-4.10). Opened from Subject view
 * ([openFor]), the guide goes to the photographer through [subject] instead of this phone's camera (Design 2.6.5).
 * The Reference confirm of a pose chosen on Pick a pose is on the subject's phone too, through [confirms], and the
 * first *Use this guide* to reach this phone decides (Design 2.6.5, *Reference confirm on both phones*).
 * Owner: PM (#5); saved guides #58; pose tips; the guide from the subject Iter3-P9; the subject's pick and use
 * Iter3-P16; the library #170; the screenshot's cut #174.
 */
class ReferenceViewModel(
    private val maker: ReferenceGuideMaker,
    private val guides: GuideRepository,
    private val savedGuideStore: SavedGuideStore = SavedGuideStore.InMemory(),
    private val tips: PoseTips? = null,
    private val subject: SubjectGuideTarget? = null,
    private val confirms: ConfirmShare = ConfirmShare.None,
    /**
     * Tells the subject's phone that the photographer put a guide on the camera from a photo or the library
     * (`RemoteControlHandler.didHere`, FR-7.4, #292). Not for a chosen pose, whose Reference confirm says so itself.
     */
    private val onGuideSet: () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferenceUiState>(ReferenceUiState.Idle)
    val uiState: StateFlow<ReferenceUiState> = _uiState.asStateFlow()

    private var making: Job? = null

    /** The photo picked last, which *Use the whole image* makes the guide from again; null for a chosen pose. */
    private var picked: Uri? = null

    /**
     * Whether the reference on Reference confirm, or the one No person found is about, is a photo from the picker and
     * not a generated pose: Back then returns to where photos are chosen (#294).
     */
    val fromPhoto: Boolean
        get() = picked != null

    /** The guide whose tip was asked for and that has not gone on the camera yet. */
    private var tipFor: String? = null

    /**
     * How the phone was held for the scene photo of the pose on Reference confirm (`PhoneTurns`, #206): its guide goes
     * on the camera turned back by it. 0 for a picked photo.
     */
    private var heldAt = 0

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

    /** Newest first, for the *My library* row on Add a pose guide. */
    val savedGuides: StateFlow<List<SavedGuide>> = savedGuideStore.saved

    private val libraryFilter = MutableStateFlow(LibraryFilter.ALL)

    private val librarySituation = MutableStateFlow<Situation?>(null)

    /** The guides selected in the library (#297); null while not selecting. */
    private val librarySelection = MutableStateFlow<Set<String>?>(null)

    /**
     * The library (FR-3.11): the guides of the chosen filter and situation (#270), how many there are for each, and
     * the selection (#297).
     */
    val library: StateFlow<LibraryPage> =
        combine(savedGuideStore.saved, libraryFilter, librarySituation, librarySelection, ::libraryPage)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                libraryPage(savedGuideStore.saved.value, LibraryFilter.ALL),
            )

    private val _favoriteFailures = Channel<Unit>(Channel.BUFFERED)

    /** Each ♥ in the library the storage could not keep, for the library to say so; read once. */
    val favoriteFailures: Flow<Unit> = _favoriteFailures.receiveAsFlow()

    private val _memoFailures = Channel<Unit>(Channel.BUFFERED)

    /** Each memo the storage could not keep, for the library to say so; read once. */
    val memoFailures: Flow<Unit> = _memoFailures.receiveAsFlow()

    /** The thumbnails shown last, up to [THUMBNAIL_CACHE_BYTES], so scrolling back does not read them again. */
    private val thumbnails = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

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

    /**
     * Add a pose guide opened from the Camera screen or, with [fromSubject], from Subject view (FR-6.15). The library
     * opened from it starts on *All* (#297).
     */
    fun openFor(fromSubject: Boolean) {
        forSubject = fromSubject && subject != null
        libraryFilter.value = LibraryFilter.ALL
        librarySituation.value = null
        librarySelection.value = null
    }

    /** A filter chip in the library. */
    fun onLibraryFilter(filter: LibraryFilter) {
        libraryFilter.value = filter
    }

    /** A situation's chip in the library (#270): shows its guides only, and a second tap shows every situation again. */
    fun onLibrarySituation(situation: Situation) {
        // Judged by what the library shows: a situation whose guides were all deleted is no longer chosen.
        librarySituation.value = if (library.value.situation == situation) null else situation
    }

    /** *Save* on a saved guide's memo in the library (FR-3.11, #270); an empty one removes it. */
    fun onSaveMemo(id: String, memo: String) {
        viewModelScope.launch {
            if (savedGuideStore.setMemo(id, memo).isFailure) _memoFailures.send(Unit)
        }
    }

    /** A saved guide's thumbnail (Design 2.8, *Guide store*); null when its files cannot be read. */
    suspend fun thumbnail(id: String): Bitmap? {
        thumbnails[id]?.let { return it }
        return savedGuideStore.thumbnail(id)?.also { thumbnails.put(id, it) }
    }

    /** Whether a guide is on the camera or the video, so Add a pose guide asks to replace it (FR-3.6). */
    val replacing: Boolean
        get() = if (forSubject) subject?.current != null else guides.guide.value != null

    /**
     * Puts [guide] on the camera, or offers it to the photographer from the subject's phone, without the picked image
     * Reference confirm showed, which neither camera needs (#295). A pose's guide, cut from a candidate of a scene photo
     * held at [turn], is turned back into the camera's portrait frame (Design 2.6.2), so it lies on the preview held
     * that way, on both phones (NFR-8).
     */
    private fun use(guide: ReferenceGuide, turn: Int = 0, shared: Boolean = false) {
        val target = subject
        val used = guide.withoutPicked()
        if (forSubject && target != null) {
            target.offer(used)
            return
        }
        guides.setGuide(used.intoFrameHeldAt(turn))
        if (!shared) onGuideSet()
    }

    /** A photo from the system photo picker; on a Reference confirm the subject shares, it stays shared. */
    fun onPhotoPicked(uri: Uri) {
        picked = uri
        heldAt = 0
        _chosenBy.value = null
        sharedId?.let(confirms::finding)
        make { maker.make(uri) }
    }

    /**
     * *Use the whole image* on a screenshot cut to its photo (FR-2.10): the guide is made again from all of it, and a
     * Reference confirm the subject shares stays shared, as with another photo.
     */
    fun onUseWholeImage() {
        val uri = picked ?: return
        sharedId?.let(confirms::finding)
        make { maker.make(uri, wholeImage = true) }
    }

    /**
     * The pose chosen on Pick a pose. With [tipFrom], the scene and the pose it was made from, its tip is asked for
     * as soon as its person is found, so it is usually ready when Camera opens (FR-4.10). [chosenBy] names the subject
     * when it was their pick in a session (FR-6.15). Its Reference confirm goes to the subject's phone too, which
     * shows it while a session is connected. The guide is the one the library [kept] for the candidate (#297), so its
     * person is not cut out again, and on Camera the guide is the kept one, which *Save guide* finds; without [kept] the
     * person is cut out here. [turn] is how the phone was held for the scene
     * photo; the guide is shown here on the candidate as it is, and turned back by it on the camera. The guide keeps
     * [situation], the set's, for the library (#270).
     */
    fun onCandidatePicked(
        image: Bitmap,
        tipFrom: TipSource? = null,
        chosenBy: String? = null,
        kept: (suspend () -> Result<ReferenceGuide>)? = null,
        turn: Int = 0,
        situation: Situation? = null,
    ) {
        _chosenBy.value = chosenBy
        picked = null
        heldAt = turn
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
        ) {
            kept?.invoke() ?: maker.make(image, GuideSource.GENERATED).map { it.forSituation(situation) }
        }
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
        // A confirm the subject's phone shows too ends with its own line there, "… used this guide".
        use(ready.guide, heldAt, shared = sharedId != null)
        keepPhotoGuide(ready.guide)
        _uiState.value = ReferenceUiState.Idle
        endShare { confirms.used(it, ready.guide.id, by) }
        return true
    }

    /**
     * A guide from a photo, once used, stays in the library as history (#297), as each pose does through the library's
     * own keeping; *Save guide* then marks it a favorite. A failed write leaves the guide on the camera all the same.
     */
    private fun keepPhotoGuide(guide: ReferenceGuide) {
        if (guide.source != GuideSource.GALLERY) return
        viewModelScope.launch { savedGuideStore.save(guide, GuideState(guideId = guide.id), favorite = false) }
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

    /** The sheet or the library closed: a saved guide still opening must not replace the guide kept on the camera. */
    fun cancelOpening() {
        opening?.cancel()
    }

    /** The ♥ on a guide in the library (#297): it joins *Favorites*, or leaves it and stays under *All*. */
    fun onToggleFavorite(id: String) {
        val guide = savedGuideStore.saved.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            savedGuideStore.setFavorite(id, !guide.favorite).onFailure { _favoriteFailures.trySend(Unit) }
        }
    }

    /** *Delete* on guides, after the library asked; a guide on the camera stays there. Selecting ends. */
    fun onDeleteSaved(ids: Collection<String>) {
        librarySelection.value = null
        viewModelScope.launch {
            savedGuideStore.delete(ids)
            ids.forEach(thumbnails::remove)
        }
    }

    /** A long press on a guide, or *Select* under ⋮ with no guide (#297): selecting starts, with [id] selected. */
    fun onStartSelecting(id: String? = null) {
        librarySelection.value = setOfNotNull(id) + librarySelection.value.orEmpty()
    }

    /** A tap on a guide while selecting: it is selected, or no longer is. */
    fun onToggleSelected(id: String) {
        // The selection itself, not the page, which follows it a moment later: quick taps must each count.
        val selected = librarySelection.value ?: return
        librarySelection.value = if (id in selected) selected - id else selected + id
    }

    /** *Select all*, while selecting or under ⋮ (#297): every guide listed, selecting from then on. */
    fun onSelectAll() {
        librarySelection.value = library.value.guides.mapTo(HashSet()) { it.id }
    }

    /** ✕ or Back while selecting: no guide is selected any longer. */
    fun onStopSelecting() {
        librarySelection.value = null
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

    private companion object {
        /** About thirty full-size thumbnails: more than the library shows at once, a small part of the app's memory. */
        const val THUMBNAIL_CACHE_BYTES = 24 * 1024 * 1024
    }
}
