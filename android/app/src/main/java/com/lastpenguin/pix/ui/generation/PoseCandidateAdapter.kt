// AI-generated with Claude Code, 2026-10-24, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemPoseCandidateBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.PlannedPose

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

    class Holder(val binding: ItemPoseCandidateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPoseCandidateBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val slot = getItem(position)
        val binding = holder.binding
        val image = (slot.event as? CandidateEvent.Ready)?.image

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
