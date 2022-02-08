/*
 * LRUCache.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.util

import java.lang.ref.SoftReference
import java.util.HashMap

/**
 * A cache that deletes the least-recently-used items.
 */
class LRUCache<K, V>(capacity: Int) {
    private val capacity: Int
    private val map: MutableMap<K, TimestampedValue>

    @Synchronized
    operator fun get(key: K): V? {
        val value = map[key]
        var result: V? = null
        if (value != null) {
            value.updateTimestamp()
            result = value.getValue()
        }
        return result
    }

    @Synchronized
    fun put(key: K, value: V) {
        if (map.size >= capacity) {
            removeOldest()
        }
        map[key] = TimestampedValue(value)
    }

    fun clear() {
        map.clear()
    }

    private fun removeOldest() {
        var oldestKey: K? = null
        var oldestTimestamp = Long.MAX_VALUE
        for ((key, value) in map) {
            if (value.timestamp < oldestTimestamp) {
                oldestTimestamp = value.timestamp
                oldestKey = key
            }
        }
        if (oldestKey != null) {
            map.remove(oldestKey)
        }
    }

    private inner class TimestampedValue(value: V) {
        private val value: SoftReference<V> = SoftReference(value)

        var timestamp: Long = 0
            private set

        fun getValue(): V? {
            return value.get()
        }

        fun updateTimestamp() {
            timestamp = System.currentTimeMillis()
        }

        init {
            updateTimestamp()
        }
    }

    init {
        map = HashMap(capacity)
        this.capacity = capacity
    }
}
