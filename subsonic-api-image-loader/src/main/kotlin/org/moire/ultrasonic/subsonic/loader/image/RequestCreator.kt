package org.moire.ultrasonic.subsonic.loader.image

import android.net.Uri

internal const val SCHEME = "subsonic_api"
internal const val AUTHORITY = BuildConfig.APPLICATION_ID
internal const val COVER_ART_PATH = "cover_art"
internal const val AVATAR_PATH = "avatar"
internal const val QUERY_ID = "id"
internal const val QUERY_USERNAME = "username"

internal fun createLoadCoverArtRequest(entityId: String): Uri = Uri.Builder()
    .scheme(SCHEME)
    .authority(AUTHORITY)
    .appendPath(COVER_ART_PATH)
    .appendQueryParameter(QUERY_ID, entityId)
    .build()

internal fun createLoadAvatarRequest(username: String): Uri = Uri.Builder()
    .scheme(SCHEME)
    .authority(AUTHORITY)
    .appendPath(AVATAR_PATH)
    .appendQueryParameter(QUERY_USERNAME, username)
    .build()
