// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.guide

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemLibraryGuideBinding
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.ui.generation.title

/**
 * The library's cells (FR-3.11, R&S 6.2): each guide's thumbnail with the icon of its kind, a ♥ that adds
 * it to Favorites or takes it out ([onFavorite], #297), and under it its situation and memo when it has them (#270). A
 * tap uses the guide ([onUse]); the pencil edits its memo ([onMemo]). A long press, which TalkBack offers as *Select*, starts selecting with the guide
 * ([onSelect], #297); while [selection] is set, a tap selects a guide or no longer does ([onToggle]), and each cell
 * shows its mark.
 * Owner: the library (#170); situations and memos (#270); favorites and selection (#297).
 */
class LibraryAdapter(
    private val showThumbnail: (ImageView, SavedGuide) -> Unit,
    private val onUse: (SavedGuide) -> Unit,
    private val onSelect: (SavedGuide) -> Unit,
    private val onToggle: (SavedGuide) -> Unit,
    private val onMemo: (SavedGuide) -> Unit,
    private val onFavorite: (SavedGuide) -> Unit,
) : ListAdapter<SavedGuide, LibraryAdapter.Holder>(Diff) {

    class Holder(val binding: ItemLibraryGuideBinding) : RecyclerView.ViewHolder(binding.root)

    /** The selected guides while selecting; null otherwise. A change rebinds only the marks. */
    var selection: Set<String>? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, SELECTION)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLibraryGuideBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val guide = getItem(position)
        val thumbnail = holder.binding.thumbnail
        thumbnail.setOnClickListener { if (selection != null) onToggle(guide) else onUse(guide) }
        thumbnail.setOnLongClickListener {
            if (selection != null) onToggle(guide) else onSelect(guide)
            true
        }
        ViewCompat.replaceAccessibilityAction(
            thumbnail,
            AccessibilityActionCompat.ACTION_LONG_CLICK,
            thumbnail.context.getString(R.string.library_select),
            null,
        )
        holder.binding.kindIcon.setImageResource(guide.kindIcon)
        showNotes(holder, guide)
        showSelection(holder, guide)
        showThumbnail(thumbnail, guide)
    }

    /**
     * A new memo, a favorite marked, or the selection changes the cell's words and marks only: its thumbnail stays,
     * with no blink while it would be read again.
     */
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == NOTES || it == SELECTION }) {
            val guide = getItem(position)
            showNotes(holder, guide)
            showSelection(holder, guide)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun showNotes(holder: Holder, guide: SavedGuide) {
        holder.binding.memoButton.setOnClickListener { onMemo(guide) }
        val heart = holder.binding.favoriteButton
        heart.setOnClickListener { onFavorite(guide) }
        heart.setImageResource(if (guide.favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        ImageViewCompat.setImageTintList(
            heart,
            ColorStateList.valueOf(
                heart.context.getColor(if (guide.favorite) R.color.pix_heart else R.color.pix_text),
            ),
        )
        heart.contentDescription = heart.context.getString(
            if (guide.favorite) R.string.library_favorite_remove else R.string.library_favorite_add,
        )
        holder.binding.situationText.isVisible = guide.situation != null
        guide.situation?.let { holder.binding.situationText.setText(it.title) }
        holder.binding.memoText.isVisible = guide.memo.isNotEmpty()
        holder.binding.memoText.text = guide.memo
    }

    /** The mark and what TalkBack says of it while selecting; the pencil and the ♥ wait until selecting ends. */
    private fun showSelection(holder: Holder, guide: SavedGuide) {
        val selected = selection?.contains(guide.id)
        val thumbnail = holder.binding.thumbnail
        thumbnail.contentDescription = thumbnail.context.describe(guide)
        holder.binding.selectMark.isVisible = selected != null
        holder.binding.selectMark.setImageResource(
            if (selected ==
                true
            ) {
                R.drawable.ic_select_on
            } else {
                R.drawable.ic_select_off
            },
        )
        holder.binding.memoButton.isVisible = selected == null
        holder.binding.favoriteButton.isVisible = selected == null
        ViewCompat.setStateDescription(
            thumbnail,
            selected?.let {
                thumbnail.context.getString(
                    if (it) R.string.library_guide_selected else R.string.library_guide_not_selected,
                )
            },
        )
    }

    private object Diff : DiffUtil.ItemCallback<SavedGuide>() {
        override fun areItemsTheSame(oldItem: SavedGuide, newItem: SavedGuide) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SavedGuide, newItem: SavedGuide) = oldItem == newItem

        override fun getChangePayload(oldItem: SavedGuide, newItem: SavedGuide): Any? = NOTES.takeIf {
            oldItem.copy(memo = newItem.memo, situation = newItem.situation, favorite = newItem.favorite) == newItem
        }
    }

    private companion object {
        /** Only the memo, the situation, or the favorite changed. */
        const val NOTES = "notes"

        /** Only the selection changed. */
        const val SELECTION = "selection"
    }
}
