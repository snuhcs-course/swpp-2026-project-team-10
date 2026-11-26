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
 * (`bg_text_press`); and on a square, the gallery's square cells, the only place it may stay. And Android's
 * `?attr/selectableItemBackgroundBorderless`, a circle as wide as its view's diagonal, set on a view or by its style:
 * on an option beside others of its style, as the camera menu's and the ratios', it spreads over the next option and is
 * cut flat by its panel (`bg_panel_option` instead); around a rounded thumbnail, as the scene photo's frame on Generate
 * poses, it spreads past the thumbnail's corners (the thumbnail's own press instead, such as
 * `fg_small_thumbnail_press`); on a lone icon, with room around it, it may stay.
 */
class RoundedHighlightTest {
    private val layouts = File("src/main/res/layout").listFiles { file -> file.extension == "xml" }.orEmpty()
    private val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }

    /** Every style of the app, by its name. */
    private val styles: Map<String, Element> by lazy {
        File("src/main/res/values").listFiles { file -> file.extension == "xml" }.orEmpty()
            .flatMap { file ->
                val all = factory.newDocumentBuilder().parse(file).getElementsByTagName("style")
                (0 until all.length).map { all.item(it) as Element }
            }
            .associateBy { it.getAttribute("name") }
    }

    /** What [style] sets [attr] to, itself or through its parent: the one it names, else the one its name extends. */
    private fun styleSets(style: String, attr: String): String? {
        val element = styles[style] ?: return null
        val items = element.getElementsByTagName("item")
        (0 until items.length).map { items.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == "android:$attr" }
            ?.let { return it.textContent.trim() }
        val parent = if (element.hasAttribute("parent")) {
            element.getAttribute("parent").removePrefix("@style/")
        } else {
            style.substringBeforeLast('.', "")
        }
        return parent.takeIf { it.isNotEmpty() }?.let { styleSets(it, attr) }
    }

    private fun Element.android(name: String): String? =
        getAttributeNS(ANDROID, name).takeIf { hasAttributeNS(ANDROID, name) }

    private fun Element.elements(): List<Element> {
        val all = getElementsByTagName("*")
        return listOf(this) + (0 until all.length).map { all.item(it) as Element }
    }

    private fun Element.isCard() = android("background") == CARD

    /** What [this] has as its [attr], set on it or by its style. */
    private fun Element.light(attr: String): String? = android(attr) ?: getAttribute("style")
        .takeIf { it.startsWith("@style/") }
        ?.let { styleSets(it.removePrefix("@style/"), attr) }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

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

    @Test
    fun noBorderlessLight_onAnOptionBesideOthers_orAroundARoundedThumbnail() {
        var options = 0
        val wrong = mutableListOf<String>()
        for (layout in layouts) {
            for (element in factory.newDocumentBuilder().parse(layout).documentElement.elements()) {
                val style = element.getAttribute("style")
                val row = element.parentNode as? Element ?: continue
                val lights = listOf("background", "foreground").mapNotNull { element.light(it) }
                val where = "${layout.name}: ${element.android("id") ?: style}"
                if (style.isNotEmpty() && row.children().count { it.getAttribute("style") == style } > 1 &&
                    lights.isNotEmpty()
                ) {
                    options++
                    if (BORDERLESS in lights) wrong += "$where, an option beside others"
                }
                val thumbnail = element.children().any {
                    it.tagName.endsWith("ShapeableImageView") && it.hasAttributeNS(APP, "shapeAppearanceOverlay")
                }
                if (thumbnail && BORDERLESS in lights) wrong += "$where, around a rounded thumbnail"
            }
        }
        assertEquals(emptyList<String>(), wrong)
        assertTrue("$options options found", options >= OPTIONS)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val APP = "http://schemas.android.com/apk/res-auto"
        const val SQUARE = "?attr/selectableItemBackground"
        const val BORDERLESS = "?attr/selectableItemBackgroundBorderless"

        /** At least the camera menu's options and the ratios on Camera, and the menu's on Subject view. */
        const val OPTIONS = 11
        const val CARD = "@drawable/bg_list_card"

        /** The layouts whose touched thing is a square: the gallery's cells. */
        val SQUARES = setOf("item_photo_cell.xml")
    }
}
