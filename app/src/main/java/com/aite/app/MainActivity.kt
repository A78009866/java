package com.aite.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
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
            webPermissionRequest?.grant(webPermissionRequest?.resources)
        } else {
            webPermissionRequest?.deny()
            Toast.makeText(this, "يجب تفعيل إذن الميكروفون لتسجيل الصوت", Toast.LENGTH_SHORT).show()
        }
        webPermissionRequest = null
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebViewSettings()
        setupWebChromeClient()
        setupWebViewClient()

        // --- إضافة 1: منع القائمة المنبثقة عند الضغط المطول (منع النسخ) ---
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false

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
        // التحقق من وجود رابط قادم من الإشعار
    val urlFromNotif = intent.getStringExtra("target_url")
    if (!urlFromNotif.isNullOrEmpty()) {
        // إذا كنت تستخدم WebView، افتح الرابط فيه:
         myWebView.loadUrl(urlFromNotif)
        
        // أو افتحه في متصفح خارجي:
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlFromNotif))
        startActivity(browserIntent)
    }
}

        // رابط موقعك
        webView.loadUrl("https://aite-lite.vercel.app") 
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // --- إضافة 2: إصلاح مشكلة حجم الشاشة (Oppo A17 وغيرها) ---
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        
        // **هام جداً**: هذا السطر يجبر التطبيق على تجاهل إعدادات تكبير الخط في الهاتف
        // ويجعل الموقع يظهر بحجمه الطبيعي المتناسق 100%
        settings.textZoom = 100 

        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    if (progressBar.visibility == View.GONE) progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                var isAudioRequest = false
                for (resource in resources) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE == resource) {
                        isAudioRequest = true
                        break
                    }
                }

                if (isAudioRequest) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.resources)
                    } else {
                        webPermissionRequest = request
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    request.grant(request.resources)
                }
            }

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
                return false 
            }

            // --- إضافة 3: حقن كود CSS لمنع تحديد النصوص فور انتهاء التحميل ---
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // هذا الكود يضيف Style يمنع تحديد النصوص (Select) ويمنع القوائم اللمسية (Callout)
                val js = "javascript:(function() { " +
                        "var style = document.createElement('style');" +
                        "style.innerHTML = 'body { -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }';" +
                        "document.head.appendChild(style);" +
                        "})()"
                
                view?.evaluateJavascript(js, null)
            }
        }
    }
}
