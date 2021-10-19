package org.moire.ultrasonic.domain

import java.io.Serializable

data class PodcastsChannel(
    override val id: String,
    val title: String?,
    val url: String?,
    val description: String?,
    val status: String?
) : Serializable, GenericEntry() {
    companion object {
        private const val serialVersionUID = -4160515427075433798L
    }
}
