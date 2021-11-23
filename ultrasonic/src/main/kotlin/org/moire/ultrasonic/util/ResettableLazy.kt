package org.moire.ultrasonic.util

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

class ResettableLazy<T>(private val initializer: () -> T) {
    private val lazyRef: AtomicReference<Lazy<T>> = AtomicReference(
        lazy(
            initializer
        )
    )

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return lazyRef.get().getValue(thisRef, property)
    }

    val value: T
        get() {
            return lazyRef.get().value
        }

    fun reset() {
        lazyRef.set(lazy(initializer))
    }
}