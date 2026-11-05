// AI-generated with Claude Code, 2026-11-05, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/*
 * The Korean strings (FR-10.2, Iter3-P7; Testing Documentation 2.1.6), read from values/ and values-ko/ as written.
 * Android Lint's MissingTranslation already fails a key without Korean; these check what lint does not. Format
 * arguments: none, one, two in another order (ready_count), one with a width (invitation_time_left), and a Korean
 * string that drops one or adds one; a quantity string's Korean (only `other`) against the English `other`. Language:
 * a Korean string or quantity in Hangul, one left in English, and a name Korean keeps (Pix ID). Not translated: a
 * string without words, the app's name, and a sentence marked so. The idea chips: the same number, each within the description field.
 * Fonts: a text style that names a @font/pix_* alias or DM Mono, one that names a Manrope file itself (which would leave
 * Korean in the phone's font), and an alias with and without its Korean (Pretendard) counterpart in res/font-ko/.
 */
class KoreanStringsTest {
    private val english = Strings(File("src/main/res/values/strings.xml"))
    private val korean = Strings(File("src/main/res/values-ko/strings.xml"))

    @Test
    fun everyKoreanString_hasTheEnglishFormatArguments() {
        // A missing argument drops a name or a number; an extra one throws when the screen formats the string.
        val different = korean.strings.filter { (key, text) ->
            arguments(text) != arguments(english.strings.getValue(key))
        }
        assertEquals(emptyMap<String, String>(), different)
    }

    @Test
    fun everyKoreanQuantity_hasTheEnglishFormatArguments_andIsInHangul() {
        // Korean counts with `other` alone; each of its quantities must still take the English count.
        val wrong = korean.plurals.flatMap { (key, items) ->
            val english = arguments(this.english.plurals.getValue(key).getValue("other"))
            items.filter { (_, text) -> arguments(text) != english || HANGUL !in text }.map { "$key/${it.key}" }
        }
        assertEquals(emptyList<String>(), wrong)
        assertTrue(korean.plurals.isNotEmpty())
    }

    @Test
    fun everyKoreanString_isInHangul_butTheNamesKoreanKeeps() {
        val keptInEnglish = setOf("pix_id_label") // "Pix ID", as on the English screen
        val notKorean = korean.strings.filter { (key, text) -> key !in keptInEnglish && HANGUL !in text }
        assertEquals(emptyMap<String, String>(), notKorean)
    }

    @Test
    fun stringsMarkedNotTranslatable_haveNoWords_butTheAppName() {
        // Marking a sentence translatable="false" would keep it out of lint's check and leave it in English.
        val worded = english.untranslatable.filter { (key, text) ->
            key != "app_name" && LETTER in text.replace("%%", "").replace(ARGUMENT, "")
        }
        assertEquals(emptyMap<String, String>(), worded)
    }

    @Test
    fun everyTextStyle_namesAFontAlias_soKoreanGetsPretendard() {
        val styles = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/styles.xml")).getElementsByTagName("item")
        val fonts = (0 until styles.length).map { styles.item(it) as Element }
            .filter { it.getAttribute("name") in setOf("android:fontFamily", "fontFamily") }
            .map { it.textContent.trim() }
        val direct = fonts.filterNot { it.startsWith("@font/pix_") || it == "@font/dm_mono_medium" }
        assertEquals(emptyList<String>(), direct)
        assertTrue(fonts.isNotEmpty())
    }

    @Test
    fun everyFontAlias_hasItsKoreanFont() {
        fun aliases(
            folder: String,
        ) = File(folder).listFiles { f -> f.name.startsWith("pix_") }.orEmpty().map { it.name }.sorted()
        val english = aliases("src/main/res/font")
        assertTrue(english.isNotEmpty())
        assertEquals(english, aliases("src/main/res/font-ko"))
    }

    /** The format arguments of [text], `%1$s` and the like; `%%` is a percent sign, not an argument. */
    private fun arguments(text: String): List<String> =
        ARGUMENT.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    /** One strings.xml: the translatable strings, the ones marked not translatable, the arrays, and the plurals. */
    private class Strings(file: File) {
        val strings = mutableMapOf<String, String>()
        val untranslatable = mutableMapOf<String, String>()
        val arrays = mutableMapOf<String, List<String>>()

        /** Each quantity string's texts by quantity (`one`, `other`). */
        val plurals = mutableMapOf<String, Map<String, String>>()

        init {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" ->
                        if (element.getAttribute("translatable") == "false") {
                            untranslatable[name] = element.textContent
                        } else {
                            strings[name] = element.textContent
                        }

                    "string-array" -> {
                        val items = element.getElementsByTagName("item")
                        arrays[name] = (0 until items.length).map { items.item(it).textContent }
                    }

                    "plurals" -> {
                        val items = element.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).map { items.item(it) as Element }
                            .associate { it.getAttribute("quantity") to it.textContent }
                    }
                }
            }
        }
    }

    private companion object {
        // %1$s, %d, and %2$02d, with flags and a width.
        val ARGUMENT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*[a-zA-Z]""")
        val HANGUL = Regex("""\p{IsHangul}""")
        val LETTER = Regex("""\p{L}""")
    }
}
