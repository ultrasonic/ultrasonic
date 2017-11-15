// Helper functions to convert User entity to domain entity
@file:JvmName("APIUserConverter")
package org.moire.ultrasonic.data

import org.moire.ultrasonic.domain.UserInfo
import org.moire.ultrasonic.api.subsonic.models.User

fun User.toDomainEntity(): UserInfo = UserInfo().apply {
    adminRole = this@toDomainEntity.adminRole
    commentRole = this@toDomainEntity.commentRole
    coverArtRole = this@toDomainEntity.coverArtRole
    downloadRole = this@toDomainEntity.downloadRole
    email = this@toDomainEntity.email
    jukeboxRole = this@toDomainEntity.jukeboxRole
    playlistRole = this@toDomainEntity.playlistRole
    podcastRole = this@toDomainEntity.podcastRole
    scrobblingEnabled = this@toDomainEntity.scrobblingEnabled
    settingsRole = this@toDomainEntity.settingsRole
    shareRole = this@toDomainEntity.shareRole
    streamRole = this@toDomainEntity.streamRole
    uploadRole = this@toDomainEntity.uploadRole
    userName = this@toDomainEntity.username
}
