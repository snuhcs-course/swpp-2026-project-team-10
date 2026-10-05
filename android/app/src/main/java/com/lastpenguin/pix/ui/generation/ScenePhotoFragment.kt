package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.databinding.FragmentScenePhotoBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Scene photo (R&S 6.3): the camera with a shutter, then the photo to confirm with *Use this photo* or
 * *Shoot again*. Nothing is sent before the photo is confirmed (FR-4.1).
 * Owner: Server/AI/Sync (#7).
 */
class ScenePhotoFragment : Fragment(R.layout.fragment_scene_photo) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentScenePhotoBinding.bind(view)
        // The same fit-centered 3:4 view as the Camera screen, so the photo is what the user framed there.
        binding.scenePreview.scaleType = PreviewView.ScaleType.FIT_CENTER
        binding.scenePreview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        // The camera follows this screen's view, as it follows the Camera screen's view there.
        viewModel.bindCamera(viewLifecycleOwner, binding.scenePreview.surfaceProvider)

        binding.shutterButton.setOnClickListener { viewModel.takeScene() }
        binding.cancelButton.setOnClickListener { backToCamera() }
        binding.usePhotoButton.setOnClickListener {
            if (viewModel.uiState.value.phase != GenerationUiState.Phase.REVIEWING) return@setOnClickListener
            viewModel.useScene()
            findNavController().navigate(R.id.action_scenePhoto_to_generating)
        }
        binding.shootAgainButton.setOnClickListener { viewModel.retakeScene() }
        // System back does the same as the secondary action (R&S 6.9): Shoot again on the photo, Cancel on the camera.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (viewModel.uiState.value.phase == GenerationUiState.Phase.REVIEWING) {
                viewModel.retakeScene()
            } else {
                backToCamera()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.uiState, viewModel.cameraStatus, ::Pair).collect { (state, camera) ->
                    render(binding, state, camera)
                }
            }
        }
    }

    private fun backToCamera() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun render(binding: FragmentScenePhotoBinding, state: GenerationUiState, camera: CameraStatus) {
        val reviewing = state.phase == GenerationUiState.Phase.REVIEWING
        binding.sceneTitle.setText(if (reviewing) R.string.scene_review_title else R.string.scene_title)
        binding.sceneBody.setText(if (reviewing) R.string.scene_review_body else R.string.scene_body)
        binding.sceneStill.isVisible = reviewing
        binding.sceneStill.setImageBitmap(if (reviewing) state.scene else null)
        binding.framingControls.isVisible = !reviewing
        binding.reviewControls.isVisible = reviewing

        val starting = camera == CameraStatus.IDLE || camera == CameraStatus.STARTING
        binding.sceneProgress.isVisible = !reviewing && starting
        binding.shutterButton.isEnabled = camera == CameraStatus.READY
        val message = when {
            reviewing -> null
            camera == CameraStatus.UNAVAILABLE -> R.string.camera_unavailable
            state.shotFailed -> R.string.scene_shot_failed
            else -> null
        }
        binding.sceneMessage.isVisible = message != null
        message?.let(binding.sceneMessage::setText)

        // No scene photo is being taken, for example after the app process was restarted on this screen.
        val navController = findNavController()
        val idle = state.phase == GenerationUiState.Phase.IDLE
        if (idle && navController.currentDestination?.id == R.id.scenePhotoFragment) {
            navController.popBackStack(R.id.cameraFragment, false)
        }
    }
}
