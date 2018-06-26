package org.moire.ultrasonic.subsonic.loader.image

import android.net.Uri

internal const val SCHEME = "subsonic_api"
internal const val AUTHORITY = BuildConfig.APPLICATION_ID
internal const val COVER_ART_PATH = "cover_art"

internal fun createLoadCoverArtRequest(entityId: String): Uri = Uri.Builder()
    .scheme(SCHEME)
    .authority(AUTHORITY)
    .appendPath(COVER_ART_PATH)
    .appendQueryParameter("id", entityId)
    .build()
