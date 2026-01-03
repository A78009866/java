package com.aite.app;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false); // منع الزوم
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // منع تحديد النص والنسخ
                view.loadUrl("javascript:(function() { " +
                        "document.body.style.webkitUserSelect='none'; " +
                        "document.body.style.userSelect='none'; " +
                        "})()");
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // طلب الإذن فور محاولة الموقع استخدام الميكروفون
                ActivityCompat.requestPermissions(MainActivity.this, 
                        new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                request.grant(request.getResources());
            }
        });

        webView.loadUrl("https://aite-lite.vercel.app");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) { webView.goBack(); } 
        else { super.onBackPressed(); }
    }
}
