// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/*
 * A touch lights what was touched in its own shape (#210). Android's `?attr/selectableItemBackground` is a square, so on
 * a rounded card or thumbnail its corners show past the shape. Partitions over every layout's element with that light:
 * on a list card itself (`bg_list_card`, which takes `fg_list_card_press`); on a row inside one, also a row a card
 * includes from another layout, as Settings' rows (`bg_card_row`); on a rounded thumbnail, a `ShapeableImageView` with
 * a shape (the press of its shape, such as `fg_thumbnail_press`); on anything else, such as the Pix ID that a tap copies
 * (`bg_text_press`); and on a square, the gallery's square cells, the only place it may stay.
 */
class RoundedHighlightTest {
    private val layouts = File("src/main/res/layout").listFiles { file -> file.extension == "xml" }.orEmpty()
    private val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }

    private fun Element.android(name: String): String? =
        getAttributeNS(ANDROID, name).takeIf { hasAttributeNS(ANDROID, name) }

    private fun Element.elements(): List<Element> {
        val all = getElementsByTagName("*")
        return listOf(this) + (0 until all.length).map { all.item(it) as Element }
    }

    private fun Element.isCard() = android("background") == CARD

    /** The layouts that some list card includes as its rows, by their names. */
    private fun rowsOfCards(): Set<String> = layouts.flatMap { layout ->
        factory.newDocumentBuilder().parse(layout).documentElement.elements()
            .filter { it.tagName == "include" && (it.parentNode as? Element)?.isCard() == true }
            .map { it.getAttribute("layout").removePrefix("@layout/") }
    }.toSet()

    @Test
    fun noSquareLight_onARoundedCard_aRowInOne_orARoundedThumbnail() {
        val rows = rowsOfCards()
        assertTrue("$rows", "item_setting_row" in rows)
        val wrong = mutableListOf<String>()
        for (layout in layouts) {
            val root = factory.newDocumentBuilder().parse(layout).documentElement
            for (element in root.elements()) {
                val square = listOf("background", "foreground").any { element.android(it) == SQUARE }
                if (!square) continue
                val parent = element.parentNode as? Element
                val where = "${layout.name}: ${element.android("id") ?: element.tagName}"
                when {
                    element.isCard() -> wrong += "$where, a card"

                    parent?.isCard() == true -> wrong += "$where, a row in a card"

                    element === root && layout.nameWithoutExtension in rows -> wrong += "$where, a card's row"

                    element.tagName.endsWith("ShapeableImageView") &&
                        element.hasAttributeNS(APP, "shapeAppearanceOverlay") -> wrong += "$where, a rounded thumbnail"

                    layout.name !in SQUARES -> wrong += "$where, not a square"
                }
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val APP = "http://schemas.android.com/apk/res-auto"
        const val SQUARE = "?attr/selectableItemBackground"
        const val CARD = "@drawable/bg_list_card"

        /** The layouts whose touched thing is a square: the gallery's cells. */
        val SQUARES = setOf("item_photo_cell.xml")
    }
}
