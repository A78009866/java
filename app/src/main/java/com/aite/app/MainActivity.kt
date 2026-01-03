package com.aite.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.ProgressBar
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    
    // متغيرات لرفع الملفات
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    
    // متغير لحفظ طلب الإذن القادم من الويب (للميكروفون)
    private var webPermissionRequest: PermissionRequest? = null

    // 1. معالج رفع الملفات
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            fileUploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // 2. معالج طلب إذن الميكروفون من نظام الأندرويد
    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // إذا وافق المستخدم، نمنح الإذن لصفحة الويب
            webPermissionRequest?.grant(webPermissionRequest?.resources)
        } else {
            // إذا رفض، نرفض الطلب في الويب
            webPermissionRequest?.deny()
            Toast.makeText(this, "يجب تفعيل إذن الميكروفون لتسجيل الصوت", Toast.LENGTH_SHORT).show()
        }
        webPermissionRequest = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView) // تأكد أن المعرف في activity_main.xml هو webView
        progressBar = findViewById(R.id.progressBar) // تأكد أن المعرف هو progressBar

        setupWebViewSettings()
        setupWebChromeClient()
        setupWebViewClient()

        // التعامل مع زر الرجوع
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // رابط موقعك
        webView.loadUrl("https://aite-lite.vercel.app") // استبدل هذا برابط موقعك الحقيقي
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        
        // إعدادات الجافاسكريبت والتخزين
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // --- إصلاح مشكلة حجم الشاشة (Oppo A17 وغيرها) ---
        // هذه الإعدادات تجعل الـ WebView يعتمد على meta tag viewport الموجود في HTML
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false) // نمنع التكبير اليدوي لضمان ثبات التصميم
        
        // إعدادات الوسائط (مهم للصوت)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        
        // تحسين الأداء
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // السماح بالكوكي
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            
            // شريط التقدم
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    if (progressBar.visibility == View.GONE) progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            // --- إصلاح مشكلة الميكروفون ---
            override fun onPermissionRequest(request: PermissionRequest) {
                // التحقق مما إذا كان الطلب يتضمن الميكروفون
                val resources = request.resources
                var isAudioRequest = false
                for (resource in resources) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE == resource) {
                        isAudioRequest = true
                        break
                    }
                }

                if (isAudioRequest) {
                    // التحقق من إذن الأندرويد نفسه
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        // الإذن ممنوح مسبقاً، اسمح للويب فوراً
                        request.grant(request.resources)
                    } else {
                        // الإذن غير ممنوح، اطلبه من المستخدم واحفظ طلب الويب لوقت لاحق
                        webPermissionRequest = request
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    // لأي أذونات أخرى لا تتطلب تدخل النظام المباشر
                    request.grant(request.resources)
                }
            }

            // رفع الملفات
            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    return false
                }
                return true
            }
        }
    }

    private fun setupWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // فتح جميع الروابط داخل التطبيق
            }
        }
    }
}
