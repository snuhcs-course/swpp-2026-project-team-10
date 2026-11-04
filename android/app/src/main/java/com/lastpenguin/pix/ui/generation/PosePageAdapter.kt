// AI-generated with Claude Code, 2026-11-04, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemPosePageBinding
import com.lastpenguin.pix.generation.CandidateEvent

/** A page of Pick a pose: a created candidate and its ♥, null while it is not saved (FR-4.14). */
data class PosePage(val slot: PoseSlot, val heart: Heart? = null)

/**
 * The pages of Pick a pose (R&S 6.3): one created candidate per page, as large as the screen allows in its own shape,
 * so a swipe shows the next, with its ♥ at the photo's top end, which [onHeart] takes (FR-4.14). Only candidates whose
 * image arrived are given (FR-4.7). Owner: Server/AI/Sync (#7); the ♥ the library (#171).
 */
class PosePageAdapter(
    private val onHeart: (PoseSlot) -> Unit = {},
) : ListAdapter<PosePage, PosePageAdapter.Holder>(Diff) {

    class Holder(val binding: ItemPosePageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPosePageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = getItem(position)
        val image = holder.binding.poseImage
        val candidate = (page.slot.event as? CandidateEvent.Ready)?.image
        // Each page takes its own candidate's shape, the scene's (FR-4.3), so the subject's phone, which has no scene,
        // shows it whole too.
        image.keepAspect(aspectOf(candidate))
        image.setImageBitmap(candidate)
        // The pose is not written on the image (FR-4.6); a screen reader still says which pose it is.
        image.contentDescription = page.slot.pose.text

        val heart = holder.binding.heartButton
        val saved = page.heart is Heart.Saved
        heart.setIconResource(if (saved) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        // Filled in red, so a saved pose shows at a glance over any photo; empty in the other icons' white.
        heart.iconTint = ContextCompat.getColorStateList(
            heart.context,
            if (saved) R.color.pix_heart else R.color.button_secondary_text,
        )
        heart.contentDescription =
            heart.context.getString(if (saved) R.string.remove_saved_pose else R.string.save_pose)
        // While the person is cut out and saved, a second tap would save it twice.
        heart.isEnabled = page.heart != Heart.Saving
        heart.setOnClickListener { onHeart(page.slot) }
    }

    private object Diff : DiffUtil.ItemCallback<PosePage>() {
        override fun areItemsTheSame(oldItem: PosePage, newItem: PosePage) =
            oldItem.slot.pose.id == newItem.slot.pose.id

        // Selection follows the page on screen, so it does not change how a page looks.
        override fun areContentsTheSame(oldItem: PosePage, newItem: PosePage) =
            oldItem.slot.pose == newItem.slot.pose && oldItem.slot.event == newItem.slot.event &&
                oldItem.heart == newItem.heart
    }
}
