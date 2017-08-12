// Converts entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient] to app entities.
@file:JvmName("APIConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.api.subsonic.models.MusicFolder as APIMusicFolder

fun APIMusicFolder.toDomainEntity(): MusicFolder = MusicFolder(this.id.toString(), this.name)

fun List<APIMusicFolder>.toDomainEntityList(): List<MusicFolder>
        = this.map { it.toDomainEntity() }

fun convertMusicFolderList(entitiesList: List<APIMusicFolder>): List<MusicFolder> {
    return entitiesList.map { convertMusicFolder(it) }
}
