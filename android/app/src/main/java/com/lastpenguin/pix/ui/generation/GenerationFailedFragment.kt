// AI-generated with Claude Code, 2026-11-10, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.showFailureTopBar

/**
 * Couldn't create poses (R&S 6.3, FR-4.7, FR-4.13, US-5). Back at the top left returns to Generate poses with the
 * photo and the description; the top right retries: *Try again*, or *Shoot again* when the server found no one. A
 * refused description has no retry there, since the same one would be refused again (#199).
 * Owner: Server/AI/Sync (#7); the top bar Iter4-P2.
 */
class GenerationFailedFragment : Fragment(R.layout.fragment_failure) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        // The scene photo is kept for Try again (FR-4.7).
        val state = viewModel.uiState.value
        val scene = state.scene
        if (scene == null) {
            // Nothing failed here: the app process was restarted on this screen and the photo is gone.
            view.post { if (isAdded) backToCamera() }
            return
        }
        binding.failureTitle.setText(R.string.generation_failed_title)
        binding.failurePhoto.isVisible = true
        binding.failurePhotoImage.setImageBitmap(scene)

        when {
            // The server found no one in the photo (FR-4.13): only another photo helps.
            state.noPerson -> {
                binding.failureMessage.setText(R.string.generation_no_person_message)
                binding.showFailureTopBar(R.string.shoot_again, ::describeAgain) {
                    viewModel.beginScene()
                    findNavController().popBackStack(R.id.cameraFragment, false)
                }
            }

            // The service refused the description: Back changes it (US-5).
            state.descriptionRejected -> {
                binding.failureMessage.text = getString(R.string.description_rejected_message, state.description.trim())
                binding.showFailureTopBar(null, ::describeAgain)
            }

            else -> {
                binding.failureMessage.setText(R.string.generation_failed_message)
                binding.showFailureTopBar(R.string.try_again, ::describeAgain) {
                    viewModel.retry()
                    findNavController().navigate(R.id.action_generationFailed_to_generating)
                }
            }
        }
        // System back does what Back does (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { describeAgain() }
    }

    /** Back: Generate poses with the photo and the description kept. */
    private fun describeAgain() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.generationFailedFragment) return
        viewModel.describeAgain()
        navController.navigate(R.id.action_generationFailed_to_describePose)
    }

    private fun backToCamera() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }
}
