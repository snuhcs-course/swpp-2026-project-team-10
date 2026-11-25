// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/*
 * A header's icon buttons draw their round light whole on every screen (#210). Back (`Widget.Pix.Header.Back`) reaches
 * 12 dp past its header's start, and an icon at its end, such as Delete or ⋮, past its end, into padding, and the
 * 40 dp light (`bg_header_icon`), drawn inside the button, reaches there too: a container that clips there cuts the
 * light in half. Partitions over every layout with such a button, at either side: its header pads that side, which
 * must not clip to that padding; or its header does not, and the container around the header must not clip its
 * children, nor its padding when it pads that side. A button placed by a margin of its own (the scene photo's Back,
 * the large photo's) reaches past nothing.
 */
class HeaderBackTest {
    private val layouts = File("src/main/res/layout").listFiles { file -> file.extension == "xml" }.orEmpty()

    private fun Element.android(name: String): String? =
        getAttributeNS(ANDROID, name).takeIf { hasAttributeNS(ANDROID, name) }

    private fun Element.pads(side: String) =
        listOf("padding", "paddingHorizontal", "padding$side", if (side == "Start") "paddingLeft" else "paddingRight")
            .any { android(it) != null }

    /** The sides past which [this] reaches by a negative margin: Back by its style's, unless it sets its own. */
    private fun Element.reachesPast(): List<String> {
        val back = getAttribute("style") == "@style/Widget.Pix.Header.Back"
        val start = android("layout_marginStart")?.startsWith("-") ?: back
        val end = android("layout_marginEnd")?.startsWith("-") == true
        return listOfNotNull("Start".takeIf { start }, "End".takeIf { end })
    }

    @Test
    fun everyHeaderIcon_drawsItsLightWhole() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        var icons = 0
        for (layout in layouts) {
            val elements = factory.newDocumentBuilder().parse(layout).getElementsByTagName("*")
            for (i in 0 until elements.length) {
                val icon = elements.item(i) as Element
                val lit = icon.getAttribute("style") == "@style/Widget.Pix.Header.Back" ||
                    icon.android("background") == "@drawable/bg_header_icon"
                if (!lit) continue
                for (side in icon.reachesPast()) {
                    icons++
                    val header = icon.parentNode as Element
                    val where = "${layout.name}: ${icon.android("id")} at its ${side.lowercase()}"
                    if (header.pads(side)) {
                        assertEquals(
                            "$where, in a header clipping to its padding",
                            "false",
                            header.android("clipToPadding"),
                        )
                    } else {
                        val screen = header.parentNode as Element
                        assertEquals("$where, in a header the screen clips", "false", screen.android("clipChildren"))
                        if (screen.pads(side)) {
                            assertEquals(
                                "$where, in a screen clipping to its padding",
                                "false",
                                screen.android("clipToPadding"),
                            )
                        }
                    }
                }
            }
        }
        assertTrue("$icons icons found", icons > 15)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
