package org.moire.ultrasonic.api.subsonic.models

/**
 * Type of album list used in [org.moire.ultrasonic.api.subsonic.SubsonicAPIDefinition.getAlbumList]
 * calls.
 *
 * @author Yahor Berdnikau
 */
enum class AlbumListType(val typeName: String) {
    RANDOM("random"),
    NEWEST("newest"),
    HIGHEST("highest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    SORTED_BY_NAME("alphabeticalByName"),
    SORTED_BY_ARTIST("alphabeticalByArtist"),
    STARRED("starred"),
    BY_YEAR("byYear"),
    BY_GENRE("byGenre");

    override fun toString(): String {
        return super.toString().toLowerCase()
    }
}
