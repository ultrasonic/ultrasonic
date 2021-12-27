package org.moire.ultrasonic.domain

import androidx.room.Ignore

open class GenericEntry {
    // TODO Should be non-null!
    @Ignore
    open val id: String? = null
    @Ignore
    open val name: String? = null

    // These are just a formality and will never be called,
    // because Kotlin data classes will have autogenerated equals() and hashCode() functions
    override operator fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (name?.hashCode() ?: 0)
        return result
    }
}