package org.moire.ultrasonic.domain

import java.io.Serializable

data class Indexes(
        val lastModified: Long,
        val ignoredArticles: String,
        val shortcuts: MutableList<Artist> = mutableListOf(),
        val artists: MutableList<Artist> = mutableListOf()
) : Serializable {
    companion object {
        private const val serialVersionUID = 8156117238598414701L
    }
}
