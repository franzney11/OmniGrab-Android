package com.franzney.omnigrab.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DownloadForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "omnigrab_downloads"
        private const val NOTIF_ID = 1001
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_PROGRESS = "extra_progress"

        fun startOrUpdate(context: Context, title: String, progress: Int) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Procesando multimedia..."
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val notif = buildNotification(title, progress)
        startForeground(NOTIF_ID, notif)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas de OmniGrab",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("OmniGrab")
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }
}
