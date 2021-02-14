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
package org.moire.ultrasonic.service;

import android.content.Context;
import android.graphics.Bitmap;

import org.moire.ultrasonic.domain.Bookmark;
import org.moire.ultrasonic.domain.ChatMessage;
import org.moire.ultrasonic.domain.Genre;
import org.moire.ultrasonic.domain.Indexes;
import org.moire.ultrasonic.domain.JukeboxStatus;
import org.moire.ultrasonic.domain.Lyrics;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.MusicFolder;
import org.moire.ultrasonic.domain.Playlist;
import org.moire.ultrasonic.domain.PodcastsChannel;
import org.moire.ultrasonic.domain.SearchCriteria;
import org.moire.ultrasonic.domain.SearchResult;
import org.moire.ultrasonic.domain.Share;
import org.moire.ultrasonic.domain.UserInfo;
import org.moire.ultrasonic.util.CancellableTask;

import java.io.InputStream;
import java.util.List;

import kotlin.Pair;

/**
 * @author Sindre Mehus
 */
public interface MusicService
{

	void ping(Context context) throws Exception;

	boolean isLicenseValid(Context context) throws Exception;

	List<Genre> getGenres(boolean refresh, Context context) throws Exception;

	void star(String id, String albumId, String artistId, Context context) throws Exception;

	void unstar(String id, String albumId, String artistId, Context context) throws Exception;

	void setRating(String id, int rating, Context context) throws Exception;

	List<MusicFolder> getMusicFolders(boolean refresh, Context context) throws Exception;

	Indexes getIndexes(String musicFolderId, boolean refresh, Context context) throws Exception;

	Indexes getArtists(boolean refresh, Context context) throws Exception;

	MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context) throws Exception;

	MusicDirectory getArtist(String id, String name, boolean refresh, Context context) throws Exception;

	MusicDirectory getAlbum(String id, String name, boolean refresh, Context context) throws Exception;

	SearchResult search(SearchCriteria criteria, Context context) throws Exception;

	MusicDirectory getPlaylist(String id, String name, Context context) throws Exception;

	List<PodcastsChannel> getPodcastsChannels(boolean refresh, Context context) throws Exception;

	List<Playlist> getPlaylists(boolean refresh, Context context) throws Exception;

	void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context) throws Exception;

	void deletePlaylist(String id, Context context) throws Exception;

	void updatePlaylist(String id, String name, String comment, boolean pub, Context context) throws Exception;

	Lyrics getLyrics(String artist, String title, Context context) throws Exception;

	void scrobble(String id, boolean submission, Context context) throws Exception;

	MusicDirectory getAlbumList(String type, int size, int offset, Context context) throws Exception;

	MusicDirectory getAlbumList2(String type, int size, int offset, Context context) throws Exception;

	MusicDirectory getRandomSongs(int size, Context context) throws Exception;

	MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context) throws Exception;

	SearchResult getStarred(Context context) throws Exception;

	SearchResult getStarred2(Context context) throws Exception;

	Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality) throws Exception;

	/**
	 * Return response {@link InputStream} and a {@link Boolean} that indicates if this response is
	 * partial.
	 */
	Pair<InputStream, Boolean> getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, CancellableTask task) throws Exception;

	@Deprecated String getVideoUrl(Context context, String id, boolean useFlash) throws Exception;

	JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context) throws Exception;

	JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context) throws Exception;

	JukeboxStatus stopJukebox(Context context) throws Exception;

	JukeboxStatus startJukebox(Context context) throws Exception;

	JukeboxStatus getJukeboxStatus(Context context) throws Exception;

	JukeboxStatus setJukeboxGain(float gain, Context context) throws Exception;

	List<Share> getShares(boolean refresh, Context context) throws Exception;

	List<ChatMessage> getChatMessages(Long since, Context context) throws Exception;

	void addChatMessage(String message, Context context) throws Exception;

	List<Bookmark> getBookmarks(Context context) throws Exception;

	void deleteBookmark(String id, Context context) throws Exception;

	void createBookmark(String id, int position, Context context) throws Exception;

	MusicDirectory getVideos(boolean refresh, Context context) throws Exception;

	UserInfo getUser(String username, Context context) throws Exception;

	List<Share> createShare(List<String> ids, String description, Long expires, Context context) throws Exception;

	void deleteShare(String id, Context context) throws Exception;

	void updateShare(String id, String description, Long expires, Context context) throws Exception;

	Bitmap getAvatar(Context context, String username, int size, boolean saveToFile, boolean highQuality) throws Exception;

	MusicDirectory getPodcastEpisodes(String podcastChannelId, Context context) throws Exception;
}