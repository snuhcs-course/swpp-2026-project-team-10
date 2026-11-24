// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park
package com.lastpenguin.pix.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideThickness
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The settings kept across runs (FR-10.1, Design 2.8): the defaults before any choice (usage data shared, FR-10.6; the
 * grid and the horizon bar on, FR-1.9, FR-1.10), then each choice in the flows and read back by a new instance, as after
 * a restart; and a store that cannot be read,
 * whose usage choice stays unread instead of taking the default (FR-10.6) while the other settings take theirs. One test, because the DataStore behind the context extension lives
 * for the process.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreAppSettingsTest {
    @Test
    fun defaultsAtFirst_thenEachChoiceIsKept() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = DataStoreAppSettings(ApplicationProvider.getApplicationContext<Context>(), scope)
        assertEquals(GuideLook(GuideColor.WHITE, GuideThickness.NORMAL), settings.guideLook.value)
        assertFalse(settings.showLatency.value)
        assertTrue(settings.showGrid.value)
        assertTrue(settings.showLevel.value)
        assertEquals(
            emptySet<TutorialScreen>(),
            withTimeout(TIMEOUT_MS) {
                settings.tutorialSeen.filterNotNull().first()
            },
        )
        assertTrue(withTimeout(TIMEOUT_MS) { settings.shareUsage.filterNotNull().first() })

        settings.setGuideColor(GuideColor.BLACK)
        settings.setGuideThickness(GuideThickness.THICK)
        settings.setShowLatency(true)
        settings.setShowGrid(false)
        settings.setShowLevel(false)
        settings.setShareUsage(false)
        // Each screen's tips on their own: the second keeps the first.
        settings.setTutorialSeen(setOf(TutorialScreen.CAMERA))
        settings.setTutorialSeen(setOf(TutorialScreen.SUBJECT_VIEW))
        val seen = setOf(TutorialScreen.CAMERA, TutorialScreen.SUBJECT_VIEW)
        val chosen = GuideLook(GuideColor.BLACK, GuideThickness.THICK)
        assertEquals(chosen, withTimeout(TIMEOUT_MS) { settings.guideLook.first { it == chosen } })
        assertTrue(withTimeout(TIMEOUT_MS) { settings.showLatency.first { it } })
        assertFalse(withTimeout(TIMEOUT_MS) { settings.showGrid.first { !it } })
        assertFalse(withTimeout(TIMEOUT_MS) { settings.showLevel.first { !it } })
        assertEquals(seen, withTimeout(TIMEOUT_MS) { settings.tutorialSeen.first { it == seen } })
        assertEquals(false, withTimeout(TIMEOUT_MS) { settings.shareUsage.first { it == false } })

        val restarted = DataStoreAppSettings(ApplicationProvider.getApplicationContext<Context>(), scope)
        assertEquals(chosen, withTimeout(TIMEOUT_MS) { restarted.guideLook.first { it == chosen } })
        assertTrue(withTimeout(TIMEOUT_MS) { restarted.showLatency.first { it } })
        // Both switches stay off after a restart (#271).
        assertFalse(withTimeout(TIMEOUT_MS) { restarted.showGrid.first { !it } })
        assertFalse(withTimeout(TIMEOUT_MS) { restarted.showLevel.first { !it } })
        assertEquals(seen, withTimeout(TIMEOUT_MS) { restarted.tutorialSeen.first { it == seen } })
        // Off from the first read: a phone whose user turned it off never sends in between (FR-10.6).
        assertEquals(false, withTimeout(TIMEOUT_MS) { restarted.shareUsage.filterNotNull().first() })

        // Show the tutorial again: every screen's tips due again, also after a restart.
        restarted.resetTutorial()
        assertEquals(
            emptySet<TutorialScreen>(),
            withTimeout(TIMEOUT_MS) {
                restarted.tutorialSeen.first {
                    it ==
                        emptySet<TutorialScreen>()
                }
            },
        )
        val again = DataStoreAppSettings(ApplicationProvider.getApplicationContext<Context>(), scope)
        assertEquals(emptySet<TutorialScreen>(), withTimeout(TIMEOUT_MS) { again.tutorialSeen.filterNotNull().first() })
        scope.cancel()
    }

    @Test
    fun aStoreThatCannotBeRead_leavesTheUsageChoiceUnread_andTheOthersAtTheirDefaults() = runTest {
        val unreadable = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("The settings file cannot be read") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("The settings file cannot be written")
        }

        val settings = DataStoreAppSettings(unreadable, backgroundScope)
        testScheduler.runCurrent()

        // A user who turned sharing off must not have it on again because the file could not be read.
        assertNull(settings.shareUsage.value)
        assertEquals(emptySet<TutorialScreen>(), settings.tutorialSeen.value)
        assertFalse(settings.showLatency.value)
        assertTrue(settings.showGrid.value)
        assertTrue(settings.showLevel.value)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
