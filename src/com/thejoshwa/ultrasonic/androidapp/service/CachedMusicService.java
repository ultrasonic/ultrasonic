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
package com.thejoshwa.ultrasonic.androidapp.service;

import android.content.Context;
import android.graphics.Bitmap;

import com.thejoshwa.ultrasonic.androidapp.domain.Bookmark;
import com.thejoshwa.ultrasonic.androidapp.domain.ChatMessage;
import com.thejoshwa.ultrasonic.androidapp.domain.Genre;
import com.thejoshwa.ultrasonic.androidapp.domain.Indexes;
import com.thejoshwa.ultrasonic.androidapp.domain.JukeboxStatus;
import com.thejoshwa.ultrasonic.androidapp.domain.Lyrics;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicFolder;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchCriteria;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchResult;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;
import com.thejoshwa.ultrasonic.androidapp.domain.UserInfo;
import com.thejoshwa.ultrasonic.androidapp.domain.Version;
import com.thejoshwa.ultrasonic.androidapp.util.CancellableTask;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.LRUCache;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;
import com.thejoshwa.ultrasonic.androidapp.util.TimeLimitedCache;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import org.apache.http.HttpResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Sindre Mehus
 */
public class CachedMusicService implements MusicService
{

	private static final int MUSIC_DIR_CACHE_SIZE = 100;

	private final MusicService musicService;
	private final LRUCache<String, TimeLimitedCache<MusicDirectory>> cachedMusicDirectories;
	private final LRUCache<String, TimeLimitedCache<MusicDirectory>> cachedArtist;
	private final LRUCache<String, TimeLimitedCache<MusicDirectory>> cachedAlbum;
	private final LRUCache<String, TimeLimitedCache<UserInfo>> cachedUserInfo;
	private final TimeLimitedCache<Boolean> cachedLicenseValid = new TimeLimitedCache<Boolean>(120, TimeUnit.SECONDS);
	private final TimeLimitedCache<Indexes> cachedIndexes = new TimeLimitedCache<Indexes>(60 * 60, TimeUnit.SECONDS);
	private final TimeLimitedCache<Indexes> cachedArtists = new TimeLimitedCache<Indexes>(60 * 60, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<Playlist>> cachedPlaylists = new TimeLimitedCache<List<Playlist>>(3600, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<MusicFolder>> cachedMusicFolders = new TimeLimitedCache<List<MusicFolder>>(10 * 3600, TimeUnit.SECONDS);
	private final TimeLimitedCache<List<Genre>> cachedGenres = new TimeLimitedCache<List<Genre>>(10 * 3600, TimeUnit.SECONDS);

	private String restUrl;

	public CachedMusicService(MusicService musicService)
	{
		this.musicService = musicService;
		cachedMusicDirectories = new LRUCache<String, TimeLimitedCache<MusicDirectory>>(MUSIC_DIR_CACHE_SIZE);
		cachedArtist = new LRUCache<String, TimeLimitedCache<MusicDirectory>>(MUSIC_DIR_CACHE_SIZE);
		cachedAlbum = new LRUCache<String, TimeLimitedCache<MusicDirectory>>(MUSIC_DIR_CACHE_SIZE);
		cachedUserInfo = new LRUCache<String, TimeLimitedCache<UserInfo>>(MUSIC_DIR_CACHE_SIZE);
	}

	@Override
	public void ping(Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		musicService.ping(context, progressListener);
	}

	@Override
	public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		Boolean result = cachedLicenseValid.get();
		if (result == null)
		{
			result = musicService.isLicenseValid(context, progressListener);
			cachedLicenseValid.set(result, result ? 30L * 60L : 2L * 60L, TimeUnit.SECONDS);
		}
		return result;
	}

	@Override
	public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		if (refresh)
		{
			cachedMusicFolders.clear();
		}
		List<MusicFolder> result = cachedMusicFolders.get();
		if (result == null)
		{
			result = musicService.getMusicFolders(refresh, context, progressListener);
			cachedMusicFolders.set(result);
		}
		return result;
	}

	@Override
	public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		if (refresh)
		{
			cachedIndexes.clear();
			cachedMusicFolders.clear();
			cachedMusicDirectories.clear();
		}
		Indexes result = cachedIndexes.get();
		if (result == null)
		{
			result = musicService.getIndexes(musicFolderId, refresh, context, progressListener);
			cachedIndexes.set(result);
		}
		return result;
	}

	@Override
	public Indexes getArtists(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		if (refresh)
		{
			cachedArtists.clear();
		}
		Indexes result = cachedArtists.get();
		if (result == null)
		{
			result = musicService.getArtists(refresh, context, progressListener);
			cachedArtists.set(result);
		}
		return result;
	}

	@Override
	public MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedMusicDirectories.get(id);

		MusicDirectory dir = cache == null ? null : cache.get();

		if (dir == null)
		{
			dir = musicService.getMusicDirectory(id, name, refresh, context, progressListener);
			cache = new TimeLimitedCache<MusicDirectory>(Util.getDirectoryCacheTime(context), TimeUnit.SECONDS);
			cache.set(dir);
			cachedMusicDirectories.put(id, cache);
		}
		return dir;
	}

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedArtist.get(id);
		MusicDirectory dir = cache == null ? null : cache.get();
		if (dir == null)
		{
			dir = musicService.getArtist(id, name, refresh, context, progressListener);
			cache = new TimeLimitedCache<MusicDirectory>(Util.getDirectoryCacheTime(context), TimeUnit.SECONDS);
			cache.set(dir);
			cachedArtist.put(id, cache);
		}
		return dir;
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedAlbum.get(id);
		MusicDirectory dir = cache == null ? null : cache.get();
		if (dir == null)
		{
			dir = musicService.getAlbum(id, name, refresh, context, progressListener);
			cache = new TimeLimitedCache<MusicDirectory>(Util.getDirectoryCacheTime(context), TimeUnit.SECONDS);
			cache.set(dir);
			cachedAlbum.put(id, cache);
		}
		return dir;
	}

	@Override
	public SearchResult search(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.search(criteria, context, progressListener);
	}

	@Override
	public MusicDirectory getPlaylist(String id, String name, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getPlaylist(id, name, context, progressListener);
	}

	@Override
	public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		List<Playlist> result = refresh ? null : cachedPlaylists.get();
		if (result == null)
		{
			result = musicService.getPlaylists(refresh, context, progressListener);
			cachedPlaylists.set(result);
		}
		return result;
	}

	@Override
	public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception
	{
		cachedPlaylists.clear();
		musicService.createPlaylist(id, name, entries, context, progressListener);
	}

	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.deletePlaylist(id, context, progressListener);
	}

	@Override
	public void updatePlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.updatePlaylist(id, toAdd, context, progressListener);
	}

	@Override
	public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.removeFromPlaylist(id, toRemove, context, progressListener);
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.updatePlaylist(id, name, comment, pub, context, progressListener);
	}

	@Override
	public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getLyrics(artist, title, context, progressListener);
	}

	@Override
	public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.scrobble(id, submission, context, progressListener);
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getAlbumList(type, size, offset, context, progressListener);
	}

	@Override
	public MusicDirectory getAlbumList2(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getAlbumList2(type, size, offset, context, progressListener);
	}

	@Override
	public MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getRandomSongs(size, context, progressListener);
	}

	@Override
	public SearchResult getStarred(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getStarred(context, progressListener);
	}

	@Override
	public SearchResult getStarred2(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getStarred2(context, progressListener);
	}

	@Override
	public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		return musicService.getCoverArt(context, entry, size, saveToFile, highQuality, progressListener);
	}

	@Override
	public HttpResponse getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, CancellableTask task) throws Exception
	{
		return musicService.getDownloadInputStream(context, song, offset, maxBitrate, task);
	}

	@Override
	public Version getLocalVersion(Context context) throws Exception
	{
		return musicService.getLocalVersion(context);
	}

	@Override
	public Version getLatestVersion(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getLatestVersion(context, progressListener);
	}

	@Override
	public String getVideoUrl(Context context, String id, boolean useFlash) throws Exception
	{
		return musicService.getVideoUrl(context, id, useFlash);
	}

	@Override
	public String getVideoStreamUrl(int maxBitrate, Context context, String id)
	{
		return musicService.getVideoStreamUrl(maxBitrate, context, id);
	}

	@Override
	public JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.updateJukeboxPlaylist(ids, context, progressListener);
	}

	@Override
	public JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.skipJukebox(index, offsetSeconds, context, progressListener);
	}

	@Override
	public JukeboxStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.stopJukebox(context, progressListener);
	}

	@Override
	public JukeboxStatus startJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.startJukebox(context, progressListener);
	}

	@Override
	public JukeboxStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getJukeboxStatus(context, progressListener);
	}

	@Override
	public JukeboxStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.setJukeboxGain(gain, context, progressListener);
	}

	private void checkSettingsChanged(Context context)
	{
		String newUrl = Util.getRestUrl(context, null);
		if (!Util.equals(newUrl, restUrl))
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
		}
	}

	@Override
	public void star(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.star(id, albumId, artistId, context, progressListener);

	}

	@Override
	public void unstar(String id, String albumId, String artistId, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.unstar(id, albumId, artistId, context, progressListener);
	}

	@Override
	public List<Genre> getGenres(Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		List<Genre> result = cachedGenres.get();

		if (result == null)
		{
			result = musicService.getGenres(context, progressListener);
			cachedGenres.set(result);
		}

		return result;
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getSongsByGenre(genre, count, offset, context, progressListener);
	}

	@Override
	public List<Share> getShares(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getShares(context, progressListener);
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getChatMessages(since, context, progressListener);
	}

	@Override
	public void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.addChatMessage(message, context, progressListener);
	}

	@Override
	public List<Bookmark> getBookmarks(Context context, ProgressListener progressListener) throws Exception
	{
		return musicService.getBookmarks(context, progressListener);
	}

	@Override
	public void deleteBookmark(String id, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.deleteBookmark(id, context, progressListener);
	}

	@Override
	public void createBookmark(String id, int position, Context context, ProgressListener progressListener) throws Exception
	{
		musicService.createBookmark(id, position, context, progressListener);
	}

	@Override
	public MusicDirectory getVideos(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);
		TimeLimitedCache<MusicDirectory> cache = refresh ? null : cachedMusicDirectories.get(Constants.INTENT_EXTRA_NAME_VIDEOS);

		MusicDirectory dir = cache == null ? null : cache.get();

		if (dir == null)
		{
			dir = musicService.getVideos(refresh, context, progressListener);
			cache = new TimeLimitedCache<MusicDirectory>(Util.getDirectoryCacheTime(context), TimeUnit.SECONDS);
			cache.set(dir);
			cachedMusicDirectories.put(Constants.INTENT_EXTRA_NAME_VIDEOS, cache);
		}

		return dir;
	}

	@Override
	public UserInfo getUser(String username, Context context, ProgressListener progressListener) throws Exception
	{
		checkSettingsChanged(context);

		TimeLimitedCache<UserInfo> cache = cachedUserInfo.get(username);

		UserInfo userInfo = cache == null ? null : cache.get();

		if (userInfo == null)
		{
			userInfo = musicService.getUser(username, context, progressListener);
			cache = new TimeLimitedCache<UserInfo>(Util.getDirectoryCacheTime(context), TimeUnit.SECONDS);
			cache.set(userInfo);
			cachedUserInfo.put(username, cache);
		}

		return userInfo;
	}
}
