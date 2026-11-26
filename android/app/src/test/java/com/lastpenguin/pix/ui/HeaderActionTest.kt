// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Joonhyung Han, and Dongje Park
package com.lastpenguin.pix.ui

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The step's action at a screen's top right is yellow text without a pill (R&S 6.9, #204): `Widget.Pix.Button.Header`
 * on each screen that has one. Partitions: each of the five screens (the camera's scene photo review, Reference
 * confirm, Pick a pose, Generate poses, the failure screen); the action enabled (the accent) and disabled (faint); the
 * pill under a touch, 32 dp tall around the text with 12 dp on each side, in a touch area at least 48 dp each way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderActionTest {
    @get:Rule
    val activities = OwnActivities()

    private val activity = activities.start(R.style.Theme_Pix)

    private fun action(layout: Int, id: Int): MaterialButton =
        LayoutInflater.from(activity).inflate(layout, null).findViewById<View>(id) as MaterialButton

    private val actions
        get() = listOf(
            action(R.layout.fragment_reference_confirm, R.id.useGuideButton),
            action(R.layout.fragment_pick_pose, R.id.selectButton),
            action(R.layout.fragment_describe_pose, R.id.generateButton),
            action(R.layout.fragment_failure, R.id.topActionButton),
        )

    private fun color(id: Int): Int = ContextCompat.getColor(activity, id)

    @Test
    fun everyTopRightAction_isYellowText_withoutAPill() {
        for (button in actions) {
            // Use on Reference confirm starts disabled until a person is found.
            button.isEnabled = true
            val name = activity.resources.getResourceEntryName(button.id)
            assertEquals(name, Color.TRANSPARENT, button.backgroundTintList?.defaultColor)
            assertEquals(name, color(R.color.pix_accent), button.currentTextColor)
        }
    }

    @Test
    fun aDisabledAction_isFaintText() {
        for (button in actions) {
            button.isEnabled = false
            val name = activity.resources.getResourceEntryName(button.id)
            assertEquals(name, color(R.color.pix_text_faint), button.currentTextColor)
            assertEquals(
                name,
                Color.TRANSPARENT,
                button.backgroundTintList?.getColorForState(intArrayOf(-android.R.attr.state_enabled), Color.RED),
            )
        }
    }

    @Test
    fun everyTopRightAction_hasAPillAroundItsText_32dpTall_inATouchArea48dpEachWay() {
        val density = activity.resources.displayMetrics.density
        for (button in actions) {
            val name = activity.resources.getResourceEntryName(button.id)
            assertEquals(name, Gravity.CENTER_HORIZONTAL, button.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
            assertEquals(name, 12, (button.paddingStart / density).roundToInt())
            assertEquals(name, 12, (button.paddingEnd / density).roundToInt())
            assertEquals(name, 32, ((button.minHeight - button.insetTop - button.insetBottom) / density).roundToInt())
            assertEquals(name, 48, (button.minHeight / density).roundToInt())
            assertEquals(name, 48, (button.minWidth / density).roundToInt())
        }
    }
}
