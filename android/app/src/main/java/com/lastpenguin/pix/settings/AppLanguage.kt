// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The languages Pix has screens in, and the phone's own (FR-10.2). */
enum class Language(val tag: String?) {
    PHONE(null),
    ENGLISH("en"),
    KOREAN("ko"),
}

/**
 * Pix's own language, chosen on the settings screen (FR-10.2). Android keeps it as the per-app language: from Android
 * 13 the system, before that AppCompat's `AppLocalesMetadataHolderService` in the manifest. Choosing one recreates the
 * activity in it, with its back stack, and `ScreenLanguages` follows the change.
 * Owner: Settings (#123).
 */
interface AppLanguage {
    val current: Language

    fun choose(language: Language)

    /** Remembers the choice without applying it; for tests. */
    class InMemory(override var current: Language = Language.PHONE) : AppLanguage {
        override fun choose(language: Language) {
            current = language
        }
    }
}

/** Through `AppCompatDelegate`, which needs the activity to be an `AppCompatActivity` (MainActivity is). */
class AndroidAppLanguage : AppLanguage {
    override val current: Language
        get() {
            val chosen = AppCompatDelegate.getApplicationLocales()
            if (chosen.isEmpty) return Language.PHONE
            return Language.entries.firstOrNull { it.tag != null && it.tag == chosen[0]?.language } ?: Language.PHONE
        }

    override fun choose(language: Language) {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
