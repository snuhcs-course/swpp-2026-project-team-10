// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/*
 * A header's icon buttons draw their round light whole on every screen, and nothing scrolls over a header (#210, #320).
 * Back (`Widget.Pix.Header.Back`) reaches 12 dp past its header's start, and an icon at a header's end, such as Delete
 * or ⋮, past its end, with its 40 dp light (`bg_header_icon`) drawn inside the button. Partitions over every layout:
 * - Back, or an icon at the end, in a header that pads that side: the header must not clip to that padding, and when
 *   the header itself reaches past its screen's padding by a negative margin, as each Back's header does, the screen
 *   must not clip to its padding either. A header that does not pad the side fails: its Back would stand outside it,
 *   and only a screen that stopped clipping its children could show the light whole.
 * - A button placed by a margin of its own (the scene photo's Back, the large photo's): reaches past nothing.
 * - A scrolling view (a ScrollView, a list, a pager) that draws into its own padding: its container must clip its
 *   children, or what scrolls draws past the view, over the header above it, as Settings' did.
 */
class HeaderBackTest {
    private val layouts = File("src/main/res/layout").listFiles { file -> file.extension == "xml" }.orEmpty()
    private val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }

    private fun Element.android(name: String): String? =
        getAttributeNS(ANDROID, name).takeIf { hasAttributeNS(ANDROID, name) }

    private fun Element.pads(side: String) =
        listOf("padding", "paddingHorizontal", "padding$side", if (side == "Start") "paddingLeft" else "paddingRight")
            .any { android(it) != null }

    private fun Element.reachesPast(side: String) = android("layout_margin$side")?.startsWith("-") == true

    /** The sides past which [this] reaches by a negative margin: Back by its style's, unless it sets its own. */
    private fun Element.iconReachesPast(): List<String> {
        val back = getAttribute("style") == "@style/Widget.Pix.Header.Back"
        val start = android("layout_marginStart")?.startsWith("-") ?: back
        return listOfNotNull("Start".takeIf { start }, "End".takeIf { reachesPast("End") })
    }

    /** Whether [this] header reaches past [side] of a screen that clips to its padding there. */
    private fun Element.reachesPastAClippingScreen(side: String): Boolean {
        val screen = parentNode as Element
        return reachesPast(side) && screen.pads(side) && screen.android("clipToPadding") != "false"
    }

    private fun elements(layout: File): List<Element> {
        val all = factory.newDocumentBuilder().parse(layout).getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }
    }

    @Test
    fun everyHeaderIcon_drawsItsLightWhole_insideItsHeader() {
        var icons = 0
        val wrong = mutableListOf<String>()
        for (layout in layouts) {
            for (icon in elements(layout)) {
                val lit = icon.getAttribute("style") == "@style/Widget.Pix.Header.Back" ||
                    icon.android("background") == "@drawable/bg_header_icon"
                if (!lit) continue
                for (side in icon.iconReachesPast()) {
                    icons++
                    val header = icon.parentNode as Element
                    val where = "${layout.name}: ${icon.android("id")} at its ${side.lowercase()}"
                    when {
                        !header.pads(side) -> wrong += "$where, in a header that does not pad it"
                        header.android("clipToPadding") != "false" -> wrong += "$where, in a header clipping to it"
                        header.reachesPastAClippingScreen(side) -> wrong += "$where, in a screen clipping to it"
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), wrong)
        assertTrue("$icons icons found", icons > 15)
    }

    @Test
    fun nothingThatScrolls_drawsPastItsView_overTheHeader() {
        var scrolling = 0
        val wrong = mutableListOf<String>()
        for (layout in layouts) {
            for (view in elements(layout)) {
                if (view.tagName.substringAfterLast('.') !in SCROLLING) continue
                scrolling++
                val container = view.parentNode as? Element ?: continue
                if (view.android("clipToPadding") == "false" && container.android("clipChildren") == "false") {
                    wrong += "${layout.name}: ${view.android("id") ?: view.tagName}"
                }
            }
        }
        assertEquals(emptyList<String>(), wrong)
        assertTrue("$scrolling scrolling views found", scrolling > 10)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val SCROLLING = setOf("ScrollView", "NestedScrollView", "HorizontalScrollView", "RecyclerView", "ViewPager2")
    }
}
