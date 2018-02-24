package org.moire.ultrasonic.domain

/**
 * The result of a search.  Contains matching artists, albums and songs.
 */
data class SearchResult(
        val artists: List<Artist>,
        val albums: List<MusicDirectory.Entry>,
        val songs: List<MusicDirectory.Entry>
)
