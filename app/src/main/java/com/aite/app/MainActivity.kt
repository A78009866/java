package com.aite.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.provider.Settings
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // متغيرات صفحة الخطأ الجديدة
    private lateinit var layoutError: View
    private lateinit var btnRetry: View
    private lateinit var tvAppName: TextView
    private lateinit var logoContainer: CardView

    // علامة لتتبع حالة الخطأ حتى لا يتم إخفاء صفحة الخطأ المخصصة
    private var isErrorOccurred = false

    // علامة لتتبع أول تحميل ناجح
    private var isFirstLoadComplete = false

    // أنيميشن شريط التقدم
    private var progressAnimator: ObjectAnimator? = null

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

    // طلب إذن الإشعارات تلقائياً
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            // إذا رفض المستخدم، افتح إعدادات الإشعارات للتطبيق مباشرة
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        // التبديل من ثيم التشغيل إلى الثيم الأساسي قبل setContentView
        setTheme(R.style.Theme_Aite)
        super.onCreate(savedInstanceState)

        // عرض edge-to-edge لمظهر أصلي
        setupEdgeToEdge()

        setContentView(R.layout.activity_main)

        // تعريف العناصر
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        layoutError = findViewById(R.id.layoutError)
        btnRetry = findViewById(R.id.btnRetry)
        tvAppName = findViewById(R.id.tvAppName)
        logoContainer = findViewById(R.id.logoContainer)

        // إنشاء قناة الإشعارات مبكراً حتى تعمل الإشعارات في الخلفية
        createNotificationChannel()

        // إعداد WebView كتطبيق أصلي
        setupNativeWebView()
        setupWebViewSettings()
        setupWebChromeClient()
        setupWebViewClient()

        // إخفاء WebView حتى يتم التحميل الكامل (لإظهار splash بدلاً من صفحة بيضاء)
        webView.visibility = View.INVISIBLE

        // تحميل الصفحة فوراً
        handleNotificationIntent(intent)
        if (webView.url == null) {
            webView.loadUrl("https://$ALLOWED_HOST")
        }

        // تأجيل الأعمال غير الضرورية بعد العرض الأول
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            // تطبيق تأثيرات التصميم
            applyDesignEffects()

            // برمجة زر إعادة المحاولة
            btnRetry.setOnClickListener {
                isErrorOccurred = false
                isFirstLoadComplete = false
                layoutError.visibility = View.GONE
                webView.visibility = View.INVISIBLE
                webView.reload()
            }

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

            // طلب إذن الإشعارات تلقائياً عند كل تشغيل
            requestNotificationPermission()
        }
    }

    /**
     * طلب إذن الإشعارات تلقائياً - إذا رُفض يفتح إعدادات التطبيق
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * إنشاء قناة الإشعارات عند بدء التطبيق حتى تعمل الإشعارات في الخلفية
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // حذف القناة القديمة إن وجدت
            try {
                notificationManager.deleteNotificationChannel("fcm_default_channel")
            } catch (_: Exception) {}

            val existingChannel = notificationManager.getNotificationChannel("fcm_channel_v2")
            if (existingChannel == null) {
                val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.notification}")
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    "fcm_channel_v2",
                    "App Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات التطبيق"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    enableLights(true)
                    lightColor = Color.parseColor("#3982f7")
                    setSound(soundUri, audioAttributes)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * إعداد العرض بحيث لا يتجاوز المحتوى أشرطة النظام
     */
    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // شريط حالة وتنقل أسود ليتناسب مع خلفية التطبيق
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        // أيقونات شريط الحالة فاتحة (لأن الخلفية داكنة)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    /**
     * إعداد WebView ليبدو كتطبيق أصلي بدون أي علامات ويب
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupNativeWebView() {
        // إخفاء أشرطة التمرير بالكامل
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // إزالة تأثير الإفراط في التمرير (overscroll glow)
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        // خلفية سوداء مطابقة للتطبيق
        webView.setBackgroundColor(Color.BLACK)

        // منع القائمة المنبثقة عند الضغط المطول
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false

        // تعيين أولوية عالية للتقديم
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        // تفعيل تسريع العتاد
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
        
        // تحسينات الأمان
        settings.allowFileAccess = false 
        settings.allowContentAccess = true
        
        // تحسينات الأداء والتخزين المؤقت
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setGeolocationEnabled(false)
        
        // إعدادات لمظهر أصلي
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setNeedInitialFocus(false)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    // أكمل الشريط بسرعة ثم أخفه
                    progressAnimator?.cancel()
                    progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, 100)
                    progressAnimator?.duration = 100
                    progressAnimator?.interpolator = DecelerateInterpolator()
                    progressAnimator?.start()

                    progressBar.animate()
                        .alpha(0f)
                        .setStartDelay(150)
                        .setDuration(200)
                        .withEndAction {
                            progressBar.visibility = View.GONE
                            progressBar.alpha = 1f
                            progressBar.progress = 0
                        }
                        .start()
                } else {
                    if (progressBar.visibility == View.GONE) {
                        progressBar.progress = 0
                        progressBar.alpha = 0f
                        progressBar.visibility = View.VISIBLE
                        progressBar.animate().alpha(1f).setDuration(100).setStartDelay(0).start()
                    }
                    // أنيميشن سريع لقيمة التقدم
                    progressAnimator?.cancel()
                    val targetProgress = if (newProgress < 15) 15 else newProgress
                    progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, targetProgress)
                    progressAnimator?.duration = 150
                    progressAnimator?.interpolator = DecelerateInterpolator()
                    progressAnimator?.start()
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
                    isErrorOccurred = true
                    showErrorState()
                }
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                isErrorOccurred = true
                showErrorState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isErrorOccurred) {
                    return
                }

                // حقن CSS و JS شامل لإزالة جميع علامات الويب
                injectNativeStyles(view)

                // إظهار WebView بتأثير تلاشي سلس
                if (!isFirstLoadComplete) {
                    isFirstLoadComplete = true
                    layoutError.visibility = View.GONE
                    webView.alpha = 0f
                    webView.visibility = View.VISIBLE
                    webView.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                } else {
                    layoutError.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }

                sendFcmTokenToWeb(view)
            }
            
            // إضافة أمان إضافي للتأكد من أن الروابط الخارجية لا تخرج عن السيطرة
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // إذا كان الرابط آمناً (من موقعنا) اسمح به، غير ذلك يمكن فتحه في المتصفح الخارجي أو منعه
                return if (isSafeUrl(url)) {
                    isErrorOccurred = false
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

    /**
     * حقن CSS و JS لإزالة جميع علامات الويب وجعل التطبيق يبدو أصلي
     */
    private fun injectNativeStyles(view: WebView?) {
        val nativeJS = """(function() {
            var style = document.createElement('style');
            style.id = 'native-app-styles';
            style.innerHTML = '\
                * { \
                    -webkit-tap-highlight-color: transparent !important; \
                    -webkit-touch-callout: none !important; \
                    -webkit-user-select: none !important; \
                    user-select: none !important; \
                    outline: none !important; \
                } \
                input, textarea, [contenteditable="true"] { \
                    -webkit-user-select: text !important; \
                    user-select: text !important; \
                } \
                ::-webkit-scrollbar { \
                    display: none !important; \
                    width: 0 !important; \
                    height: 0 !important; \
                } \
                * { \
                    scrollbar-width: none !important; \
                    -ms-overflow-style: none !important; \
                } \
                body { \
                    -webkit-user-drag: none !important; \
                    -webkit-text-size-adjust: none !important; \
                    overscroll-behavior: none !important; \
                    overflow-x: hidden !important; \
                } \
                a, button, input, select, textarea { \
                    -webkit-tap-highlight-color: rgba(0,0,0,0) !important; \
                } \
                img { \
                    -webkit-user-drag: none !important; \
                    user-drag: none !important; \
                } \
            ';
            var old = document.getElementById('native-app-styles');
            if (old) old.remove();
            document.head.appendChild(style);
            document.addEventListener('contextmenu', function(e) {
                var tag = e.target.tagName.toLowerCase();
                if (tag !== 'input' && tag !== 'textarea' && !e.target.isContentEditable) {
                    e.preventDefault();
                }
            }, { passive: false });
            document.addEventListener('dragstart', function(e) {
                e.preventDefault();
            }, { passive: false });
        })();""".trimIndent()

        view?.evaluateJavascript(nativeJS, null)
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
