// AI-generated with Claude Code, 2026-11-13, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Every row or card that a tap opens ends in the same chevron, its tip 16 dp from the right edge (#204):
 * `Widget.Pix.Chevron` after 8 dp of end padding. Partitions: each screen that has one (a Settings row, Friends' Show
 * a code, Account's Add Google and Add a password, and Add a guide's Generate poses here); a row without a tile, a
 * card with a tile, and a card with text only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class ChevronTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java)
        .apply { get().setTheme(R.style.Theme_Pix) }
        .setup()
        .get()

    private val chevrons = listOf(
        R.layout.item_setting_row to R.id.settingChevron,
        R.layout.fragment_friends to R.id.showCodeChevron,
        R.layout.sheet_account to R.id.addGoogleChevron,
        R.layout.sheet_account to R.id.addPasswordChevron,
        R.layout.sheet_add_guide to R.id.generateChevron,
    )

    /** [id] in [layout], laid out 360 dp wide. */
    private fun chevron(layout: Int, id: Int): ImageView {
        val root = LayoutInflater.from(activity).inflate(layout, FrameLayout(activity), false)
        val chevron = root.findViewById<ImageView>(id)
        // A card that shows only for some accounts is laid out as it shows.
        var view: View = chevron
        while (view !== root) {
            view.visibility = View.VISIBLE
            view = view.parent as View
        }
        val density = activity.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).roundToInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return chevron
    }

    @Test
    fun everyChevron_is24dpAndFaint_withItsTip16dpFromItsRowsEdge() {
        val density = activity.resources.displayMetrics.density
        for ((layout, id) in chevrons) {
            val chevron = chevron(layout, id)
            val name = activity.resources.getResourceEntryName(id)
            val row = chevron.parent as View
            assertEquals(name, 24, (chevron.width / density).roundToInt())
            assertEquals(
                name,
                ContextCompat.getColor(activity, R.color.pix_text_faint),
                ImageViewCompat.getImageTintList(chevron)?.defaultColor,
            )
            // ic_chevron_right's tip, with half its stroke, is 8 dp inside its 24 dp box.
            val tip = (row.width - chevron.right) / density + 8
            assertEquals(name, 16f, tip, 0.5f)
        }
    }
}
