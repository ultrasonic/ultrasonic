package org.moire.ultrasonic.domain

import androidx.room.Ignore

open class GenericEntry(
    @Ignore override val id: String
) : Identifiable {
    @Ignore
    open val name: String? = null
    override fun compareTo(other: Identifiable): Int {
        return this.id.toInt().compareTo(other.id.toInt())
    }
}

interface Identifiable : Comparable<Identifiable> {
    val id: String
}
