// AI-generated with Claude Code, 2026-11-09, reviewed by Dongje Park
package com.lastpenguin.pix.ui

import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.settings.SettingsFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * `Theme.Pix` is a DayNight theme that takes the mode Pix sets (Design 2.8, [#202]): Pix starts dark whatever the
 * phone's setting until light mode adds the choice ([#212]), and a screen is light in the day configuration and dark in
 * the night one. Partitions: the mode Pix sets at start; a screen in the night and in the day configuration.
 */
@RunWith(AndroidJUnit4::class)
class DayNightThemeTest {
    private var scenario: FragmentScenario<*>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun pix_startsDark() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun aScreen_isDark_inTheNightConfiguration() {
        TestAppContainer().install()
        assertFalse(lightTheme(settings()))
    }

    @Test
    fun aScreen_isLight_inTheDayConfiguration() {
        TestAppContainer().install()
        RuntimeEnvironment.setQualifiers("+notnight")
        assertTrue(lightTheme(settings()))
    }

    private fun settings(): View {
        var view: View? = null
        scenario = launchFragmentInContainer<SettingsFragment>(themeResId = R.style.Theme_Pix).also { launched ->
            idle()
            launched.onFragment { view = it.requireView() }
        }
        return view!!
    }

    private fun lightTheme(view: View): Boolean {
        val value = TypedValue()
        assertTrue(view.context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, value, true))
        return value.data != 0
    }
}
