package org.moire.ultrasonic.util

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat

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

    fun ReleaseCachedMediaSessionToken() {
        synchronized(this) {
            cachedToken = null
        }
    }

    fun RaiseMediaSessionTokenCreatedEvent(token: MediaSessionCompat.Token) {
        synchronized(this) {
            cachedToken = token
            eventListenerList.forEach { listener -> listener.onMediaSessionTokenCreated(token) }
        }
    }

    fun RaisePlayFromMediaIdRequestedEvent(mediaId: String?, extras: Bundle?) {
        eventListenerList.forEach { listener -> listener.onPlayFromMediaIdRequested(mediaId, extras) }
    }

    fun RaisePlayFromSearchRequestedEvent(query: String?, extras: Bundle?) {
        eventListenerList.forEach { listener -> listener.onPlayFromSearchRequested(query, extras) }
    }
}
