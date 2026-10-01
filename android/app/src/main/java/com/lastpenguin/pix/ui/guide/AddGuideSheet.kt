package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationViewModel

/** Add a pose guide (R&S 6.2). Owner: PM (#5); *Generate poses here* starts #7. */
class AddGuideSheet : BottomSheetDialogFragment() {

    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetAddGuideBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetAddGuideBinding.bind(view)

        binding.uploadButton.setOnClickListener {
            // TODO(#5): open the system photo picker, then referenceViewModel.onPhotoPicked(uri).
            findNavController().navigate(R.id.action_addGuide_to_referenceConfirm)
        }
        binding.generateButton.setOnClickListener {
            generationViewModel.startFromCamera()
            findNavController().navigate(R.id.action_addGuide_to_generating)
        }
        binding.cancelButton.setOnClickListener { dismiss() }
    }
}
