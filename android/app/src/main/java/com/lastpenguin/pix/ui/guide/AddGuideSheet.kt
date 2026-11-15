// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.generation.SituationTiles
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import com.lastpenguin.pix.ui.settings.tutorialFor
import kotlinx.coroutines.launch

/**
 * Add a pose guide, situation first (R&S 6.2, FR-4.8): *What are you taking?* with a tile per situation, the last one
 * chosen selected, then a guide from a photo, *Choose from the gallery* or *My library*, which opens the library and
 * says how many are saved (FR-2.7, FR-3.8, FR-3.11), then *Take a scene photo for poses* for the chosen situation
 * (FR-4.1). With a guide on the camera it replaces that guide, and *Keep the current guide* closes it (FR-3.6). Opened
 * from Subject view ([ARG_FROM_SUBJECT]), the guide goes to the photographer, and the situations and the button wait for
 * the shared steps (FR-6.15, Design 2.6.5).
 * The first time it opens from Camera, its tutorial tips come over it (FR-10.4).
 * Owner: PM (#5); *Generate poses here* starts #7; saved guides #58; from Subject view Iter3-P9; the library #170.
 */
class AddGuideSheet : BottomSheetDialogFragment() {

    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }

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
        referenceViewModel.openFor(arguments?.getBoolean(ARG_FROM_SUBJECT) == true)
        val poses = !referenceViewModel.forSubject
        binding.newPosesSection.isVisible = poses
        binding.generateButton.isVisible = poses
        if (poses && savedInstanceState == null) generationViewModel.followCamera()
        val situations = SituationTiles(binding.situationRowTop, binding.situationRowBottom) {
            generationViewModel.chooseSituation(it)
        }
        // The Guide button opens this sheet in both cases; with a guide on the camera it is the way to replace it.
        when {
            referenceViewModel.replacing -> {
                binding.titleText.setText(R.string.replace_guide_title)
                binding.cancelButton.setText(R.string.keep_current_guide)
                binding.cancelButton.isVisible = true
            }

            !poses -> binding.titleText.setText(R.string.add_guide_title)
        }

        binding.galleryRow.setOnClickListener { pickPhoto() }
        binding.libraryRow.setOnClickListener { findNavController().navigate(R.id.action_addGuide_to_library) }
        binding.generateButton.setOnClickListener {
            if (generationViewModel.needsConsent) showConsentNotice() else takeScenePhoto()
        }
        binding.cancelButton.setOnClickListener { dismiss() }

        val savedGuides = SavedGuideList(binding)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Its tutorial tips the first time it opens (FR-10.4); from Subject view it lacks the poses they point at.
                if (!referenceViewModel.forSubject) {
                    launch { showTutorialWhenDue(settingsViewModel.tutorialFor(TutorialScreen.ADD_GUIDE)) }
                }
                launch { referenceViewModel.savedGuides.collect(savedGuides::render) }
                launch { generationViewModel.situationChoice.collect(situations::render) }
            }
        }
    }

    /** Opens whole, so the situations and the button show without a drag; Back or a drag down closes it. */
    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
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

    companion object {
        /** The navigation argument set by Subject view's *Guide* button. */
        const val ARG_FROM_SUBJECT = "fromSubject"
    }
}
