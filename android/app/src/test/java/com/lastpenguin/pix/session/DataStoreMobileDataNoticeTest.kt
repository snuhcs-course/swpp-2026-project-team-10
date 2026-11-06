// AI-generated with Claude Code, 2026-10-26, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The mobile-data notice's flag (FR-6.13), kept across runs: not shown at first, shown once marked, and still shown
 * to a new instance. One test, because the DataStore behind the context extension lives for the process.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreMobileDataNoticeTest {
    @Test
    fun notShownAtFirst_thenMarkedAndRemembered() = runBlocking {
        val notice = DataStoreMobileDataNotice(ApplicationProvider.getApplicationContext())

        assertFalse(notice.wasShown())

        notice.markShown()
        assertTrue(notice.wasShown())
        assertTrue(DataStoreMobileDataNotice(ApplicationProvider.getApplicationContext()).wasShown())
    }
}
