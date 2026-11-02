// AI-generated with Claude Code, 2026-11-02, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.generation

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/*
 * The language the screens show, for the pose tip (FR-10.2; Testing Documentation 2.1.6), from the resources Android
 * picks for the phone's language: none named (English), English, Korean, Korean of a region, and a language Pix does
 * not have, whose screens are in English. While Pix runs: changed to Korean, then to a language Pix does not have; and
 * Pix's own language, which on Android 10–12 reaches an activity's resources only (`follow`).
 */
@RunWith(AndroidJUnit4::class)
class ScreenLanguageTest {
    private fun language() = screenLanguage(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun noLanguageNamed_isEnglish() = assertEquals("en", language())

    @Test
    @Config(qualifiers = "en")
    fun english_isEnglish() = assertEquals("en", language())

    @Test
    @Config(qualifiers = "ko")
    fun korean_isKorean() = assertEquals("ko", language())

    @Test
    @Config(qualifiers = "ko-rKR")
    fun koreanOfARegion_isKorean() = assertEquals("ko", language())

    @Test
    @Config(qualifiers = "ja-rJP")
    fun aLanguagePixDoesNotHave_isEnglish() = assertEquals("en", language())

    @Test
    fun aLanguageChangedWhilePixRuns_isFollowed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val languages = ScreenLanguages(app)
        assertEquals("en", languages.current.value)

        app.onConfigurationChanged(Configuration(app.resources.configuration).apply { setLocale(Locale.KOREAN) })
        assertEquals("ko", languages.current.value)

        app.onConfigurationChanged(Configuration(app.resources.configuration).apply { setLocale(Locale.JAPANESE) })
        assertEquals("en", languages.current.value)
    }

    @Test
    fun pixsOwnLanguage_inAnActivitysResources_isFollowed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val languages = ScreenLanguages(app)
        val korean = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocale(Locale.KOREAN) },
        )

        languages.follow(korean.resources)
        assertEquals("ko", languages.current.value)

        languages.follow(app.resources)
        assertEquals("en", languages.current.value)
    }
}
