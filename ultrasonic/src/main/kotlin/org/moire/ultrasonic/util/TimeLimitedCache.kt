/*
 * TimeLimitedCache.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.util

import java.lang.ref.SoftReference
import java.util.concurrent.TimeUnit

class TimeLimitedCache<T>(expiresAfter: Long = 60L, timeUnit: TimeUnit = TimeUnit.MINUTES) {
    private var value: SoftReference<T>? = null
    private val expiresMillis: Long = TimeUnit.MILLISECONDS.convert(expiresAfter, timeUnit)
    private var expires: Long = 0

    fun get(): T? {
        return if (System.currentTimeMillis() < expires) value!!.get() else null
    }

    @JvmOverloads
    fun set(value: T, ttl: Long = expiresMillis, timeUnit: TimeUnit = TimeUnit.MILLISECONDS) {
        this.value = SoftReference(value)
        expires = System.currentTimeMillis() + timeUnit.toMillis(ttl)
    }

    fun clear() {
        expires = 0L
        value = null
    }
}
