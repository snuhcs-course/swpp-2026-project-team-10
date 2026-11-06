// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetShootTogetherBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Who takes the photo?, the sheet Camera's *Shoot together* opens (R&S 6.7, FR-5.2). *I'll take the photo* opens Share
 * my camera, the photographer's ways to connect, and *I'll be in the photo* opens See their camera, the subject's, so
 * neither phone is shown the other's (#188). Each replaces the sheet, so Back from either is Camera. While the sheet is
 * shown, *I'll be in the photo* counts the received invitations waiting there (FR-6.17). *I'll be in the photo* also
 * ends a room this phone still waits in with nobody, as after its subject left, which would refuse a code or an
 * *Accept* there. Owner: Real-time (#8).
 */
class ShootTogetherSheet : BottomSheetDialogFragment() {

    private val incoming: IncomingInvitationsViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetShootTogetherBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetShootTogetherBinding.bind(view)
        binding.photographerButton.setOnClickListener { navigate(R.id.action_shootTogether_to_friends) }
        binding.subjectButton.setOnClickListener {
            // A room this phone still waits in alone, after its subject left, would refuse every way in (#188).
            sessionViewModel.leaveWaitingRoom()
            navigate(R.id.action_shootTogether_to_joinCode)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { incoming.keepRefreshing() }
                launch { incoming.uiState.collect { showInvitations(binding, it.invitations.size) } }
            }
        }
    }

    private fun showInvitations(binding: SheetShootTogetherBinding, count: Int) {
        binding.invitationsBadge.isVisible = count > 0
        if (count > 0) {
            binding.invitationsBadge.text = resources.getQuantityString(R.plurals.invitations_waiting, count, count)
        }
        binding.subjectButton.setBackgroundResource(
            if (count > 0) R.drawable.bg_option_card_invited else R.drawable.bg_option_card,
        )
    }

    /** Navigates only from this sheet, so a second tap while it closes does nothing. */
    private fun navigate(@IdRes action: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.shootTogetherSheet) navController.navigate(action)
    }
}

/**
 * [text] with [part] drawn in [color]: the other phone's buttons on the code screens, such as *I'll be in the photo* in
 * "Shoot together › I'll be in the photo", which the texts name by the buttons' own labels.
 */
internal fun Context.withColoredPart(text: String, part: String, @ColorRes color: Int): CharSequence {
    val start = text.lastIndexOf(part)
    if (start < 0) return text
    return SpannableString(text).apply {
        setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this@withColoredPart, color)),
            start,
            start + part.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}
