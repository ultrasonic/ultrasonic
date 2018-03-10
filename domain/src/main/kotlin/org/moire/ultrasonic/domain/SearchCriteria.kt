package org.moire.ultrasonic.domain

/**
 * The criteria for a music search.
 */
data class SearchCriteria(
        val query: String,
        val artistCount: Int,
        val albumCount: Int,
        val songCount: Int
)
