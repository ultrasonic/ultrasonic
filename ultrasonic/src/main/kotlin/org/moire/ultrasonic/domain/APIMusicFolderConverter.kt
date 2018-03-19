// Converts MusicFolder entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APIMusicFolderConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.MusicFolder as APIMusicFolder

fun APIMusicFolder.toDomainEntity(): MusicFolder = MusicFolder(this.id, this.name)

fun List<APIMusicFolder>.toDomainEntityList(): List<MusicFolder> =
        this.map { it.toDomainEntity() }
