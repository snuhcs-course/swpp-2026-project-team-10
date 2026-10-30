// AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/*
 * The language the screens show, for the pose tip (FR-10.2; Testing Documentation 2.1.6), from the resources Android
 * picks for the phone's language: none named (English), English, Korean, Korean of a region, and a language Pix does
 * not have, whose screens are in English.
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
}
