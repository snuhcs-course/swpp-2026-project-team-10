// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.ui.account.AccountViewModel
import com.lastpenguin.pix.ui.account.FriendsViewModel
import com.lastpenguin.pix.ui.camera.CameraViewModel
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.session.SubjectConfirmViewModel
import com.lastpenguin.pix.ui.session.SubjectPickViewModel
import com.lastpenguin.pix.ui.session.SubjectViewModel

/**
 * Creates every ViewModel with its modules from [AppContainer].
 * In a Fragment: `private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }`.
 * ViewModels shared by several screens use `activityViewModels` instead.
 */
object PixViewModels {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CameraViewModel(
                container.cameraController,
                container.guideRepository,
                container.cameraSettings,
                container.savedGuideStore,
                container.poseTips.onCamera,
            )
        }
        initializer {
            ReferenceViewModel(
                container.referenceGuideMaker,
                container.guideRepository,
                container.savedGuideStore,
                container.poseTips,
                container.guideSyncer,
                container.confirmSyncer,
            )
        }
        initializer {
            GenerationViewModel(
                container.poseGenerator,
                container.cameraController,
                container.generationConsent,
                share = container.poseSyncer,
            )
        }
        initializer {
            SessionViewModel(
                container.sessionManager,
                container.cameraController,
                container.guideSyncer,
                container.remoteControlHandler,
                container.mobileDataNotice,
                container.poseSyncer,
                container.confirmSyncer,
            )
        }
        initializer {
            SubjectViewModel(
                container.sessionManager,
                container.mirrorGuideRepository,
                container.mirrorReference,
                container.remoteVideo,
                container.savedGuideStore,
                container.poseSyncer,
                container.confirmSyncer,
            )
        }
        initializer { SubjectPickViewModel(container.sessionManager, container.poseSyncer) }
        initializer { SubjectConfirmViewModel(container.sessionManager, container.confirmSyncer) }
        initializer { AccountViewModel(container.authRepository, container.googleIdTokens) }
        initializer { FriendsViewModel(container.authRepository, container.friendsRepository) }
    }

    private val CreationExtras.container: AppContainer
        get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).container
}
