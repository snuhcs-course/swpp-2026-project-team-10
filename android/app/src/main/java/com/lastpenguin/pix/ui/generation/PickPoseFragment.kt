// AI-generated with Claude Code, 2026-11-10, reviewed by Joonhyung Han, Jaewan Park, Dongje Park, and Sungmin Jo
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
import androidx.navigation.navOptions
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
 * next. The pose on screen is the selected one, which *Select* at the top right takes. In a session the subject sees
 * the same candidates, and a pick of theirs that comes first goes on here like this phone's own (Design 2.6.5). The ♥
 * on a candidate keeps it in this phone's library (FR-4.14), and *Select* goes on with the guide it saved.
 * Back goes one step back (#199): it asks first, since the set is lost, then goes to Generate poses with the photo
 * and the description kept. Back on Reference confirm comes back here with the set. *Regenerate* makes a new set of
 * the same request. In a session the set goes to the subject's phone, so the choice is final, and there is no
 * *Regenerate*.
 * Owner: Server/AI/Sync (#7). The chosen pose goes to Reference confirm (#5); the set on both phones Iter3-P16; the ♥
 * the library (#171); Back and *Regenerate* Iter4-P2.
 */
class PickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val saves: PoseSaveViewModel by activityViewModels { PixViewModels.Factory }

    /**
     * The set went on to Reference confirm with this screen left under it (outside a session): once this screen leaves
     * the back stack, the guide is on the camera, and the set is let go ([onDestroy]).
     */
    private var handedOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handedOn = savedInstanceState?.getBoolean(HANDED_ON) ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)
        // Back on Reference confirm came back to this set: this phone's choice is undone.
        handedOn = false
        if (subjectName() == null) viewModel.pickAgain()
        val pages = PosePageAdapter(::onHeart)
        binding.setupPosePager(pages, viewModel::select)

        binding.selectButton.setOnClickListener {
            // Read before usePose, which ends the choosing.
            val saved = savedGuide(viewModel.uiState.value)
            viewModel.usePose()?.let { goOn(it, chosenBy = null, saved) }
        }
        binding.backButton.setOnClickListener { askToGoBack() }
        // System back does what Back does (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { askToGoBack() }
        binding.regenerateButton.setOnClickListener { regenerate() }

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
                        binding.regenerateButton.isVisible = subject == null
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(HANDED_ON, handedOn)
    }

    override fun onDestroy() {
        // Left the back stack after the set went on: the guide is on the camera, and the other images are let go.
        if (handedOn && activity?.isChangingConfigurations != true) viewModel.cancel()
        super.onDestroy()
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
     * their pick. Outside a session this screen stays under it with the set, for its Back (#199); in a session the
     * choice is final on both phones, so the set is let go.
     */
    private fun goOn(use: PoseUse, chosenBy: String?, saved: ReferenceGuide?) {
        referenceViewModel.onCandidatePicked(use.image, use.tipSource, chosenBy, saved)
        if (subjectName() == null) {
            handedOn = true
            findNavController().navigate(R.id.action_pickPose_to_referenceConfirm)
            return
        }
        findNavController().navigate(
            R.id.action_pickPose_to_referenceConfirm,
            null,
            navOptions { popUpTo(R.id.pickPoseFragment) { inclusive = true } },
        )
        // Reference confirm has the image now. The scene and the other candidates cannot be reached again, so they
        // are let go; after navigating, so that this screen does not take the empty state for a restart.
        viewModel.cancel()
    }

    /** Back: asks first, as the set is lost, on the subject's phone too in a session. */
    private fun askToGoBack() {
        confirmGoingBack(generating = false, onConfirm = ::describeAgain)
    }

    /**
     * Back confirmed: Generate poses again, with the photo and the description kept, so the user can change what to
     * ask for; the set is gone.
     */
    private fun describeAgain() {
        viewModel.describeAgain()
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.pickPoseFragment) {
            navController.navigate(R.id.action_pickPose_to_describePose)
        }
    }

    /** *Regenerate*: a new set of the same request, on Generating poses. */
    private fun regenerate() {
        viewModel.regenerate()
        val navController = findNavController()
        val started = viewModel.uiState.value.phase == GenerationUiState.Phase.GENERATING
        if (started && navController.currentDestination?.id == R.id.pickPoseFragment) {
            navController.navigate(R.id.action_pickPose_to_generating)
        }
    }

    private fun showPages(binding: FragmentPickPoseBinding, pages: PosePageAdapter, state: GenerationUiState) {
        // A pose that could not be created is left out; the others can still be used (FR-4.7).
        val ready = state.slots().filter { it.event is CandidateEvent.Ready }
        val hearts = saves.hearts.value.inSet(state.setId)
        binding.showPosePages(pages, ready, state.selectedPoseId, viewModel::select, hearts) { view != null }
    }

    private fun render(binding: FragmentPickPoseBinding, pages: PosePageAdapter, state: GenerationUiState) {
        showPages(binding, pages, state)
        val choosing = state.phase == GenerationUiState.Phase.PICKING && state.chosenBy == null
        binding.selectButton.isEnabled = state.selectedImage != null && choosing
        binding.regenerateButton.isEnabled = choosing

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
        // The list is compared off the main thread, so this can come after the screen was left (Select, Back,
        // Regenerate): then there is nothing to select or show.
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

private const val HANDED_ON = "handedOn"
