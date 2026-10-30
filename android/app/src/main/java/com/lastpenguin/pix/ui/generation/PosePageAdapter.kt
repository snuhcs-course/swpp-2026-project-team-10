// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.databinding.ItemPosePageBinding
import com.lastpenguin.pix.generation.CandidateEvent

/**
 * The pages of Pick a pose (R&S 6.3): one created candidate per page, as large as the screen allows in its own shape,
 * so a swipe shows the next. Only candidates whose image arrived are given (FR-4.7). Owner: Server/AI/Sync (#7).
 */
class PosePageAdapter : ListAdapter<PoseSlot, PosePageAdapter.Holder>(Diff) {

    class Holder(val binding: ItemPosePageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPosePageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val slot = getItem(position)
        val image = holder.binding.poseImage
        val candidate = (slot.event as? CandidateEvent.Ready)?.image
        // Each page takes its own candidate's shape, the scene's (FR-4.3), so the subject's phone, which has no scene,
        // shows it whole too.
        image.keepAspect(aspectOf(candidate))
        image.setImageBitmap(candidate)
        // The pose is not written on the image (FR-4.6); a screen reader still says which pose it is.
        image.contentDescription = slot.pose.text
    }

    private object Diff : DiffUtil.ItemCallback<PoseSlot>() {
        override fun areItemsTheSame(oldItem: PoseSlot, newItem: PoseSlot) = oldItem.pose.id == newItem.pose.id

        // Selection follows the page on screen, so it does not change how a page looks.
        override fun areContentsTheSame(oldItem: PoseSlot, newItem: PoseSlot) =
            oldItem.pose == newItem.pose && oldItem.event == newItem.event
    }
}
