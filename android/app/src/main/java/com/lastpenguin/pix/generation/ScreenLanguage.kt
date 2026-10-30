// AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.content.res.Resources
import com.lastpenguin.pix.R

/**
 * The language Pix's screens show, `ko` or `en`, for text the server writes (FR-10.2; Design 2.6.3). It comes from the
 * same resources as the strings, not from `Locale.getDefault()`, which can name a language Pix does not have.
 */
fun screenLanguage(resources: Resources): String =
    if (resources.getBoolean(R.bool.screens_in_korean)) KOREAN else ENGLISH

const val ENGLISH = "en"
const val KOREAN = "ko"
