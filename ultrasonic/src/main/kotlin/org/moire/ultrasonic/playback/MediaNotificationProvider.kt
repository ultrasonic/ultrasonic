/*
 * MediaNotificationProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaNotification.ActionFactory
import org.moire.ultrasonic.R

/*
* This is a copy of DefaultMediaNotificationProvider.java with some small changes
* I have opened a bug https://github.com/androidx/media/issues/65 to make it easier to customize
* the icons and actions without creating our own copy of this class..
 */
@UnstableApi
/* package */
internal class MediaNotificationProvider(context: Context) :
    MediaNotification.Provider {
    private val context: Context = context.applicationContext
    private val notificationManager: NotificationManager = Assertions.checkStateNotNull(
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    )

    @Suppress("LongMethod")
    override fun createNotification(
        mediaController: MediaController,
        actionFactory: ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        ensureNotificationChannel()
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID
        )
        // Skip to previous action.
        builder.addAction(
            actionFactory.createMediaAction(
                IconCompat.createWithResource(
                    context,
                    R.drawable.media3_notification_seek_to_previous
                ),
                context.getString(R.string.media3_controls_seek_to_previous_description),
                ActionFactory.COMMAND_SKIP_TO_PREVIOUS
            )
        )
        if (mediaController.playbackState == Player.STATE_ENDED ||
            !mediaController.playWhenReady
        ) {
            // Play action.
            builder.addAction(
                actionFactory.createMediaAction(
                    IconCompat.createWithResource(context, R.drawable.media3_notification_play),
                    context.getString(R.string.media3_controls_play_description),
                    ActionFactory.COMMAND_PLAY
                )
            )
        } else {
            // Pause action.
            builder.addAction(
                actionFactory.createMediaAction(
                    IconCompat.createWithResource(context, R.drawable.media3_notification_pause),
                    context.getString(R.string.media3_controls_pause_description),
                    ActionFactory.COMMAND_PAUSE
                )
            )
        }
        // Skip to next action.
        builder.addAction(
            actionFactory.createMediaAction(
                IconCompat.createWithResource(context, R.drawable.media3_notification_seek_to_next),
                context.getString(R.string.media3_controls_seek_to_next_description),
                ActionFactory.COMMAND_SKIP_TO_NEXT
            )
        )

        // Set metadata info in the notification.
        val metadata = mediaController.mediaMetadata
        builder.setContentTitle(metadata.title).setContentText(metadata.artist)
        if (metadata.artworkData != null) {
            val artworkBitmap =
                BitmapFactory.decodeByteArray(metadata.artworkData, 0, metadata.artworkData!!.size)
            builder.setLargeIcon(artworkBitmap)
        }
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setCancelButtonIntent(
                actionFactory.createMediaActionPendingIntent(
                    ActionFactory.COMMAND_STOP
                )
            )
            .setShowActionsInCompactView(0, 1, 2)
        val notification: Notification = builder
            .setContentIntent(mediaController.sessionActivity)
            .setDeleteIntent(
                actionFactory.createMediaActionPendingIntent(
                    ActionFactory.COMMAND_STOP
                )
            )
            .setOnlyAlertOnce(true)
            .setSmallIcon(getSmallIconResId())
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .build()
        return MediaNotification(
            NOTIFICATION_ID,
            notification
        )
    }

    override fun handleCustomAction(
        mediaController: MediaController,
        action: String,
        extras: Bundle
    ) {
        // We don't handle custom commands.
    }

    private fun ensureNotificationChannel() {
        if (Util.SDK_INT < Build.VERSION_CODES.O ||
            notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null
        ) {
            return
        }
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "default_channel_id"
        private const val NOTIFICATION_CHANNEL_NAME = "Now playing"
        private fun getSmallIconResId(): Int {
            return R.drawable.ic_stat_ultrasonic
        }
    }
}
