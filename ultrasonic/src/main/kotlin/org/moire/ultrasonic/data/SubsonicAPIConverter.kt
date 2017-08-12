// Converts entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient] to app domain entities.
@file:JvmName("APIConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Indexes
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.api.subsonic.models.Artist as APIArtist
import org.moire.ultrasonic.api.subsonic.models.Indexes as APIIndexes
import org.moire.ultrasonic.api.subsonic.models.MusicFolder as APIMusicFolder

fun APIMusicFolder.toDomainEntity(): MusicFolder = MusicFolder(this.id.toString(), this.name)

fun List<APIMusicFolder>.toDomainEntityList(): List<MusicFolder>
        = this.map { it.toDomainEntity() }

fun APIIndexes.toDomainEntity(): Indexes = Indexes(this.lastModified, this.ignoredArticles,
        this.shortcuts.foldIndexToArtistList(), this.indexList.foldIndexToArtistList())

private fun List<Index>.foldIndexToArtistList(): List<Artist> = this.fold(listOf(), {
    acc, index -> acc + index.artists.map { it.toDomainEntity() }
})

fun APIArtist.toDomainEntity(): Artist = Artist().apply {
    id = this@toDomainEntity.id.toString()
    name = this@toDomainEntity.name
}