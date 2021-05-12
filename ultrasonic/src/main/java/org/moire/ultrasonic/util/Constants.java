/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package org.moire.ultrasonic.util;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public final class Constants
{

	// Character encoding used throughout.
	public static final String UTF_8 = "UTF-8";

	// REST protocol version and client ID.
	// Note: Keep it as low as possible to maintain compatibility with older servers.
	public static final String REST_PROTOCOL_VERSION = "1.7.0";
	public static final String REST_CLIENT_ID = "Ultrasonic";

	// Names for intent extras.
	public static final String INTENT_EXTRA_NAME_ID = "subsonic.id";
	public static final String INTENT_EXTRA_NAME_NAME = "subsonic.name";
	public static final String INTENT_EXTRA_NAME_ARTIST = "subsonic.artist";
	public static final String INTENT_EXTRA_NAME_TITLE = "subsonic.title";
	public static final String INTENT_EXTRA_NAME_AUTOPLAY = "subsonic.playall";
	public static final String INTENT_EXTRA_NAME_QUERY = "subsonic.query";
	public static final String INTENT_EXTRA_NAME_PLAYLIST_ID = "subsonic.playlist.id";
	public static final String INTENT_EXTRA_NAME_PODCAST_CHANNEL_ID = "subsonic.podcastChannel.id";
	public static final String INTENT_EXTRA_NAME_PARENT_ID = "subsonic.parent.id";
	public static final String INTENT_EXTRA_NAME_PLAYLIST_NAME = "subsonic.playlist.name";
	public static final String INTENT_EXTRA_NAME_SHARE_ID = "subsonic.share.id";
	public static final String INTENT_EXTRA_NAME_SHARE_NAME = "subsonic.share.name";
	public static final String INTENT_EXTRA_NAME_ALBUM_LIST_TYPE = "subsonic.albumlisttype";
	public static final String INTENT_EXTRA_NAME_ALBUM_LIST_TITLE = "subsonic.albumlisttitle";
	public static final String INTENT_EXTRA_NAME_ALBUM_LIST_SIZE = "subsonic.albumlistsize";
	public static final String INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET = "subsonic.albumlistoffset";
	public static final String INTENT_EXTRA_NAME_SHUFFLE = "subsonic.shuffle";
	public static final String INTENT_EXTRA_NAME_REFRESH = "subsonic.refresh";
	public static final String INTENT_EXTRA_NAME_STARRED = "subsonic.starred";
	public static final String INTENT_EXTRA_NAME_RANDOM = "subsonic.random";
	public static final String INTENT_EXTRA_NAME_GENRE_NAME = "subsonic.genre";
	public static final String INTENT_EXTRA_NAME_IS_ALBUM = "subsonic.isalbum";
	public static final String INTENT_EXTRA_NAME_VIDEOS = "subsonic.videos";
	public static final String INTENT_EXTRA_NAME_SHOW_PLAYER = "subsonic.showplayer";
	public static final String INTENT_EXTRA_NAME_APPEND = "subsonic.append";

	// Names for Intent Actions
	public static final String CMD_PROCESS_KEYCODE = "org.moire.ultrasonic.CMD_PROCESS_KEYCODE";
	public static final String CMD_PLAY = "org.moire.ultrasonic.CMD_PLAY";
	public static final String CMD_RESUME_OR_PLAY = "org.moire.ultrasonic.CMD_RESUME_OR_PLAY";
	public static final String CMD_TOGGLEPAUSE = "org.moire.ultrasonic.CMD_TOGGLEPAUSE";
	public static final String CMD_PAUSE = "org.moire.ultrasonic.CMD_PAUSE";
	public static final String CMD_STOP = "org.moire.ultrasonic.CMD_STOP";
	public static final String CMD_PREVIOUS = "org.moire.ultrasonic.CMD_PREVIOUS";
	public static final String CMD_NEXT = "org.moire.ultrasonic.CMD_NEXT";

	// Preferences keys.
	public static final String PREFERENCES_KEY_SERVER_INSTANCE = "serverInstanceId";
	public static final String PREFERENCES_KEY_SERVERS_EDIT = "editServers";
	public static final String PREFERENCES_KEY_THEME = "theme";
	public static final String PREFERENCES_KEY_THEME_LIGHT = "light";
	public static final String PREFERENCES_KEY_THEME_DARK = "dark";
	public static final String PREFERENCES_KEY_THEME_BLACK = "black";
	public static final String PREFERENCES_KEY_DISPLAY_BITRATE_WITH_ARTIST = "displayBitrateWithArtist";
	public static final String PREFERENCES_KEY_USE_FOLDER_FOR_ALBUM_ARTIST = "useFolderForAlbumArtist";
	public static final String PREFERENCES_KEY_SHOW_TRACK_NUMBER = "showTrackNumber";
	public static final String PREFERENCES_KEY_MAX_BITRATE_WIFI = "maxBitrateWifi";
	public static final String PREFERENCES_KEY_MAX_BITRATE_MOBILE = "maxBitrateMobile";
	public static final String PREFERENCES_KEY_CACHE_SIZE = "cacheSize";
	public static final String PREFERENCES_KEY_CACHE_LOCATION = "cacheLocation";
	public static final String PREFERENCES_KEY_PRELOAD_COUNT = "preloadCount";
	public static final String PREFERENCES_KEY_HIDE_MEDIA = "hideMedia";
	public static final String PREFERENCES_KEY_MEDIA_BUTTONS = "mediaButtons";
	public static final String PREFERENCES_KEY_SCREEN_LIT_ON_DOWNLOAD = "screenLitOnDownload";
	public static final String PREFERENCES_KEY_SCROBBLE = "scrobble";
	public static final String PREFERENCES_KEY_SERVER_SCALING = "serverScaling";
	public static final String PREFERENCES_KEY_REPEAT_MODE = "repeatMode";
	public static final String PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD = "wifiRequiredForDownload";
	public static final String PREFERENCES_KEY_BUFFER_LENGTH = "bufferLength";
	public static final String PREFERENCES_KEY_NETWORK_TIMEOUT = "networkTimeout";
	public static final String PREFERENCES_KEY_SHOW_NOTIFICATION = "showNotification";
	public static final String PREFERENCES_KEY_ALWAYS_SHOW_NOTIFICATION = "alwaysShowNotification";
	public static final String PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS = "showLockScreen";
	public static final String PREFERENCES_KEY_MAX_ALBUMS = "maxAlbums";
	public static final String PREFERENCES_KEY_MAX_SONGS = "maxSongs";
	public static final String PREFERENCES_KEY_MAX_ARTISTS = "maxArtists";
	public static final String PREFERENCES_KEY_DEFAULT_ALBUMS = "defaultAlbums";
	public static final String PREFERENCES_KEY_DEFAULT_SONGS = "defaultSongs";
	public static final String PREFERENCES_KEY_DEFAULT_ARTISTS = "defaultArtists";
	public static final String PREFERENCES_KEY_SHOW_NOW_PLAYING = "showNowPlaying";
	public static final String PREFERENCES_KEY_GAPLESS_PLAYBACK = "gaplessPlayback";
	public static final String PREFERENCES_KEY_PLAYBACK_CONTROL_SETTINGS = "playbackControlSettings";
	public static final String PREFERENCES_KEY_CLEAR_SEARCH_HISTORY = "clearSearchHistory";
	public static final String PREFERENCES_KEY_DOWNLOAD_TRANSITION = "transitionToDownloadOnPlay";
	public static final String PREFERENCES_KEY_INCREMENT_TIME = "incrementTime";
	public static final String PREFERENCES_KEY_ID3_TAGS = "useId3Tags";
	public static final String PREFERENCES_KEY_SHOW_ARTIST_PICTURE = "showArtistPicture";
	public static final String PREFERENCES_KEY_TEMP_LOSS = "tempLoss";
	public static final String PREFERENCES_KEY_CHAT_REFRESH_INTERVAL = "chatRefreshInterval";
	public static final String PREFERENCES_KEY_DIRECTORY_CACHE_TIME = "directoryCacheTime";
	public static final String PREFERENCES_KEY_CLEAR_PLAYLIST = "clearPlaylist";
	public static final String PREFERENCES_KEY_CLEAR_BOOKMARK = "clearBookmark";
	public static final String PREFERENCES_KEY_DISC_SORT = "discAndTrackSort";
	public static final String PREFERENCES_KEY_VIDEO_PLAYER = "videoPlayer";
	public static final String PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS = "sendBluetoothNotifications";
	public static final String PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART = "sendBluetoothAlbumArt";
	public static final String PREFERENCES_KEY_VIEW_REFRESH = "viewRefresh";
	public static final String PREFERENCES_KEY_ASK_FOR_SHARE_DETAILS = "sharingAlwaysAskForDetails";
	public static final String PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION = "sharingDefaultDescription";
	public static final String PREFERENCES_KEY_DEFAULT_SHARE_GREETING = "sharingDefaultGreeting";
	public static final String PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION = "sharingDefaultExpiration";
	public static final String PREFERENCES_KEY_SHOW_ALL_SONGS_BY_ARTIST = "showAllSongsByArtist";
	public static final String PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY = "imageLoaderConcurrency";
	public static final String PREFERENCES_KEY_FF_IMAGE_LOADER = "ff_new_image_loader";
	public static final String PREFERENCES_KEY_USE_FIVE_STAR_RATING = "use_five_star_rating";
	public static final String PREFERENCES_KEY_CATEGORY_NOTIFICATIONS = "notificationsCategory";
	public static final String PREFERENCES_KEY_FIRST_RUN_EXECUTED = "firstRunExecuted";
	public static final String PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE = "resumeOnBluetoothDevice";
	public static final String PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE = "pauseOnBluetoothDevice";
	public static final String PREFERENCES_KEY_SINGLE_BUTTON_PLAY_PAUSE = "singleButtonPlayPause";
	public static final String PREFERENCES_KEY_DEBUG_LOG_TO_FILE = "debugLogToFile";

	public static final int PREFERENCE_VALUE_ALL = 0;
	public static final int PREFERENCE_VALUE_A2DP = 1;
	public static final int PREFERENCE_VALUE_DISABLED = 2;

	public static final String FILENAME_DOWNLOADS_SER = "downloadstate.ser";

	public static final String ALBUM_ART_FILE = "folder.jpeg";
	public static final String STARRED = "starred";
	public static final String ALPHABETICAL_BY_NAME = "alphabeticalByName";
	public static final int RESULT_CLOSE_ALL = 1337;

	private Constants()
	{
	}
}
