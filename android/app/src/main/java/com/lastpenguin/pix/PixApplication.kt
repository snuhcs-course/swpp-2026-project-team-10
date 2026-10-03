package com.lastpenguin.pix

import android.app.Application
import com.lastpenguin.pix.core.AppContainer
import com.lastpenguin.pix.core.Timings

class PixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timings.mark("app.start")
    }

    /** Creates every module once. ViewModels get their modules from here (see ui/PixViewModels.kt). */
    val container: AppContainer by lazy { AppContainer(this) }
}
