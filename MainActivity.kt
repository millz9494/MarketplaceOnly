package com.marketplaceonly.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val marketplaceHome = "https://www.facebook.com/marketplace/"

    // A Marketplace message thread is allowed only if the user opened it from Marketplace.
    private var allowedMessageThreadPrefix: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        val backButton: Button = findViewById(R.id.backButton)
        val homeButton: Button = findViewById(R.id.homeButton)
        val refreshButton: Button = findViewById(R.id.refreshButton)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val fromMarketplace = view.url?.let { isMarketplaceUrl(Uri.parse(it)) } == true

                if (isMarketplaceUrl(url) || isLoginOrSecurityUrl(url) || isRequiredFacebookRedirect(url)) {
                    return false
                }

                if (isMessageThread(url) && fromMarketplace) {
                    allowedMessageThreadPrefix = messageThreadPrefix(url)
                    return false
                }

                if (isAllowedMessageThread(url)) return false

                blockNavigation(url)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val uri = runCatching { Uri.parse(url ?: return) }.getOrNull() ?: return

                // Catch server-side redirects that bypass shouldOverrideUrlLoading.
                if (!isAllowedDestination(uri)) {
                    view?.stopLoading()
                    blockNavigation(uri)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val uri = runCatching { Uri.parse(url ?: return) }.getOrNull() ?: return

                // Once logged in, if Facebook lands on Feed/root, immediately send back to Marketplace.
                if (isFacebookHost(uri.host) && isFacebookRootOrFeed(uri)) {
                    view?.loadUrl(marketplaceHome)
                }
            }
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else webView.loadUrl(marketplaceHome)
        }
        homeButton.setOnClickListener {
            allowedMessageThreadPrefix = null
            webView.loadUrl(marketplaceHome)
        }
        refreshButton.setOnClickListener { webView.reload() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl(marketplaceHome)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun isAllowedDestination(uri: Uri): Boolean =
        isMarketplaceUrl(uri) ||
        isLoginOrSecurityUrl(uri) ||
        isRequiredFacebookRedirect(uri) ||
        isAllowedMessageThread(uri)

    private fun isMarketplaceUrl(uri: Uri): Boolean {
        if (!isFacebookHost(uri.host)) return false
        val p = uri.path.orEmpty().lowercase()
        return p == "/marketplace" || p.startsWith("/marketplace/")
    }

    private fun isLoginOrSecurityUrl(uri: Uri): Boolean {
        if (!isFacebookHost(uri.host)) return false
        val p = uri.path.orEmpty().lowercase()
        return p.startsWith("/login") ||
            p.startsWith("/checkpoint") ||
            p.startsWith("/recover") ||
            p.startsWith("/two_factor") ||
            p.startsWith("/confirmemail") ||
            p.startsWith("/device") ||
            p.startsWith("/reg") ||
            p.startsWith("/privacy/consent") ||
            p.startsWith("/cookie")
    }

    private fun isRequiredFacebookRedirect(uri: Uri): Boolean {
        if (!isFacebookHost(uri.host)) return false
        val p = uri.path.orEmpty().lowercase()
        return p.startsWith("/ajax/") ||
            p.startsWith("/dialog/") ||
            p.startsWith("/oauth/")
    }

    private fun isMessageThread(uri: Uri): Boolean {
        if (!isFacebookHost(uri.host)) return false
        val p = uri.path.orEmpty().lowercase()
        return p.startsWith("/messages/t/")
    }

    private fun messageThreadPrefix(uri: Uri): String {
        val pieces = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        return if (pieces.size >= 3) "/messages/t/${pieces[2]}" else uri.path.orEmpty()
    }

    private fun isAllowedMessageThread(uri: Uri): Boolean {
        if (!isFacebookHost(uri.host)) return false
        val prefix = allowedMessageThreadPrefix ?: return false
        return uri.path.orEmpty().startsWith(prefix)
    }

    private fun isFacebookRootOrFeed(uri: Uri): Boolean {
        val p = uri.path.orEmpty().trimEnd('/').lowercase()
        return p.isEmpty() || p == "/home.php" || p == "/home"
    }

    private fun isFacebookHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "facebook.com" || h.endsWith(".facebook.com") || h == "fb.com" || h.endsWith(".fb.com")
    }

    private fun blockNavigation(uri: Uri) {
        Toast.makeText(this, "Blocked — this app only allows Facebook Marketplace", Toast.LENGTH_SHORT).show()
        if (webView.url?.let { runCatching { isMarketplaceUrl(Uri.parse(it)) }.getOrDefault(false) } != true) {
            webView.post { webView.loadUrl(marketplaceHome) }
        }
    }
}
