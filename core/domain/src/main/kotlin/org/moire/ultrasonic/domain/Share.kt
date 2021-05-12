package org.moire.ultrasonic.domain

import java.io.Serializable
import org.moire.ultrasonic.domain.MusicDirectory.Entry

data class Share(
    override var id: String? = null,
    var url: String? = null,
    var description: String? = null,
    var username: String? = null,
    var created: String? = null,
    var lastVisited: String? = null,
    var expires: String? = null,
    var visitCount: Long? = null,
    private val entries: MutableList<Entry> = mutableListOf()
) : Serializable, GenericEntry() {
    override val name: String?
        get() = url?.let { urlPattern.matcher(url).replaceFirst("$1") }

    fun getEntries(): List<Entry> {
        return entries.toList()
    }

    fun addEntry(entry: Entry) {
        entries.add(entry)
    }

    companion object {
        private const val serialVersionUID = 1487561657691009668L
        private val urlPattern = ".*/([^/?]+).*".toPattern()
    }
}
