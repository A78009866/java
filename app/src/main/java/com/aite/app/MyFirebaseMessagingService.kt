package com.aite.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "fcm_channel_v2"
        private const val CHANNEL_NAME = "App Notifications"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "إشعار جديد"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val targetUrl = remoteMessage.data["url"]

        // إشعارات ذكية: لا تعرض إشعار إذا كان المستخدم يشاهد نفس المحادثة
        if (shouldShowNotification(targetUrl)) {
            showNotification(title, body, targetUrl)
        }
    }

    /**
     * تحقق ذكي: هل يجب عرض الإشعار؟
     * لا يتم عرض الإشعار إذا كان التطبيق في المقدمة والمستخدم يشاهد نفس الصفحة/المحادثة
     */
    private fun shouldShowNotification(targetUrl: String?): Boolean {
        // إذا كان التطبيق ليس في المقدمة، اعرض الإشعار دائماً
        if (!MainActivity.isAppInForeground) {
            return true
        }

        // إذا لا يوجد رابط مستهدف، اعرض الإشعار
        if (targetUrl.isNullOrEmpty()) {
            return true
        }

        val currentUrl = MainActivity.currentVisibleUrl
        // إذا لا يوجد رابط حالي، اعرض الإشعار
        if (currentUrl.isNullOrEmpty()) {
            return true
        }

        // مقارنة ذكية: استخراج مسار المحادثة من الرابط
        try {
            val targetUri = android.net.Uri.parse(targetUrl)
            val currentUri = android.net.Uri.parse(currentUrl)

            val targetPath = targetUri.path ?: ""
            val currentPath = currentUri.path ?: ""

            // إذا كان المسار متطابق (نفس المحادثة/الصفحة)، لا تعرض الإشعار
            if (targetPath.isNotEmpty() && targetPath == currentPath) {
                return false
            }

            // مقارنة إضافية بالمعرفات: إذا كان كلا المسارين يحتويان على نفس معرف المحادثة
            val targetSegments = targetPath.split("/").filter { it.isNotEmpty() }
            val currentSegments = currentPath.split("/").filter { it.isNotEmpty() }

            // إذا كان كلاهما يشير لنفس القسم ونفس المعرف
            if (targetSegments.size >= 2 && currentSegments.size >= 2) {
                if (targetSegments.last() == currentSegments.last()) {
                    return false
                }
            }
        } catch (_: Exception) {
            // في حالة خطأ في التحليل، اعرض الإشعار
        }

        return true
    }

    private fun showNotification(title: String, body: String, url: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // إنشاء قناة الإشعارات بأعلى أهمية (مطلوب في أندرويد 8 وما فوق)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                // استخدام صوت الإشعار المخصص من مجلد raw
                val soundUri = Uri.parse("android.resource://${packageName}/${R.raw.notification}")
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات التطبيق - رسائل، إعجابات، تعليقات، طلبات صداقة"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    enableLights(true)
                    lightColor = android.graphics.Color.parseColor("#3982f7")
                    setSound(soundUri, audioAttributes)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (url != null) putExtra("TARGET_URL", url)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // استخدام صوت الإشعار المخصص
        val customSoundUri = Uri.parse("android.resource://${packageName}/${R.raw.notification}")

        // صورة التطبيق كأيقونة كبيرة في الإشعار
        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setColor(android.graphics.Color.parseColor("#3982f7"))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // أولوية عالية لإظهار الإشعار كـ Heads-up (مثل ماسنجر)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // صوت الإشعار المخصص
            .setSound(customSoundUri)
            // اهتزاز عند وصول الإشعار
            .setVibrate(longArrayOf(0, 300, 200, 300))
            // إضاءة LED
            .setLights(android.graphics.Color.parseColor("#3982f7"), 1000, 500)
            // تصنيف الإشعار كرسالة (يساعد النظام في إعطائه أولوية)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // إظهار الإشعار على شاشة القفل
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // عرض النص الطويل بالكامل
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // إظهار الوقت
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        notificationManager.notify(Random.nextInt(100000), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("_", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
    }
}
