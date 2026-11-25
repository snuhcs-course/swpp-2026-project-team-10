// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import com.lastpenguin.pix.generation.PoseOptions
import com.lastpenguin.pix.generation.PromptEntry
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.partner
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import com.lastpenguin.pix.ui.settings.tutorialFor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Generate poses (R&S 6.3, FR-4.8): the user leaves the field, which says Auto, for the server to suggest four poses
 * for the scene, or taps it and describes a pose, chooses the situation's options, or picks a description used before
 * (FR-4.11, FR-4.15). Nothing is sent before the one button at the top right, *Generate* (Iter3-A13, #199). It opens
 * straight from the scene photo's shutter, the photo shrinking from where the camera held it into its place here
 * (#296), and Back at the top left returns to the camera to shoot again. In a session the subject sees this step too:
 * one field at a time holds
 * the description, and the subject's button goes on here like this phone's own (Design 2.6.5). The photo's card names
 * the situation it was taken for (FR-4.8), and a tap on the photo shows it large (FR-4.15). The first time it opens,
 * its tutorial tips come over it (FR-10.4).
 * Owner: Server/AI/Sync (#7); the step on both phones Iter3-P16; Back Iter4-P2; Auto, the options, and the large photo
 * Iter5-P10; the photo straight from the shutter Iter5-A5.
 */
class DescribePoseFragment : Fragment(R.layout.fragment_describe_pose), ScenePhotoFragment.Host {

    /** The scene photo, for its large view. */
    override val scenePhoto: Bitmap?
        get() = viewModel.uiState.value.scene

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }
    private var form: DescriptionForm? = null

    /** The scene photo shrinking into its place, while it does (#296). */
    private var sceneArrival: ValueAnimator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDescribePoseBinding.bind(view)
        val state = viewModel.uiState.value
        if (state.phase != GenerationUiState.Phase.DESCRIBING || state.scene == null) {
            // Nothing to describe, for example after the app process was restarted on this screen.
            view.post { if (isAdded) leaveForCamera() }
            return
        }
        binding.sceneImage.showScene(state.scene)
        // Straight from the shutter (#296): the photo comes from where the camera held it into its place, once, and
        // not when this screen is made again.
        if (state.sceneArriving) {
            viewModel.sceneArrived()
            if (savedInstanceState == null) {
                binding.root.doOnLayout {
                    // As the camera held it: a photo taken sideways turns upright on its way.
                    val arrival =
                        binding.root.showArriving(
                            state.scene,
                            binding.sceneImage,
                            binding.flowProgress,
                            state.sceneTurn,
                        )
                    sceneArrival = arrival?.animator?.also { it.doOnEnd { sceneArrival = null } }
                }
            }
        }
        val form = DescriptionForm(
            binding,
            // After a recreation the field restores its own text; otherwise it shows what the ViewModel kept (US-5).
            text = if (savedInstanceState == null) state.description else null,
            rejected = state.descriptionRejected,
            onTextChanged = viewModel::onDescriptionChanged,
            // A blank description asks for suggested poses (PoseRequest.of).
            onGenerate = { text ->
                viewModel.generate(text)
                toGenerating()
            },
            onFocusChanged = { focused -> viewModel.onDescriptionFocus(focused) },
            onForget = viewModel::forget,
        )
        this.form = form
        // The situation the photo was taken for (FR-4.8); another needs another photo. Auto is what the field says.
        binding.situationTag.isVisible = state.situation != Situation.DEFAULT
        binding.situationTag.setText(state.situation.title)
        form.showOptions(state.situation)
        // The photo large, over this screen (FR-4.15); the typing ends first, so the keyboard is not left under it.
        binding.scenePhotoButton.opensScenePhoto(this) {
            form.stopTyping()
            hideKeyboard()
        }
        binding.backButton.setOnClickListener { backToScene() }
        // System back does what Back does (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToScene() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Its tutorial tips the first time it opens, on either phone (FR-10.4).
                launch { showTutorialWhenDue(settingsViewModel.tutorialFor(TutorialScreen.GENERATE_POSES)) }
                launch { viewModel.posesKept.collect { showPosesKept(view) } }
                combine(viewModel.uiState, sessionViewModel.state, viewModel.usedBefore, ::Triple)
                    .collect { (generation, session, used) ->
                        // In a session the subject writes the description too, one field at a time (FR-6.15).
                        val connected = (session as? SessionState.Connected)?.takeIf { it.role == Role.PHOTOGRAPHER }
                        val subject = connected?.peer?.displayName
                        val typing = subject.takeIf { generation.descriptionWriter == Role.SUBJECT }
                        form.showShared(generation.description, typing, subject)
                        // The descriptions used before, with the friend of an invitation's session, also while it reconnects (FR-4.11).
                        form.showHistory(used, session.partner)
                        // The subject's button came first: it goes on as this phone's own.
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
        sceneArrival?.cancel()
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

    /** Back: the camera, to take another scene photo, with the text kept for it (#296). */
    private fun backToScene() {
        hideKeyboard()
        form?.stopTyping()
        // Pops first, so that this screen does not take the camera's phase for anything to follow.
        findNavController().popBackStack(R.id.cameraFragment, false)
        viewModel.backToScene()
    }

    /** Nothing to describe: the camera, with the step discarded. */
    private fun leaveForCamera() {
        hideKeyboard()
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun hideKeyboard() {
        val view = view ?: return
        WindowCompat.getInsetsController(requireActivity().window, view).hide(WindowInsetsCompat.Type.ime())
    }

    /**
     * Back left a set whose poses are in the library (#297): a notice says so, with *View*, which opens the library at
     * *All*, newest first, and ends the pose flow, so Back there is Camera.
     */
    private fun showPosesKept(view: View) {
        val context = requireContext()
        Snackbar.make(view, R.string.poses_kept_notice, Snackbar.LENGTH_LONG)
            .setBackgroundTint(context.getColor(R.color.pix_surface_3))
            .setTextColor(context.getColor(R.color.pix_text))
            .setActionTextColor(context.getColor(R.color.pix_accent))
            .setAction(R.string.poses_kept_view) { viewPoses() }
            .show()
    }

    private fun viewPoses() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.describePoseFragment) return
        referenceViewModel.openFor(fromSubject = false)
        // Navigates first, so that this screen does not take the ended flow for a restart.
        navController.navigate(R.id.action_describePose_to_library)
        viewModel.cancel()
    }
}

/**
 * The description field, its counter, the line under it, the situation's options (FR-4.15), the descriptions used before
 * (FR-4.11), and the one button of Generate poses (FR-4.4, FR-4.8, US-5), on either phone. The button starts suggested
 * poses while the field is blank, which a blank description asks for, else poses of the description (Iter3-A13); its
 * label is the same for both, since the field itself says which (FR-4.15). Apart from the fragments, so the form can be
 * tested on the real layout without a ViewModel or navigation.
 *
 * Empty and left alone, the field says Auto and what Auto does, with a line under it that says to tap it, and nothing
 * else under it; while the field holds text, that line is *Back to Auto*, the way to the other choice in the same
 * place. A tap, or a text, opens what is under it: [showOptions]'s chips, each of which writes its words into
 * the field and takes them out again (PoseOptions), and [showHistory]'s, the descriptions used before, all of them or,
 * through the switch, those used with the session's friend; such a chip fills the field, and its ✕ asks [onForget] to
 * drop it. What a tap on the field or on a chip opened stays open when the field is empty again, so a closed keyboard
 * uncovers it and a chip does not go from under the finger, until *Back to Auto* empties the field and folds it; what
 * only a text opened, such as the other phone's, folds when the text goes.
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
    /** The button: the field's text as typed, blank for suggested poses. */
    onGenerate: (String) -> Unit,
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

    /** The situation whose options show, and their words in the chips' order; none before [showOptions]. */
    private var situation: Situation? = null
    private var options: List<String> = emptyList()

    /** The field or a chip was tapped on this phone: what is under the field shows until *Back to Auto* folds it. */
    private var touched = false

    /** What is under the field shows: this phone opened it, or the field holds a text, typed or the other phone's. */
    private val opened: Boolean
        get() = touched || input.length() > 0

    /** Whether the field's hint says Auto, or shows the example; null before the first one is set. */
    private var saysAuto: Boolean? = null

    private val autoHint = autoHint()

    init {
        // One line of input, so Done closes the keyboard, shown wrapped over up to four lines. Three lines high at
        // least, which the hint that says Auto takes, so nothing under the field moves when a text replaces the hint;
        // set here, since a one-line input type sets the lines to one after the layout's own.
        input.setHorizontallyScrolling(false)
        input.maxLines = 4
        input.minLines = AUTO_LINES
        if (text != null) input.setText(text)
        binding.descriptionError.isVisible = rejected
        render()
        input.doAfterTextChanged { changed ->
            if (!showing) onTextChanged(changed?.toString().orEmpty())
            render()
        }
        input.setOnFocusChangeListener { _, focused ->
            onFocusChanged(focused)
            render()
        }
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
        binding.historyAll.setOnClickListener { showAll(true) }
        binding.historyWithPartner.setOnClickListener { showAll(false) }

        binding.generateButton.setOnClickListener {
            stopTyping()
            onGenerate(input.text.toString())
        }
        // As if the text were deleted by hand, on both phones: the field says Auto again, with nothing under it.
        binding.clearDescriptionButton.setOnClickListener {
            stopTyping()
            touched = false
            input.setText("")
        }
    }

    /** The field no longer holds the description. */
    fun stopTyping() {
        if (input.hasFocus()) input.clearFocus()
    }

    /** Whether the service rejected the last description, said under the field (US-5). */
    fun showRefused(rejected: Boolean) {
        if (binding.descriptionError.isVisible == rejected) return
        binding.descriptionError.isVisible = rejected
        render()
    }

    /**
     * The options of [situation], the one the scene photo was taken for, as chips (FR-4.15); *Auto*'s without one, as
     * from an older photographer. A chip writes its words into the field and is checked while they are there, typed or
     * shown; one whose words would pass the longest description cannot be chosen.
     */
    fun showOptions(situation: Situation?) {
        val shown = situation ?: Situation.DEFAULT
        if (shown == this.situation) return
        this.situation = shown
        val context = binding.root.context
        options = shown.poseOptions.map(context::getString)
        val chips = binding.optionChips
        chips.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (option in options) {
            val chip = inflater.inflate(R.layout.item_option_chip, chips, false) as Chip
            chip.text = option
            chip.setOnClickListener { choose(option) }
            chips.addView(chip)
        }
        render()
        // A description used before that is one of these options is not listed again under them.
        renderHistory()
    }

    /** A tap on [option]'s chip: its words go into the field, or leave it. */
    private fun choose(option: String) {
        val chosen = PoseOptions.toggle(input.text.toString(), option)
        if (chosen == null || locked) {
            // The tap checked the chip by itself: back to what the field says.
            renderOptions()
            return
        }
        // Stays open if this takes the last words out: the chip does not go from under the finger.
        touched = true
        input.setText(chosen)
        input.setSelection(input.length())
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

    /** The descriptions used before that are not an option on the screen, whose chip is right above. */
    private fun listed(): List<PromptEntry> =
        usedBefore.filterNot { used -> options.any { it.equals(used.text.trim(), ignoreCase = true) } }

    private fun renderHistory() {
        val context = binding.root.context
        val partner = partner
        val listed = listed()
        binding.historySection.isVisible = opened && listed.isNotEmpty()
        binding.historyFilter.isVisible = partner != null
        if (partner != null) {
            binding.historyWithPartner.text = context.getString(R.string.history_with, partner.displayName)
            binding.historyEmpty.text = context.getString(R.string.history_empty_with, partner.displayName)
        }
        val withPartner = partner?.takeUnless { showingAll }
        binding.historyAll.isSelected = withPartner == null
        binding.historyWithPartner.isSelected = withPartner != null
        val shown = if (withPartner == null) listed else listed.filter { withPartner.userId in it.partners }
        val chips = binding.historyChips
        chips.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (entry in shown) {
            val chip = inflater.inflate(R.layout.item_history_chip, chips, false) as Chip
            chip.text = entry.text
            chip.isEnabled = !locked
            // The chip's text replaces the field's, which the user can then edit (US-5).
            chip.setOnClickListener {
                touched = true
                input.setText(entry.text)
                input.setSelection(input.length())
            }
            chip.closeIconContentDescription = context.getString(R.string.history_delete, entry.text)
            chip.setOnCloseIconClickListener { onForget(entry.text) }
            chips.addView(chip)
        }
        binding.historyEmpty.isVisible = shown.isEmpty() && listed.isNotEmpty()
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
        render()
        binding.descriptionTyping.isVisible = typing != null
        if (typing != null) binding.descriptionTyping.text = context.getString(R.string.peer_typing, typing)
        binding.sharedHint.isVisible = sharedWith != null
        if (sharedWith != null) binding.sharedHint.text = context.getString(R.string.shared_hint, sharedWith)
    }

    /**
     * What follows the field's text and focus, after each change of either, typed or shown: Auto or the example as its
     * hint, the line under it or the counter, *Back to Auto*, the checked options, and whether what is under it shows.
     */
    private fun render() {
        val context = binding.root.context
        val empty = input.length() == 0
        val focused = input.hasFocus()
        if (focused) touched = true
        // Left alone and empty, the field says Auto (FR-4.15); tapped, it shows an example to follow.
        val auto = empty && !focused
        if (saysAuto != auto) {
            saysAuto = auto
            input.hint = if (auto) autoHint else context.getString(R.string.describe_hint)
            // The outline in the accent's tint: Auto is the value the field stands on.
            input.isActivated = auto
        }
        binding.descriptionCounter.text =
            context.getString(R.string.description_counter, input.length(), DESCRIPTION_MAX_LENGTH)
        binding.descriptionCounter.isVisible = !auto
        // One line under the field: how to type while it says Auto, and the way back to Auto while it holds text.
        binding.descriptionHelper.isVisible = auto && !locked
        binding.clearDescriptionButton.isVisible = !empty && !locked
        renderOptions()
        binding.optionsSection.isVisible = opened && options.isNotEmpty()
        binding.historySection.isVisible = opened && listed().isNotEmpty()
    }

    /** Each option checked while the field's text has it, and off while it cannot be chosen. */
    private fun renderOptions() {
        val text = input.text.toString()
        binding.optionChips.children.forEachIndexed { i, view ->
            val chip = view as Chip
            chip.isChecked = PoseOptions.has(text, options[i])
            chip.isEnabled = !locked && PoseOptions.toggle(text, options[i]) != null
        }
    }

    /**
     * "Auto" over what Auto does, as the empty field's hint (FR-4.15): the first as a value in the accent, the way a
     * camera shows a setting left on auto, the second as small text. Its three lines are no higher than three of the
     * field's text, which the field always has room for ([AUTO_LINES]), so the field is as high with one as with the
     * other.
     */
    private fun autoHint(): CharSequence {
        val context = binding.root.context
        val title = context.getString(R.string.describe_auto)
        val body = context.getString(R.string.describe_auto_body)
        return SpannableString("$title\n$body").apply {
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            setSpan(TextAppearanceSpan(context, R.style.TextAppearance_Pix_Action_Accent), 0, title.length, flags)
            setSpan(TextAppearanceSpan(context, R.style.TextAppearance_Pix_Body_Small), title.length + 1, length, flags)
            // The field adds its line spacing to the hint's lines too. A pixel under the text's own line, so the hint
            // is never the higher of the two, whatever the font does with its last line.
            setSpan(LineHeightSpan.Standard(input.paint.getFontMetricsInt(null) - 1), 0, length, flags)
        }
    }

    private companion object {
        /** The lines of the hint that says Auto: "Auto", and what it does on two. */
        const val AUTO_LINES = 3
    }
}
