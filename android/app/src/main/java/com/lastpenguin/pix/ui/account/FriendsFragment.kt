// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.Friend
import com.lastpenguin.pix.account.FriendRequest
import com.lastpenguin.pix.databinding.FragmentFriendsBinding
import com.lastpenguin.pix.databinding.ItemFriendBinding
import com.lastpenguin.pix.databinding.ItemFriendRequestBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel
import kotlinx.coroutines.launch

/**
 * Friends, the screen *Shoot together* opens (R&S 6.4; FR-5.4–5.10, FR-5.16, FR-8.1). Signed out it offers *Sign in to
 * add friends* and *Use a room code instead*, and an account made with Google first chooses its Pix ID; room codes
 * never need a sign-in (FR-5.1). Requests the user sent open on Sent requests. Owner: Accounts (#54).
 */
class FriendsFragment : Fragment(R.layout.fragment_friends) {

    private val viewModel: FriendsViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFriendsBinding.bind(view)
        val screen = FriendsScreen(
            binding,
            object : FriendsScreen.Actions {
                override fun signIn() = navigate(R.id.action_friends_to_signIn)

                override fun choosePixId() = navigate(R.id.action_friends_to_pixIdSetup)

                override fun addFriend() = navigate(R.id.action_friends_to_addFriend)

                override fun openSentRequests() = navigate(R.id.action_friends_to_sentRequests)

                override fun openAccount() = navigate(R.id.action_friends_to_account)

                override fun useRoomCode() {
                    if (findNavController().currentDestination?.id != R.id.friendsFragment) return
                    sessionViewModel.startRoom()
                    findNavController().navigate(R.id.action_friends_to_roomCode)
                }

                override fun copyPixId(pixId: String) = copy(pixId)

                override fun accept(request: FriendRequest) = viewModel.accept(request)

                override fun decline(request: FriendRequest) = viewModel.decline(request)

                override fun remove(friend: Friend) = confirmRemove(friend)
            },
        )
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(screen::render) }
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

    /** Navigates only from this screen, so a second tap during the transition does nothing. */
    private fun navigate(@IdRes action: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.friendsFragment) navController.navigate(action)
    }

    private fun copy(pixId: String) {
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.pix_id_label), pixId))
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), R.string.pix_id_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmRemove(friend: Friend) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.remove_friend_title, friend.displayName))
            .setMessage(R.string.remove_friend_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove_friend) { _, _ -> viewModel.remove(friend) }
            .show()
    }
}

/**
 * Draws [FriendsUiState] on Friends' layout and reports taps to [actions]. Apart from the fragment, so every state can
 * be rendered on the real layout in a test without a ViewModel or navigation.
 */
class FriendsScreen(private val binding: FragmentFriendsBinding, private val actions: Actions) {

    interface Actions {
        fun signIn()
        fun choosePixId()
        fun addFriend()
        fun openSentRequests()
        fun openAccount()
        fun useRoomCode()
        fun copyPixId(pixId: String)
        fun accept(request: FriendRequest)
        fun decline(request: FriendRequest)
        fun remove(friend: Friend)
    }

    private val context = binding.root.context
    private val inflater = LayoutInflater.from(context)
    private var shownFriends: List<Friend>? = null
    private var shownRequests: Pair<List<FriendRequest>, Set<String>>? = null

    init {
        binding.signInButton.setOnClickListener { actions.signIn() }
        binding.choosePixIdButton.setOnClickListener { actions.choosePixId() }
        binding.addFriendButton.setOnClickListener { actions.addFriend() }
        binding.sentRequestsButton.setOnClickListener { actions.openSentRequests() }
        binding.accountButton.setOnClickListener { actions.openAccount() }
        binding.roomCodeButton.setOnClickListener { actions.useRoomCode() }
        binding.roomCodeInsteadButton.setOnClickListener { actions.useRoomCode() }
    }

    fun render(state: FriendsUiState) {
        val account = (state.auth as? AuthState.SignedIn)?.account
        val signedOut = state.auth is AuthState.SignedOut
        val needsPixId = account != null && account.pixId == null
        val pixId = account?.pixId

        binding.accountButton.isVisible = account != null
        binding.promptCard.isVisible = signedOut || needsPixId
        if (signedOut) {
            binding.promptTitle.setText(R.string.signed_out_title)
            binding.promptBody.setText(R.string.signed_out_body)
        } else if (needsPixId) {
            binding.promptTitle.setText(R.string.choose_pix_id_title)
            binding.promptBody.setText(R.string.choose_pix_id_body)
        }
        binding.signInButton.isVisible = signedOut
        binding.roomCodeInsteadButton.isVisible = signedOut
        binding.choosePixIdButton.isVisible = needsPixId
        binding.addFriendButton.isVisible = pixId != null
        binding.roomCodeButton.isVisible = !signedOut

        binding.myPixIdText.isVisible = pixId != null
        if (pixId != null) {
            binding.myPixIdText.text = context.getString(R.string.my_pix_id, pixId)
            binding.myPixIdText.setOnClickListener { actions.copyPixId(pixId) }
            ViewCompat.replaceAccessibilityAction(
                binding.myPixIdText,
                AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.copy_pix_id),
                null,
            )
        }
        binding.offlineText.isVisible = pixId != null && state.offline

        val requests = if (pixId != null) state.requests else emptyList()
        val friends = if (pixId != null) state.friends else emptyList()
        val sent = if (pixId != null) state.sent else emptyList()
        binding.sentRequestsButton.isVisible = sent.isNotEmpty()
        binding.sentRequestsButton.text = context.getString(R.string.sent_requests_count, sent.size)
        renderRequests(requests, state.answering)
        renderFriends(friends)
        binding.emptyCard.isVisible = pixId != null && friends.isEmpty()
    }

    private fun renderRequests(requests: List<FriendRequest>, answering: Set<String>) {
        binding.requestsLabel.isVisible = requests.isNotEmpty()
        binding.requestsList.isVisible = requests.isNotEmpty()
        if (shownRequests == requests to answering) return
        shownRequests = requests to answering
        binding.requestsList.removeAllViews()
        for (request in requests) {
            val row = ItemFriendRequestBinding.inflate(inflater, binding.requestsList, true)
            row.avatarText.text = avatarInitial(request.from.displayName)
            row.nameText.text = request.from.displayName
            row.pixIdText.text = context.getString(R.string.at_pix_id, request.from.pixId)
            val idle = request.id !in answering
            row.acceptButton.isEnabled = idle
            row.declineButton.isEnabled = idle
            row.acceptButton.setOnClickListener { actions.accept(request) }
            row.declineButton.setOnClickListener { actions.decline(request) }
        }
    }

    private fun renderFriends(friends: List<Friend>) {
        if (shownFriends == friends) return
        shownFriends = friends
        binding.friendsList.removeAllViews()
        for (friend in friends) {
            val row = ItemFriendBinding.inflate(inflater, binding.friendsList, true)
            row.avatarText.text = avatarInitial(friend.displayName)
            row.nameText.text = friend.displayName
            val (status, color) = when {
                friend.inSession -> R.string.in_session to R.color.pix_accent
                friend.online -> R.string.online to R.color.pix_live
                else -> R.string.offline to R.color.pix_text_faint
            }
            row.statusText.text = context.getString(R.string.friend_status, context.getString(status), friend.pixId)
            row.statusText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_status_dot, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(
                row.statusText,
                ColorStateList.valueOf(ContextCompat.getColor(context, color)),
            )
            row.inviteButton.contentDescription = context.getString(R.string.invite_not_yet, friend.displayName)
            row.root.setOnLongClickListener {
                actions.remove(friend)
                true
            }
            ViewCompat.addAccessibilityAction(row.root, context.getString(R.string.remove_friend)) { _, _ ->
                actions.remove(friend)
                true
            }
        }
    }
}
