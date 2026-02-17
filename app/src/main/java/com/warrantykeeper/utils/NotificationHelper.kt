package com.warrantykeeper.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.warrantykeeper.R
import com.warrantykeeper.presentation.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    
    companion object {
        const val CHANNEL_ID = "warranty_expiry_channel"
        const val CHANNEL_NAME = "Warranty Expiry Notifications"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about warranty expiry dates"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showWarrantyExpiryNotification(
        documentId: Long,
        productName: String,
        daysUntilExpiry: Int,
        expiryDate: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("document_id", documentId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            documentId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            daysUntilExpiry <= 0 -> "Гарантия истекла"
            daysUntilExpiry == 1 -> "Гарантия истекает завтра"
            else -> "Гарантия скоро истечёт"
        }

        val message = when {
            daysUntilExpiry <= 0 -> "Гарантия на \"$productName\" истекла $expiryDate"
            daysUntilExpiry == 1 -> "Гарантия на \"$productName\" истекает завтра ($expiryDate)"
            else -> "Гарантия на \"$productName\" истекает через $daysUntilExpiry дней ($expiryDate)"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(documentId.toInt(), notification)
    }

    fun cancelNotification(documentId: Long) {
        NotificationManagerCompat.from(context).cancel(documentId.toInt())
    }

    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
