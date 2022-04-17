/*
 * UltrasonicAppWidgetProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.provider

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.view.KeyEvent
import android.widget.RemoteViews
import java.lang.Exception
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.BitmapUtils
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver
import org.moire.ultrasonic.util.Constants
import timber.log.Timber

/**
 * Widget Provider for the Ultrasonic Widgets
 */
@Suppress("MagicNumber")
open class UltrasonicAppWidgetProvider : AppWidgetProvider() {
    @JvmField
    protected var layoutId = 0
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        defaultAppWidget(context, appWidgetIds)
    }

    /**
     * Initialize given widgets to default state, where we launch Ultrasonic on default click
     * and hide actions if service not running.
     */
    private fun defaultAppWidget(context: Context, appWidgetIds: IntArray) {
        val res = context.resources
        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.title, null)
        views.setTextViewText(R.id.album, null)
        views.setTextViewText(R.id.artist, res.getText(R.string.widget_initial_text))
        linkButtons(context, views, false)
        pushUpdate(context, appWidgetIds, views)
    }

    private fun pushUpdate(context: Context, appWidgetIds: IntArray?, views: RemoteViews) {
        // Update specific list of appWidgetIds if given, otherwise default to all
        val manager = AppWidgetManager.getInstance(context)
        if (manager != null) {
            if (appWidgetIds != null) {
                manager.updateAppWidget(appWidgetIds, views)
            } else {
                manager.updateAppWidget(ComponentName(context, this.javaClass), views)
            }
        }
    }

    /**
     * Handle a change notification coming over from [MediaPlayerController]
     */
    fun notifyChange(context: Context, currentSong: Track?, playing: Boolean, setAlbum: Boolean) {
        if (hasInstances(context)) {
            performUpdate(context, currentSong, playing, setAlbum)
        }
    }

    /**
     * Check against [AppWidgetManager] if there are any instances of this widget.
     */
    private fun hasInstances(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (manager != null) {
            val appWidgetIds = manager.getAppWidgetIds(ComponentName(context, javaClass))
            return appWidgetIds.isNotEmpty()
        }
        return false
    }

    /**
     * Update all active widget instances by pushing changes
     */
    private fun performUpdate(
        context: Context,
        currentSong: Track?,
        playing: Boolean,
        setAlbum: Boolean
    ) {
        val res = context.resources
        val views = RemoteViews(context.packageName, layoutId)
        val title = currentSong?.title
        val artist = currentSong?.artist
        val album = currentSong?.album
        var errorState: CharSequence? = null

        // Show error message?
        val status = Environment.getExternalStorageState()
        if (status == Environment.MEDIA_SHARED || status == Environment.MEDIA_UNMOUNTED) {
            errorState = res.getText(R.string.widget_sdcard_busy)
        } else if (status == Environment.MEDIA_REMOVED) {
            errorState = res.getText(R.string.widget_sdcard_missing)
        } else if (currentSong == null) {
            errorState = res.getText(R.string.widget_initial_text)
        }
        if (errorState != null) {
            // Show error state to user
            views.setTextViewText(R.id.title, null)
            views.setTextViewText(R.id.artist, errorState)
            if (setAlbum) {
                views.setTextViewText(R.id.album, null)
            }
            views.setImageViewResource(R.id.appwidget_coverart, R.drawable.unknown_album)
        } else {
            // No error, so show normal titles
            views.setTextViewText(R.id.title, title)
            views.setTextViewText(R.id.artist, artist)
            if (setAlbum) {
                views.setTextViewText(R.id.album, album)
            }
        }

        // Set correct drawable for pause state
        if (playing) {
            views.setImageViewResource(R.id.control_play, R.drawable.media_pause_normal_dark)
        } else {
            views.setImageViewResource(R.id.control_play, R.drawable.media_start_normal_dark)
        }

        // Set the cover art
        try {
            val bitmap =
                if (currentSong == null) null else BitmapUtils.getAlbumArtBitmapFromDisk(
                    currentSong,
                    240
                )
            if (bitmap == null) {
                // Set default cover art
                views.setImageViewResource(R.id.appwidget_coverart, R.drawable.unknown_album)
            } else {
                views.setImageViewBitmap(R.id.appwidget_coverart, bitmap)
            }
        } catch (all: Exception) {
            Timber.e(all, "Failed to load cover art")
            views.setImageViewResource(R.id.appwidget_coverart, R.drawable.unknown_album)
        }

        // Link actions buttons to intents
        linkButtons(context, views, currentSong != null)
        pushUpdate(context, null, views)
    }

    companion object {
        /**
         * Link up various button actions using [PendingIntent].
         */
        private fun linkButtons(context: Context, views: RemoteViews, playerActive: Boolean) {
            var intent = Intent(
                context,
                NavigationActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (playerActive) intent.putExtra(Constants.INTENT_SHOW_PLAYER, true)
            intent.action = "android.intent.action.MAIN"
            intent.addCategory("android.intent.category.LAUNCHER")
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // needed starting Android 12 (S = 31)
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            var pendingIntent =
                PendingIntent.getActivity(context, 10, intent, flags)
            views.setOnClickPendingIntent(R.id.appwidget_coverart, pendingIntent)
            views.setOnClickPendingIntent(R.id.appwidget_top, pendingIntent)

            // Emulate media button clicks.
            intent = Intent(Constants.CMD_PROCESS_KEYCODE)
            intent.component = ComponentName(context, MediaButtonIntentReceiver::class.java)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // needed starting Android 12 (S = 31)
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            pendingIntent = PendingIntent.getBroadcast(context, 11, intent, flags)
            views.setOnClickPendingIntent(R.id.control_play, pendingIntent)
            intent = Intent(Constants.CMD_PROCESS_KEYCODE)
            intent.component = ComponentName(context, MediaButtonIntentReceiver::class.java)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
            )
            pendingIntent = PendingIntent.getBroadcast(context, 12, intent, flags)
            views.setOnClickPendingIntent(R.id.control_next, pendingIntent)
            intent = Intent(Constants.CMD_PROCESS_KEYCODE)
            intent.component = ComponentName(context, MediaButtonIntentReceiver::class.java)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            )
            pendingIntent = PendingIntent.getBroadcast(context, 13, intent, flags)
            views.setOnClickPendingIntent(R.id.control_previous, pendingIntent)
        }
    }
}
