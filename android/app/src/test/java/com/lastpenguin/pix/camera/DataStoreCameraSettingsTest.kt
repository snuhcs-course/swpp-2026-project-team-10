// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The aspect ratio kept across runs (Design 2.8): nothing before the first choice, then each choice read back, and
 * the last one winning. One test, because the DataStore behind the context extension lives for the process.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreCameraSettingsTest {
    @Test
    fun nothingAtFirst_thenTheLastChoiceIsReadBack() = runBlocking {
        val settings = DataStoreCameraSettings(ApplicationProvider.getApplicationContext())

        assertNull(settings.aspectRatio())

        settings.saveAspectRatio(AspectRatio.R16_9)
        assertEquals(AspectRatio.R16_9, settings.aspectRatio())

        settings.saveAspectRatio(AspectRatio.R1_1)
        assertEquals(
            AspectRatio.R1_1,
            DataStoreCameraSettings(ApplicationProvider.getApplicationContext()).aspectRatio(),
        )
    }
}
