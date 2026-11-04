// AI-generated with Claude Code, 2026-11-04, reviewed by Joonhyung Han, Jaewan Park, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import com.lastpenguin.pix.ui.session.SessionViewModel
import kotlinx.coroutines.launch

/**
 * Pick a pose (R&S 6.3): the created candidates one at a time, as large as the screen allows, and a swipe for the
 * next. The pose on screen is the selected one, which *Use this pose* takes. In a session the subject sees the same
 * candidates, and a pick of theirs that comes first goes on here like this phone's own (Design 2.6.5). The ♥ on a
 * candidate keeps it in this phone's library (FR-4.14), and *Use this pose* goes on with the guide it saved.
 * Owner: Server/AI/Sync (#7). The chosen pose goes to Reference confirm (#5); the set on both phones Iter3-P16; the ♥
 * the library (#171).
 */
class PickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val saves: PoseSaveViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)
        val pages = PosePageAdapter(::onHeart)
        binding.setupPosePager(pages, viewModel::select)

        binding.usePoseButton.setOnClickListener {
            // Read before usePose, which ends the choosing.
            val saved = savedGuide(viewModel.uiState.value)
            viewModel.usePose()?.let { goOn(it, chosenBy = null, saved) }
        }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.tryOtherButton.setOnClickListener {
            // Generate poses again, with the photo and the description kept, so the user can change what to ask for.
            viewModel.describeAgain()
            findNavController().navigate(R.id.action_pickPose_to_describePose)
        }
        // Leaving this screen with system back discards the candidates.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToCamera() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(binding, pages, state) } }
                launch { saves.hearts.collect { showPages(binding, pages, viewModel.uiState.value) } }
                launch {
                    saves.results.collect { result ->
                        if (result.setId == viewModel.uiState.value.setId) showPoseSaveNotice(result.notice)
                    }
                }
                launch {
                    // In a session the subject sees these candidates too, and the first choice decides (FR-6.15).
                    sessionViewModel.state.collect { session ->
                        val subject = subjectName(session)
                        binding.sharedHint.isVisible = subject != null
                        if (subject != null) binding.sharedHint.text = getString(R.string.shared_hint, subject)
                    }
                }
            }
        }
    }

    /** The connected subject's name while this phone is the photographer; null outside a session. */
    private fun subjectName(session: SessionState = sessionViewModel.state.value): String? =
        (session as? SessionState.Connected)?.takeIf { it.role == Role.PHOTOGRAPHER }?.peer?.displayName

    /** The ♥ on a candidate (FR-4.14): saves it to this phone's library, or removes it once saved. */
    private fun onHeart(slot: PoseSlot) {
        val setId = viewModel.uiState.value.setId ?: return
        val image = (slot.event as? CandidateEvent.Ready)?.image ?: return
        saves.toggle(PoseKey(setId, slot.pose.id), image)
    }

    /** The guide the ♥ saved for the selected candidate, if it did. */
    private fun savedGuide(state: GenerationUiState): ReferenceGuide? {
        val setId = state.setId ?: return null
        val poseId = state.selectedPoseId ?: return null
        return saves.savedGuide(PoseKey(setId, poseId))
    }

    /**
     * The chosen pose goes to Reference confirm, the same step as an uploaded photo: the person is separated on the
     * phone (FR-4.6), unless its ♥ [saved] it already, whose guide goes on. [chosenBy] names the subject when it was
     * their pick.
     */
    private fun goOn(use: PoseUse, chosenBy: String?, saved: ReferenceGuide?) {
        referenceViewModel.onCandidatePicked(use.image, use.tipSource, chosenBy, saved)
        findNavController().navigate(R.id.action_pickPose_to_referenceConfirm)
        // Reference confirm has the image now. The scene and the other candidates cannot be reached again, so they
        // are let go; after navigating, so that this screen does not take the empty state for a restart.
        viewModel.cancel()
    }

    private fun backToCamera() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun showPages(binding: FragmentPickPoseBinding, pages: PosePageAdapter, state: GenerationUiState) {
        // A pose that could not be created is left out; the others can still be used (FR-4.7).
        val ready = state.slots().filter { it.event is CandidateEvent.Ready }
        val hearts = saves.hearts.value.inSet(state.setId)
        binding.showPosePages(pages, ready, state.selectedPoseId, viewModel::select, hearts) { view != null }
    }

    private fun render(binding: FragmentPickPoseBinding, pages: PosePageAdapter, state: GenerationUiState) {
        showPages(binding, pages, state)
        binding.usePoseButton.isEnabled = state.selectedImage != null && state.chosenBy == null

        val navController = findNavController()
        // The subject's pick came first (Design 2.6.5): it goes on as this phone's own would have.
        val image = state.selectedImage
        val inFront = navController.currentDestination?.id == R.id.pickPoseFragment
        if (state.chosenBy == Role.SUBJECT && state.phase == GenerationUiState.Phase.PICKING && image != null &&
            inFront
        ) {
            goOn(PoseUse(image, state.tipSource), chosenBy = subjectName(), savedGuide(state))
            return
        }

        // There are no candidates to pick from, for example after the app process was restarted on this screen.
        val idle = state.phase == GenerationUiState.Phase.IDLE
        if (idle && navController.currentDestination?.id == R.id.pickPoseFragment) {
            navController.popBackStack(R.id.cameraFragment, false)
        }
    }
}

/** The pager of Pick a pose on either phone (R&S 6.3, 6.6): the neighbors at the sides; the page on screen is selected. */
internal fun FragmentPickPoseBinding.setupPosePager(pages: PosePageAdapter, select: (poseId: String) -> Unit) {
    val pager = candidatePager
    val resources = root.resources
    pager.adapter = pages
    pager.offscreenPageLimit = 1
    // The neighbors show at the sides, so it is clear that the photos can be swiped.
    (pager.getChildAt(0) as RecyclerView).apply {
        val peek = resources.getDimensionPixelSize(R.dimen.pose_page_peek)
        setPadding(peek, 0, peek, 0)
        clipToPadding = false
    }
    pager.setPageTransformer(MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.pose_page_gap)))
    pager.registerOnPageChangeCallback(
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pages.currentList.getOrNull(position)?.let { select(it.slot.pose.id) }
                showPoseCounter(position, pages.itemCount)
            }
        },
    )
}

/**
 * Shows [slots], with the pose [selectedId] on screen and each one's ♥ from [hearts], by pose id: the pose on screen is
 * the selected one, so the one on screen is selected when none of these is. [shown] is false once the screen was left.
 */
internal fun FragmentPickPoseBinding.showPosePages(
    pages: PosePageAdapter,
    slots: List<PoseSlot>,
    selectedId: String?,
    select: (poseId: String) -> Unit,
    hearts: Map<String, Heart>,
    shown: () -> Boolean,
) {
    pages.submitList(slots.map { PosePage(it, hearts[it.pose.id]) }) {
        // The list is compared off the main thread, so this can come after the screen was left (Use this pose,
        // Try other poses): then there is nothing to select or show.
        if (!shown()) return@submitList
        val pager = candidatePager
        val selected = slots.indexOfFirst { it.pose.id == selectedId }
        if (selected < 0) {
            slots.getOrNull(pager.currentItem)?.let { select(it.pose.id) }
        } else if (selected != pager.currentItem) {
            pager.setCurrentItem(selected, false)
        }
        showPoseCounter(pager.currentItem, slots.size)
    }
}

/** "2 / 4" under the photos; nothing to count with a single pose. */
private fun FragmentPickPoseBinding.showPoseCounter(position: Int, count: Int) {
    val context = root.context
    pageCounter.isVisible = count > 1
    pageCounter.text = context.getString(R.string.pose_page_counter, position + 1, count)
    pageCounter.contentDescription = context.getString(R.string.pose_page_counter_description, position + 1, count)
}

/** What a ♥ did (FR-4.14), on either phone's Pick a pose. */
internal fun Fragment.showPoseSaveNotice(notice: PoseSaveNotice) {
    val context = requireContext()
    val text = when (notice) {
        PoseSaveNotice.SAVED -> context.getString(R.string.pose_saved_notice)

        PoseSaveNotice.SAVED_OLDEST_REMOVED ->
            context.getString(R.string.guide_saved_oldest_removed_notice, SavedGuideStore.MAX_SAVED)

        PoseSaveNotice.REMOVED -> context.getString(R.string.pose_removed_notice)

        PoseSaveNotice.NO_PERSON -> context.getString(R.string.pose_no_person_notice)

        PoseSaveNotice.FAILED -> context.getString(R.string.guide_save_failed_notice)
    }
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
