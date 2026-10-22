// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Relation
import com.lastpenguin.pix.account.keepPixIdCharacters
import com.lastpenguin.pix.databinding.FragmentAddFriendBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Add a friend, with No user found as its failure state (R&S 6.4; FR-5.5, FR-5.6, FR-5.8, US-7). *Search again* keeps
 * the text so it can be fixed. Owner: Accounts (#54).
 */
class AddFriendFragment : Fragment(R.layout.fragment_add_friend) {

    private val viewModel: FriendsViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAddFriendBinding.bind(view)
        if (savedInstanceState == null) viewModel.clearSearch()
        val form = AddFriendForm(
            binding,
            onSearch = { text ->
                hideKeyboard()
                viewModel.search(text)
            },
            onAdd = viewModel::addFound,
            onAccept = viewModel::acceptFound,
            onWithdraw = viewModel::withdrawFound,
            onEdited = viewModel::clearSearch,
        )
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.search.collect(form::render) }
                launch {
                    viewModel.events.collect { event ->
                        if (event is FriendsEvent.Failed) {
                            Toast.makeText(
                                requireContext(),
                                requireContext().accountMessage(event.error),
                                Toast.LENGTH_LONG,
                            )
                                .show()
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            binding.pixIdInput.post {
                binding.pixIdInput.requestFocus()
                WindowCompat.getInsetsController(requireActivity().window, binding.pixIdInput)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    private fun hideKeyboard() {
        val view = view ?: return
        WindowCompat.getInsetsController(requireActivity().window, view).hide(WindowInsetsCompat.Type.ime())
    }
}

/** The search field, the result, and No user found. Apart from the fragment, so it can be tested on the real layout. */
class AddFriendForm(
    private val binding: FragmentAddFriendBinding,
    onSearch: (String) -> Unit,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
    onWithdraw: () -> Unit,
    /** The text changed after a search, or *Search again*: the result no longer matches the field. */
    onEdited: () -> Unit,
) {
    private val context = binding.root.context
    private var phase = SearchPhase.IDLE
    private var searched = ""

    init {
        val input = binding.pixIdInput
        // Only what a Pix ID can hold, in lowercase (FR-5.5).
        input.filters = input.filters + InputFilter { source, start, end, _, _, _ ->
            val kept = keepPixIdCharacters(source.subSequence(start, end))
            if (kept.contentEquals(source.subSequence(start, end))) null else kept
        }
        input.doAfterTextChanged { text ->
            if (phase != SearchPhase.IDLE && phase != SearchPhase.SEARCHING && text.toString() != searched) onEdited()
            renderButton()
        }
        input.setOnEditorActionListener { _, actionId, event ->
            val search = actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (search && binding.searchButton.isEnabled) onSearch(input.text.toString())
            search
        }
        binding.searchButton.setOnClickListener {
            if (phase == SearchPhase.NOT_FOUND) {
                // Search again: back to the field with the text kept, to fix it (US-7).
                onEdited()
                input.requestFocus()
                input.setSelection(input.length())
            } else {
                onSearch(input.text.toString())
            }
        }
        binding.resultButton.setOnClickListener {
            if (binding.resultButton.tag == Relation.INCOMING) onAccept() else onAdd()
        }
        binding.withdrawButton.setOnClickListener { onWithdraw() }
    }

    fun render(state: SearchUiState) {
        phase = state.phase
        searched = state.query
        binding.statusText.isVisible = state.phase == SearchPhase.SEARCHING || state.phase == SearchPhase.FAILED
        binding.statusText.text = when (state.phase) {
            SearchPhase.SEARCHING -> context.getString(R.string.searching)
            SearchPhase.FAILED -> state.error?.let(context::accountMessage).orEmpty()
            else -> ""
        }
        binding.notFoundSection.isVisible = state.phase == SearchPhase.NOT_FOUND
        binding.pixIdInput.setBackgroundResource(
            if (state.phase == SearchPhase.NOT_FOUND) R.drawable.bg_text_field_error else R.drawable.bg_text_field,
        )

        val found = state.result
        binding.resultSection.isVisible = state.phase == SearchPhase.FOUND && found != null
        if (found != null) {
            val user = found.user
            binding.avatarText.text = avatarInitial(user.displayName)
            binding.nameText.text = user.displayName
            binding.pixIdText.text = context.getString(R.string.at_pix_id, user.pixId)
            val button = when (found.relation) {
                Relation.NONE -> R.string.add
                Relation.INCOMING -> R.string.accept
                else -> null
            }
            binding.resultButton.isVisible = button != null
            binding.resultButton.tag = found.relation
            button?.let(binding.resultButton::setText)
            binding.resultButton.isEnabled = !state.busy
            val label = when (found.relation) {
                Relation.REQUESTED -> R.string.requested
                Relation.FRIEND -> R.string.already_friends
                Relation.SELF -> R.string.this_is_you
                else -> null
            }
            binding.relationText.isVisible = label != null
            label?.let(binding.relationText::setText)
            val waiting = found.relation == Relation.NONE || found.relation == Relation.REQUESTED
            binding.noteText.isVisible = waiting
            binding.noteText.text = context.getString(R.string.add_friend_note, user.displayName)
            binding.withdrawButton.isVisible = found.relation == Relation.REQUESTED
            binding.withdrawButton.isEnabled = !state.busy
        }
        renderButton()
    }

    private fun renderButton() {
        val notFound = phase == SearchPhase.NOT_FOUND
        binding.searchButton.setText(if (notFound) R.string.search_again else R.string.search)
        binding.searchButton.isEnabled =
            phase != SearchPhase.SEARCHING && (notFound || !binding.pixIdInput.text.isNullOrBlank())
    }
}
