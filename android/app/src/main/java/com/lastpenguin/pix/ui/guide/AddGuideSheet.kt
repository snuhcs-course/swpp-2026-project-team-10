// AI-generated with Claude Code, 2026-10-27, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import kotlinx.coroutines.launch

/**
 * Add a pose guide, or Replace the pose guide with one on the camera (R&S 6.2), with the saved guides (FR-3.8).
 * Owner: PM (#5); *Generate poses here* starts #7; saved guides #58.
 */
class AddGuideSheet : BottomSheetDialogFragment() {

    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    /** A picked photo opens Reference confirm; closing the picker returns to the camera, guide unchanged (FR-2.7). */
    private val pickPhoto = registerPhotoPicker { uri ->
        if (uri == null) {
            dismiss()
        } else {
            referenceViewModel.onPhotoPicked(uri)
            findNavController().navigate(R.id.action_addGuide_to_referenceConfirm)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetAddGuideBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetAddGuideBinding.bind(view)
        // The Guide button opens this sheet in both cases; with a guide on the camera it is the way to replace it.
        if (referenceViewModel.replacing) {
            binding.titleText.setText(R.string.replace_guide_title)
            binding.bodyText.setText(R.string.replace_guide_body)
            binding.cancelButton.setText(R.string.keep_current_guide)
        }

        binding.uploadButton.setOnClickListener { pickPhoto() }
        binding.generateButton.setOnClickListener {
            if (generationViewModel.needsConsent) showConsentNotice() else takeScenePhoto()
        }
        binding.cancelButton.setOnClickListener { dismiss() }

        val savedGuides =
            SavedGuideList(binding, onUse = { referenceViewModel.onUseSaved(it.id) }, onDelete = ::confirmDelete)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { referenceViewModel.savedGuides.collect(savedGuides::render) }
                launch {
                    referenceViewModel.savedGuideResults.collect { result ->
                        when (result) {
                            SavedGuideResult.USED -> dismiss()

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
     * However the sheet closes, a saved guide still opening must not replace the guide on the camera. A configuration
     * change (the language, the font size) also tears the dialog down, but the sheet comes back, so the guide opens.
     */
    override fun onDismiss(dialog: DialogInterface) {
        if (activity?.isChangingConfigurations != true) referenceViewModel.cancelOpening()
        super.onDismiss(dialog)
    }

    /** A saved guide is deleted only after asking (FR-3.8); the guide on the camera, if it is that one, stays. */
    private fun confirmDelete(guide: SavedGuide) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_saved_guide_title)
            .setMessage(R.string.delete_saved_guide_message)
            .setPositiveButton(R.string.delete) { _, _ -> referenceViewModel.onDeleteSaved(guide.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Asked before the first request only (FR-4.2). *Not now* sends nothing and leaves this sheet open. */
    private fun showConsentNotice() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.consent_title)
            .setMessage(R.string.consent_message)
            .setPositiveButton(R.string.consent_agree) { _, _ ->
                generationViewModel.onConsentGiven()
                takeScenePhoto()
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    /** The poses start from a photo the user takes and confirms on the camera behind this sheet (FR-4.1). */
    private fun takeScenePhoto() {
        generationViewModel.beginScene()
        dismiss()
    }
}
