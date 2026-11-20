// AI-generated with Claude Code, 2026-11-09, reviewed by Dongje Park
package com.lastpenguin.pix.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Node

/**
 * Every color comes from a token (README, *Look*; Design 2.8, [#202]): no layout, drawable, color list, style, or theme
 * writes a color as a value but `values/colors.xml`, where a mode can change it. Kept: the app's own icons, which carry
 * the logo's colors (`ic_launcher_foreground`, `ic_launcher_monochrome`, `ic_notification`), and an icon's white and
 * transparent, which only shape it, since the tint where it is used gives its color. Partitions: a color in an
 * attribute and in an element's text (a style's item); an icon's white, transparent, and any other color; a comment,
 * which may name an issue such as #212, is no color.
 */
class ColorTokensTest {
    private val res = File("src/main/res")

    @Test
    fun everyColor_isAToken() {
        val found = colorFiles().flatMap { file ->
            literals(file).filterNot { kept(file, it) }.map { "${file.relativeTo(res)}: $it" }
        }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun theCheck_readsEveryKindOfFile_andFindsColorsInAttributesAndTexts() {
        assertTrue(colorFiles().map { it.parentFile.name }.containsAll(listOf("layout", "drawable", "color", "values")))
        // colors.xml writes its colors as texts, the logo's icon in attributes; the comments of both name issues.
        assertTrue("#0B0B0D" in literals(File(res, "values/colors.xml")))
        assertTrue("#FFF2C14E" in literals(File(res, "drawable/ic_launcher_foreground.xml")))
        assertTrue(literals(File(res, "values/colors.xml")).none { it == "#202" || it == "#212" })
        // An icon keeps its white and transparent, not another color; nothing else keeps them.
        val icon = File(res, "drawable/ic_close.xml")
        assertTrue(kept(icon, "#FFFFFFFF") && kept(icon, "#00000000") && !kept(icon, "#FFF2C14E"))
        assertTrue(!kept(File(res, "drawable/bg_shutter.xml"), "#FFFFFFFF"))
    }

    private fun colorFiles(): List<File> = res.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.substringBefore('-') in FOLDERS }
        .flatMap { folder -> folder.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
        .filterNot { it.name == "colors.xml" }
        .sortedBy { it.path }

    /** The colors written as values in [file]: in its attributes and its elements' texts, never its comments. */
    private fun literals(file: File): List<String> {
        val found = mutableListOf<String>()
        fun visit(node: Node) {
            when (node.nodeType) {
                Node.ELEMENT_NODE -> {
                    val attributes = node.attributes
                    for (i in 0 until attributes.length) {
                        found +=
                            COLOR.findAll(attributes.item(i).nodeValue).map { it.value }
                    }
                }

                Node.TEXT_NODE -> found += COLOR.findAll(node.nodeValue).map { it.value }
            }
            val children = node.childNodes
            for (i in 0 until children.length) visit(children.item(i))
        }
        visit(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement)
        return found
    }

    private fun kept(file: File, color: String): Boolean =
        file.nameWithoutExtension in LOGO || (file.name.startsWith("ic_") && color.uppercase() in SHAPE)

    private companion object {
        val FOLDERS =
            setOf("anim", "animator", "color", "drawable", "layout", "menu", "mipmap", "navigation", "values", "xml")
        val COLOR = Regex("#[0-9A-Fa-f]{3,8}\\b")
        val LOGO = setOf("ic_launcher_foreground", "ic_launcher_monochrome", "ic_notification")
        val SHAPE = setOf("#FFF", "#FFFFFF", "#FFFFFFFF", "#00000000")
    }
}
