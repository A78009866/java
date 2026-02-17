package com.aite.app

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // متغيرات صفحة الخطأ الجديدة
    private lateinit var layoutError: View
    private lateinit var btnRetry: View
    private lateinit var tvAppName: TextView
    private lateinit var logoContainer: CardView

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var webPermissionRequest: PermissionRequest? = null

    // النطاق المسموح به فقط (للحماية من الروابط الخبيثة)
    // هام: تأكد أن هذا الرابط يطابق رابط موقعك بالضبط
    private val ALLOWED_HOST = "aite-lite.vercel.app"

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            fileUploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            webPermissionRequest?.grant(webPermissionRequest?.resources)
        } else {
            webPermissionRequest?.deny()
        }
        webPermissionRequest = null
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // تعريف العناصر
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        layoutError = findViewById(R.id.layoutError)
        btnRetry = findViewById(R.id.btnRetry)
        tvAppName = findViewById(R.id.tvAppName)
        logoContainer = findViewById(R.id.logoContainer)

        // تطبيق تأثيرات التصميم
        applyDesignEffects()

        // برمجة زر إعادة المحاولة
        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }

        setupWebViewSettings()
        setupWebChromeClient()
        setupWebViewClient()

        webView.setOnLongClickListener { true }
        webView.isLongClickable = false

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutError.visibility == View.VISIBLE) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        // معالجة الرابط القادم من الإشعار بشكل آمن
        handleNotificationIntent(intent)

        if (webView.url == null) {
            webView.loadUrl("https://$ALLOWED_HOST")
        }
    }

    private fun applyDesignEffects() {
        val paint = tvAppName.paint
        val width = paint.measureText(tvAppName.text.toString())
        val textShader: Shader = LinearGradient(
            0f, 0f, width, tvAppName.textSize,
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#3982f7")
            ), null, Shader.TileMode.CLAMP
        )
        tvAppName.paint.shader = textShader

        val floater = ObjectAnimator.ofFloat(logoContainer, "translationY", 0f, -30f)
        floater.duration = 2000
        floater.repeatCount = ObjectAnimator.INFINITE
        floater.repeatMode = ObjectAnimator.REVERSE
        floater.interpolator = AccelerateDecelerateInterpolator()
        floater.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * دالة الأمان المصححة: تتحقق من الرابط قبل تحميله
     * هذا يحل مشكلة Cross-App Scripting
     */
    private fun handleNotificationIntent(intent: Intent?) {
        val targetUrl = intent?.getStringExtra("TARGET_URL")
        
        if (!targetUrl.isNullOrEmpty()) {
            // التحقق مما إذا كان الرابط آمناً وينتمي لنطاق تطبيقك
            if (isSafeUrl(targetUrl)) {
                webView.loadUrl(targetUrl)
            } else {
                // إذا كان الرابط مشبوهاً، قم بتحميل الصفحة الرئيسية بدلاً منه
                // أو تجاهله تماماً للحماية
                if (webView.url == null) {
                    webView.loadUrl("https://$ALLOWED_HOST")
                }
            }
        }
    }

    /**
     * دالة للتحقق من أن الرابط ينتمي لنطاقنا وليس كود خبيث
     */
    private fun isSafeUrl(url: String): Boolean {
        try {
            // منع تنفيذ أكواد جافا سكريبت أو ملفات محلية عبر الـ Intent
            if (url.startsWith("javascript:", true) || url.startsWith("file:", true)) {
                return false
            }

            val uri = Uri.parse(url)
            val scheme = uri.scheme
            val host = uri.host

            // 1. يجب أن يكون البروتوكول http أو https
            // 2. يجب أن يكون النطاق (Host) هو موقعك فقط
            return (scheme.equals("https", true) || scheme.equals("http", true)) &&
                    (host != null && (host.equals(ALLOWED_HOST, true) || host.endsWith(".$ALLOWED_HOST", true)))
        } catch (e: Exception) {
            return false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.textZoom = 100
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        settings.mediaPlaybackRequiresUserGesture = false
        
        // تحسينات الأمان:
        // السماح بالوصول للملفات فقط إذا كان ضرورياً جداً، ولكن يفضل إيقافه
        // لمنع سرقة ملفات المستخدم إذا تم استغلال ثغرة
        settings.allowFileAccess = false 
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
                        requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
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
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    showErrorState()
                }
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                showErrorState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                layoutError.visibility = View.GONE
                webView.visibility = View.VISIBLE

                val cssJs = "javascript:(function() { " +
                        "var style = document.createElement('style');" +
                        "style.innerHTML = 'body { -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }';" +
                        "document.head.appendChild(style);" +
                        "})()"
                view?.evaluateJavascript(cssJs, null)
                sendFcmTokenToWeb(view)
            }
            
            // إضافة أمان إضافي للتأكد من أن الروابط الخارجية لا تخرج عن السيطرة
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // إذا كان الرابط آمناً (من موقعنا) اسمح به، غير ذلك يمكن فتحه في المتصفح الخارجي أو منعه
                return if (isSafeUrl(url)) {
                    false // اسمح للـ WebView بتحميله
                } else {
                    // يمكنك هنا فتح الروابط الخارجية في المتصفح الافتراضي بدلاً من التطبيق
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {}
                    true // منع الـ WebView من تحميله
                }
            }
        }
    }

    private fun showErrorState() {
        webView.visibility = View.GONE
        layoutError.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
    }

    private fun sendFcmTokenToWeb(view: WebView?) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                view?.evaluateJavascript("if(window.receiveAndroidToken) { window.receiveAndroidToken('$token'); }", null)
            }
        }
    }
}
