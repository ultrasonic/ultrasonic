@file:Suppress("IllegalIdentifier")

package org.moire.ultrasonic.domain

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should equal`
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
            adminRole `should be equal to` entity.adminRole
            commentRole `should be equal to` entity.commentRole
            coverArtRole `should be equal to` entity.coverArtRole
            downloadRole `should be equal to` entity.downloadRole
            email `should equal` entity.email
            jukeboxRole `should be equal to` entity.jukeboxRole
            playlistRole `should be equal to` entity.playlistRole
            podcastRole `should be equal to` entity.podcastRole
            scrobblingEnabled `should be equal to` entity.scrobblingEnabled
            settingsRole `should be equal to` entity.settingsRole
            shareRole `should be equal to` entity.shareRole
            streamRole `should be equal to` entity.streamRole
            uploadRole `should be equal to` entity.uploadRole
            userName `should equal` entity.username
        }
    }
}
