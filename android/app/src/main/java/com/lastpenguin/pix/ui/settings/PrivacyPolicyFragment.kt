// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.settings

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.BuildConfig
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPrivacyPolicyBinding
import com.lastpenguin.pix.generation.screenLanguage

/** The address of the privacy policy's page in [language], `ko` or `en`, on the server at [serverUrl] (FR-10.7). */
fun privacyPolicyUrl(serverUrl: String, language: String): String = serverUrl.trimEnd('/') + "/privacy/" + language

/** Opens the privacy policy over [from], unless a second tap finds another screen already on top. */
fun Fragment.openPrivacyPolicy(@IdRes from: Int) {
    val navController = findNavController()
    if (navController.currentDestination?.id == from) navController.navigate(R.id.action_global_privacyPolicy)
}

/**
 * The privacy policy (R&S 6.10; FR-10.7, FR-5.18), from Settings › Privacy and the sign-up screens: the server's page
 * in the screens' language, so Pix, the store's link, and the web show one text (Design 2.5.3). Owner: Accounts (#54).
 */
class PrivacyPolicyFragment : Fragment(R.layout.fragment_privacy_policy) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPrivacyPolicyBinding.bind(view)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        val page =
            PolicyPage(binding, privacyPolicyUrl(BuildConfig.SERVER_URL, screenLanguage(requireContext().resources)))
        binding.retryButton.setOnClickListener { page.load() }
        page.load()
    }

    override fun onDestroyView() {
        view?.findViewById<WebView>(R.id.policyWeb)?.destroy()
        super.onDestroyView()
    }
}

/**
 * The page in its WebView: a spinner while it loads; the page once it has; when it cannot load, or the server answers
 * with an error, the reason and *Try again*. Links on the page, such as the other language's, open in it. JavaScript
 * stays off, since the page needs none. Apart from the fragment, so it can be tested on the real layout.
 */
class PolicyPage(private val binding: FragmentPrivacyPolicyBinding, private val url: String) {
    private var failed = false

    init {
        val web = binding.policyWeb
        web.settings.javaScriptEnabled = false
        // The page's own ground, so no white shows before it draws.
        web.setBackgroundColor(ContextCompat.getColor(web.context, R.color.pix_ground))
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = loading()

            override fun onPageFinished(view: WebView, url: String) = shown()

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) failedToLoad()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) failedToLoad()
            }
        }
    }

    fun load() {
        loading()
        binding.policyWeb.loadUrl(url)
    }

    private fun loading() {
        failed = false
        binding.loading.isVisible = true
        binding.failure.isVisible = false
        binding.policyWeb.isInvisible = true
    }

    /** WebView reports a page finished after a failure too, which must not hide the failure. */
    private fun shown() {
        if (failed) return
        binding.loading.isVisible = false
        binding.policyWeb.isInvisible = false
    }

    private fun failedToLoad() {
        failed = true
        binding.loading.isVisible = false
        binding.failure.isVisible = true
        binding.policyWeb.isInvisible = true
    }
}
