/*
 * MediaNotificationProvider.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.playback

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession

@UnstableApi
class MediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: MutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
    }

    override fun getMediaButtons(
        playerCommands: Player.Commands,
        customLayout: MutableList<CommandButton>,
        playWhenReady: Boolean
    ): MutableList<CommandButton> {
        val commands = super.getMediaButtons(playerCommands, customLayout, playWhenReady)

        commands.forEachIndexed { index, command ->
            command.extras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index)
        }

        return commands
    }
}
