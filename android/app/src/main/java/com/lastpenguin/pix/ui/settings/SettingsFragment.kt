// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park, Sungmin Jo, and Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.databinding.FragmentSettingsBinding
import com.lastpenguin.pix.databinding.ItemSettingRowBinding
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideThickness
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.settings.Language
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.camera.ExposureScale
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.showNotice
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Settings (FR-10.1, R&S 6.10), from the gear on Camera, and in a session from Subject view's too (#204), which this
 * screen covers, so it goes back to Subject view when the session ends, as the guide screens do. The base brightness,
 * the grid and the horizon bar, the guide's look, and the latency indicator are kept by [SettingsViewModel]; the language is Android's per-app language, which recreates the screen in it; notifications open
 * Android's settings for Pix; the AI consent can be withdrawn here and is asked for again before the next scene photo;
 * *Share usage data* turns crash reports and usage events off or on (FR-10.6, Design 2.8, Telemetry).
 * Owner: Settings (#123).
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private var binding: FragmentSettingsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSettingsBinding.bind(view)
        this.binding = binding
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        backToSubjectViewWhenTheSessionEnds()

        swatches(binding).forEach { (color, swatch) ->
            swatch.color = color.argb
            swatch.setOnClickListener { viewModel.setGuideColor(color) }
        }
        thicknesses(binding).forEach { (thickness, button) ->
            button.setOnClickListener { viewModel.setGuideThickness(thickness) }
        }
        binding.latencySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.showLatency.value) viewModel.setShowLatency(checked)
        }
        binding.latencyRow.setOnClickListener { binding.latencySwitch.toggle() }
        // The grid and the horizon bar (FR-1.9, FR-1.10): the render sets the switches too, so only a change is saved.
        binding.gridSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.showGrid.value) viewModel.setShowGrid(checked)
        }
        binding.gridRow.setOnClickListener { binding.gridSwitch.toggle() }
        binding.levelSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.showLevel.value) viewModel.setShowLevel(checked)
        }
        binding.levelRow.setOnClickListener { binding.levelSwitch.toggle() }
        binding.usageSwitch.setOnCheckedChangeListener { _, checked ->
            // The render sets the switch too: only a change from the stored answer is saved, never one before it loads.
            val stored = viewModel.shareUsage.value ?: return@setOnCheckedChangeListener
            if (checked != stored) viewModel.setShareUsage(checked)
        }
        binding.usageRow.setOnClickListener { if (binding.usageSwitch.isEnabled) binding.usageSwitch.toggle() }
        // The base brightness (FR-1.11), saved as each step is chosen; the Camera screen applies it when it shows.
        binding.baseBrightnessSlider.setLabelFormatter { ExposureScale.formatEv(brightnessEv(it)) }
        binding.baseBrightnessSlider.addOnChangeListener { _, value, fromUser ->
            binding.baseBrightnessValue.text = ExposureScale.formatEv(brightnessEv(value))
            if (fromUser) viewModel.setBaseBrightness(brightnessEv(value))
        }

        binding.languageRow.settingTitle.setText(R.string.settings_language)
        binding.languageRow.root.setOnClickListener { chooseLanguage() }
        binding.notificationsRow.settingTitle.setText(R.string.settings_notifications)
        binding.notificationsRow.root.setOnClickListener { openNotificationSettings() }
        binding.consentRow.settingTitle.setText(R.string.settings_ai_consent)
        binding.consentRow.root.setOnClickListener { confirmWithdrawal() }
        binding.privacyPolicyRow.settingTitle.setText(R.string.settings_privacy_policy)
        binding.privacyPolicyRow.settingValue.isVisible = false
        binding.privacyPolicyRow.root.setOnClickListener { openPrivacyPolicy(R.id.settingsFragment) }
        binding.accountRow.settingTitle.setText(R.string.settings_account)
        binding.accountRow.root.setOnClickListener { openAccount() }
        binding.helpRow.settingTitle.setText(R.string.settings_how_to)
        binding.helpRow.settingValue.isVisible = false
        binding.helpRow.root.setOnClickListener { findNavController().navigate(R.id.action_settings_to_help) }
        binding.tutorialRow.settingTitle.setText(R.string.settings_tutorial)
        binding.tutorialRow.settingValue.isVisible = false
        binding.tutorialRow.root.setOnClickListener {
            viewModel.resetTutorial()
            showNotice(R.string.settings_tutorial_reset)
        }
        binding.aboutRow.settingTitle.setText(R.string.settings_about)
        binding.aboutRow.settingValue.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        binding.aboutRow.root.setOnClickListener { findNavController().navigate(R.id.action_settings_to_about) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.guideLook.collect { renderLook(binding, it) } }
                launch { viewModel.showLatency.collect { binding.latencySwitch.isChecked = it } }
                launch { viewModel.showGrid.collect { binding.gridSwitch.isChecked = it } }
                launch { viewModel.showLevel.collect { binding.levelSwitch.isChecked = it } }
                launch { viewModel.shareUsage.collect { renderUsage(binding, it) } }
                launch { viewModel.baseBrightness.collect { renderBrightness(binding, it) } }
                launch { viewModel.account.collect { renderAccount(binding.accountRow, it) } }
                // Followed, not read once: Android may restore Pix straight to this screen before the answer loads.
                launch { generationViewModel.consentGiven.collect { renderConsent(binding.consentRow, it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // What can change elsewhere while Pix is in the background, or in a dialog here.
        val binding = binding ?: return
        renderLanguage(binding.languageRow)
        renderNotifications(binding.notificationsRow)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /** Off and disabled until the stored answer is read, so a tap never acts on the default (FR-10.6). */
    private fun renderUsage(binding: FragmentSettingsBinding, share: Boolean?) {
        binding.usageSwitch.isChecked = share == true
        binding.usageSwitch.isEnabled = share != null
    }

    private fun renderBrightness(binding: FragmentSettingsBinding, ev: Float) {
        val slider = binding.baseBrightnessSlider
        val position = (ev * BRIGHTNESS_STEPS_PER_EV).roundToInt() + BRIGHTNESS_ZERO
        slider.value = position.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        binding.baseBrightnessValue.text = ExposureScale.formatEv(brightnessEv(slider.value))
    }

    /** A slider position in EV: thirds of an EV, with 0 EV in the middle. */
    private fun brightnessEv(position: Float): Float = (position - BRIGHTNESS_ZERO) / BRIGHTNESS_STEPS_PER_EV

    private fun swatches(binding: FragmentSettingsBinding) = listOf(
        GuideColor.WHITE to binding.colorWhite,
        GuideColor.BLACK to binding.colorBlack,
        GuideColor.YELLOW to binding.colorYellow,
        GuideColor.MINT to binding.colorMint,
        GuideColor.PINK to binding.colorPink,
    )

    private fun thicknesses(binding: FragmentSettingsBinding) = listOf(
        GuideThickness.THIN to binding.thicknessThin,
        GuideThickness.NORMAL to binding.thicknessNormal,
        GuideThickness.THICK to binding.thicknessThick,
    )

    private fun renderLook(binding: FragmentSettingsBinding, look: GuideLook) {
        binding.guidePreview.look = look
        swatches(binding).forEach { (color, swatch) -> swatch.isSelected = color == look.color }
        thicknesses(binding).forEach { (thickness, button) -> button.isSelected = thickness == look.thickness }
    }

    // ---- Language (FR-10.2) -------------------------------------------------------------------------------------

    private fun renderLanguage(row: ItemSettingRowBinding) {
        row.settingValue.text = languageLabel(viewModel.language)
    }

    /** A language's name in its own language, as language pickers show it, so either reader finds theirs. */
    private fun languageLabel(language: Language): String = when (language) {
        Language.PHONE -> getString(R.string.settings_language_phone)
        Language.ENGLISH -> ENGLISH_NAME
        Language.KOREAN -> KOREAN_NAME
    }

    /**
     * Opened from Subject view, Settings covers it, so Subject view does not see the session end: go back to it, which
     * shows Connection lost or returns to the camera. Over the camera nothing changes, since Camera shows the end itself.
     */
    private fun backToSubjectViewWhenTheSessionEnds() {
        val session = ViewModelProvider(requireActivity(), PixViewModels.Factory)[SessionViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.state.collect { state ->
                    if (state is SessionState.Ended) findNavController().popBackStack(R.id.subjectFragment, false)
                }
            }
        }
    }

    private fun chooseLanguage() {
        val choices = Language.entries
        val current = choices.indexOf(viewModel.language)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(choices.map(::languageLabel).toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                // Android keeps the choice and recreates the activity in it; the back stack, and this screen, stay.
                if (which != current) viewModel.chooseLanguage(choices[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Notifications ------------------------------------------------------------------------------------------

    private fun renderNotifications(row: ItemSettingRowBinding) {
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        row.settingValue.setText(if (enabled) R.string.settings_on else R.string.settings_off)
    }

    /** Android's own page for Pix's notifications, where invitations and friend requests are turned on or off. */
    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().packageName, null)),
            )
        }
    }

    // ---- AI consent (FR-4.2) ------------------------------------------------------------------------------------

    private fun renderConsent(row: ItemSettingRowBinding, given: Boolean) {
        row.settingValue.setText(if (given) R.string.settings_consent_given else R.string.settings_consent_not_given)
        // Only an agreement can be taken back; without one, Pix asks before it sends anything.
        row.settingChevron.isVisible = given
        row.root.isClickable = given
        // The row's end padding leaves room for the chevron, whose tip is 16 dp from the edge; without it, the value
        // ends there itself.
        row.settingValue.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = if (given) 0 else (8 * resources.displayMetrics.density).roundToInt()
        }
    }

    private fun confirmWithdrawal() {
        if (generationViewModel.needsConsent) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_withdraw_title)
            .setMessage(R.string.settings_withdraw_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.settings_withdraw) { _, _ -> generationViewModel.withdrawConsent() }
            .show()
    }

    // ---- Account (F5) -------------------------------------------------------------------------------------------

    private fun renderAccount(row: ItemSettingRowBinding, state: AuthState) {
        row.settingValue.text = when (state) {
            is AuthState.SignedIn -> state.account.pixId?.let { getString(R.string.at_pix_id, it) }
                ?: state.account.displayName

            AuthState.SignedOut -> getString(R.string.settings_signed_out)

            AuthState.Loading -> ""
        }
    }

    /** The account sheet once signed in; signed out, Sign in, which comes back here (FR-10.5). */
    private fun openAccount() {
        when (viewModel.account.value) {
            is AuthState.SignedIn -> findNavController().navigate(R.id.action_settings_to_account)
            AuthState.SignedOut -> findNavController().navigate(R.id.action_settings_to_signIn)
            AuthState.Loading -> Unit
        }
    }

    internal companion object {
        const val ENGLISH_NAME = "English"
        const val KOREAN_NAME = "한국어"

        // The base brightness slider's positions, 0..12: thirds of an EV, 0 EV at 6.
        const val BRIGHTNESS_STEPS_PER_EV = 3f
        const val BRIGHTNESS_ZERO = 6
    }
}
