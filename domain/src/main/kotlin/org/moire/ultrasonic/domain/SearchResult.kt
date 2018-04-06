package org.moire.ultrasonic.domain

import org.moire.ultrasonic.domain.MusicDirectory.Entry

/**
 * The result of a search.  Contains matching artists, albums and songs.
 */
data class SearchResult(
    val artists: List<Artist>,
    val albums: List<Entry>,
    val songs: List<Entry>
)
