/*
 * MediaButtonIntentReceiver.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import java.lang.Exception
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

/**
 * This class is used to receive commands from the widget
 */
class MediaButtonIntentReceiver : BroadcastReceiver(), KoinComponent {
    private val lifecycleSupport: MediaPlayerLifecycleSupport by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action

        // If media button are turned off and we received a media button, exit
        if (!Settings.mediaButtonsEnabled && Intent.ACTION_MEDIA_BUTTON == intentAction) return

        // Only process media buttons and CMD_PROCESS_KEYCODE, which is received from the widgets
        if (Intent.ACTION_MEDIA_BUTTON != intentAction &&
            Constants.CMD_PROCESS_KEYCODE != intentAction
        ) return
        val extras = intent.extras ?: return
        val event = extras[Intent.EXTRA_KEY_EVENT] as Parcelable?
        Timber.i("Got MEDIA_BUTTON key event: %s", event)
        try {
            val serviceIntent = Intent(Constants.CMD_PROCESS_KEYCODE)
            serviceIntent.putExtra(Intent.EXTRA_KEY_EVENT, event)
            lifecycleSupport.receiveIntent(serviceIntent)
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        } catch (ignored: Exception) {
            // Ignored.
        }
    }
}
