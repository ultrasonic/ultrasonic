/*
 * Constants.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.util

object Constants {
    // Character encoding used throughout.
    const val UTF_8 = "UTF-8"

    // REST protocol version and client ID.
    // Note: Keep it as low as possible to maintain compatibility with older servers.
    const val REST_PROTOCOL_VERSION = "1.7.0"
    const val REST_CLIENT_ID = "Ultrasonic"

    // Names for intent extras.
    const val INTENT_ID = "subsonic.id"
    const val INTENT_NAME = "subsonic.name"
    const val INTENT_ARTIST = "subsonic.artist"
    const val INTENT_TITLE = "subsonic.title"
    const val INTENT_AUTOPLAY = "subsonic.playall"
    const val INTENT_QUERY = "subsonic.query"
    const val INTENT_PLAYLIST_ID = "subsonic.playlist.id"
    const val INTENT_PODCAST_CHANNEL_ID = "subsonic.podcastChannel.id"
    const val INTENT_PARENT_ID = "subsonic.parent.id"
    const val INTENT_PLAYLIST_NAME = "subsonic.playlist.name"
    const val INTENT_SHARE_ID = "subsonic.share.id"
    const val INTENT_SHARE_NAME = "subsonic.share.name"
    const val INTENT_ALBUM_LIST_TYPE = "subsonic.albumlisttype"
    const val INTENT_ALBUM_LIST_TITLE = "subsonic.albumlisttitle"
    const val INTENT_ALBUM_LIST_SIZE = "subsonic.albumlistsize"
    const val INTENT_ALBUM_LIST_OFFSET = "subsonic.albumlistoffset"
    const val INTENT_SHUFFLE = "subsonic.shuffle"
    const val INTENT_REFRESH = "subsonic.refresh"
    const val INTENT_STARRED = "subsonic.starred"
    const val INTENT_RANDOM = "subsonic.random"
    const val INTENT_GENRE_NAME = "subsonic.genre"
    const val INTENT_IS_ALBUM = "subsonic.isalbum"
    const val INTENT_VIDEOS = "subsonic.videos"
    const val INTENT_SHOW_PLAYER = "subsonic.showplayer"
    const val INTENT_APPEND = "subsonic.append"

    // Names for Intent Actions
    const val CMD_PROCESS_KEYCODE = "org.moire.ultrasonic.CMD_PROCESS_KEYCODE"
    const val CMD_PLAY = "org.moire.ultrasonic.CMD_PLAY"
    const val CMD_RESUME_OR_PLAY = "org.moire.ultrasonic.CMD_RESUME_OR_PLAY"
    const val CMD_TOGGLEPAUSE = "org.moire.ultrasonic.CMD_TOGGLEPAUSE"
    const val CMD_PAUSE = "org.moire.ultrasonic.CMD_PAUSE"
    const val CMD_STOP = "org.moire.ultrasonic.CMD_STOP"
    const val CMD_PREVIOUS = "org.moire.ultrasonic.CMD_PREVIOUS"
    const val CMD_NEXT = "org.moire.ultrasonic.CMD_NEXT"

    // Preferences keys.
    const val PREFERENCES_KEY_SERVER_INSTANCE = "serverInstanceId"
    const val PREFERENCES_KEY_THEME = "theme"
    const val PREFERENCES_KEY_THEME_LIGHT = "light"
    const val PREFERENCES_KEY_THEME_DARK = "dark"
    const val PREFERENCES_KEY_THEME_BLACK = "black"
    const val PREFERENCES_KEY_DISPLAY_BITRATE_WITH_ARTIST = "displayBitrateWithArtist"
    const val PREFERENCES_KEY_USE_FOLDER_FOR_ALBUM_ARTIST = "useFolderForAlbumArtist"
    const val PREFERENCES_KEY_SHOW_TRACK_NUMBER = "showTrackNumber"
    const val PREFERENCES_KEY_MAX_BITRATE_WIFI = "maxBitrateWifi"
    const val PREFERENCES_KEY_MAX_BITRATE_MOBILE = "maxBitrateMobile"
    const val PREFERENCES_KEY_CACHE_SIZE = "cacheSize"
    const val PREFERENCES_KEY_CUSTOM_CACHE_LOCATION = "customCacheLocation"
    const val PREFERENCES_KEY_CACHE_LOCATION = "cacheLocation"
    const val PREFERENCES_KEY_PRELOAD_COUNT = "preloadCount"
    const val PREFERENCES_KEY_HIDE_MEDIA = "hideMedia"
    const val PREFERENCES_KEY_MEDIA_BUTTONS = "mediaButtons"
    const val PREFERENCES_KEY_SCROBBLE = "scrobble"
    const val PREFERENCES_KEY_SERVER_SCALING = "serverScaling"
    const val PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD = "wifiRequiredForDownload"
    const val PREFERENCES_KEY_BUFFER_LENGTH = "bufferLength"
    const val PREFERENCES_KEY_NETWORK_TIMEOUT = "networkTimeout"
    const val PREFERENCES_KEY_MAX_ALBUMS = "maxAlbums"
    const val PREFERENCES_KEY_MAX_SONGS = "maxSongs"
    const val PREFERENCES_KEY_MAX_ARTISTS = "maxArtists"
    const val PREFERENCES_KEY_DEFAULT_ALBUMS = "defaultAlbums"
    const val PREFERENCES_KEY_DEFAULT_SONGS = "defaultSongs"
    const val PREFERENCES_KEY_DEFAULT_ARTISTS = "defaultArtists"
    const val PREFERENCES_KEY_SHOW_NOW_PLAYING = "showNowPlaying"
    const val PREFERENCES_KEY_CLEAR_SEARCH_HISTORY = "clearSearchHistory"
    const val PREFERENCES_KEY_DOWNLOAD_TRANSITION = "transitionToDownloadOnPlay"
    const val PREFERENCES_KEY_INCREMENT_TIME = "incrementTime"
    const val PREFERENCES_KEY_SHOW_NOW_PLAYING_DETAILS = "showNowPlayingDetails"
    const val PREFERENCES_KEY_ID3_TAGS = "useId3Tags"
    const val PREFERENCES_KEY_SHOW_ARTIST_PICTURE = "showArtistPicture"
    const val PREFERENCES_KEY_CHAT_REFRESH_INTERVAL = "chatRefreshInterval"
    const val PREFERENCES_KEY_DIRECTORY_CACHE_TIME = "directoryCacheTime"
    const val PREFERENCES_KEY_CLEAR_BOOKMARK = "clearBookmark"
    const val PREFERENCES_KEY_DISC_SORT = "discAndTrackSort"
    const val PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS = "sharingAlwaysAskForDetails"
    const val PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION = "sharingDefaultDescription"
    const val PREFERENCES_KEY_DEFAULT_SHARE_GREETING = "sharingDefaultGreeting"
    const val PREFERENCES_KEY_SHARE_ON_SERVER = "sharingCreateOnServer"
    const val PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION = "sharingDefaultExpiration"
    const val PREFERENCES_KEY_USE_FIVE_STAR_RATING = "use_five_star_rating"
    const val PREFERENCES_KEY_HARDWARE_OFFLOAD = "use_hw_offload"
    const val PREFERENCES_KEY_CATEGORY_NOTIFICATIONS = "notificationsCategory"
    const val PREFERENCES_KEY_FIRST_RUN_EXECUTED = "firstRunExecuted"
    const val PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE = "resumeOnBluetoothDevice"
    const val PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE = "pauseOnBluetoothDevice"
    const val PREFERENCES_KEY_DEBUG_LOG_TO_FILE = "debugLogToFile"
    const val PREFERENCES_KEY_OVERRIDE_LANGUAGE = "overrideLanguage"
    const val PREFERENCE_VALUE_ALL = 0
    const val PREFERENCE_VALUE_A2DP = 1
    const val PREFERENCE_VALUE_DISABLED = 2
    const val FILENAME_PLAYLIST_SER = "downloadstate.ser"
    const val ALBUM_ART_FILE = "folder.jpeg"
    const val STARRED = "starred"
    const val ALPHABETICAL_BY_NAME = "alphabeticalByName"
    const val ALBUMS_OF_ARTIST = "albumsOfArtist"
    const val RESULT_CLOSE_ALL = 1337
}
