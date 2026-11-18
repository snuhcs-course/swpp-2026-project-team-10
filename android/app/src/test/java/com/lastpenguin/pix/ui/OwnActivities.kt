// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui

import android.app.Activity
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import org.junit.rules.ExternalResource
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/**
 * The activities a test sets up itself with `Robolectric.buildActivity`, taken down after the test, as
 * `FragmentScenario.close()` takes down a launched fragment's. One left up stays alive with its theme, about 18 MB,
 * until the run ends, and the test process has 512 MB: `CameraLayoutTest`'s 27 once filled it, and the classes after
 * it never ran (android/CLAUDE.md, Testing). Declare it before any field that starts an activity.
 */
class OwnActivities : ExternalResource() {
    private val controllers = mutableListOf<ActivityController<out Activity>>()

    /** [controller] as it is, to be taken down after the test; for a test that drives its lifecycle itself. */
    fun <A : Activity> keep(controller: ActivityController<A>): ActivityController<A> =
        controller.also { controllers += it }

    /** A started `AppCompatActivity`, in [theme] when one is given: what a view under test is inflated in. */
    fun start(@StyleRes theme: Int? = null): AppCompatActivity =
        keep(
            Robolectric.buildActivity(AppCompatActivity::class.java).apply {
                theme?.let { get().setTheme(it) }
            }.setup(),
        ).get()

    override fun after() {
        for (controller in controllers) {
            // A test may have taken its own down already, as one that sends Pix to the background does.
            if (!controller.get().isDestroyed) controller.pause().stop().destroy()
        }
        controllers.clear()
    }
}
