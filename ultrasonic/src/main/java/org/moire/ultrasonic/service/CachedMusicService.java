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

import org.moire.ultrasonic.data.ActiveServerProvider;
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
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.LRUCache;
import org.moire.ultrasonic.util.TimeLimitedCache;
import org.moire.ultrasonic.util.Util;

import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kotlin.Lazy;
import kotlin.Pair;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus
 */
public class CachedMusicService implements MusicService
{
	private final Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);

	private static final int MUSIC_DIR_CACHE_SIZE = 100;

	private final MusicService musicService;
	private final LRUCache<String, TimeLimitedCache<MusicDirectory>> cachedMusicDirectories;
	private final LRUCache<String, TimeLimitedCache<MusicDirectory>> cachedArtist;
	private final LRUCache<String, TimeLimitedCache<MusicDirectory>> cachedAlbum;
	private final LRUCache<String, TimeLimitedCache<UserInfo>> cachedUserInfo;
	private final TimeLimitedCache<Boolean> cachedLicenseValid = new TimeLimitedCache<>(120, TimeUnit.SECONDS);
	private final TimeLimitedCache<Indexes> cachedIndexes = new TimeLimitedCache<>(60 * 60, TimeUnit.SECONDS);
	private final TimeLimitedCache<Indexes> cachedArtists = new TimeLimitedCache<>(60 * 60, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<Playlist>> cachedPlaylists = new TimeLimitedCache<>(3600, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<PodcastsChannel>> cachedPodcastsChannels = new TimeLimitedCache<>(3600, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<MusicFolder>> cachedMusicFolders = new TimeLimitedCache<>(10 * 3600, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<Genre>> cachedGenres = new TimeLimitedCache<>(10 * 3600, TimeUnit.SECONDS);

	private String restUrl;
	private String cachedMusicFolderId;

	public CachedMusicService(MusicService musicService)
	{
		this.musicService = musicService;
		cachedMusicDirectories = new LRUCache<>(MUSIC_DIR_CACHE_SIZE);
		cachedArtist = new LRUCache<>(MUSIC_DIR_CACHE_SIZE);
		cachedAlbum = new LRUCache<>(MUSIC_DIR_CACHE_SIZE);
		cachedUserInfo = new LRUCache<>(MUSIC_DIR_CACHE_SIZE);
	}

	@Override
	public void ping() throws Exception
	{
		checkSettingsChanged();
		musicService.ping();
	}

	@Override
	public boolean isLicenseValid() throws Exception
	{
		checkSettingsChanged();
		Boolean result = cachedLicenseValid.get();
		if (result == null)
		{
			result = musicService.isLicenseValid();
			cachedLicenseValid.set(result, result ? 30L * 60L : 2L * 60L, TimeUnit.SECONDS);
		}
		return result;
	}

	@Override
	public List<MusicFolder> getMusicFolders(boolean refresh) throws Exception
	{
		checkSettingsChanged();
		if (refresh)
		{
			cachedMusicFolders.clear();
		}
		List<MusicFolder> result = cachedMusicFolders.get();
		if (result == null)
		{
			result = musicService.getMusicFolders(refresh);
			cachedMusicFolders.set(result);
		}
		return result;
	}

	@Override
	public Indexes getIndexes(String musicFolderId, boolean refresh) throws Exception
	{
		checkSettingsChanged();
		if (refresh)
		{
			cachedIndexes.clear();
			cachedMusicFolders.clear();
			cachedMusicDirectories.clear();
		}
		Indexes result = cachedIndexes.get();
		if (result == null)
		{
			result = musicService.getIndexes(musicFolderId, refresh);
			cachedIndexes.set(result);
		}
		return result;
	}

	@Override
	public Indexes getArtists(boolean refresh) throws Exception
	{
		checkSettingsChanged();
		if (refresh)
		{
			cachedArtists.clear();
		}
		Indexes result = cachedArtists.get();
		if (result == null)
		{
			result = musicService.getArtists(refresh);
			cachedArtists.set(result);
		}
		return result;
	}

	@Override
	public MusicDirectory getMusicDirectory(String id, String name, boolean refresh) throws Exception
	{
		checkSettingsChanged();
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedMusicDirectories.get(id);

		MusicDirectory dir = cache == null ? null : cache.get();

		if (dir == null)
		{
			dir = musicService.getMusicDirectory(id, name, refresh);
			cache = new TimeLimitedCache<>(Util.getDirectoryCacheTime(), TimeUnit.SECONDS);
			cache.set(dir);
			cachedMusicDirectories.put(id, cache);
		}
		return dir;
	}

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh) throws Exception
	{
		checkSettingsChanged();
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedArtist.get(id);
		MusicDirectory dir = cache == null ? null : cache.get();
		if (dir == null)
		{
			dir = musicService.getArtist(id, name, refresh);
			cache = new TimeLimitedCache<>(Util.getDirectoryCacheTime(), TimeUnit.SECONDS);
			cache.set(dir);
			cachedArtist.put(id, cache);
		}
		return dir;
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh) throws Exception
	{
		checkSettingsChanged();
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedAlbum.get(id);
		MusicDirectory dir = cache == null ? null : cache.get();
		if (dir == null)
		{
			dir = musicService.getAlbum(id, name, refresh);
			cache = new TimeLimitedCache<>(Util.getDirectoryCacheTime(), TimeUnit.SECONDS);
			cache.set(dir);
			cachedAlbum.put(id, cache);
		}
		return dir;
	}

	@Override
	public SearchResult search(SearchCriteria criteria) throws Exception
	{
		return musicService.search(criteria);
	}

	@Override
	public MusicDirectory getPlaylist(String id, String name) throws Exception
	{
		return musicService.getPlaylist(id, name);
	}

	@Override
	public List<PodcastsChannel> getPodcastsChannels(boolean refresh) throws Exception {
		checkSettingsChanged();
		List<PodcastsChannel> result = refresh ? null : cachedPodcastsChannels.get();
		if (result == null)
		{
			result = musicService.getPodcastsChannels(refresh);
			cachedPodcastsChannels.set(result);
		}
		return result;
	}

	@Override
	public MusicDirectory getPodcastEpisodes(String podcastChannelId) throws Exception {
		return musicService.getPodcastEpisodes(podcastChannelId);
	}


	@Override
	public List<Playlist> getPlaylists(boolean refresh) throws Exception
	{
		checkSettingsChanged();
		List<Playlist> result = refresh ? null : cachedPlaylists.get();
		if (result == null)
		{
			result = musicService.getPlaylists(refresh);
			cachedPlaylists.set(result);
		}
		return result;
	}

	@Override
	public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries) throws Exception
	{
		cachedPlaylists.clear();
		musicService.createPlaylist(id, name, entries);
	}

	@Override
	public void deletePlaylist(String id) throws Exception
	{
		musicService.deletePlaylist(id);
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub) throws Exception
	{
		musicService.updatePlaylist(id, name, comment, pub);
	}

	@Override
	public Lyrics getLyrics(String artist, String title) throws Exception
	{
		return musicService.getLyrics(artist, title);
	}

	@Override
	public void scrobble(String id, boolean submission) throws Exception
	{
		musicService.scrobble(id, submission);
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, String musicFolderId) throws Exception
	{
		return musicService.getAlbumList(type, size, offset, musicFolderId);
	}

	@Override
	public MusicDirectory getAlbumList2(String type, int size, int offset, String musicFolderId) throws Exception
	{
		return musicService.getAlbumList2(type, size, offset, musicFolderId);
	}

	@Override
	public MusicDirectory getRandomSongs(int size) throws Exception
	{
		return musicService.getRandomSongs(size);
	}

	@Override
	public SearchResult getStarred() throws Exception
	{
		return musicService.getStarred();
	}

	@Override
	public SearchResult getStarred2() throws Exception
	{
		return musicService.getStarred2();
	}

	@Override
	public Bitmap getCoverArt(MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality) throws Exception
	{
		return musicService.getCoverArt(entry, size, saveToFile, highQuality);
	}

	@Override
	public Pair<InputStream, Boolean> getDownloadInputStream(MusicDirectory.Entry song, long offset, int maxBitrate) throws Exception
	{
		return musicService.getDownloadInputStream(song, offset, maxBitrate);
	}

	@Override
	public String getVideoUrl(String id, boolean useFlash) throws Exception
	{
		return musicService.getVideoUrl(id, useFlash);
	}

	@Override
	public JukeboxStatus updateJukeboxPlaylist(List<String> ids) throws Exception
	{
		return musicService.updateJukeboxPlaylist(ids);
	}

	@Override
	public JukeboxStatus skipJukebox(int index, int offsetSeconds) throws Exception
	{
		return musicService.skipJukebox(index, offsetSeconds);
	}

	@Override
	public JukeboxStatus stopJukebox() throws Exception
	{
		return musicService.stopJukebox();
	}

	@Override
	public JukeboxStatus startJukebox() throws Exception
	{
		return musicService.startJukebox();
	}

	@Override
	public JukeboxStatus getJukeboxStatus() throws Exception
	{
		return musicService.getJukeboxStatus();
	}

	@Override
	public JukeboxStatus setJukeboxGain(float gain) throws Exception
	{
		return musicService.setJukeboxGain(gain);
	}

	private void checkSettingsChanged()
	{
		String newUrl = activeServerProvider.getValue().getRestUrl(null);
		String newFolderId = activeServerProvider.getValue().getActiveServer().getMusicFolderId();
		if (!Util.equals(newUrl, restUrl) || !Util.equals(cachedMusicFolderId,newFolderId))
		{
			cachedMusicFolders.clear();
			cachedMusicDirectories.clear();
			cachedLicenseValid.clear();
			cachedIndexes.clear();
			cachedPlaylists.clear();
			cachedGenres.clear();
			cachedAlbum.clear();
			cachedArtist.clear();
			cachedUserInfo.clear();
			restUrl = newUrl;
			cachedMusicFolderId = newFolderId;
		}
	}

	@Override
	public void star(String id, String albumId, String artistId) throws Exception
	{
		musicService.star(id, albumId, artistId);
	}

	@Override
	public void unstar(String id, String albumId, String artistId) throws Exception
	{
		musicService.unstar(id, albumId, artistId);
	}

	@Override
	public void setRating(String id, int rating) throws Exception
	{
		musicService.setRating(id, rating);
	}

	@Override
	public List<Genre> getGenres(boolean refresh) throws Exception
	{
		checkSettingsChanged();
		if (refresh)
		{
			cachedGenres.clear();
		}
		List<Genre> result = cachedGenres.get();

		if (result == null)
		{
			result = musicService.getGenres(refresh);
			cachedGenres.set(result);
		}

		Collections.sort(result, new Comparator<Genre>()
		{
			@Override
			public int compare(Genre genre, Genre genre2)
			{
				return genre.getName().compareToIgnoreCase(genre2.getName());
			}
		});

		return result;
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset) throws Exception
	{
		return musicService.getSongsByGenre(genre, count, offset);
	}

	@Override
	public List<Share> getShares(boolean refresh) throws Exception
	{
		return musicService.getShares(refresh);
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since) throws Exception
	{
		return musicService.getChatMessages(since);
	}

	@Override
	public void addChatMessage(String message) throws Exception
	{
		musicService.addChatMessage(message);
	}

	@Override
	public List<Bookmark> getBookmarks() throws Exception
	{
		return musicService.getBookmarks();
	}

	@Override
	public void deleteBookmark(String id) throws Exception
	{
		musicService.deleteBookmark(id);
	}

	@Override
	public void createBookmark(String id, int position) throws Exception
	{
		musicService.createBookmark(id, position);
	}

	@Override
	public MusicDirectory getVideos(boolean refresh) throws Exception
	{
		checkSettingsChanged();
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedMusicDirectories.get(Constants.INTENT_EXTRA_NAME_VIDEOS);

		MusicDirectory dir = cache == null ? null : cache.get();

		if (dir == null)
		{
			dir = musicService.getVideos(refresh);
			cache = new TimeLimitedCache<>(Util.getDirectoryCacheTime(), TimeUnit.SECONDS);
			cache.set(dir);
			cachedMusicDirectories.put(Constants.INTENT_EXTRA_NAME_VIDEOS, cache);
		}

		return dir;
	}

	@Override
	public UserInfo getUser(String username) throws Exception
	{
		checkSettingsChanged();

		TimeLimitedCache<UserInfo> cache = cachedUserInfo.get(username);

		UserInfo userInfo = cache == null ? null : cache.get();

		if (userInfo == null)
		{
			userInfo = musicService.getUser(username);
			cache = new TimeLimitedCache<>(Util.getDirectoryCacheTime(), TimeUnit.SECONDS);
			cache.set(userInfo);
			cachedUserInfo.put(username, cache);
		}

		return userInfo;
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires) throws Exception
	{
		return musicService.createShare(ids, description, expires);
	}

	@Override
	public void deleteShare(String id) throws Exception
	{
		musicService.deleteShare(id);
	}

	@Override
	public void updateShare(String id, String description, Long expires) throws Exception
	{
		musicService.updateShare(id, description, expires);
	}

	@Override
	public Bitmap getAvatar(String username, int size, boolean saveToFile, boolean highQuality) throws Exception
	{
		return musicService.getAvatar(username, size, saveToFile, highQuality);
	}
}
