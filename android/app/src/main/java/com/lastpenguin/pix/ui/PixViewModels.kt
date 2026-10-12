// AI-generated with Claude Code, 2026-10-01, reviewed by Dongje Park
package com.lastpenguin.pix.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.ui.camera.CameraViewModel
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.session.SubjectViewModel

/**
 * Creates every ViewModel with its modules from [AppContainer].
 * In a Fragment: `private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }`.
 * ViewModels shared by several screens use `activityViewModels` instead.
 */
object PixViewModels {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { CameraViewModel(container.cameraController, container.guideRepository) }
        initializer { ReferenceViewModel(container.referenceGuideMaker, container.guideRepository) }
        initializer {
            GenerationViewModel(container.poseGenerator, container.cameraController, container.generationConsent)
        }
        initializer {
            SessionViewModel(
                container.sessionManager,
                container.cameraController,
                container.guideSyncer,
                container.remoteControlHandler,
            )
        }
        initializer {
            SubjectViewModel(container.sessionManager, container.mirrorGuideRepository, container.remoteVideo)
        }
    }

    private val CreationExtras.container: AppContainer
        get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).container
}
