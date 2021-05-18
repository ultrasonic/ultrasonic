package org.moire.ultrasonic.domain

import java.io.Serializable

data class Artist(
    override var id: String? = null,
    override var name: String? = null,
    var index: String? = null,
    var coverArt: String? = null,
    var albumCount: Long? = null,
    var closeness: Int = 0
) : Serializable, GenericEntry() {
    companion object {
        private const val serialVersionUID = -5790532593784846982L
    }
}
