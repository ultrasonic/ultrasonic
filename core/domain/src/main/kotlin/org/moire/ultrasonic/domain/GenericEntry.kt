package org.moire.ultrasonic.domain

abstract class GenericEntry {
    // TODO Should be non-null!
    abstract val id: String?
    open val name: String? = null
}
