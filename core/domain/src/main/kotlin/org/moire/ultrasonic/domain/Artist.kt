package org.moire.ultrasonic.domain

import java.io.Serializable

data class Artist(
    var id: String? = null,
    var name: String? = null,
    var index: String? = null,
    var coverArt: String? = null,
    var albumCount: Long? = null,
    var closeness: Int = 0
) : Serializable {
    companion object {
        private const val serialVersionUID = -5790532593784846982L
    }
}
