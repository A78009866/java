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

    // طلب الإذن فقط عند الحاجة (تمت إزالة الطلب التلقائي من هنا)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            webView.reload() // إعادة التحميل لتفعيل الميكروفون بعد الموافقة
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()
        
        // --- إصلاح حفظ تسجيل الدخول (الجلسة) ---
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        webView.loadUrl("https://aite-lite.vercel.app")
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        // --- إعدادات العرض (الحجم الطبيعي) ---
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true 
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        
        // --- تسريع الأداء ---
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH)

        // --- إعدادات الميكروفون ---
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // حفظ الكوكيز فور انتهاء التحميل لضمان عدم الخروج
                CookieManager.getInstance().flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            // هذا الجزء يمنح الإذن للموقع للوصول للميكروفون برمجياً
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val resources = request.resources
                    if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        // إذا كان إذن النظام مفقوداً، نطلبه هنا
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) 
                            != PackageManager.PERMISSION_GRANTED) {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
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
