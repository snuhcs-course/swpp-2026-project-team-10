// AI-generated with Claude Code, 2026-11-12, reviewed by Jaewan Park, Sungmin Jo, and Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Settings (FR-10.1, R&S 6.10), from the gear on Camera, and in a session from Subject view's too (#204), which this
 * screen covers, so it goes back to Subject view when the session ends, as the guide screens do. The base brightness, the guide's look, and the latency indicator are kept by
 * [SettingsViewModel]; the language is Android's per-app language, which recreates the screen in it; notifications open
 * Android's settings for Pix; the AI consent can be withdrawn here and is asked for again before the next scene photo.
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
        binding.accountRow.settingTitle.setText(R.string.settings_account)
        binding.accountRow.root.setOnClickListener { openAccount() }
        binding.helpRow.settingTitle.setText(R.string.settings_how_to)
        binding.helpRow.settingValue.isVisible = false
        binding.helpRow.root.setOnClickListener { findNavController().navigate(R.id.action_settings_to_help) }
        binding.tutorialRow.settingTitle.setText(R.string.settings_tutorial)
        binding.tutorialRow.settingValue.isVisible = false
        binding.tutorialRow.root.setOnClickListener {
            viewModel.resetTutorial()
            Toast.makeText(requireContext(), R.string.settings_tutorial_reset, Toast.LENGTH_SHORT).show()
        }
        binding.aboutRow.settingTitle.setText(R.string.settings_about)
        binding.aboutRow.settingValue.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        binding.aboutRow.root.setOnClickListener { findNavController().navigate(R.id.action_settings_to_about) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.guideLook.collect { renderLook(binding, it) } }
                launch { viewModel.showLatency.collect { binding.latencySwitch.isChecked = it } }
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
