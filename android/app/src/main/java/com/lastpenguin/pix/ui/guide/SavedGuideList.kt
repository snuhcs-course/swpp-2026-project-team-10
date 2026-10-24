// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemSavedGuideBinding
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.SavedGuide
import java.text.DateFormat
import java.util.Date

/**
 * The saved guides on Add a pose guide (FR-3.8, R&S 6.2): newest first, each as its reference, and hidden while there
 * are none. A tap on a guide uses it ([onUse]); its ✕ asks to delete it ([onDelete]). Apart from the sheet, so it can
 * be tested on the real layout. Owner: Guide controls (#58).
 */
class SavedGuideList(
    private val binding: SheetAddGuideBinding,
    private val onUse: (SavedGuide) -> Unit,
    private val onDelete: (SavedGuide) -> Unit,
) {
    private val context = binding.root.context
    private val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    fun render(saved: List<SavedGuide>) {
        binding.savedGuidesTitle.isVisible = saved.isNotEmpty()
        binding.savedGuidesScroll.isVisible = saved.isNotEmpty()
        val list = binding.savedGuidesList
        list.removeAllViews()
        val inflater = LayoutInflater.from(list.context)
        for (guide in saved) {
            val item = ItemSavedGuideBinding.inflate(inflater, list, false)
            val savedAt = dates.format(Date(guide.savedAt))
            item.thumbnail.setImageBitmap(guide.thumbnail)
            item.thumbnail.contentDescription = context.getString(R.string.saved_guide_description, savedAt)
            item.thumbnail.setOnClickListener { onUse(guide) }
            item.deleteButton.contentDescription = context.getString(R.string.delete_saved_guide_description, savedAt)
            item.deleteButton.setOnClickListener { onDelete(guide) }
            list.addView(item.root)
        }
    }
}
