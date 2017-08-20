// Converts entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient] to app domain entities.
@file:JvmName("APIConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.api.subsonic.models.Album
import org.moire.ultrasonic.api.subsonic.models.Index
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Indexes
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.api.subsonic.models.Artist as APIArtist
import org.moire.ultrasonic.api.subsonic.models.Indexes as APIIndexes
import org.moire.ultrasonic.api.subsonic.models.MusicDirectory as APIMusicDirectory
import org.moire.ultrasonic.api.subsonic.models.MusicFolder as APIMusicFolder

fun APIMusicFolder.toDomainEntity(): MusicFolder = MusicFolder(this.id.toString(), this.name)

fun List<APIMusicFolder>.toDomainEntityList(): List<MusicFolder>
        = this.map { it.toDomainEntity() }

fun APIIndexes.toDomainEntity(): Indexes = Indexes(this.lastModified, this.ignoredArticles,
        this.shortcutList.map { it.toDomainEntity() }, this.indexList.foldIndexToArtistList())

private fun List<Index>.foldIndexToArtistList(): List<Artist> = this.fold(listOf(), {
    acc, index -> acc + index.artists.map { it.toDomainEntity() }
})

fun APIArtist.toDomainEntity(): Artist = Artist().apply {
    id = this@toDomainEntity.id.toString()
    name = this@toDomainEntity.name
}

fun APIArtist.toMusicDirectoryDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toMusicDirectoryDomainEntity.name
    addAll(this@toMusicDirectoryDomainEntity.albumsList.map { it.toDomainEntity() })
}

fun Album.toDomainEntity(): MusicDirectory.Entry = MusicDirectory.Entry().apply {
    id = this@toDomainEntity.id.toString()
    title = this@toDomainEntity.name
    coverArt = this@toDomainEntity.coverArt
    artist = this@toDomainEntity.artist
    artistId = this@toDomainEntity.artistId.toString()
    songCount = this@toDomainEntity.songCount.toLong()
    duration = this@toDomainEntity.duration
    created = this@toDomainEntity.created?.time
    year = this@toDomainEntity.year
    genre = this@toDomainEntity.genre
}

fun MusicDirectoryChild.toDomainEntity(): MusicDirectory.Entry = MusicDirectory.Entry().apply {
    id = this@toDomainEntity.id.toString()
    parent = this@toDomainEntity.parent.toString()
    setIsDirectory(this@toDomainEntity.isDir)
    title = this@toDomainEntity.title
    album = this@toDomainEntity.album
    albumId = this@toDomainEntity.albumId.toString()
    artist = this@toDomainEntity.artist
    artistId = this@toDomainEntity.artistId.toString()
    track = this@toDomainEntity.track
    year = this@toDomainEntity.year
    genre = this@toDomainEntity.genre
    contentType = this@toDomainEntity.contentType
    suffix = this@toDomainEntity.suffix
    transcodedContentType = this@toDomainEntity.transcodedContentType
    transcodedSuffix = this@toDomainEntity.transcodedSuffix
    coverArt = this@toDomainEntity.coverArt.toString()
    size = this@toDomainEntity.size
    duration = this@toDomainEntity.duration
    bitRate = this@toDomainEntity.bitRate
    path = this@toDomainEntity.path
    setIsVideo(this@toDomainEntity.isVideo)
    setCreated(this@toDomainEntity.created?.time)
    starred = this@toDomainEntity.starred != null
    discNumber = this@toDomainEntity.discNumber
    type = this@toDomainEntity.type
}

fun APIMusicDirectory.toDomainEntity(): MusicDirectory = MusicDirectory().apply {
    name = this@toDomainEntity.name
    addAll(this@toDomainEntity.childList.map { it.toDomainEntity() })
}
