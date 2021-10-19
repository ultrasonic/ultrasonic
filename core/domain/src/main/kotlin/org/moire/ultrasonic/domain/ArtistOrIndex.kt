package org.moire.ultrasonic.domain

import androidx.room.Ignore

abstract class ArtistOrIndex(
    @Ignore
    override var id: String,
    @Ignore
    override var name: String? = null,
    @Ignore
    open var index: String? = null,
    @Ignore
    open var coverArt: String? = null,
    @Ignore
    open var albumCount: Long? = null,
    @Ignore
    open var closeness: Int = 0
) : GenericEntry()
