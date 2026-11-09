package com.elkheta.smsmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "sms_payment_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // محاولة إرسال أي رسائل معلقة في طابور الانتظار عند بدء الخدمة
        SupabaseSyncHelper.flushPendingQueueAsync(this)

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deviceId = SupabaseSyncHelper.getDeviceId(this)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("راصد مدفوعات SMS نشط ($deviceId)")
            .setContentText("يتم رصد رسائل التحويل وإرسالها تلقائياً إلى Supabase")
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة رصد مدفوعات الرسائل",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تحافظ على عمل مستقبل رسائل المدفوعات في الخلفية دون انقطاع"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
