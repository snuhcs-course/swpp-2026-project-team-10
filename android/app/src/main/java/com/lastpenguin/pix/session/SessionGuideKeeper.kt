// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * In a session, keeps on this phone, as history in the library (FR-3.11, #297), what the other phone keeps by itself:
 * every guide that goes on the camera and every pose candidate, so both phones' libraries hold the same guides.
 *
 * Photographer: each guide adopted from the subject's phone ([adopted]), with the reference photo it came with.
 * Subject: each guide on its copy of the camera ([mirror]), once its reference photo ([references]) is here, or once it
 * leaves the video without one; and each candidate of the photographer's sets ([poses]) as it arrives, cut out on this
 * phone through [library], under the id the photographer's phone gives it (`poseGuideId`), so a pose that then goes on
 * the camera is already kept. A guide already in the library stays as it is, favorite or not, and each is asked for
 * once, so one deleted meanwhile is not kept again. The guides wait in a queue and are written one at a time, apart
 * from watching the camera, so a guide that comes while another is written is not missed.
 * Owner: the library (#297), with Real-time.
 */
class SessionGuideKeeper(
    private val store: SavedGuideStore,
    private val library: PoseLibrary,
    private val guides: GuideRepository,
    private val adopted: Flow<String>,
    private val mirror: GuideRepository,
    private val references: StateFlow<GuideReference?>,
    private val poses: StateFlow<MirrorPoseSet?>,
) {
    // What was asked for already, by guide id or by set and pose; its collectors run on several threads.
    private val asked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // The guides to write, in the order they came: the collectors below never wait for the disk.
    private val queue = Channel<ReferenceGuide>(Channel.UNLIMITED)

    fun start(scope: CoroutineScope) {
        scope.launch { for (guide in queue) write(guide) }
        scope.launch {
            adopted.collect { id ->
                guides.guide.value?.takeIf { it.id == id }?.let(::keep)
            }
        }
        scope.launch {
            // The guide whose reference photo has not come yet: kept when it does, or as it is once it leaves.
            var waiting: ReferenceGuide? = null
            combine(mirror.guide, references) { guide, reference -> guide to reference }.collect { (guide, reference) ->
                waiting?.takeIf { it.id != guide?.id }?.let { keep(it) }
                waiting = null
                if (guide == null) return@collect
                val photo = reference?.takeIf { it.guideId == guide.id }?.photo ?: guide.reference
                if (photo != null) keep(guide.copy(reference = photo)) else waiting = guide
            }
        }
        scope.launch {
            poses.collect { set ->
                set ?: return@collect
                for ((poseId, image) in set.candidates) {
                    if (asked.add("${set.setId}/$poseId")) {
                        library.keep(set.setId, poseId, image, set.sceneTurn, set.situation)
                    }
                }
            }
        }
    }

    private fun keep(guide: ReferenceGuide) {
        queue.trySend(guide)
    }

    private suspend fun write(guide: ReferenceGuide) {
        if (!asked.add(guide.id) || store.saved.value.any { it.id == guide.id }) return
        store.save(guide, GuideState(guideId = guide.id), favorite = false)
    }
}
