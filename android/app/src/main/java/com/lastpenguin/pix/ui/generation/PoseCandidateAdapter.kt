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
import com.lastpenguin.pix.generation.PoseTemplate

/** One cell of the candidate grid: a template and what has arrived for it; null while it is being created. */
data class PoseSlot(val template: PoseTemplate, val event: CandidateEvent?, val selected: Boolean = false)

/** A slot per template, in the server's order. */
fun GenerationUiState.slots(): List<PoseSlot> =
    templates.map { PoseSlot(it, candidates[it.id], selected = it.id == selectedTemplateId) }

/**
 * The 2×2 candidate grid of Generating poses and Pick a pose (R&S 6.3). Cells can be picked only when [onPick] is
 * given, and only once their image is there.
 * Owner: Server/AI/Sync (#7).
 */
class PoseCandidateAdapter(
    private val onPick: ((PoseTemplate) -> Unit)? = null,
) : ListAdapter<PoseSlot, PoseCandidateAdapter.Holder>(Diff) {

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

        val card = binding.root
        // The pose name is not shown; a screen reader still says which pose the image is.
        card.contentDescription = slot.template.label
        card.isChecked = slot.selected
        binding.poseCheck.isVisible = slot.selected
        card.strokeWidth = card.resources.getDimensionPixelSize(
            if (slot.selected) R.dimen.pose_card_stroke_selected else R.dimen.pose_card_stroke,
        )
        if (onPick != null && image != null) {
            card.setOnClickListener { onPick.invoke(slot.template) }
        } else {
            card.setOnClickListener(null)
            card.isClickable = false
        }
    }

    private object Diff : DiffUtil.ItemCallback<PoseSlot>() {
        override fun areItemsTheSame(oldItem: PoseSlot, newItem: PoseSlot) = oldItem.template.id == newItem.template.id

        override fun areContentsTheSame(oldItem: PoseSlot, newItem: PoseSlot) = oldItem == newItem
    }
}
