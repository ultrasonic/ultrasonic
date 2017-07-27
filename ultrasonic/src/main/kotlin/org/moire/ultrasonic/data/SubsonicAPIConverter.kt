// Converts entities from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient] to app entities.
@file:JvmName("APIConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.domain.MusicFolder

typealias APIMusicFolder = org.moire.ultrasonic.api.subsonic.models.MusicFolder

fun convertMusicFolder(entity: APIMusicFolder): MusicFolder {
    return MusicFolder(entity.id.toString(), entity.name)
}

fun convertMusicFolderList(entitiesList: List<APIMusicFolder>): List<MusicFolder> {
    return entitiesList.map { convertMusicFolder(it) }
}