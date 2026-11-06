// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentAboutBinding
import com.lastpenguin.pix.databinding.ItemLicenseBinding

/**
 * About Pix, from Settings: the version, and the open-source libraries and fonts in the app with their licenses, which
 * ask to be credited. The names are not translated.
 * Owner: Settings (#123).
 */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAboutBinding.bind(view)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.versionText.text = BuildConfig.VERSION_NAME
        val inflater = LayoutInflater.from(view.context)
        for (library in LIBRARIES) {
            val row = ItemLicenseBinding.inflate(inflater, binding.licenseList, true)
            row.libraryName.text = library.name
            row.libraryLicense.text = library.license
        }
    }

    internal data class Library(val name: String, val license: String)

    internal companion object {
        private const val APACHE = "Apache License 2.0"
        private const val OFL = "SIL Open Font License 1.1"

        /** Everything in `app/build.gradle.kts` that ships in the app, and the fonts in `res/font`. */
        val LIBRARIES = listOf(
            Library("AndroidX (CameraX, Navigation, DataStore, Credentials)", APACHE),
            Library("Material Components for Android", APACHE),
            Library("Kotlin, kotlinx.coroutines, kotlinx.serialization", APACHE),
            Library("OkHttp, Retrofit", APACHE),
            Library("WebRTC", "BSD 3-Clause License"),
            Library("Firebase Cloud Messaging", APACHE),
            Library("ML Kit Subject Segmentation", "ML Kit Terms of Service"),
            Library("Google Identity Services", "Google APIs Terms of Service"),
            Library("Pretendard", OFL),
            Library("Manrope", OFL),
            Library("DM Mono", OFL),
            Library("Feather Icons", "MIT License"),
        )
    }
}
