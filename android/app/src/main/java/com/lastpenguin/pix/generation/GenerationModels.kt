// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap

/** Candidates per set (FR-4.3). */
const val POSE_COUNT = 4

/** The description field stops here, and the server refuses longer descriptions (FR-4.8). */
const val DESCRIPTION_MAX_LENGTH = 100

/** What *Generate poses* asks for (FR-4.4, FR-4.8). The prompt around the pose stays on the server (Design 2.6.3). */
sealed interface PoseRequest {
    /** The user's own description, in Korean or English, for every pose of the set. */
    data class Described(val text: String) : PoseRequest

    /** Four poses the server chooses for the scene, one per candidate. */
    data object Suggested : PoseRequest

    companion object {
        /**
         * The field's text with each run of whitespace made one space, as the server reads it. A blank field asks
         * for suggestions, as *Generate 4 poses* with an empty field does (US-5).
         */
        fun of(description: String): PoseRequest {
            val text = description.trim().split(Regex("\\s+")).joinToString(" ")
            return if (text.isEmpty()) Suggested else Described(text)
        }
    }
}

/** One pose of a set: [id] names its slot (`pose-1` … `pose-4`), [text] is what the image model is asked for. */
data class PlannedPose(val id: String, val text: String)

enum class GenerationError { OFFLINE, TIMEOUT, RATE_LIMITED, REJECTED, SERVER }

/** What a run reports: the poses of the set once, then one result per pose. */
sealed interface GenerationEvent {
    data class Planned(val poses: List<PlannedPose>) : GenerationEvent
}

/** One result per pose. */
sealed interface CandidateEvent : GenerationEvent {
    val poseId: String

    data class Ready(override val poseId: String, val image: Bitmap) : CandidateEvent

    data class Failed(override val poseId: String, val error: GenerationError) : CandidateEvent
}
