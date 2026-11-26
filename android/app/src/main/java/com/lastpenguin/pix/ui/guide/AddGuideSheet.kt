// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Dongje Park
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
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.generation.SituationTiles
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import com.lastpenguin.pix.ui.settings.tutorialFor
import kotlinx.coroutines.launch

/**
 * Add a pose guide (R&S 6.2): *What are you taking?* with a tile per situation, each the button that takes the scene
 * photo for it (FR-4.1, FR-4.8); then a guide from a photo, *Choose from the gallery* or *My library*, which opens the
 * library and says how many are saved (FR-2.1, FR-3.8, FR-3.11). With a guide on the camera it replaces that guide, and
 * *Keep the current guide* closes it (FR-3.6). Opened from Subject view ([ARG_FROM_SUBJECT]), the guide goes to the
 * photographer, and the situations wait for the shared steps (FR-6.15, Design 2.6.5). A tile dims and shrinks under the
 * finger and goes on at release; once a tap goes on, the sheet ignores every other (#243). The first time it opens from
 * Camera, its tutorial tips come over it (FR-10.4). Back on Reference confirm and on No person found open it again for
 * a photo picked from it, the second with the gallery's picker over it ([ARG_OPEN_GALLERY], #294).
 * Owner: PM (#5); *Generate poses here* starts #7; saved guides #58; from Subject view Iter3-P9; the library #170; the
 * tiles as buttons and the picker kept open #243.
 */
class AddGuideSheet : BottomSheetDialogFragment() {

    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    /**
     * Set once a tap goes on from this sheet (#243): to the scene photo, the library, the picker, or the camera. Every
     * later tap is ignored, so a second tap in the moment the sheet takes to leave starts nothing else.
     */
    private var leaving = false

    /** A picked photo opens Reference confirm; closing the picker stays here, any guide unchanged (FR-2.7). */
    private val pickPhoto = registerPhotoPicker { uri ->
        if (uri != null) {
            referenceViewModel.onPhotoPicked(uri)
            findNavController().navigate(R.id.action_addGuide_to_referenceConfirm)
        } else {
            leaving = false
        }
    }

    /** Runs [go] for the first tap that goes on from this sheet, and ignores every tap after it. */
    private fun goOn(go: () -> Unit) {
        if (leaving) return
        leaving = true
        go()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetAddGuideBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetAddGuideBinding.bind(view)
        referenceViewModel.openFor(arguments?.getBoolean(ARG_FROM_SUBJECT) == true)
        // From Subject view too (#276): the subject picks the situation, and the photographer's phone goes on from
        // there as if its own user had picked it, with the scene photo taken there (FR-6.15).
        val forSubject = referenceViewModel.forSubject
        val situations = SituationTiles(binding.situationRowTop, binding.situationRowBottom, { leaving }) { situation ->
            // This phone's own cameras decide only a scene photo taken here.
            val go = if (forSubject) ({ askThePhotographer(situation) }) else ::takeScenePhoto
            when {
                leaving || (!forSubject && !generationViewModel.chooseSituation(situation)) -> false

                generationViewModel.needsConsent -> {
                    showConsentNotice(forSubject, go)
                    false
                }

                else -> {
                    goOn(go)
                    true
                }
            }
        }
        // The Guide button opens this sheet in both cases; with a guide on the camera it is the way to replace it.
        if (referenceViewModel.replacing) {
            binding.titleText.setText(R.string.replace_guide_title)
            binding.cancelButton.setText(R.string.keep_current_guide)
            binding.cancelButton.isVisible = true
        }

        binding.galleryRow.setOnClickListener { goOn(pickPhoto) }
        binding.libraryRow.setOnClickListener {
            goOn { findNavController().navigate(R.id.action_addGuide_to_library) }
        }
        binding.cancelButton.setOnClickListener { goOn(::dismiss) }
        // Back on No person found opens this sheet with its gallery (#294): the picker at once, as a tap on its row,
        // and only for the sheet as it first opens, not for one made anew while the picker is up.
        if (savedInstanceState == null && arguments?.getBoolean(ARG_OPEN_GALLERY) == true) goOn(pickPhoto)

        val savedGuides = SavedGuideList(binding)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Its tutorial tips the first time it opens from Camera (FR-10.4); from Subject view it shows none.
                if (!forSubject) {
                    launch { showTutorialWhenDue(settingsViewModel.tutorialFor(TutorialScreen.ADD_GUIDE)) }
                }
                launch { referenceViewModel.savedGuides.collect(savedGuides::render) }
                // The subject's selfie is taken with the photographer's front camera, which answers when it has none.
                if (forSubject) {
                    situations.render(true)
                } else {
                    launch { generationViewModel.selfieAvailable.collect(situations::render) }
                }
            }
        }
    }

    /** Opens whole, so the situations show without a drag; Back or a drag down closes it. */
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

    /**
     * Asked before the first request only (FR-4.2). *Not now* sends nothing and leaves this sheet open. On the
     * subject's phone it says that the photographer takes the photo (#276), and agreeing goes [on] to ask them.
     */
    private fun showConsentNotice(forSubject: Boolean, on: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.consent_title)
            .setMessage(if (forSubject) R.string.consent_message_subject else R.string.consent_message)
            .setPositiveButton(R.string.consent_agree) { _, _ ->
                generationViewModel.onConsentGiven()
                goOn(on)
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    /**
     * From Subject view: the photographer's phone is asked to begin the poses for [situation] (FR-6.15, #276). What
     * became of it is said on Subject view, which this sheet gives way to.
     */
    private fun askThePhotographer(situation: Situation) {
        sessionViewModel.askForPoses(situation)
        dismiss()
    }

    /** The poses start from a photo, for the tile's situation, that the user takes on the camera behind it (FR-4.1). */
    private fun takeScenePhoto() {
        generationViewModel.beginScene()
        dismiss()
    }

    companion object {
        /** The navigation argument set by Subject view's *Guide* button. */
        const val ARG_FROM_SUBJECT = "fromSubject"

        /** The navigation argument set by Back on No person found: the gallery's picker opens with the sheet (#294). */
        const val ARG_OPEN_GALLERY = "openGallery"
    }
}
