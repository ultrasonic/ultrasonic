package org.moire.ultrasonic.util

class NowPlayingEventDistributor {
    var eventListenerList: MutableList<NowPlayingEventListener> = listOf<NowPlayingEventListener>().toMutableList()

    fun subscribe(listener: NowPlayingEventListener) {
        eventListenerList.add(listener)
    }

    fun unsubscribe(listener: NowPlayingEventListener) {
        eventListenerList.remove(listener)
    }

    fun RaiseShowNowPlayingEvent() {
        eventListenerList.forEach{ listener -> listener.onShowNowPlaying() }
    }

    fun RaiseHideNowPlayingEvent() {
        eventListenerList.forEach{ listener -> listener.onHideNowPlaying() }
    }

    fun RaiseNowPlayingDismissedEvent() {
        eventListenerList.forEach{ listener -> listener.onDismissNowPlaying() }
    }
}