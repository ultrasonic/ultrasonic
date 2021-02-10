package org.moire.ultrasonic.util

class ThemeChangedEventDistributor {
    var eventListenerList: MutableList<ThemeChangedEventListener> = listOf<ThemeChangedEventListener>().toMutableList()

    fun subscribe(listener: ThemeChangedEventListener) {
        eventListenerList.add(listener)
    }

    fun unsubscribe(listener: ThemeChangedEventListener) {
        eventListenerList.remove(listener)
    }

    fun RaiseThemeChangedEvent() {
        eventListenerList.forEach{ listener -> listener.onThemeChanged() }
    }
}