package org.moire.ultrasonic.util

/**
 * This class distributes Now Playing related events to its subscribers.
 * It is a primitive implementation of a pub-sub event bus
 */
class NowPlayingEventDistributor {
    private var eventListenerList: MutableList<NowPlayingEventListener> =
        listOf<NowPlayingEventListener>().toMutableList()

    fun subscribe(listener: NowPlayingEventListener) {
        eventListenerList.add(listener)
    }

    fun unsubscribe(listener: NowPlayingEventListener) {
        eventListenerList.remove(listener)
    }

    fun raiseShowNowPlayingEvent() {
        eventListenerList.forEach { listener -> listener.onShowNowPlaying() }
    }

    fun raiseHideNowPlayingEvent() {
        eventListenerList.forEach { listener -> listener.onHideNowPlaying() }
    }

    fun raiseNowPlayingDismissedEvent() {
        eventListenerList.forEach { listener -> listener.onDismissNowPlaying() }
    }
}
