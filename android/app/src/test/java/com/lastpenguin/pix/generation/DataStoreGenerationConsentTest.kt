// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.generation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The consent to send scene photos (FR-4.2), kept across runs: not given at first, given once agreed, and still
 * given to a new instance. One test, because the DataStore behind the context extension lives for the process.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreGenerationConsentTest {
    @Test
    fun notGivenAtFirst_thenGivenAndRemembered() = runBlocking {
        val consent = DataStoreGenerationConsent(ApplicationProvider.getApplicationContext())

        assertFalse(consent.isGiven())

        consent.give()
        assertTrue(consent.isGiven())
        assertTrue(DataStoreGenerationConsent(ApplicationProvider.getApplicationContext()).isGiven())
    }
}
