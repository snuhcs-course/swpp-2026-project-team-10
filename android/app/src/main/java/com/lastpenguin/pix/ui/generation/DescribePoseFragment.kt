// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import com.lastpenguin.pix.ui.PixViewModels

/**
 * Generate poses (R&S 6.3, FR-4.8): the user describes a pose, picks an idea chip, or lets the server suggest four
 * poses for the scene. Nothing is sent before *Generate 4 poses* or *Skip*.
 * Owner: Server/AI/Sync (#7).
 */
class DescribePoseFragment : Fragment(R.layout.fragment_describe_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDescribePoseBinding.bind(view)
        val state = viewModel.uiState.value
        if (state.phase != GenerationUiState.Phase.DESCRIBING || state.scene == null) {
            // Nothing to describe, for example after the app process was restarted on this screen.
            view.post { if (isAdded) backToCamera() }
            return
        }
        binding.sceneImage.setImageBitmap(state.scene)
        DescriptionForm(
            binding,
            ideas = resources.getStringArray(R.array.pose_ideas).toList(),
            // After a recreation the field restores its own text; otherwise it shows what the ViewModel kept (US-5).
            text = if (savedInstanceState == null) state.description else null,
            rejected = state.descriptionRejected,
            onTextChanged = viewModel::onDescriptionChanged,
            onGenerate = { text ->
                viewModel.generate(text)
                toGenerating()
            },
            onSuggest = {
                viewModel.suggestPoses()
                toGenerating()
            },
        )
        binding.cancelButton.setOnClickListener { backToCamera() }
        // System back is Cancel (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToCamera() }
    }

    private fun toGenerating() {
        val navController = findNavController()
        val started = viewModel.uiState.value.phase == GenerationUiState.Phase.GENERATING
        if (started && navController.currentDestination?.id == R.id.describePoseFragment) {
            hideKeyboard()
            navController.navigate(R.id.action_describePose_to_generating)
        }
    }

    private fun backToCamera() {
        hideKeyboard()
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun hideKeyboard() {
        val view = view ?: return
        WindowCompat.getInsetsController(requireActivity().window, view).hide(WindowInsetsCompat.Type.ime())
    }
}

/**
 * The description field, its counter, the idea chips, and the two buttons of Generate poses (FR-4.8, US-5). Apart
 * from the fragment, so the form can be tested on the real layout without a ViewModel or navigation.
 */
class DescriptionForm(
    private val binding: FragmentDescribePoseBinding,
    ideas: List<String>,
    /** The text to show, or null to keep what the field already holds. */
    text: String?,
    /** The service rejected the last description: say so under the field (US-5). */
    rejected: Boolean,
    onTextChanged: (String) -> Unit,
    onGenerate: (String) -> Unit,
    onSuggest: () -> Unit,
) {
    private val input = binding.descriptionInput

    init {
        // One line of input, so Done closes the keyboard, shown wrapped over up to four lines.
        input.setHorizontallyScrolling(false)
        input.maxLines = 4
        if (text != null) input.setText(text)
        renderCounter()
        input.doAfterTextChanged { changed ->
            onTextChanged(changed?.toString().orEmpty())
            renderCounter()
        }
        binding.descriptionError.isVisible = rejected

        val inflater = LayoutInflater.from(binding.root.context)
        for (idea in ideas) {
            val chip = inflater.inflate(R.layout.item_idea_chip, binding.ideaChips, false) as Chip
            chip.text = idea
            // The chip's text replaces the field's, which the user can then edit (US-5).
            chip.setOnClickListener {
                input.setText(idea)
                input.setSelection(input.length())
            }
            binding.ideaChips.addView(chip)
        }

        binding.generateButton.setOnClickListener { onGenerate(input.text.toString()) }
        binding.suggestButton.setOnClickListener { onSuggest() }
    }

    private fun renderCounter() {
        val context = binding.root.context
        binding.descriptionCounter.text =
            context.getString(R.string.description_counter, input.length(), DESCRIPTION_MAX_LENGTH)
    }
}
