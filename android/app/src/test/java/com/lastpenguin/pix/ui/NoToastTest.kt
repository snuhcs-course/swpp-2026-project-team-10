// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Short notices are Snackbars through ui/Notices.kt, never Toasts (#298), so each can offer an action, takes Pix's look,
 * and stays clear of the camera screens' shutter. Partitions: every Kotlin file of the app's sources (none uses
 * `Toast`), and the check itself finding a use, whether imported or written in full.
 */
class NoToastTest {
    private val sources = File("src/main/java")

    @Test
    fun noSourceShowsAToast() {
        val files = sources.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("${files.size} files read", files.size > 100)
        assertEquals(emptyList<String>(), files.filter { usesToast(it.readText()) }.map { it.name })
    }

    @Test
    fun theCheck_findsAToastImported_orWrittenInFull() {
        assertTrue(usesToast("import android.widget.Toast\n"))
        assertTrue(usesToast("android.widget.Toast.makeText(context, text, 0).show()"))
        assertTrue(!usesToast("// a Toast's two lengths\nshowNotice(text)"))
    }

    private fun usesToast(text: String): Boolean = "android.widget.Toast" in text
}
