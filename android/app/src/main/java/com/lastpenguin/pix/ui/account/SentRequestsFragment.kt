// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.databinding.FragmentSentRequestsBinding
import com.lastpenguin.pix.databinding.ItemSentRequestBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Sent requests, from *Sent requests · N* on Friends (R&S 6.4; FR-5.16): the requests the user sent that nobody
 * answered yet, each with *Withdraw*. The list refreshes as on Friends, so an accepted request leaves it. Signed out, or
 * without a Pix ID, it goes back to Friends. Owner: Accounts (#54).
 */
class SentRequestsFragment : Fragment(R.layout.fragment_sent_requests) {

    private val viewModel: FriendsViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSentRequestsBinding.bind(view)
        val screen = SentRequestsScreen(binding, onWithdraw = viewModel::withdraw)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val account = (state.auth as? AuthState.SignedIn)?.account
                        if (state.auth != AuthState.Loading &&
                            account?.pixId == null
                        ) {
                            backToFriends()
                        } else {
                            screen.render(state)
                        }
                    }
                }
                launch { viewModel.keepRefreshing() }
                launch {
                    viewModel.events.collect { event ->
                        val message = when (event) {
                            FriendsEvent.SignedOutByServer -> getString(R.string.signed_out_expired)
                            is FriendsEvent.Failed -> requireContext().accountMessage(event.error)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun backToFriends() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.sentRequestsFragment) {
            navController.popBackStack(R.id.friendsFragment, false)
        }
    }
}

/** Draws the sent requests of [FriendsUiState]. Apart from the fragment, so it can be tested on the real layout. */
class SentRequestsScreen(
    private val binding: FragmentSentRequestsBinding,
    private val onWithdraw: (SentRequest) -> Unit,
) {
    private val context = binding.root.context
    private val inflater = LayoutInflater.from(context)
    private var shown: Pair<List<SentRequest>, Set<String>>? = null

    fun render(state: FriendsUiState) {
        binding.offlineText.isVisible = state.offline
        binding.emptyCard.isVisible = state.sent.isEmpty()
        if (shown == state.sent to state.answering) return
        shown = state.sent to state.answering
        binding.sentList.removeAllViews()
        for (request in state.sent) {
            val row = ItemSentRequestBinding.inflate(inflater, binding.sentList, true)
            row.avatarText.text = avatarInitial(request.to.displayName)
            row.nameText.text = request.to.displayName
            row.pixIdText.text = context.getString(R.string.at_pix_id, request.to.pixId)
            row.withdrawButton.contentDescription =
                context.getString(R.string.withdraw_request_to, request.to.displayName)
            row.withdrawButton.isEnabled = request.id !in state.answering
            row.withdrawButton.setOnClickListener { onWithdraw(request) }
        }
    }
}
