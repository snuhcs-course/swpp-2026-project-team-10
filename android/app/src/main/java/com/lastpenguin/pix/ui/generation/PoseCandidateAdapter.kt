// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemPoseCandidateBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.guide.GuideGeometry

/** One cell of the candidate grid: a pose and what has arrived for it; null while it is being created. */
data class PoseSlot(val pose: PlannedPose, val event: CandidateEvent?, val selected: Boolean = false)

/** A slot per pose, in the set's order. */
fun GenerationUiState.slots(): List<PoseSlot> =
    poses.map { PoseSlot(it, candidates[it.id], selected = it.id == selectedPoseId) }

/**
 * The 2×2 candidate grid of Generating poses (R&S 6.3), filling in as candidates arrive; Pick a pose shows them one
 * at a time instead ([PosePageAdapter]).
 * Owner: Server/AI/Sync (#7).
 */
class PoseCandidateAdapter : ListAdapter<PoseSlot, PoseCandidateAdapter.Holder>(Diff) {

    /**
     * Width / height of every slot: the scene photo's, which each candidate is made in (FR-4.3). A change binds the
     * slots again, which also measures [CandidateGridFit]'s insets again.
     */
    var frameAspect: Float = GuideGeometry.DEFAULT_FRAME_ASPECT
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    class Holder(val binding: ItemPoseCandidateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPoseCandidateBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val slot = getItem(position)
        val binding = holder.binding
        val image = (slot.event as? CandidateEvent.Ready)?.image

        // A model can round the ratio by a few pixels; the image is fitted into the slot, so none of it is cut off.
        binding.poseImage.keepAspect(frameAspect)
        binding.poseImage.setImageBitmap(image)
        binding.poseProgress.isVisible = slot.event == null
        binding.poseStatus.isVisible = image == null
        binding.poseStatus.setText(if (slot.event == null) R.string.creating_pose else R.string.pose_failed)

        // The pose is not written on the card (FR-4.6); a screen reader still says which pose the image is.
        binding.root.contentDescription = slot.pose.text
    }

    private object Diff : DiffUtil.ItemCallback<PoseSlot>() {
        override fun areItemsTheSame(oldItem: PoseSlot, newItem: PoseSlot) = oldItem.pose.id == newItem.pose.id

        override fun areContentsTheSame(oldItem: PoseSlot, newItem: PoseSlot) = oldItem == newItem
    }
}

/**
 * Keeps the 2×2 grid of Generating poses whole on screen: slots of the scene's shape as wide as half the grid when two
 * rows of them fit its height, and narrower, with the grid centered, when they would not, as for a 9:16 scene.
 */
class CandidateGridFit(private val margin: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val aspect = (parent.adapter as? PoseCandidateAdapter)?.frameAspect ?: GuideGeometry.DEFAULT_FRAME_ASPECT
        val inset = candidateGridInset(parent.width, parent.height, aspect, margin)
        // The left column keeps the space on its left, the right column on its right.
        if (parent.getChildAdapterPosition(view) % 2 == 0) outRect.set(inset, 0, 0, 0) else outRect.set(0, 0, inset, 0)
    }
}

/**
 * How far in from each side of a [width] × [height] px grid its two columns start, so that two rows of slots of
 * [aspect] (width / height) fit its height: 0 when slots half the grid wide fit. [margin] is a slot's on each side.
 */
internal fun candidateGridInset(width: Int, height: Int, aspect: Float, margin: Int): Int {
    val halfWidth = width / 2 - 2 * margin
    val fitsHeight = ((height / 2 - 2 * margin) * aspect).toInt().coerceAtLeast(0)
    return (halfWidth - fitsHeight).coerceAtLeast(0)
}
