package org.moire.ultrasonic.util

import java.util.Comparator
import org.moire.ultrasonic.domain.MusicDirectory

class EntryByDiscAndTrackComparator : Comparator<MusicDirectory.Child> {
    override fun compare(x: MusicDirectory.Child, y: MusicDirectory.Child): Int {
        val discX = x.discNumber
        val discY = y.discNumber
        val trackX = if (x is MusicDirectory.Track) x.track else null
        val trackY = if (y is MusicDirectory.Track) y.track else null
        val albumX = x.album
        val albumY = y.album
        val pathX = x.path
        val pathY = y.path
        val albumComparison = compare(albumX, albumY)
        if (albumComparison != 0) {
            return albumComparison
        }
        val discComparison = compare(discX ?: 0, discY ?: 0)
        if (discComparison != 0) {
            return discComparison
        }
        val trackComparison = compare(trackX ?: 0, trackY ?: 0)
        return if (trackComparison != 0) {
            trackComparison
        } else compare(
            pathX ?: "",
            pathY ?: ""
        )
    }

    companion object {
        private fun compare(a: Int, b: Int): Int {
            return a.compareTo(b)
        }

        private fun compare(a: String?, b: String?): Int {
            if (a == null && b == null) {
                return 0
            }
            if (a == null) {
                return -1
            }
            return if (b == null) {
                1
            } else a.compareTo(b)
        }
    }
}
