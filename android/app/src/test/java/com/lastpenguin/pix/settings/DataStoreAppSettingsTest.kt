// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideThickness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The settings kept across runs (FR-10.1, Design 2.8): the defaults before any choice, then each choice in the flows and
 * read back by a new instance, as after a restart. One test, because the DataStore behind the context extension lives
 * for the process.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreAppSettingsTest {
    @Test
    fun defaultsAtFirst_thenEachChoiceIsKept() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = DataStoreAppSettings(ApplicationProvider.getApplicationContext(), scope)
        assertEquals(GuideLook(GuideColor.WHITE, GuideThickness.NORMAL), settings.guideLook.value)
        assertFalse(settings.showLatency.value)
        assertEquals(false, withTimeout(TIMEOUT_MS) { settings.tutorialSeen.filterNotNull().first() })

        settings.setGuideColor(GuideColor.BLACK)
        settings.setGuideThickness(GuideThickness.THICK)
        settings.setShowLatency(true)
        settings.setTutorialSeen()
        val chosen = GuideLook(GuideColor.BLACK, GuideThickness.THICK)
        assertEquals(chosen, withTimeout(TIMEOUT_MS) { settings.guideLook.first { it == chosen } })
        assertTrue(withTimeout(TIMEOUT_MS) { settings.showLatency.first { it } })
        assertEquals(true, withTimeout(TIMEOUT_MS) { settings.tutorialSeen.first { it == true } })

        val restarted = DataStoreAppSettings(ApplicationProvider.getApplicationContext(), scope)
        assertEquals(chosen, withTimeout(TIMEOUT_MS) { restarted.guideLook.first { it == chosen } })
        assertTrue(withTimeout(TIMEOUT_MS) { restarted.showLatency.first { it } })
        assertEquals(true, withTimeout(TIMEOUT_MS) { restarted.tutorialSeen.first { it == true } })
        scope.cancel()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
