// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupMenu
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.DialogTextInputBinding
import com.lastpenguin.pix.databinding.FragmentLibraryBinding
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.ui.NoticeLength
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.title
import com.lastpenguin.pix.ui.showNotice
import java.text.NumberFormat
import kotlinx.coroutines.launch

/**
 * The library (FR-3.11, R&S 6.2), from *All* on Add a pose guide's *My library*: every guide kept, newest first, with
 * its kind, under *All*, which it opens on, or *Favorites* (#297), and filtered by the situation a pose was made for
 * (#270). Each guide's ♥ adds it to Favorites or takes it out, and it shows its
 * situation and memo; its pencil edits the memo, which is kept and shown only here. A tap puts the guide on the camera
 * as a saved guide does on the sheet, or, opened from Subject view, offers it to the photographer (FR-6.16). A long
 * press, or *Select* under ⋮, selects guides as a gallery app does (#297): a tap then selects one or no longer does,
 * the header says how many with *Select all*, and *Delete* shows at the bottom and asks first; ✕ or Back stops
 * selecting. *Select all* under ⋮ starts selecting with every guide listed. Guides come only from the poses and the
 * photo guides used, so the library adds none itself. Opened from Subject view, it covers Subject view, so it watches the session itself
 * (`leaveWhenTheSessionEnds`).
 * Owner: the library (#170); situations and memos (#270); favorites and selection (#297).
 */
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLibraryBinding.bind(view)
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        // Back stops selecting first, as a gallery app does.
        val stopSelecting = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, false) {
            viewModel.onStopSelecting()
        }
        binding.moreButton.setOnClickListener { showMore(it) }
        binding.selectAllButton.setOnClickListener { viewModel.onSelectAll() }
        binding.deleteSelectedButton.setOnClickListener {
            confirmDelete(viewModel.library.value.selection.orEmpty())
        }
        filters(binding).forEach { (filter, button) -> button.setOnClickListener { viewModel.onLibraryFilter(filter) } }

        val adapter = LibraryAdapter(
            showThumbnail = { image, guide ->
                image.showThumbnail(guide, viewModel, viewLifecycleOwner.lifecycleScope)
            },
            onUse = { viewModel.onUseSaved(it.id) },
            onSelect = { viewModel.onStartSelecting(it.id) },
            onToggle = { viewModel.onToggleSelected(it.id) },
            onMemo = ::editMemo,
            onFavorite = { viewModel.onToggleFavorite(it.id) },
        )
        binding.libraryGrid.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.libraryGrid.adapter = adapter
        leaveWhenTheSessionEnds(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.library.collect { page ->
                        stopSelecting.isEnabled = page.selecting
                        render(binding, adapter, page)
                    }
                }
                launch {
                    viewModel.favoriteFailures.collect {
                        showNotice(R.string.guide_save_failed_notice, NoticeLength.LONG)
                    }
                }
                launch {
                    viewModel.memoFailures.collect { showNotice(R.string.library_memo_failed, NoticeLength.LONG) }
                }
                launch {
                    viewModel.savedGuideResults.collect { result ->
                        when (result) {
                            SavedGuideResult.USED -> backToShooting(viewModel)

                            SavedGuideResult.FAILED ->
                                showNotice(R.string.saved_guide_open_failed, NoticeLength.LONG)
                        }
                    }
                }
            }
        }
    }

    /**
     * However the library is left, a saved guide still opening must not replace the guide on the camera. A
     * configuration change (the language, the font size) brings the screen back, so the guide still opens.
     */
    override fun onDestroyView() {
        if (activity?.isChangingConfigurations != true) viewModel.cancelOpening()
        super.onDestroyView()
    }

    private fun render(binding: FragmentLibraryBinding, adapter: LibraryAdapter, page: LibraryPage) {
        val total = page.count(LibraryFilter.ALL)
        renderHeader(binding, page, total)
        binding.filterRow.isVisible = total > 0
        filters(binding).forEach { (filter, button) ->
            button.text = getString(filter.label, page.count(filter))
            button.isSelected = filter == page.filter
        }
        renderSituations(binding, page)
        adapter.submitList(page.guides)
        adapter.selection = page.selection
        binding.emptyState.isVisible = page.guides.isEmpty()
        // Nothing kept yet, or no favorite yet.
        binding.emptyTitle.setText(if (total == 0) R.string.library_empty else R.string.library_empty_favorites)
        binding.emptyBody.setText(
            if (total ==
                0
            ) {
                R.string.library_empty_body
            } else {
                R.string.library_empty_favorites_body
            },
        )
    }

    /**
     * The header: the title and the count with ⋮, or, while selecting (#297), ✕, how many are selected, and *Select
     * all*; *Delete* at the bottom, off while nothing is selected.
     */
    private fun renderHeader(binding: FragmentLibraryBinding, page: LibraryPage, total: Int) {
        val selection = page.selection
        if (selection == null) {
            binding.titleText.setText(R.string.library_title)
            binding.titleText.contentDescription = getString(R.string.library_title_description, total)
            binding.backButton.setImageResource(R.drawable.ic_arrow_back)
            binding.backButton.contentDescription = getString(R.string.back)
            binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        } else {
            binding.titleText.text =
                resources.getQuantityString(R.plurals.library_selected, selection.size, selection.size)
            binding.titleText.contentDescription = null
            binding.backButton.setImageResource(R.drawable.ic_close)
            binding.backButton.contentDescription = getString(R.string.library_close_selection)
            binding.backButton.setOnClickListener { viewModel.onStopSelecting() }
        }
        binding.countText.text = NumberFormat.getIntegerInstance().format(total)
        binding.countText.isVisible = selection == null
        binding.moreButton.isVisible = selection == null && page.guides.isNotEmpty()
        binding.selectAllButton.isVisible = selection != null
        binding.selectAllButton.isEnabled = selection != null && selection.size < page.guides.size
        binding.deleteSelectedButton.isVisible = selection != null
        binding.deleteSelectedButton.isEnabled = !selection.isNullOrEmpty()
    }

    /** ⋮ (#297): *Select*, as a long press does, and *Select all*, which selects every guide listed. */
    private fun showMore(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.library_more, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.selectGuides -> viewModel.onStartSelecting()
                    R.id.selectAllGuides -> viewModel.onSelectAll()
                }
                true
            }
            show()
        }
    }

    /**
     * The situations' chips (#270), in the order of *Add a pose guide*'s tiles, each with its count; the chosen one
     * checked. Built again only when the situations or their counts change, so a chip never goes from under the finger.
     */
    private fun renderSituations(binding: FragmentLibraryBinding, page: LibraryPage) {
        binding.situationScroll.isVisible = page.situations.isNotEmpty()
        val chips = binding.situationChips
        if (chips.tag != page.situations) {
            chips.tag = page.situations
            chips.removeAllViews()
            page.situations.forEach { (situation, count) ->
                val chip = layoutInflater.inflate(R.layout.item_option_chip, chips, false) as Chip
                chip.tag = situation
                chip.text = getString(R.string.library_situation_chip, getString(situation.title), count)
                chip.setOnClickListener { viewModel.onLibrarySituation(situation) }
                chips.addView(chip)
            }
        }
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as Chip
            chip.isChecked = chip.tag as Situation == page.situation
        }
    }

    /**
     * The memo of [guide] (FR-3.11, #270): one line of at most [SavedGuideStore.MAX_MEMO] characters, kept with *Save*;
     * an empty one removes it.
     */
    private fun editMemo(guide: SavedGuide) {
        val field = DialogTextInputBinding.inflate(layoutInflater)
        field.input.setHint(R.string.library_memo_hint)
        field.input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        field.input.filters = arrayOf(InputFilter.LengthFilter(SavedGuideStore.MAX_MEMO))
        field.input.setText(guide.memo)
        field.input.setSelection(field.input.length())
        fun count() {
            field.ruleText.text =
                getString(R.string.description_counter, field.input.length(), SavedGuideStore.MAX_MEMO)
        }
        // The count is read with the field; said again at every key, it would cover what is typed.
        field.ruleText.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        field.input.doAfterTextChanged { count() }
        count()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.library_memo_title)
            .setView(field.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ -> viewModel.onSaveMemo(guide.id, field.input.text.toString()) }
            .show()
        field.input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    /** Guides are deleted only after asking (FR-3.8, #297); the guide on the camera, if it is one, stays. */
    private fun confirmDelete(ids: Set<String>) {
        if (ids.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.delete_guides_title, ids.size, ids.size))
            .setMessage(R.string.delete_saved_guide_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.onDeleteSaved(ids) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun filters(binding: FragmentLibraryBinding): List<Pair<LibraryFilter, Button>> = listOf(
        LibraryFilter.ALL to binding.filterAll,
        LibraryFilter.FAVORITES to binding.filterFavorites,
    )

    private val LibraryFilter.label: Int
        get() = when (this) {
            LibraryFilter.ALL -> R.string.library_all
            LibraryFilter.FAVORITES -> R.string.library_favorites
        }

    private companion object {
        const val COLUMNS = 2
    }
}
