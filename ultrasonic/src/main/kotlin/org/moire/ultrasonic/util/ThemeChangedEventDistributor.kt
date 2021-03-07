package org.moire.ultrasonic.util

/**
 * This class distributes Theme change related events to its subscribers.
 * It is a primitive implementation of a pub-sub event bus
 */
class ThemeChangedEventDistributor {
    var eventListenerList: MutableList<ThemeChangedEventListener> =
        listOf<ThemeChangedEventListener>().toMutableList()

    fun subscribe(listener: ThemeChangedEventListener) {
        eventListenerList.add(listener)
    }

    fun unsubscribe(listener: ThemeChangedEventListener) {
        eventListenerList.remove(listener)
    }

    fun RaiseThemeChangedEvent() {
        eventListenerList.forEach { listener -> listener.onThemeChanged() }
    }
}
