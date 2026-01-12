package com.example.aite

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // يتم استدعاؤها عندما يحصل الجهاز على توكن جديد (عنوان الجهاز)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // حفظ التوكن في قاعدة البيانات ليعرف السيرفر أين يرسل
            FirebaseDatabase.getInstance().getReference("users")
                .child(userId).child("fcmToken").setValue(token)
        }
    }

    // يتم استدعاؤها عند وصول إشعار والتطبيق في الخلفية أو مغلق
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "إشعار جديد"
        val body = remoteMessage.notification?.body ?: ""
        val url = remoteMessage.data["url"] // الرابط الذي أرسله السيرفر

        showNotification(title, body, url)
    }

    private fun showNotification(title: String, body: String, url: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("target_url", url) // نضع الرابط هنا لفتحه لاحقاً
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "main_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notifications", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        manager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
