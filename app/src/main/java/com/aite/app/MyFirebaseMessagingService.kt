package com.aite.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // استلام البيانات من السيرفر
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "إشعار جديد"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val targetUrl = remoteMessage.data["url"] // الرابط الذي نريد التوجيه إليه

        showNotification(title, body, targetUrl)
    }

    private fun showNotification(title: String, body: String, url: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // نمرر الرابط للـ MainActivity
            if (url != null) putExtra("TARGET_URL", url)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // تأكد من وجود أيقونة مناسبة
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // إعداد قناة الإشعارات (مطلوب في أندرويد 8 وما فوق)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        // يتم استدعاء هذه الدالة عند إنشاء توكن جديد
        // سنقوم بحفظه في SharedPreferences لإرساله للسيرفر لاحقاً عبر الـ WebView
        getSharedPreferences("_", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }
}
