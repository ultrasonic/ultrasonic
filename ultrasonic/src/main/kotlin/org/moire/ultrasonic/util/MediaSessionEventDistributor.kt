/*
 * MediaSessionEventDistributor.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent

/**
 * This class distributes MediaSession related events to its subscribers.
 * It is a primitive implementation of a pub-sub event bus
 */
class MediaSessionEventDistributor {
    var eventListenerList: MutableList<MediaSessionEventListener> =
        listOf<MediaSessionEventListener>().toMutableList()

    var cachedToken: MediaSessionCompat.Token? = null

    fun subscribe(listener: MediaSessionEventListener) {
        eventListenerList.add(listener)

        synchronized(this) {
            if (cachedToken != null)
                listener.onMediaSessionTokenCreated(cachedToken!!)
        }
    }

    fun unsubscribe(listener: MediaSessionEventListener) {
        eventListenerList.remove(listener)
    }

    fun releaseCachedMediaSessionToken() {
        synchronized(this) {
            cachedToken = null
        }
    }

    fun raiseMediaSessionTokenCreatedEvent(token: MediaSessionCompat.Token) {
        synchronized(this) {
            cachedToken = token
            eventListenerList.forEach { listener -> listener.onMediaSessionTokenCreated(token) }
        }
    }

    fun raisePlayFromMediaIdRequestedEvent(mediaId: String?, extras: Bundle?) {
        eventListenerList.forEach {
            listener ->
            listener.onPlayFromMediaIdRequested(mediaId, extras)
        }
    }

    fun raisePlayFromSearchRequestedEvent(query: String?, extras: Bundle?) {
        eventListenerList.forEach { listener -> listener.onPlayFromSearchRequested(query, extras) }
    }

    fun raiseSkipToQueueItemRequestedEvent(id: Long) {
        eventListenerList.forEach { listener -> listener.onSkipToQueueItemRequested(id) }
    }

    fun raiseMediaButtonEvent(keyEvent: KeyEvent?) {
        eventListenerList.forEach { listener -> listener.onMediaButtonEvent(keyEvent) }
    }
}
