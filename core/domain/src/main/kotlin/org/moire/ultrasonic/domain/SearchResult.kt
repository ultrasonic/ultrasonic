package org.moire.ultrasonic.domain

import org.moire.ultrasonic.domain.MusicDirectory.Entry

/**
 * The result of a search.  Contains matching artists, albums and songs.
 */
data class SearchResult(
    val artists: List<Artist> = listOf(),
    val albums: List<Entry> = listOf(),
    val songs: List<Entry> = listOf()
)
