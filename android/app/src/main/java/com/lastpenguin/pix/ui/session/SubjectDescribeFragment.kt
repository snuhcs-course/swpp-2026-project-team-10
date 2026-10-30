// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.session.DescriptionEnd
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.DescriptionForm
import kotlinx.coroutines.launch

/**
 * *Generate poses* on the subject's phone (R&S 6.6, FR-6.15): the photographer's scene photo and description, as on
 * their phone. This phone's field holds the description while it is typed in and the photographer's does not, and
 * says who types otherwise; *Generate 4 poses* and *Skip* ask the photographer's phone, which starts the set. The screen
 * closes when the description ends, saying so when the photographer closed it. It covers Subject view, so it follows
 * the session itself, like the guide screens (Design 2.6.5).
 * Owner: Shared steps (Iter3-P16).
 */
class SubjectDescribeFragment : Fragment(R.layout.fragment_describe_pose) {

    private val viewModel: SubjectDescribeViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var form: DescriptionForm? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDescribePoseBinding.bind(view)
        val input = binding.descriptionInput
        val ui = viewModel.uiState.value
        val form = DescriptionForm(
            binding,
            ideas = resources.getStringArray(R.array.pose_ideas).toList(),
            text = ui.text,
            rejected = ui.rejected,
            onTextChanged = viewModel::onTextChanged,
            onGenerate = { text -> viewModel.go(text, suggest = false) },
            onSuggest = { viewModel.go(input.text.toString(), suggest = true) },
            onFocusChanged = { focused -> viewModel.onFocus(focused, input.text.toString()) },
        )
        this.form = form
        // Back and Cancel leave the photographer's description as it is; Subject view opens it again.
        binding.cancelButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToSubject() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { ui -> render(binding, form, ui) } }
                launch {
                    viewModel.ended.collect { end ->
                        // Only this screen's description: an end kept from an earlier one says nothing about it.
                        val ui = viewModel.uiState.value
                        if (end.descriptionId != ui.descriptionId) return@collect
                        describeEndNotice(requireContext(), end, ui.photographerName)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                        backToSubject()
                    }
                }
                launch {
                    viewModel.goFailed.collect {
                        Toast.makeText(requireContext(), R.string.describe_go_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    // Subject view shows Connection lost or goes back to the camera once it is in front again.
                    sessionViewModel.state.collect { state -> if (state is SessionState.Ended) backToSubject() }
                }
            }
        }
    }

    override fun onPause() {
        // Leaving the screen ends the typing, so the photographer's field can take the description.
        form?.stopTyping()
        super.onPause()
    }

    override fun onDestroyView() {
        form = null
        super.onDestroyView()
    }

    private fun render(binding: FragmentDescribePoseBinding, form: DescriptionForm, ui: SubjectDescribeUiState) {
        if (ui.gone) {
            backToSubject()
            return
        }
        binding.sceneImage.setImageBitmap(ui.scene)
        binding.descriptionError.visibility = if (ui.rejected) View.VISIBLE else View.GONE
        val photographer = ui.photographerName
        val theirs = ui.writer == Role.PHOTOGRAPHER
        form.showShared(
            ui.text,
            typing = photographer.takeIf { theirs },
            sharedWith = photographer,
            waiting = ui.waiting,
            // Without the photographer's name yet, their typing still holds this field, without the notice.
            paused = ui.reconnecting || (theirs && photographer == null),
        )
    }

    private fun backToSubject() {
        view?.let { WindowCompat.getInsetsController(requireActivity().window, it).hide(WindowInsetsCompat.Type.ime()) }
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectDescribeFragment) nav.popBackStack()
    }
}

/**
 * What the subject's phone says when the photographer's Generate poses [end]ed (Design 2.6.5): that the photographer
 * closed it; nothing when a set started from it, which Subject view's "… is creating poses…" says.
 */
internal fun describeEndNotice(context: Context, end: DescriptionEnd, photographer: String?): String? {
    if (end.started || photographer == null) return null
    return context.getString(R.string.peer_closed_describe, photographer)
}
