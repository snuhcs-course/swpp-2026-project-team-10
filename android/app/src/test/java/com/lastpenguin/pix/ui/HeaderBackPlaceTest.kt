// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.w3c.dom.Element

/*
 * Back stays put from screen to screen (R&S 6.9): every layout's header Back (`Widget.Pix.Header.Back`), inflated on
 * the Galaxy S22's app area, stands 8 dp from the screen's left edge with its 48 dp button's top 12 dp down, in the
 * middle of a 56 dp header under the screen's 8 dp, as on the pose steps; a photo opened over a screen, such as the
 * large scene photo and the photo viewer, has its Back over the screen's own. Partitions: a header in a screen that
 * pads it, a header that pads itself, a header with margins of its own, and a Back placed alone over a photo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class HeaderBackPlaceTest {
    @get:Rule
    val activities = OwnActivities()

    private val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }

    /** Each layout with a header Back, by its name, and the Back's id. */
    private fun backs(): Map<String, String> = File("src/main/res/layout").listFiles { file -> file.extension == "xml" }
        .orEmpty()
        .mapNotNull { file ->
            val elements = factory.newDocumentBuilder().parse(file).getElementsByTagName("*")
            (0 until elements.length).map { elements.item(it) as Element }
                .firstOrNull { it.getAttribute("style") == "@style/Widget.Pix.Header.Back" }
                ?.let { file.nameWithoutExtension to it.getAttributeNS(ANDROID, "id").substringAfter("/") }
        }
        .toMap()

    @Test
    fun everyHeadersBack_standsWhereThePoseStepsBackDoes() {
        val activity = activities.start(R.style.Theme_Pix)
        val density = activity.resources.displayMetrics.density
        val backs = backs()
        assertTrue("${backs.keys}", backs.size > 15)
        val wrong = mutableListOf<String>()
        for ((layout, id) in backs) {
            val root = activity.layoutInflater.inflate(
                activity.resources.getIdentifier(layout, "layout", activity.packageName),
                null,
                false,
            )
            activity.setContentView(root)
            val back = root.findViewById<View>(activity.resources.getIdentifier(id, "id", activity.packageName))
            // Shown, as its screen shows it.
            var shown: View? = back
            while (shown != null) {
                shown.visibility = View.VISIBLE
                shown = shown.parent as? View
            }
            root.measure(
                View.MeasureSpec.makeMeasureSpec(activity.window.decorView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((root.parent as ViewGroup).height, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val at = IntArray(2).also(back::getLocationInWindow)
            val from = IntArray(2).also(root::getLocationInWindow)
            val left = (at[0] - from[0]) / density
            val top = (at[1] - from[1]) / density
            val wide = back.width / density
            if (abs(left - IN_DP) > 0.5f || abs(top - DOWN_DP) > 0.5f || abs(wide - WIDE_DP) > 0.5f) {
                wrong += "$layout: $left dp in, $top dp down, $wide dp wide"
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"

        /** Where the pose steps have Back: 8 dp in, its top 12 dp down, 48 dp wide. */
        const val IN_DP = 8f
        const val DOWN_DP = 12f
        const val WIDE_DP = 48f
    }
}
