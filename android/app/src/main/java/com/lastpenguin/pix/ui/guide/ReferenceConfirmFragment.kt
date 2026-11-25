// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.fadeTo
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.showOutline
import com.lastpenguin.pix.ui.showNotice
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reference confirm (R&S 6.2): the outline only, with *Use* at the top right and Back at the top left, which returns to
 * the step before, where another photo or pose can be chosen (#199); on the card, whose pick it is when the subject
 * chose the candidate (FR-6.15). In a session the subject sees the outline of a chosen pose too, and a *Use* of theirs
 * that comes first goes back to the camera like this phone's own (Design 2.6.5). A screenshot cut to its photo
 * (FR-2.10) plays the cut once, keeps the photo in the corner, and offers *Use the whole image*. The outline fades in as
 * its person is found, a screenshot's with its cut (Iter3-A13).
 * Owner: PM (#5); the outline on both phones Iter3-P16; the screenshot's cut #174; the top bar Iter4-P2.
 */
class ReferenceConfirmFragment : Fragment(R.layout.fragment_reference_confirm) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }

    /** The state this view showed last: a cut plays only when the person was found while it watched. */
    private var shown: ReferenceUiState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentReferenceConfirmBinding.bind(view)
        shown = null
        binding.screenshotCut.outline = binding.referenceImage

        // Back leaves without the guide: a person still being found and the tip asked for it stop (FR-4.10). It goes
        // to where the reference was chosen, for a photo from Add a pose guide that sheet again (#294).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            viewModel.onConfirmLeft()
            backToChoosing(
                viewModel,
                from = R.id.referenceConfirmFragment,
                toSheet = R.id.action_referenceConfirm_to_addGuide,
                gallery = false,
            )
        }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.useGuideButton.setOnClickListener {
            viewModel.onUseGuide()
            backToShooting(viewModel)
        }
        binding.useWholeImageButton.setOnClickListener { viewModel.onUseWholeImage() }
        leaveWhenTheSessionEnds(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(binding, state) } }
                // The outline alone, in the settings' color and thickness (FR-2.3, FR-3.10).
                launch {
                    val guides = viewModel.uiState.map { (it as? ReferenceUiState.Ready)?.guide }
                    binding.referenceImage.showOutline(settingsViewModel.guideLook, guides)
                }
                launch {
                    viewModel.chosenBy.collect { name ->
                        binding.chosenByText.isVisible = name != null
                        if (name != null) binding.chosenByText.text = getString(R.string.peer_chose_this_pose, name)
                    }
                }
                launch {
                    // The subject's phone shows this outline too while a session is connected (FR-6.15).
                    combine(viewModel.sharing, sessionViewModel.state) { sharing, session ->
                        subjectName(session).takeIf { sharing }
                    }.collect { name ->
                        binding.sharedHint.isVisible = name != null
                        if (name != null) binding.sharedHint.text = getString(R.string.shared_hint, name)
                    }
                }
                launch {
                    // The subject's Use this guide came first: the guide is on the camera, as after this phone's own.
                    viewModel.usedBySubject.collect {
                        subjectName(sessionViewModel.state.value)?.let { name ->
                            val text = getString(R.string.peer_used_guide, name)
                            showNotice(text)
                        }
                        backToShooting(viewModel)
                    }
                }
            }
        }
    }

    /** The connected subject's name while this phone is the photographer; null outside a session. */
    private fun subjectName(session: SessionState): String? =
        (session as? SessionState.Connected)?.takeIf { it.role == Role.PHOTOGRAPHER }?.peer?.displayName

    private fun render(binding: FragmentReferenceConfirmBinding, state: ReferenceUiState) {
        val ready = state as? ReferenceUiState.Ready
        val working = state is ReferenceUiState.Working
        binding.referenceProgress.isVisible = working
        binding.referenceImage.isVisible = ready != null
        binding.referenceStatus.isVisible = working || ready != null
        binding.referenceStatus.setText(if (ready != null) R.string.person_found else R.string.finding_person)
        binding.referenceStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (ready != null) R.drawable.ic_person_check_small else 0,
            0,
            0,
            0,
        )
        binding.useGuideButton.isEnabled = ready != null
        // A screenshot cut to its photo (FR-2.10): the cut plays once, not again when the screen is made anew.
        val crop = ready?.guide?.crop
        val found = ready != null && shown == ReferenceUiState.Working
        binding.screenshotCut.show(crop, play = found)
        // A photo's outline fades in where the spinner was; a screenshot's cut fades it in at its own end.
        if (found && crop == null) {
            binding.referenceImage.alpha = 0f
            binding.referenceImage.fadeTo(1f, Motion.FADE_MS)
        }
        binding.cutChip.isVisible = crop != null
        binding.useWholeImageButton.isVisible = crop != null
        shown = state

        when (state) {
            ReferenceUiState.NoPersonFound -> findNavController().navigate(
                R.id.action_referenceConfirm_to_noPersonFound,
            )

            ReferenceUiState.Failed -> {
                showNotice(R.string.couldnt_use_photo)
                backToShooting(viewModel)
            }

            else -> Unit
        }
    }
}

/**
 * Back on Reference confirm and No person found, the screen [from] (#294): to where the reference was chosen. A photo
 * picked from Add a pose guide opens that sheet again through [toSheet], over the camera or Subject view, and with
 * [gallery] the sheet opens its picker at once. The sheet is a dialog, which closed when the photo was picked, so it
 * cannot wait under these screens. A photo from the library and a generated pose go one step back: to the library,
 * and to Pick a pose or, in a session, the camera. A second Back while the first still moves the screen does nothing.
 */
internal fun Fragment.backToChoosing(
    viewModel: ReferenceViewModel,
    @IdRes from: Int,
    @IdRes toSheet: Int,
    gallery: Boolean,
) {
    val navController = findNavController()
    if (navController.currentDestination?.id != from) return
    val before = navController.previousBackStackEntry?.destination?.id
    val fromSheet = viewModel.fromPhoto && (before == R.id.cameraFragment || before == R.id.subjectFragment)
    if (!fromSheet) {
        navController.popBackStack()
        return
    }
    navController.navigate(
        toSheet,
        bundleOf(
            AddGuideSheet.ARG_FROM_SUBJECT to viewModel.forSubject,
            AddGuideSheet.ARG_OPEN_GALLERY to gallery,
        ),
    )
}

/** Back to the screen *Add a pose guide* was opened from: Subject view for the subject's guide, else the camera. */
internal fun Fragment.backToShooting(viewModel: ReferenceViewModel) {
    findNavController().popBackStack(if (viewModel.forSubject) R.id.subjectFragment else R.id.cameraFragment, false)
}

/**
 * Opened from Subject view, this screen covers it, so Subject view does not see the session end: go back to it, which
 * shows Connection lost or returns to the camera, instead of making a guide for a session that is gone.
 */
internal fun Fragment.leaveWhenTheSessionEnds(viewModel: ReferenceViewModel) {
    if (!viewModel.forSubject) return
    val session = ViewModelProvider(requireActivity(), PixViewModels.Factory)[SessionViewModel::class.java]
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            session.state.collect { state ->
                if (state is SessionState.Ended) {
                    viewModel.onConfirmLeft()
                    backToShooting(viewModel)
                }
            }
        }
    }
}
