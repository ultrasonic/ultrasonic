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

import java.io.InputStream;
import java.util.List;

import kotlin.Pair;

/**
 * @author Sindre Mehus
 */
public interface MusicService
{

	void ping() throws Exception;

	boolean isLicenseValid() throws Exception;

	List<Genre> getGenres(boolean refresh) throws Exception;

	void star(String id, String albumId, String artistId) throws Exception;

	void unstar(String id, String albumId, String artistId) throws Exception;

	void setRating(String id, int rating) throws Exception;

	List<MusicFolder> getMusicFolders(boolean refresh) throws Exception;

	Indexes getIndexes(String musicFolderId, boolean refresh) throws Exception;

	Indexes getArtists(boolean refresh) throws Exception;

	MusicDirectory getMusicDirectory(String id, String name, boolean refresh) throws Exception;

	MusicDirectory getArtist(String id, String name, boolean refresh) throws Exception;

	MusicDirectory getAlbum(String id, String name, boolean refresh) throws Exception;

	SearchResult search(SearchCriteria criteria) throws Exception;

	MusicDirectory getPlaylist(String id, String name) throws Exception;

	List<PodcastsChannel> getPodcastsChannels(boolean refresh) throws Exception;

	List<Playlist> getPlaylists(boolean refresh) throws Exception;

	void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries) throws Exception;

	void deletePlaylist(String id) throws Exception;

	void updatePlaylist(String id, String name, String comment, boolean pub) throws Exception;

	Lyrics getLyrics(String artist, String title) throws Exception;

	void scrobble(String id, boolean submission) throws Exception;

	MusicDirectory getAlbumList(String type, int size, int offset, String musicFolderId) throws Exception;

	MusicDirectory getAlbumList2(String type, int size, int offset, String musicFolderId) throws Exception;

	MusicDirectory getRandomSongs(int size) throws Exception;

	MusicDirectory getSongsByGenre(String genre, int count, int offset) throws Exception;

	SearchResult getStarred() throws Exception;

	SearchResult getStarred2() throws Exception;

	Bitmap getCoverArt(MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality) throws Exception;

	Bitmap getAvatar(String username, int size, boolean saveToFile, boolean highQuality) throws Exception;

		/**
	 * Return response {@link InputStream} and a {@link Boolean} that indicates if this response is
	 * partial.
	 */
	Pair<InputStream, Boolean> getDownloadInputStream(MusicDirectory.Entry song, long offset, int maxBitrate) throws Exception;

	// TODO: Refactor and remove this call (see RestMusicService implementation)
	String getVideoUrl(String id, boolean useFlash) throws Exception;

	JukeboxStatus updateJukeboxPlaylist(List<String> ids) throws Exception;

	JukeboxStatus skipJukebox(int index, int offsetSeconds) throws Exception;

	JukeboxStatus stopJukebox() throws Exception;

	JukeboxStatus startJukebox() throws Exception;

	JukeboxStatus getJukeboxStatus() throws Exception;

	JukeboxStatus setJukeboxGain(float gain) throws Exception;

	List<Share> getShares(boolean refresh) throws Exception;

	List<ChatMessage> getChatMessages(Long since) throws Exception;

	void addChatMessage(String message) throws Exception;

	List<Bookmark> getBookmarks() throws Exception;

	void deleteBookmark(String id) throws Exception;

	void createBookmark(String id, int position) throws Exception;

	MusicDirectory getVideos(boolean refresh) throws Exception;

	UserInfo getUser(String username) throws Exception;

	List<Share> createShare(List<String> ids, String description, Long expires) throws Exception;

	void deleteShare(String id) throws Exception;

	void updateShare(String id, String description, Long expires) throws Exception;

	MusicDirectory getPodcastEpisodes(String podcastChannelId) throws Exception;
}