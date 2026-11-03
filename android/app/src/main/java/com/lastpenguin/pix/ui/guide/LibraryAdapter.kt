// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemLibraryGuideBinding
import com.lastpenguin.pix.guide.SavedGuide

/**
 * The library's cells (FR-3.11, R&S 6.2): each saved guide's thumbnail with the icon of its kind. A tap uses the guide
 * ([onUse]); a long press, which TalkBack offers as *Delete saved guide*, asks to delete it ([onDelete]).
 * Owner: the library (#170).
 */
class LibraryAdapter(
    private val showThumbnail: (ImageView, SavedGuide) -> Unit,
    private val onUse: (SavedGuide) -> Unit,
    private val onDelete: (SavedGuide) -> Unit,
) : ListAdapter<SavedGuide, LibraryAdapter.Holder>(Diff) {

    class Holder(val binding: ItemLibraryGuideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLibraryGuideBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val guide = getItem(position)
        val thumbnail = holder.binding.thumbnail
        thumbnail.contentDescription = thumbnail.context.describe(guide)
        thumbnail.setOnClickListener { onUse(guide) }
        thumbnail.setOnLongClickListener {
            onDelete(guide)
            true
        }
        ViewCompat.replaceAccessibilityAction(
            thumbnail,
            AccessibilityActionCompat.ACTION_LONG_CLICK,
            thumbnail.context.getString(R.string.delete_saved_guide),
            null,
        )
        holder.binding.kindIcon.setImageResource(guide.kindIcon)
        showThumbnail(thumbnail, guide)
    }

    private object Diff : DiffUtil.ItemCallback<SavedGuide>() {
        override fun areItemsTheSame(oldItem: SavedGuide, newItem: SavedGuide) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SavedGuide, newItem: SavedGuide) = oldItem == newItem
    }
}
