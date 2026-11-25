// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.guide.forSituation
import com.lastpenguin.pix.guide.intoFrameHeldAt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async

/**
 * The candidates kept in the library (FR-4.14, #297): each pose the phone shows becomes a guide there, with no tap, so a
 * Back or a wrong tap loses none. The person is cut out once per candidate, which the ♥ and *Select* reuse, so a pose
 * is never kept twice under two ids. Its guide is kept turned back into the camera's frame, as *Select* puts it on the
 * camera (Design 2.6.2, #206), with the set's situation (#270), as history; the ♥ marks it a favorite. Only the latest
 * set's guides stay in memory; the earlier ones are in the store.
 * Owner: the library (#297).
 */
class PoseLibrary(
    private val maker: ReferenceGuideMaker,
    private val store: SavedGuideStore,
    private val scope: CoroutineScope,
) {
    /**
     * A candidate's guide as cut, and as turned for the library; [stored] is false while the library could not write it,
     * which leaves *Select* working all the same.
     */
    class Kept(val cut: ReferenceGuide, val saved: ReferenceGuide, val stored: Boolean = true)

    private val lock = Any()

    /** The latest set's candidates by pose id. */
    private var setId: String? = null
    private val kept = mutableMapOf<String, Deferred<Result<Kept>>>()

    /**
     * Keeps the candidate [poseId] of the set [setId], whose image is [image], of a scene photo held at [turn] for
     * [situation]: once, so a later call for it, from the ♥ or *Select*, gets the same guide. It fails only when no
     * person is found in the candidate ([com.lastpenguin.pix.guide.NoPersonFoundException]): a storage that cannot be
     * written gives the guide with [Kept.stored] false, so the pose can still be used, and a later call tries the storage
     * again. A guide the library deleted since is kept again.
     */
    fun keep(
        setId: String,
        poseId: String,
        image: Bitmap,
        turn: Int = 0,
        situation: Situation? = null,
    ): Deferred<Result<Kept>> = synchronized(lock) {
        if (setId != this.setId) {
            // A new set's candidates cannot reach the earlier ones', so their guides are let go.
            this.setId = setId
            kept.clear()
        }
        val earlier = kept[poseId]
        val done = earlier?.let(::finished)
        // Still being kept, failed (no person), or still in the library: the same answer.
        val inLibrary = done != null && done.stored && store.saved.value.any { it.id == done.saved.id }
        if (earlier != null && (done == null || inLibrary)) return earlier
        scope.async {
            try {
                // Deleted in the library since, or not written: kept again as it was cut, with its id.
                val cut = done?.cut ?: maker.make(image, GuideSource.GENERATED).getOrThrow().forSituation(situation)
                val saved = done?.saved ?: cut.intoFrameHeldAt(turn)
                val stored = store.save(saved, GuideState(guideId = saved.id), favorite = false).isSuccess
                Result.success(Kept(cut, saved, stored))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }.also { kept[poseId] = it }
    }

    /** What [keeping] kept, once it did; null while it runs or when it failed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun finished(keeping: Deferred<Result<Kept>>): Kept? =
        if (keeping.isCompleted) keeping.getCompleted().getOrNull() else null
}
