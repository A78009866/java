package com.aite.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()
        webView.loadUrl("https://aite-lite.vercel.app")
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        // --- إعدادات الحجم والملاءمة ---
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true 
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.displayZoomControls = false

        // --- إصلاح الميكروفون والصوت ---
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // منع تحديد النص والنسخ عبر CSS
                view?.evaluateJavascript(
                    "document.documentElement.style.webkitUserSelect='none'; " +
                    "document.documentElement.style.webkitTouchCallout='none';", null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            // منح الإذن الفني للميكروفون داخل الـ WebView
            override fun onPermissionRequest(request: PermissionRequest) {
                val requestedResources = request.resources
                for (resource in requestedResources) {
                    if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        return
                    }
                }
                super.onPermissionRequest(request)
            }
        }

        // طلب إذن الميكروفون من النظام فوراً عند الحاجة
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
