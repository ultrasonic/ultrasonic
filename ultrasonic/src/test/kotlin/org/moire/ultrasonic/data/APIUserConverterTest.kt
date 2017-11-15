@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.data

import org.amshove.kluent.`should equal to`
import org.junit.Test
import org.moire.ultrasonic.api.subsonic.models.User

/**
 * Test conversion  from api [User] to domain [UserInfo].
 */
class APIUserConverterTest {
    @Test
    fun `Should convert to domain entity`() {
        val entity = User(username = "Awsemo", email = "none@none.net", scrobblingEnabled = false,
                shareRole = true, streamRole = true)

        val domainEntity = entity.toDomainEntity()

        with(domainEntity) {
            adminRole `should equal to` entity.adminRole
            commentRole `should equal to` entity.commentRole
            coverArtRole `should equal to` entity.coverArtRole
            downloadRole `should equal to` entity.downloadRole
            email `should equal to` entity.email
            jukeboxRole `should equal to` entity.jukeboxRole
            playlistRole `should equal to` entity.playlistRole
            podcastRole `should equal to` entity.podcastRole
            scrobblingEnabled `should equal to` entity.scrobblingEnabled
            settingsRole `should equal to` entity.settingsRole
            shareRole `should equal to` entity.shareRole
            streamRole `should equal to` entity.streamRole
            uploadRole `should equal to` entity.uploadRole
            userName `should equal to` entity.username
        }
    }
}
