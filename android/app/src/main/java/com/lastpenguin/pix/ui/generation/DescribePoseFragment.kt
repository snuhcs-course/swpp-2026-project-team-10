// AI-generated with Claude Code, 2026-11-03, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import com.lastpenguin.pix.generation.PromptEntry
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.partner
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Generate poses (R&S 6.3, FR-4.8): the user describes a pose, picks a description used before (FR-4.11), or lets the
 * server suggest four poses for the scene. Nothing is sent before *Generate 4 poses* or *Skip*. In a session the subject sees this step
 * too: one field at a time holds the description, and the subject's *Generate 4 poses* or *Skip* goes on here like this
 * phone's own (Design 2.6.5).
 * Owner: Server/AI/Sync (#7); the step on both phones Iter3-P16.
 */
class DescribePoseFragment : Fragment(R.layout.fragment_describe_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var form: DescriptionForm? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDescribePoseBinding.bind(view)
        val state = viewModel.uiState.value
        if (state.phase != GenerationUiState.Phase.DESCRIBING || state.scene == null) {
            // Nothing to describe, for example after the app process was restarted on this screen.
            view.post { if (isAdded) backToCamera() }
            return
        }
        binding.sceneImage.showScene(state.scene)
        val form = DescriptionForm(
            binding,
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
            onFocusChanged = { focused -> viewModel.onDescriptionFocus(focused) },
            onForget = viewModel::forget,
        )
        this.form = form
        binding.cancelButton.setOnClickListener { backToCamera() }
        // System back is Cancel (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToCamera() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.uiState, sessionViewModel.state, viewModel.usedBefore, ::Triple)
                    .collect { (generation, session, used) ->
                        // In a session the subject writes the description too, one field at a time (FR-6.15).
                        val connected = (session as? SessionState.Connected)?.takeIf { it.role == Role.PHOTOGRAPHER }
                        val subject = connected?.peer?.displayName
                        val typing = subject.takeIf { generation.descriptionWriter == Role.SUBJECT }
                        form.showShared(generation.description, typing, subject)
                        // The descriptions used before, with the friend of an invitation's session, also while it reconnects (FR-4.11).
                        form.showHistory(used, session.partner)
                        // The subject's Generate 4 poses or Skip came first: it goes on as this phone's own.
                        toGenerating()
                    }
            }
        }
    }

    override fun onPause() {
        // Leaving the screen ends the typing, so the subject's field can take the description.
        form?.stopTyping()
        super.onPause()
    }

    override fun onDestroyView() {
        form = null
        super.onDestroyView()
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
 * The description field, its counter, the descriptions used before (FR-4.11), and the two buttons of Generate poses
 * (FR-4.8, US-5), on either phone. Apart from the fragments, so the form can be tested on the real layout without a
 * ViewModel or navigation. [showHistory] lists the descriptions used before as chips, all of them or, through the
 * switch, those used with the session's friend; a chip fills the field, and its ✕ asks [onForget] to drop it.
 * In a session, the field holds the description while it is focused (Design 2.6.5): Done, a closed keyboard, and
 * leaving the screen end that, and [showShared] shows what the other phone wrote and waits while it types.
 */
class DescriptionForm(
    private val binding: FragmentDescribePoseBinding,
    /** The text to show, or null to keep what the field already holds. */
    text: String?,
    /** The service rejected the last description: say so under the field (US-5). */
    rejected: Boolean,
    onTextChanged: (String) -> Unit,
    onGenerate: (String) -> Unit,
    onSuggest: () -> Unit,
    /** The field took or lost focus. */
    onFocusChanged: (Boolean) -> Unit = {},
    /** ✕ on a description used before: its text, which leaves the history (FR-4.11). */
    private val onForget: (String) -> Unit = {},
) {
    private val input = binding.descriptionInput

    /** True while [showShared] sets the other phone's text, which is not this phone's typing. */
    private var showing = false

    /** Whether the keyboard was shown at the last insets, so its closing can be told. */
    private var keyboardShown = false

    /** The descriptions used before and the session's friend, as [showHistory] gave them last. */
    private var usedBefore: List<PromptEntry> = emptyList()
    private var partner: SessionPartner? = null

    /** The switch: the whole history, or the one with [partner]. */
    private var showingAll = true

    /** The other person types, or the link is paused: the chips wait with the field. */
    private var locked = false

    init {
        // One line of input, so Done closes the keyboard, shown wrapped over up to four lines.
        input.setHorizontallyScrolling(false)
        input.maxLines = 4
        if (text != null) input.setText(text)
        renderCounter()
        input.doAfterTextChanged { changed ->
            if (!showing) onTextChanged(changed?.toString().orEmpty())
            renderCounter()
        }
        input.setOnFocusChangeListener { _, focused -> onFocusChanged(focused) }
        // Done and a closed keyboard end the typing; the keyboard still closes as before.
        input.setOnEditorActionListener { view, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) view.clearFocus()
            false
        }
        ViewCompat.setOnApplyWindowInsetsListener(input) { view, insets ->
            val shown = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (keyboardShown && !shown && view.hasFocus()) view.clearFocus()
            keyboardShown = shown
            insets
        }
        binding.descriptionError.isVisible = rejected
        binding.historyAll.setOnClickListener { showAll(true) }
        binding.historyWithPartner.setOnClickListener { showAll(false) }

        binding.generateButton.setOnClickListener {
            stopTyping()
            onGenerate(input.text.toString())
        }
        binding.suggestButton.setOnClickListener {
            stopTyping()
            onSuggest()
        }
    }

    /** The field no longer holds the description. */
    fun stopTyping() {
        if (input.hasFocus()) input.clearFocus()
    }

    /**
     * The descriptions used before, newest first, and the friend of an invitation's session, or null (FR-4.11). The
     * section is hidden while there are none, and the switch while there is no friend; a friend who changes, or goes,
     * puts the switch back on the whole history.
     */
    fun showHistory(entries: List<PromptEntry>, partner: SessionPartner?) {
        // The screens call this with every state, most of them keystrokes: the chips are only built for a change.
        if (entries == usedBefore && partner == this.partner) return
        usedBefore = entries
        if (this.partner?.userId != partner?.userId) showingAll = true
        this.partner = partner
        renderHistory()
    }

    private fun showAll(all: Boolean) {
        if (showingAll == all) return
        showingAll = all
        renderHistory()
    }

    private fun renderHistory() {
        val context = binding.root.context
        val partner = partner
        binding.historySection.isVisible = usedBefore.isNotEmpty()
        binding.historyFilter.isVisible = partner != null
        if (partner != null) {
            binding.historyWithPartner.text = context.getString(R.string.history_with, partner.displayName)
            binding.historyEmpty.text = context.getString(R.string.history_empty_with, partner.displayName)
        }
        val withPartner = partner?.takeUnless { showingAll }
        binding.historyAll.isSelected = withPartner == null
        binding.historyWithPartner.isSelected = withPartner != null
        val shown = if (withPartner == null) usedBefore else usedBefore.filter { withPartner.userId in it.partners }
        val chips = binding.historyChips
        chips.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (entry in shown) {
            val chip = inflater.inflate(R.layout.item_history_chip, chips, false) as Chip
            chip.text = entry.text
            chip.isEnabled = !locked
            // The chip's text replaces the field's, which the user can then edit (US-5).
            chip.setOnClickListener {
                input.setText(entry.text)
                input.setSelection(input.length())
            }
            chip.closeIconContentDescription = context.getString(R.string.history_delete, entry.text)
            chip.setOnCloseIconClickListener { onForget(entry.text) }
            chips.addView(chip)
        }
        binding.historyEmpty.isVisible = shown.isEmpty() && usedBefore.isNotEmpty()
    }

    /**
     * In a session (Design 2.6.5): shows [text], the description as the photographer's phone has it, unless this
     * field is being typed in. While [typing] names the other person, whose field holds the description, this field,
     * the chips, and the buttons wait, and so they do while the link is [paused]; [waiting] keeps the buttons waiting
     * for an answer. [sharedWith] names the other person for the hint; null outside a session.
     */
    fun showShared(
        text: String,
        typing: String?,
        sharedWith: String?,
        waiting: Boolean = false,
        paused: Boolean = false,
    ) {
        val context = binding.root.context
        val locked = typing != null || paused
        if (locked) stopTyping()
        if (!input.hasFocus() && input.text.toString() != text) {
            showing = true
            input.setText(text)
            input.setSelection(input.length())
            showing = false
        }
        input.isEnabled = !locked
        this.locked = locked
        binding.historyChips.children.forEach { it.isEnabled = !locked }
        binding.generateButton.isEnabled = !locked && !waiting
        binding.suggestButton.isEnabled = !locked && !waiting
        binding.descriptionTyping.isVisible = typing != null
        if (typing != null) binding.descriptionTyping.text = context.getString(R.string.peer_typing, typing)
        binding.sharedHint.isVisible = sharedWith != null
        if (sharedWith != null) binding.sharedHint.text = context.getString(R.string.describe_shared_hint, sharedWith)
    }

    private fun renderCounter() {
        val context = binding.root.context
        binding.descriptionCounter.text =
            context.getString(R.string.description_counter, input.length(), DESCRIPTION_MAX_LENGTH)
    }
}
