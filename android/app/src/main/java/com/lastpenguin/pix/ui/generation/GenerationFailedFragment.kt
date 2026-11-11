// AI-generated with Claude Code, 2026-11-11, reviewed by Jaewan Park, Joonhyung Han, and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.showFailureTopBar

/**
 * Couldn't create poses (R&S 6.3, FR-4.7, FR-4.13, US-5): why the set failed, in one line ([failureMessage]). Back at
 * the top left returns to Generate poses with the photo and the description; the top right offers *Try again* only
 * where another try may pass ([mayPass]): not after a limit, a refusal, or a photo without a person (#203).
 * Owner: Server/AI/Sync (#7); the top bar Iter4-P2; the cases Iter4-P6.
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

        val failure = state.failure ?: GenerationError.SERVER
        binding.failureMessage.setText(failureMessage(failure))
        if (mayPass(failure)) {
            binding.showFailureTopBar(R.string.try_again, ::describeAgain) {
                viewModel.retry()
                findNavController().navigate(R.id.action_generationFailed_to_generating)
            }
        } else {
            binding.showFailureTopBar(null, ::describeAgain)
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

/** What Couldn't create poses says for [failure] (FR-4.7, #203): the reason alone, in one line. */
@StringRes
internal fun failureMessage(failure: GenerationError): Int = when (failure) {
    GenerationError.OFFLINE -> R.string.generation_offline_message
    GenerationError.TIMEOUT -> R.string.generation_timeout_message
    GenerationError.RATE_LIMITED -> R.string.generation_hourly_limit_message
    GenerationError.DAILY_LIMITED -> R.string.generation_daily_limit_message
    GenerationError.REJECTED -> R.string.description_rejected_message
    GenerationError.DECLINED -> R.string.generation_declined_message
    GenerationError.NO_PERSON -> R.string.generation_no_person_message
    GenerationError.SERVER -> R.string.generation_server_message
}

/** Whether the same request may pass if tried again: after the network, the time limit, or the server failing. */
internal fun mayPass(failure: GenerationError): Boolean =
    failure == GenerationError.OFFLINE || failure == GenerationError.TIMEOUT || failure == GenerationError.SERVER
