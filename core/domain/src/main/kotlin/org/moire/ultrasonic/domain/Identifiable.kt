package org.moire.ultrasonic.domain

import androidx.room.Ignore

abstract class GenericEntry : Identifiable {
    @Ignore
    open val name: String? = null
}

interface Identifiable : Comparable<Identifiable> {
    val id: String

    val longId: Long
        get() = id.hashCode().toLong()

    override fun compareTo(other: Identifiable): Int {
        return longId.compareTo(other.longId)
    }
}
