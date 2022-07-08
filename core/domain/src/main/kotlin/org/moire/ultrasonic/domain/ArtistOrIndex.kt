/*
 * ArtistOrIndex.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.domain

import androidx.room.Ignore

@Suppress("LongParameterList")
abstract class ArtistOrIndex(
    @Ignore
    override var id: String,
    @Ignore
    open var serverId: Int,
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
) : GenericEntry() {

    fun compareTo(other: ArtistOrIndex): Int {
        return when {
            this.closeness == other.closeness -> {
                0
            }
            this.closeness > other.closeness -> {
                -1
            }
            else -> {
                1
            }
        }
    }

    override fun compareTo(other: Identifiable) = compareTo(other as ArtistOrIndex)
}
