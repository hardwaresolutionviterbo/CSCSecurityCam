package it.cscsecuritycam.surveillance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.cscsecuritycam.R

object MotionNotifier {
    private const val CHANNEL_ID = "csc_motion_events"
    private const val NOTIFICATION_ID = 3107

    fun show(context: Context) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("CSCSecurityCam")
            .setContentText("Movimento rilevato dalla videocamera")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Rilevamento movimento",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Avvisi quando CSCSecurityCam rileva un cambiamento nella scena" }
            manager.createNotificationChannel(channel)
        }
    }
}
