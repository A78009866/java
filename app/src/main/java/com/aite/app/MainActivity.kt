package com.aite.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            fileUploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) webView.reload()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()
        
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        webView.loadUrl("https://aite-lite.vercel.app")
    }

    private fun setupWebView() {
        val settings = webView.settings
        
        // --- إصلاح الشاشة الكبيرة (إجبار التصغير) ---
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true) // تفعيل الزوم برمجياً ليتمكن النظام من التصغير
        settings.builtInZoomControls = false
        
        // السطر الأهم: ضبط كثافة العرض لتناسب الموبايل
        settings.defaultTextEncodingName = "utf-8"
        webView.setInitialScale(1) // البدء بأصغر مقياس ممكن

        // محاكاة متصفح Chrome الرسمي بدقة
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"

        // --- إعدادات الميكروفون ---
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // إجبار الموقع عبر JavaScript على الانكماش ليناسب عرض الشاشة
                view?.evaluateJavascript("""
                    var meta = document.createElement('meta');
                    meta.name = 'viewport';
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                    document.getElementsByTagName('head')[0].appendChild(meta);
                    document.body.style.zoom = (window.innerWidth / document.documentElement.clientWidth);
                """.trimIndent(), null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                fileChooserLauncher.launch(intent)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // منح الإذن فوراً لجميع الموارد المطلوبة (ميكروفون وصوت)
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
