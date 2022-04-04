/*
 * MediaPlayerService.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.SimpleServiceBinder
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Android Foreground service which is used to download tracks even when the app is not visible
 *
 * "A foreground service is a service that the user is
 * actively aware of and isn’t a candidate for the system to kill when low on memory."
 *
 * TODO: Migrate this to use the Media3 DownloadHelper
 */
class DownloadService : Service() {
    private val binder: IBinder = SimpleServiceBinder(this)

    private val downloader by inject<Downloader>()

    private var mediaSession: MediaSessionCompat? = null

    private var isInForeground = false

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        // Create Notification Channel
        createNotificationChannel()
        updateNotification()

        instance = this
        Timber.i("DownloadService created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            downloader.stop()

            mediaSession?.release()
            mediaSession = null
        } catch (ignored: Throwable) {
        }
        Timber.i("DownloadService stopped")
    }

    fun notifyDownloaderStopped() {
        isInForeground = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // The suggested importance of a startForeground service notification is IMPORTANCE_LOW
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )

            channel.lightColor = android.R.color.holo_blue_dark
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)

            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // We should use a single notification builder, otherwise the notification may not be updated
    // Set some values that never change
    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ultrasonic)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(getPendingIntentForContent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun updateNotification() {

        val notification = buildForegroundNotification()

        if (isInForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            } else {
                val manager = NotificationManagerCompat.from(this)
                manager.notify(NOTIFICATION_ID, notification)
            }
            Timber.v("Updated notification")
        } else {
            startForeground(NOTIFICATION_ID, notification)
            isInForeground = true
            Timber.v("Created Foreground notification")
        }
    }

    /**
     * This method builds a notification, reusing the Notification Builder if possible
     */
    @Suppress("SpreadOperator")
    private fun buildForegroundNotification(): Notification {

        if (downloader.started) {
            // No song is playing, but Ultrasonic is downloading files
            notificationBuilder.setContentTitle(
                getString(R.string.notification_downloading_title)
            )
        }

        return notificationBuilder.build()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getPendingIntentForContent(): PendingIntent {
        val intent = Intent(this, NavigationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT
        intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    @Suppress("MagicNumber")
    companion object {

        private const val NOTIFICATION_CHANNEL_ID = "org.moire.ultrasonic"
        private const val NOTIFICATION_CHANNEL_NAME = "Ultrasonic background service"
        private const val NOTIFICATION_ID = 3033

        @Volatile
        private var instance: DownloadService? = null
        private val instanceLock = Any()

        @JvmStatic
        fun getInstance(): DownloadService? {
            val context = UApp.applicationContext()
            // Try for twenty times to retrieve a running service,
            // sleep 100 millis between each try,
            // and run the block that creates a service only synchronized.
            for (i in 0..19) {
                if (instance != null) return instance
                synchronized(instanceLock) {
                    if (instance != null) return instance
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(
                            Intent(context, DownloadService::class.java)
                        )
                    } else {
                        context.startService(Intent(context, DownloadService::class.java))
                    }
                }
                Util.sleepQuietly(100L)
            }
            return instance
        }

        @JvmStatic
        val runningInstance: DownloadService?
            get() {
                synchronized(instanceLock) { return instance }
            }

        @JvmStatic
        fun executeOnStartedMediaPlayerService(
            taskToExecute: (DownloadService) -> Unit
        ) {

            val t: Thread = object : Thread() {
                override fun run() {
                    val instance = getInstance()
                    if (instance == null) {
                        Timber.e("ExecuteOnStarted.. failed to get a DownloadService instance!")
                        return
                    } else {
                        taskToExecute(instance)
                    }
                }
            }
            t.start()
        }
    }
}
