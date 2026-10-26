// AI-generated with Claude Code, 2026-10-24, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
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
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import kotlinx.coroutines.launch

/**
 * Pick a pose (R&S 6.3): the created candidates one at a time, as large as the screen allows, and a swipe for the
 * next. The pose on screen is the selected one, which *Use this pose* takes. Owner: Server/AI/Sync (#7). The chosen
 * pose goes to Reference confirm (#5).
 */
class PickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)
        val pages = PosePageAdapter()
        setupPager(binding, pages)

        binding.usePoseButton.setOnClickListener {
            val image = viewModel.uiState.value.selectedImage ?: return@setOnClickListener
            // The same step as an uploaded photo: the person is separated on the phone (FR-4.6).
            referenceViewModel.onCandidatePicked(image, viewModel.uiState.value.tipSource)
            findNavController().navigate(R.id.action_pickPose_to_referenceConfirm)
            // Reference confirm has the image now. The scene and the other candidates cannot be reached again, so
            // they are let go; after navigating, so that this screen does not take the empty state for a restart.
            viewModel.cancel()
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
                viewModel.uiState.collect { state -> render(binding, pages, state) }
            }
        }
    }

    /** "2 / 4" under the photos; nothing to count with a single pose. */
    private fun showCounter(binding: FragmentPickPoseBinding, position: Int, count: Int) {
        binding.pageCounter.isVisible = count > 1
        binding.pageCounter.text = getString(R.string.pose_page_counter, position + 1, count)
        binding.pageCounter.contentDescription = getString(R.string.pose_page_counter_description, position + 1, count)
    }

    private fun backToCamera() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun setupPager(binding: FragmentPickPoseBinding, pages: PosePageAdapter) {
        val pager = binding.candidatePager
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
                    pages.currentList.getOrNull(position)?.let { viewModel.select(it.pose.id) }
                    showCounter(binding, position, pages.itemCount)
                }
            },
        )
    }

    private fun render(binding: FragmentPickPoseBinding, pages: PosePageAdapter, state: GenerationUiState) {
        // A pose that could not be created is left out; the others can still be used (FR-4.7).
        val ready = state.slots().filter { it.event is CandidateEvent.Ready }
        pages.submitList(ready) {
            // The list is compared off the main thread, so this can come after the screen was left (Use this pose,
            // Try other poses): then there is nothing to select or show.
            if (view == null) return@submitList
            // The pose on screen is the selected one: select it when none of these is, or show the selected one.
            val pager = binding.candidatePager
            val selected = ready.indexOfFirst { it.pose.id == state.selectedPoseId }
            if (selected < 0) {
                ready.getOrNull(pager.currentItem)?.let { viewModel.select(it.pose.id) }
            } else if (selected != pager.currentItem) {
                pager.setCurrentItem(selected, false)
            }
            showCounter(binding, pager.currentItem, ready.size)
        }
        binding.usePoseButton.isEnabled = state.selectedImage != null

        // There are no candidates to pick from, for example after the app process was restarted on this screen.
        val navController = findNavController()
        val idle = state.phase == GenerationUiState.Phase.IDLE
        if (idle && navController.currentDestination?.id == R.id.pickPoseFragment) {
            navController.popBackStack(R.id.cameraFragment, false)
        }
    }
}
