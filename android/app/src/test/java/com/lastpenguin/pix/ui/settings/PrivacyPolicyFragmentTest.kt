// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.settings

import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPrivacyPolicyBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The privacy policy on the real screen (R&S 6.10; FR-10.7, FR-5.18), with Robolectric's WebView, which records what
 * it loads and lets the test call the page's callbacks. Partitions: the address (English screens, Korean screens; a
 * server address with and without its last slash); the page (loading: the spinner; loaded: the page; the page itself
 * failed or answered an error: the reason and *Try again*, which a finish after the failure does not hide; a part of
 * the page failed: the page still shows); *Try again* (loads again); Back (leaves).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyPolicyFragmentTest {
    private var scenario: FragmentScenario<PrivacyPolicyFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** The policy over Settings, as the app opens it from Settings › Privacy. */
    private fun launch() {
        TestAppContainer().install()
        scenario = launchFragmentInContainer<PrivacyPolicyFragment>(themeResId = R.style.Theme_Pix)
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        scenario!!.onFragment { fragment ->
            nav.setGraph(R.navigation.nav_graph)
            nav.navigate(R.id.settingsFragment)
            nav.navigate(R.id.action_global_privacyPolicy)
            Navigation.setViewNavController(fragment.requireView(), nav)
        }
        idle()
    }

    private fun <T> onScreen(block: (FragmentPrivacyPolicyBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentPrivacyPolicyBinding.bind(it.requireView())) }
        return result!!
    }

    private fun web(): WebView = onScreen { it.policyWeb }

    private fun request(mainFrame: Boolean) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(shadowOf(web()).lastLoadedUrl)

        override fun isForMainFrame() = mainFrame

        override fun isRedirect() = false

        override fun hasGesture() = false

        override fun getMethod() = "GET"

        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun answer(status: Int) = WebResourceResponse("text/html", "utf-8", status, "Error", emptyMap(), null)

    private fun finished() = shadowOf(web()).webViewClient.onPageFinished(web(), shadowOf(web()).lastLoadedUrl)

    @Test
    fun thePolicy_isTheServersPage_inEnglish() {
        launch()

        assertEquals(privacyPolicyUrl(BuildConfig.SERVER_URL, "en"), shadowOf(web()).lastLoadedUrl)
        assertTrue(shadowOf(web()).lastLoadedUrl.endsWith("/privacy/en"))
    }

    @Test
    @Config(qualifiers = "ko")
    fun thePolicy_isTheServersPage_inKorean() {
        launch()

        assertTrue(shadowOf(web()).lastLoadedUrl.endsWith("/privacy/ko"))
    }

    @Test
    fun theAddress_isTheSameWithOrWithoutTheServersLastSlash() {
        assertEquals("https://pix.test/privacy/ko", privacyPolicyUrl("https://pix.test/", "ko"))
        assertEquals("https://pix.test/privacy/en", privacyPolicyUrl("https://pix.test", "en"))
    }

    @Test
    fun whileItLoads_aSpinner_thenThePage() {
        launch()
        onScreen { binding ->
            assertTrue(binding.loading.isShown)
            assertEquals(View.INVISIBLE, binding.policyWeb.visibility)
        }

        finished()

        onScreen { binding ->
            assertFalse(binding.loading.isShown)
            assertTrue(binding.policyWeb.isShown)
            assertFalse(binding.failure.isShown)
        }
    }

    @Test
    fun aFailedPage_saysWhy_evenAfterItsFinish_andTryAgainLoadsItAgain() {
        launch()
        shadowOf(web()).webViewClient.onReceivedHttpError(web(), request(mainFrame = true), answer(503))
        finished()

        onScreen { binding ->
            assertTrue(binding.failure.isShown)
            assertFalse(binding.loading.isShown)
            assertEquals(View.INVISIBLE, binding.policyWeb.visibility)
        }

        onScreen { it.retryButton.performClick() }

        onScreen { binding ->
            assertFalse(binding.failure.isShown)
            assertTrue(binding.loading.isShown)
        }
        finished()
        assertTrue(onScreen { it.policyWeb.isShown })
    }

    @Test
    fun aFailedPartOfThePage_leavesThePageShown() {
        launch()

        shadowOf(web()).webViewClient.onReceivedHttpError(web(), request(mainFrame = false), answer(404))
        finished()

        onScreen { binding ->
            assertFalse(binding.failure.isShown)
            assertTrue(binding.policyWeb.isShown)
        }
    }

    @Test
    fun back_leaves() {
        launch()

        onScreen { it.backButton.performClick() }

        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
    }
}
