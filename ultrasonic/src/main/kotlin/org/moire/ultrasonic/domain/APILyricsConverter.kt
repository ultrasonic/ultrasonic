// Converts Lyrics entity from [org.moire.ultrasonic.api.subsonic.SubsonicAPIClient]
// to app domain entities.
@file:JvmName("APILyricsConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Lyrics as APILyrics

fun APILyrics.toDomainEntity(): Lyrics = Lyrics(
    artist = this@toDomainEntity.artist,
    title = this@toDomainEntity.title,
    text = this@toDomainEntity.text
)
