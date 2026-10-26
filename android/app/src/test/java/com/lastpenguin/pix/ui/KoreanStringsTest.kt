// AI-generated with Claude Code, 2026-10-27, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/*
 * The Korean strings (FR-10.2, Iter3-P7; Testing Documentation 2.1.6), read from values/ and values-ko/ as written.
 * Android Lint's MissingTranslation already fails a key without Korean; these check what lint does not. Format
 * arguments: none, one, two in another order (ready_count), and a Korean string that drops one or adds one. Language:
 * a Korean string in Hangul, one left in English, and a name Korean keeps (Pix ID). Not translated: a string without
 * words, the app's name, and a sentence marked so. The idea chips: the same number, each within the description field.
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
    fun koreanIdeaChips_matchTheEnglishOnes_andFitTheDescriptionField() {
        val ideas = korean.arrays.getValue("pose_ideas")

        assertEquals(english.arrays.getValue("pose_ideas").size, ideas.size)
        assertEquals(ideas.size, ideas.toSet().size)
        for (idea in ideas) {
            assertTrue(idea, idea.isNotBlank())
            assertTrue(idea, idea.codePointCount(0, idea.length) <= DESCRIPTION_MAX_LENGTH)
        }
    }

    /** The format arguments of [text], `%1$s` and the like; `%%` is a percent sign, not an argument. */
    private fun arguments(text: String): List<String> =
        ARGUMENT.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    /** One strings.xml: the translatable strings, the ones marked not translatable, and the arrays. */
    private class Strings(file: File) {
        val strings = mutableMapOf<String, String>()
        val untranslatable = mutableMapOf<String, String>()
        val arrays = mutableMapOf<String, List<String>>()

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
                }
            }
        }
    }

    private companion object {
        val ARGUMENT = Regex("""%(\d+\$)?[a-zA-Z]""")
        val HANGUL = Regex("""\p{IsHangul}""")
        val LETTER = Regex("""\p{L}""")
    }
}
