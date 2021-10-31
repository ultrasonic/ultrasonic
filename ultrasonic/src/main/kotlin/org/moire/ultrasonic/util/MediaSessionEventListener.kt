/*
 * MediaSessionEventListener.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent

/**
 * Callback interface for MediaSession related event subscribers
 */
interface MediaSessionEventListener {
//    fun onMediaSessionTokenCreated(token: MediaSessionCompat.Token) {}
    fun onPlayFromMediaIdRequested(mediaId: String?, extras: Bundle?) {}
    fun onPlayFromSearchRequested(query: String?, extras: Bundle?) {}
    fun onSkipToQueueItemRequested(id: Long) {}
//    fun onMediaButtonEvent(keyEvent: KeyEvent?) {}
}
