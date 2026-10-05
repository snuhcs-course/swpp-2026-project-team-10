package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationViewModel

/** Add a pose guide (R&S 6.2). Owner: PM (#5); *Generate poses here* starts #7. */
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

        binding.uploadButton.setOnClickListener { pickPhoto() }
        binding.generateButton.setOnClickListener {
            if (generationViewModel.needsConsent) showConsentNotice() else takeScenePhoto()
        }
        binding.cancelButton.setOnClickListener { dismiss() }
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
