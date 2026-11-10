package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object CscNotificationHelper {
    const val CHANNEL_MOTION = "csc_motion_alerts"
    const val CHANNEL_SYSTEM = "csc_system_status"
    private const val NOTIF_ID_MOTION_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val motionChannel = NotificationChannel(
                CHANNEL_MOTION,
                "Avvisi Movimento CSC",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche immediate di rilevamento movimento"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Stato Sistema e Registrazioni",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche di stato connessione e inizio registrazioni"
            }

            manager.createNotificationChannel(motionChannel)
            manager.createNotificationChannel(systemChannel)
        }
    }

    fun sendMotionAlert(context: Context, cameraName: String, location: String) {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_MOTION)
            .setContentTitle("⚠️ Movimento Rilevato!")
            .setContentText("$cameraName in $location ha rilevato un movimento")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((NOTIF_ID_MOTION_BASE + (System.currentTimeMillis() % 100)).toInt(), notification)
    }

    fun sendRecordingNotification(context: Context, cameraName: String, reason: String) {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setContentTitle("🔴 Registrazione Avviata")
            .setContentText("$cameraName: $reason")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3001, notification)
    }

    fun sendBatteryAlert(context: Context, batteryPercent: Int) {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setContentTitle("🔋 Batteria Bassa ($batteryPercent%)")
            .setContentText("Collega lo smartphone al caricabatterie per garantire la videosorveglianza continua.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3002, notification)
    }

    fun sendNetworkAlert(context: Context, message: String, isRestored: Boolean) {
        createChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setContentTitle(if (isRestored) "📶 Rete Wi-Fi Ripristinata" else "⚠️ Connessione di Rete Interrotta")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(3003, notification)
    }
}
