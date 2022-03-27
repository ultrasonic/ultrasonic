package org.moire.ultrasonic.domain

/**
 * The result of a search.  Contains matching artists, albums and songs.
 */
data class SearchResult(
    val artists: List<ArtistOrIndex> = listOf(),
    val albums: List<Album> = listOf(),
    val songs: List<Track> = listOf()
)
