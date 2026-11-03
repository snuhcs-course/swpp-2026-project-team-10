// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentLibraryBinding
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.ui.PixViewModels
import java.text.NumberFormat
import kotlinx.coroutines.launch

/**
 * The library (FR-3.11, R&S 6.2), from *All* on Add a pose guide's *My library*: every saved guide, newest first, with
 * its kind, filtered by kind. A tap puts the guide on the camera as a saved guide does on the sheet, or, opened from
 * Subject view, offers it to the photographer (FR-6.16); a long press deletes it after asking. *Add photo* goes to
 * Reference confirm, whose back returns here. Opened from Subject view, it covers Subject view, so it watches the
 * session itself (`leaveWhenTheSessionEnds`).
 * Owner: the library (#170).
 */
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    /** A picked photo opens Reference confirm; closing the picker stays here. */
    private val pickPhoto = registerPhotoPicker { uri ->
        if (uri != null) {
            viewModel.onPhotoPicked(uri)
            findNavController().navigate(R.id.action_library_to_referenceConfirm)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLibraryBinding.bind(view)
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.addPhotoButton.setOnClickListener { pickPhoto() }
        filters(binding).forEach { (filter, button) -> button.setOnClickListener { viewModel.onLibraryFilter(filter) } }

        val adapter = LibraryAdapter(
            showThumbnail = { image, guide ->
                image.showThumbnail(guide, viewModel, viewLifecycleOwner.lifecycleScope)
            },
            onUse = { viewModel.onUseSaved(it.id) },
            onDelete = ::confirmDelete,
        )
        binding.libraryGrid.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.libraryGrid.adapter = adapter
        leaveWhenTheSessionEnds(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.library.collect { render(binding, adapter, it) } }
                launch {
                    viewModel.savedGuideResults.collect { result ->
                        when (result) {
                            SavedGuideResult.USED -> backToShooting(viewModel)

                            SavedGuideResult.FAILED ->
                                Toast.makeText(
                                    requireContext(),
                                    R.string.saved_guide_open_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
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
        binding.countText.text = NumberFormat.getIntegerInstance().format(total)
        binding.titleText.contentDescription = getString(R.string.library_title_description, total)
        binding.filterRow.isVisible = total > 0
        filters(binding).forEach { (filter, button) ->
            button.text = getString(filter.label, page.count(filter))
            button.isSelected = filter == page.filter
        }
        adapter.submitList(page.guides)
        binding.emptyState.isVisible = page.guides.isEmpty()
        binding.emptyTitle.setText(if (total == 0) R.string.library_empty else R.string.library_empty_filter)
        binding.emptyBody.isVisible = total == 0
    }

    /** A saved guide is deleted only after asking (FR-3.8); the guide on the camera, if it is that one, stays. */
    private fun confirmDelete(guide: SavedGuide) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_saved_guide_title)
            .setMessage(R.string.delete_saved_guide_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.onDeleteSaved(guide.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun filters(binding: FragmentLibraryBinding): List<Pair<LibraryFilter, Button>> = listOf(
        LibraryFilter.ALL to binding.filterAll,
        LibraryFilter.PHOTOS to binding.filterPhotos,
        LibraryFilter.GENERATED to binding.filterGenerated,
    )

    private val LibraryFilter.label: Int
        get() = when (this) {
            LibraryFilter.ALL -> R.string.library_all
            LibraryFilter.PHOTOS -> R.string.library_photos
            LibraryFilter.GENERATED -> R.string.library_generated
        }

    private companion object {
        const val COLUMNS = 2
    }
}
