package org.moire.ultrasonic.domain

import java.io.Serializable

data class Share(
    override var id: String,
    var url: String? = null,
    var description: String? = null,
    var username: String? = null,
    var created: String? = null,
    var lastVisited: String? = null,
    var expires: String? = null,
    var visitCount: Long? = null,
    private val tracks: MutableList<Track> = mutableListOf()
) : Serializable, GenericEntry() {
    override val name: String?
        get() {
            if (url != null) {
                return urlPattern.matcher(url!!).replaceFirst("$1")
            }
            return null
        }

    fun getEntries(): List<Track> {
        return tracks.toList()
    }

    fun addEntry(track: Track) {
        tracks.add(track)
    }

    companion object {
        private const val serialVersionUID = 1487561657691009668L
        private val urlPattern = ".*/([^/?]+).*".toPattern()
    }
}
