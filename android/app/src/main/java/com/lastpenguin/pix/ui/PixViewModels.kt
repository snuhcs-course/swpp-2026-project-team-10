// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park, Sungmin Jo, and Joonhyung Han
package com.lastpenguin.pix.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.partner
import com.lastpenguin.pix.ui.account.AccountViewModel
import com.lastpenguin.pix.ui.account.FriendsViewModel
import com.lastpenguin.pix.ui.camera.CameraViewModel
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.generation.PoseSaveViewModel
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import com.lastpenguin.pix.ui.session.IncomingInvitationsViewModel
import com.lastpenguin.pix.ui.session.InvitationViewModel
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.session.SubjectConfirmViewModel
import com.lastpenguin.pix.ui.session.SubjectDescribeViewModel
import com.lastpenguin.pix.ui.session.SubjectGeneratingViewModel
import com.lastpenguin.pix.ui.session.SubjectPickViewModel
import com.lastpenguin.pix.ui.session.SubjectViewModel
import com.lastpenguin.pix.ui.settings.SettingsViewModel

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
                container.photoSyncer,
                container.appSettings.baseBrightness,
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
                container.personDetector,
                share = container.poseSyncer,
                descriptions = container.descriptionSyncer,
                history = container.promptHistory,
                partner = { container.sessionManager.state.value.partner },
            )
        }
        initializer {
            SessionViewModel(
                container.sessionManager,
                container.cameraController,
                container.guideSyncer,
                container.remoteControlHandler,
                container.mobileDataNotice,
                container.invitationManager,
                container.poseSyncer,
                container.confirmSyncer,
                container.descriptionSyncer,
                container.photoSyncer,
            )
        }
        initializer { InvitationViewModel(container.invitationManager, container.authRepository) }
        initializer { IncomingInvitationsViewModel(container.invitationManager, container.authRepository) }
        initializer {
            SubjectViewModel(
                container.sessionManager,
                container.mirrorGuideRepository,
                container.mirrorReference,
                container.remoteVideo,
                container.savedGuideStore,
                container.poseSyncer,
                container.confirmSyncer,
                container.descriptionSyncer,
            )
        }
        initializer { SubjectPickViewModel(container.sessionManager, container.poseSyncer) }
        initializer {
            SubjectGeneratingViewModel(container.sessionManager, container.poseSyncer, container.descriptionSyncer)
        }
        initializer { PoseSaveViewModel(container.referenceGuideMaker, container.savedGuideStore) }
        initializer { SubjectConfirmViewModel(container.sessionManager, container.confirmSyncer) }
        initializer {
            SubjectDescribeViewModel(container.sessionManager, container.descriptionSyncer, container.promptHistory)
        }
        initializer { AccountViewModel(container.authRepository, container.googleIdTokens) }
        initializer { FriendsViewModel(container.authRepository, container.friendsRepository) }
        initializer { SettingsViewModel(container.appSettings, container.appLanguage, container.authRepository) }
    }

    private val CreationExtras.container: AppContainer
        get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PixApplication).container
}
