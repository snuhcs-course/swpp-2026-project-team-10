// AI-generated with ChatGPT Codex and Claude Code, 2026-11-09, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.core.Timings

class PixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timings.mark("app.start")
        // Dark whatever the phone's setting: Theme.Pix takes the mode set here (DayNight), and light mode adds its
        // colors and a choice in Settings (Iter4-P15, #212).
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private var installed: AppContainer? = null

    /** Creates every module once. ViewModels get their modules from here (see ui/PixViewModels.kt). */
    val container: AppContainer
        get() = installed ?: AppContainer.create(this).also { installed = it }

    /** The modules if they exist, without creating them: for the push service, which runs off the main thread. */
    val createdContainer: AppContainer?
        get() = installed

    /**
     * A screen test under Robolectric installs a container of fakes before the screen is launched, so no screen
     * creates the real camera, ML Kit, WebRTC, or network modules (Testing Documentation 2.1.3).
     */
    @VisibleForTesting
    fun installContainer(container: AppContainer) {
        installed = container
    }
}
