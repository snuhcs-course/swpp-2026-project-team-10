// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import androidx.core.view.isVisible
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.SavedGuide

/**
 * *My library* on Add a pose guide (FR-3.8, FR-3.11, R&S 6.2): a row that opens the library, with *All N* at its end
 * while any guide is saved. Using and deleting a guide are the library's. Apart from the sheet, so it can be tested on
 * the real layout.
 * Owner: Guide controls (#58); the library (#170); the row Iter4-P13.
 */
class SavedGuideList(private val binding: SheetAddGuideBinding) {
    private val context = binding.root.context

    fun render(saved: List<SavedGuide>) {
        binding.libraryCount.isVisible = saved.isNotEmpty()
        binding.libraryCount.text = context.getString(R.string.library_all, saved.size)
    }
}
