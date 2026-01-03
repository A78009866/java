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

    // معالج أذونات النظام للميكروفون
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            webView.reload() // إعادة تحميل لتنشيط الميكروفون بعد الموافقة
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()
        
        // حفظ جلسة الدخول (الكوكيز)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        webView.loadUrl("https://aite-lite.vercel.app")
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        // --- الحل النهائي لمشكلة العرض الواسع في Oppo ---
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true 
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        
        // السطر التالي هو السر في جعل العرض ملائماً وصغيراً كالمتصفح
        webView.setInitialScale(1) // يجعل الموقع يبدأ بأصغر حجم ممكن ليلائم الشاشة

        // --- إعدادات الميكروفون والصوت ---
        settings.mediaPlaybackRequiresUserGesture = false
        settings.databaseEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            // تفعيل الميكروفون برمجياً داخل WebView
            override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    // التحقق من إذن النظام أولاً
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) 
                        != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        // منح الإذن فوراً إذا كان إذن النظام موجوداً
                        runOnUiThread {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
