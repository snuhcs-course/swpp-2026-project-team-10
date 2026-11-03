// AI-generated with Claude Code, 2026-11-03, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.view.LayoutInflater
import android.widget.ImageView
import androidx.core.view.isVisible
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemSavedGuideBinding
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.SavedGuide

/**
 * *My library* on Add a pose guide (FR-3.8, FR-3.11, R&S 6.2): *Add photo*, then the newest [ROW_SIZE] saved guides,
 * each as its thumbnail with the icon of its kind, and *All N* to the library while any is saved. A tap on a guide
 * uses it ([onUse]); deleting is the library's. Apart from the sheet, so it can be tested on the real layout.
 * Owner: Guide controls (#58); the library (#170).
 */
class SavedGuideList(
    private val binding: SheetAddGuideBinding,
    private val showThumbnail: (ImageView, SavedGuide) -> Unit,
    private val onUse: (SavedGuide) -> Unit,
) {
    private val context = binding.root.context

    fun render(saved: List<SavedGuide>) {
        binding.libraryAllButton.isVisible = saved.isNotEmpty()
        binding.libraryAllButton.text = context.getString(R.string.library_all, saved.size)
        val list = binding.savedGuidesList
        list.removeAllViews()
        val inflater = LayoutInflater.from(list.context)
        for (guide in saved.take(ROW_SIZE)) {
            val item = ItemSavedGuideBinding.inflate(inflater, list, false)
            item.thumbnail.contentDescription = context.describe(guide)
            item.thumbnail.setOnClickListener { onUse(guide) }
            item.kindIcon.setImageResource(guide.kindIcon)
            showThumbnail(item.thumbnail, guide)
            list.addView(item.root)
        }
    }

    companion object {
        /** The newest guides the row shows; the rest are in the library. */
        const val ROW_SIZE = 10
    }
}
