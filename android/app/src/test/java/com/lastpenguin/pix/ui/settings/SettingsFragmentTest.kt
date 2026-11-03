// AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.databinding.FragmentSettingsBinding
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideThickness
import com.lastpenguin.pix.settings.Language
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.tutorialOver
import com.lastpenguin.pix.ui.TestAppContainer
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/*
 * Settings on the real screen (FR-10.1–10.4, FR-3.10, FR-4.2), with the settings in memory behind the ViewModel.
 * Partitions: the defaults (white, normal, the indicator off, the phone's language, an agreement given, signed out);
 * each color and thickness chosen; the indicator turned on from its switch and off from its row; a language chosen and
 * the dialog cancelled; the agreement withdrawn, the row without one, and the row while the stored answer loads; the account signed in and signed out;
 * notifications, How to use Pix, and About Pix opened; *Show the tutorial again*, which makes every screen's tips due
 * again and says so.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsFragmentTest {
    private var scenario: FragmentScenario<SettingsFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        val (launched, controller) = launchPrepared<SettingsFragment>(R.id.settingsFragment) {}
        scenario = launched
        nav = controller
        return modules
    }

    private fun <T> onScreen(block: (FragmentSettingsBinding) -> T): T {
        var result: Result<T>? = null
        scenario!!.onFragment { result = runCatching { block(FragmentSettingsBinding.bind(it.requireView())) } }
        idle()
        return checkNotNull(result).getOrThrow()
    }

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    @Test
    fun opensWithTheDefaults() {
        launch()

        onScreen { binding ->
            assertTrue(binding.colorWhite.isSelected)
            assertFalse(binding.colorBlack.isSelected)
            assertTrue(binding.thicknessNormal.isSelected)
            assertFalse(binding.thicknessThick.isSelected)
            assertEquals(GuideLook(), binding.guidePreview.look)
            assertFalse(binding.latencySwitch.isChecked)
            assertEquals(string(R.string.settings_language_phone), binding.languageRow.settingValue.text.toString())
            assertEquals(string(R.string.settings_on), binding.notificationsRow.settingValue.text.toString())
            assertEquals(string(R.string.settings_consent_given), binding.consentRow.settingValue.text.toString())
            assertEquals(string(R.string.settings_signed_out), binding.accountRow.settingValue.text.toString())
            assertEquals(
                string(R.string.settings_version, BuildConfig.VERSION_NAME),
                binding.aboutRow.settingValue.text.toString(),
            )
        }
    }

    @Test
    fun aColorAndAThickness_applyAtOnce() {
        val modules = launch()

        onScreen { it.colorBlack.performClick() }
        onScreen { it.thicknessThick.performClick() }

        val chosen = GuideLook(GuideColor.BLACK, GuideThickness.THICK)
        assertEquals(chosen, modules.appSettings.guideLook.value)
        onScreen { binding ->
            assertTrue(binding.colorBlack.isSelected)
            assertFalse(binding.colorWhite.isSelected)
            assertTrue(binding.thicknessThick.isSelected)
            assertFalse(binding.thicknessNormal.isSelected)
            assertEquals(chosen, binding.guidePreview.look)
        }

        onScreen { it.colorPink.performClick() }
        onScreen { it.thicknessThin.performClick() }
        assertEquals(GuideLook(GuideColor.PINK, GuideThickness.THIN), modules.appSettings.guideLook.value)
    }

    @Test
    fun theLatencyIndicator_isTurnedOnFromItsSwitch_andOffFromItsRow() {
        val modules = launch()

        onScreen { it.latencySwitch.performClick() }
        assertTrue(modules.appSettings.showLatency.value)

        onScreen { it.latencyRow.performClick() }
        assertFalse(modules.appSettings.showLatency.value)
        onScreen { assertFalse(it.latencySwitch.isChecked) }
    }

    @Test
    fun aLanguageChosen_becomesPixsOwn_andCancelKeepsIt() {
        val modules = launch()

        onScreen { it.languageRow.root.performClick() }
        latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(Language.PHONE, modules.language.current)

        onScreen { it.languageRow.root.performClick() }
        val list = latestDialog().listView
        assertEquals(3, list.adapter.count)
        assertEquals(SettingsFragment.ENGLISH_NAME, list.adapter.getItem(1))
        assertEquals(SettingsFragment.KOREAN_NAME, list.adapter.getItem(2))
        list.performItemClick(list.adapter.getView(2, null, list), 2, 2)
        idle()
        assertEquals(Language.KOREAN, modules.language.current)

        // Android recreates the screen in the new language; here, it shows the choice when it comes back.
        scenario!!.recreate()
        idle()
        onScreen { assertEquals(SettingsFragment.KOREAN_NAME, it.languageRow.settingValue.text.toString()) }
    }

    @Test
    fun theAgreement_isWithdrawnAfterConfirming() {
        val modules = launch()

        onScreen { it.consentRow.root.performClick() }
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        assertFalse(modules.consent.given)
        onScreen { binding ->
            assertEquals(string(R.string.settings_consent_not_given), binding.consentRow.settingValue.text.toString())
            assertFalse(binding.consentRow.root.isClickable)
        }
    }

    @Test
    fun theAgreementRow_followsTheStoredAnswer_whenItLoadsAfterTheScreenOpens() {
        // Android may restore Pix straight to Settings, before DataStore has read the agreement.
        val modules = TestAppContainer().apply { consent.gate = CompletableDeferred() }
        launch(modules)
        onScreen { binding ->
            assertEquals(string(R.string.settings_consent_not_given), binding.consentRow.settingValue.text.toString())
            assertFalse(binding.consentRow.root.isClickable)
        }

        modules.consent.gate!!.complete(Unit)
        idle()

        onScreen { binding ->
            assertEquals(string(R.string.settings_consent_given), binding.consentRow.settingValue.text.toString())
            assertTrue(binding.consentRow.root.isClickable)
        }
    }

    @Test
    fun withoutAnAgreement_theRowOnlySaysSo() {
        val modules = TestAppContainer().apply { consent.given = false }
        launch(modules)

        onScreen { binding ->
            assertEquals(string(R.string.settings_consent_not_given), binding.consentRow.settingValue.text.toString())
            assertFalse(binding.consentRow.root.isClickable)
        }
    }

    @Test
    fun signedIn_theAccountRowShowsThePixId_andOpensTheAccount() {
        val modules = TestAppContainer()
        modules.auth.signInAs(Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false))
        launch(modules)

        onScreen { assertEquals(string(R.string.at_pix_id, "test1"), it.accountRow.settingValue.text.toString()) }
        onScreen { it.accountRow.root.performClick() }

        assertEquals(R.id.accountSheet, nav.currentDestination?.id)
    }

    @Test
    fun signedOut_theAccountRowOpensFriends() {
        launch()

        onScreen { it.accountRow.root.performClick() }

        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun notifications_openAndroidsSettingsForPix() {
        launch()

        onScreen { it.notificationsRow.root.performClick() }

        var started: Intent? = null
        scenario!!.onFragment { started = shadowOf(it.requireActivity()).nextStartedActivity }
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started?.action)
        assertEquals(BuildConfig.APPLICATION_ID, started?.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun howToUse_andAbout_open() {
        launch()

        onScreen { it.helpRow.root.performClick() }
        assertEquals(R.id.helpFragment, nav.currentDestination?.id)

        nav.popBackStack()
        onScreen { it.aboutRow.root.performClick() }
        assertEquals(R.id.aboutFragment, nav.currentDestination?.id)
    }

    @Test
    fun showTheTutorialAgain_makesEveryScreenShowItsTipsAgain_andSaysSo() {
        val modules = launch()

        onScreen { binding ->
            assertEquals(string(R.string.settings_tutorial), binding.tutorialRow.settingTitle.text.toString())
            binding.tutorialRow.root.performClick()
        }

        assertEquals(emptySet<TutorialScreen>(), modules.appSettings.tutorialSeen.value)
        assertEquals(string(R.string.settings_tutorial_reset), ShadowToast.getTextOfLatestToast())
        // Nothing opens over Settings: each screen shows its own tips the next time it opens.
        scenario!!.onFragment { assertNull(tutorialOver(it)) }
        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
    }
}
