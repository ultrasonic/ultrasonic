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
import android.media.MediaMetadataRetriever;

import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.Artist;
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
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import kotlin.Lazy;
import kotlin.Pair;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus
 */
public class OfflineMusicService implements MusicService
{
	private static final Pattern COMPILE = Pattern.compile(" ");
	private final Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);

	@Override
	public Indexes getIndexes(String musicFolderId, boolean refresh)
	{
		List<Artist> artists = new ArrayList<>();
		File root = FileUtil.getMusicDirectory();
		for (File file : FileUtil.listFiles(root))
		{
			if (file.isDirectory())
			{
				Artist artist = new Artist();
				artist.setId(file.getPath());
				artist.setIndex(file.getName().substring(0, 1));
				artist.setName(file.getName());
				artists.add(artist);
			}
		}

		String ignoredArticlesString = "The El La Los Las Le Les";
		final String[] ignoredArticles = COMPILE.split(ignoredArticlesString);

		Collections.sort(artists, (lhsArtist, rhsArtist) -> {
			String lhs = lhsArtist.getName().toLowerCase();
			String rhs = rhsArtist.getName().toLowerCase();

			char lhs1 = lhs.charAt(0);
			char rhs1 = rhs.charAt(0);

			if (Character.isDigit(lhs1) && !Character.isDigit(rhs1))
			{
				return 1;
			}

			if (Character.isDigit(rhs1) && !Character.isDigit(lhs1))
			{
				return -1;
			}

			for (String article : ignoredArticles)
			{
				int index = lhs.indexOf(String.format("%s ", article.toLowerCase()));

				if (index == 0)
				{
					lhs = lhs.substring(article.length() + 1);
				}

				index = rhs.indexOf(String.format("%s ", article.toLowerCase()));

				if (index == 0)
				{
					rhs = rhs.substring(article.length() + 1);
				}
			}

			return lhs.compareTo(rhs);
		});

		return new Indexes(0L, ignoredArticlesString, Collections.emptyList(), artists);
	}

	@Override
	public MusicDirectory getMusicDirectory(String id, String artistName, boolean refresh)
	{
		File dir = new File(id);
		MusicDirectory result = new MusicDirectory();
		result.setName(dir.getName());

		Collection<String> names = new HashSet<>();

		for (File file : FileUtil.listMediaFiles(dir))
		{
			String name = getName(file);
			if (name != null & !names.contains(name))
			{
				names.add(name);
				result.addChild(createEntry(file, name));
			}
		}

		return result;
	}

	private static String getName(File file)
	{
		String name = file.getName();

		if (file.isDirectory())
		{
			return name;
		}

		if (name.endsWith(".partial") || name.contains(".partial.") || name.equals(Constants.ALBUM_ART_FILE))
		{
			return null;
		}

		name = name.replace(".complete", "");
		return FileUtil.getBaseName(name);
	}

	private static MusicDirectory.Entry createEntry(File file, String name)
	{
		MusicDirectory.Entry entry = new MusicDirectory.Entry();
		entry.setDirectory(file.isDirectory());
		entry.setId(file.getPath());
		entry.setParent(file.getParent());
		entry.setSize(file.length());
		String root = FileUtil.getMusicDirectory().getPath();
		entry.setPath(file.getPath().replaceFirst(String.format("^%s/", root), ""));
		entry.setTitle(name);

		if (file.isFile())
		{
			String artist = null;
			String album = null;
			String title = null;
			String track = null;
			String disc = null;
			String year = null;
			String genre = null;
			String duration = null;
			String hasVideo = null;

			try
			{
				MediaMetadataRetriever mmr = new MediaMetadataRetriever();
				mmr.setDataSource(file.getPath());
				artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
				album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
				title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
				track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
				disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
				year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
				genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
				duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
				hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
				mmr.release();
			}
			catch (Exception ignored)
			{
			}

			entry.setArtist(artist != null ? artist : file.getParentFile().getParentFile().getName());
			entry.setAlbum(album != null ? album : file.getParentFile().getName());

			if (title != null)
			{
				entry.setTitle(title);
			}

			entry.setVideo(hasVideo != null);

			Timber.i("Offline Stuff: %s", track);

			if (track != null)
			{

				int trackValue = 0;

				try
				{
					int slashIndex = track.indexOf('/');

					if (slashIndex > 0)
					{
						track = track.substring(0, slashIndex);
					}

					trackValue = Integer.parseInt(track);
				}
				catch (Exception ex)
				{
					Timber.e(ex,"Offline Stuff");
				}

				Timber.i("Offline Stuff: Setting Track: %d", trackValue);

				entry.setTrack(trackValue);
			}

			if (disc != null)
			{
				int discValue = 0;

				try
				{
					int slashIndex = disc.indexOf('/');

					if (slashIndex > 0)
					{
						disc = disc.substring(0, slashIndex);
					}

					discValue = Integer.parseInt(disc);
				}
				catch (Exception ignored)
				{
				}

				entry.setDiscNumber(discValue);
			}

			if (year != null)
			{
				int yearValue = 0;

				try
				{
					yearValue = Integer.parseInt(year);
				}
				catch (Exception ignored)
				{
				}

				entry.setYear(yearValue);
			}

			if (genre != null)
			{
				entry.setGenre(genre);
			}

			if (duration != null)
			{
				long durationValue = 0;

				try
				{
					durationValue = Long.parseLong(duration);
					durationValue = TimeUnit.MILLISECONDS.toSeconds(durationValue);
				}
				catch (Exception ignored)
				{
				}

				entry.setDuration(durationValue);
			}
		}

		entry.setSuffix(FileUtil.getExtension(file.getName().replace(".complete", "")));

		File albumArt = FileUtil.getAlbumArtFile(entry);

		if (albumArt.exists())
		{
			entry.setCoverArt(albumArt.getPath());
		}

		return entry;
	}

	@Override
	public Bitmap getAvatar(String username, int size, boolean saveToFile, boolean highQuality)
	{
		try
		{
			Bitmap bitmap = FileUtil.getAvatarBitmap(username, size, highQuality);
			return Util.scaleBitmap(bitmap, size);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	@Override
	public Bitmap getCoverArt(MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality)
	{
		try
		{
			Bitmap bitmap = FileUtil.getAlbumArtBitmap(entry, size, highQuality);
			return Util.scaleBitmap(bitmap, size);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	@Override
	public SearchResult search(SearchCriteria criteria)
	{
		List<Artist> artists = new ArrayList<>();
		List<MusicDirectory.Entry> albums = new ArrayList<>();
		List<MusicDirectory.Entry> songs = new ArrayList<>();
		File root = FileUtil.getMusicDirectory();
		int closeness;

		for (File artistFile : FileUtil.listFiles(root))
		{
			String artistName = artistFile.getName();
			if (artistFile.isDirectory())
			{
				if ((closeness = matchCriteria(criteria, artistName)) > 0)
				{
					Artist artist = new Artist();
					artist.setId(artistFile.getPath());
					artist.setIndex(artistFile.getName().substring(0, 1));
					artist.setName(artistName);
					artist.setCloseness(closeness);
					artists.add(artist);
				}

				recursiveAlbumSearch(artistName, artistFile, criteria, albums, songs);
			}
		}

		Collections.sort(artists, (lhs, rhs) -> {
			if (lhs.getCloseness() == rhs.getCloseness())
			{
				return 0;
			}

			else return lhs.getCloseness() > rhs.getCloseness() ? -1 : 1;
		});

		Collections.sort(albums, (lhs, rhs) -> {
			if (lhs.getCloseness() == rhs.getCloseness())
			{
				return 0;
			}

			else return lhs.getCloseness() > rhs.getCloseness() ? -1 : 1;
		});

		Collections.sort(songs, (lhs, rhs) -> {
			if (lhs.getCloseness() == rhs.getCloseness())
			{
				return 0;
			}

			else return lhs.getCloseness() > rhs.getCloseness() ? -1 : 1;
		});

		return new SearchResult(artists, albums, songs);
	}

	private static void recursiveAlbumSearch(String artistName, File file, SearchCriteria criteria, List<MusicDirectory.Entry> albums, List<MusicDirectory.Entry> songs)
	{
		int closeness;

		for (File albumFile : FileUtil.listMediaFiles(file))
		{
			if (albumFile.isDirectory())
			{
				String albumName = getName(albumFile);
				if ((closeness = matchCriteria(criteria, albumName)) > 0)
				{
					MusicDirectory.Entry album = createEntry(albumFile, albumName);
					album.setArtist(artistName);
					album.setCloseness(closeness);
					albums.add(album);
				}

				for (File songFile : FileUtil.listMediaFiles(albumFile))
				{
					String songName = getName(songFile);

					if (songFile.isDirectory())
					{
						recursiveAlbumSearch(artistName, songFile, criteria, albums, songs);
					}
					else if ((closeness = matchCriteria(criteria, songName)) > 0)
					{
						MusicDirectory.Entry song = createEntry(albumFile, songName);
						song.setArtist(artistName);
						song.setAlbum(albumName);
						song.setCloseness(closeness);
						songs.add(song);
					}
				}
			}
			else
			{
				String songName = getName(albumFile);

				if ((closeness = matchCriteria(criteria, songName)) > 0)
				{
					MusicDirectory.Entry song = createEntry(albumFile, songName);
					song.setArtist(artistName);
					song.setAlbum(songName);
					song.setCloseness(closeness);
					songs.add(song);
				}
			}
		}
	}

	private static int matchCriteria(SearchCriteria criteria, String name)
	{
		String query = criteria.getQuery().toLowerCase();
		String[] queryParts = COMPILE.split(query);
		String[] nameParts = COMPILE.split(name.toLowerCase());

		int closeness = 0;

		for (String queryPart : queryParts)
		{
			for (String namePart : nameParts)
			{
				if (namePart.equals(queryPart))
				{
					closeness++;
				}
			}
		}

		return closeness;
	}

	@Override
	public List<Playlist> getPlaylists(boolean refresh)
	{
		List<Playlist> playlists = new ArrayList<>();
		File root = FileUtil.getPlaylistDirectory();
		String lastServer = null;
		boolean removeServer = true;
		for (File folder : FileUtil.listFiles(root))
		{
			if (folder.isDirectory())
			{
				String server = folder.getName();
				SortedSet<File> fileList = FileUtil.listFiles(folder);
				for (File file : fileList)
				{
					if (FileUtil.isPlaylistFile(file))
					{
						String id = file.getName();
						String filename = server + ": " + FileUtil.getBaseName(id);
						Playlist playlist = new Playlist(server, filename);
						playlists.add(playlist);
					}
				}

				if (!server.equals(lastServer) && !fileList.isEmpty())
				{
					if (lastServer != null)
					{
						removeServer = false;
					}
					lastServer = server;
				}
			}
			else
			{
				// Delete legacy playlist files
				try
				{
					if (!folder.delete()) {
						Timber.w("Failed to delete old playlist file: %s", folder.getName());
					}
				}
				catch (Exception e)
				{
					Timber.w(e, "Failed to delete old playlist file: %s", folder.getName());
				}
			}
		}

		if (removeServer)
		{
			for (Playlist playlist : playlists)
			{
				playlist.setName(playlist.getName().substring(playlist.getId().length() + 2));
			}
		}
		return playlists;
	}

	@Override
	public MusicDirectory getPlaylist(String id, String name) throws Exception
	{
		Reader reader = null;
		BufferedReader buffer = null;
		try
		{
			int firstIndex = name.indexOf(id);

			if (firstIndex != -1)
			{
				name = name.substring(id.length() + 2);
			}

			File playlistFile = FileUtil.getPlaylistFile(id, name);
			reader = new FileReader(playlistFile);
			buffer = new BufferedReader(reader);

			MusicDirectory playlist = new MusicDirectory();
			String line = buffer.readLine();
			if (!"#EXTM3U".equals(line)) return playlist;

			while ((line = buffer.readLine()) != null)
			{
				File entryFile = new File(line);
				String entryName = getName(entryFile);

				if (entryFile.exists() && entryName != null)
				{
					playlist.addChild(createEntry(entryFile, entryName));
				}
			}

			return playlist;
		}
		finally
		{
			Util.close(buffer);
			Util.close(reader);
		}
	}

	@Override
	public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries) throws Exception
	{
		File playlistFile = FileUtil.getPlaylistFile(activeServerProvider.getValue().getActiveServer().getName(), name);
		FileWriter fw = new FileWriter(playlistFile);
		BufferedWriter bw = new BufferedWriter(fw);
		try
		{
			fw.write("#EXTM3U\n");
			for (MusicDirectory.Entry e : entries)
			{
				String filePath = FileUtil.getSongFile(e).getAbsolutePath();
				if (!new File(filePath).exists())
				{
					String ext = FileUtil.getExtension(filePath);
					String base = FileUtil.getBaseName(filePath);
					filePath = base + ".complete." + ext;
				}
				fw.write(filePath + '\n');
			}
		}
		catch (Exception e)
		{
			Timber.w("Failed to save playlist: %s", name);
		}
		finally
		{
			bw.close();
			fw.close();
		}
	}


	@Override
	public MusicDirectory getRandomSongs(int size)
	{
		File root = FileUtil.getMusicDirectory();
		List<File> children = new LinkedList<>();
		listFilesRecursively(root, children);
		MusicDirectory result = new MusicDirectory();

		if (children.isEmpty())
		{
			return result;
		}

		Random random = new java.security.SecureRandom();
		for (int i = 0; i < size; i++)
		{
			File file = children.get(random.nextInt(children.size()));
			result.addChild(createEntry(file, getName(file)));
		}

		return result;
	}

	private static void listFilesRecursively(File parent, List<File> children)
	{
		for (File file : FileUtil.listMediaFiles(parent))
		{
			if (file.isFile())
			{
				children.add(file);
			}
			else
			{
				listFilesRecursively(file, children);
			}
		}
	}

	@Override
	public void deletePlaylist(String id) throws Exception
	{
		throw new OfflineException("Playlists not available in offline mode");
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub) throws Exception
	{
		throw new OfflineException("Updating playlist not available in offline mode");
	}

	@Override
	public Lyrics getLyrics(String artist, String title) throws Exception
	{
		throw new OfflineException("Lyrics not available in offline mode");
	}

	@Override
	public void scrobble(String id, boolean submission) throws Exception
	{
		throw new OfflineException("Scrobbling not available in offline mode");
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, String musicFolderId) throws Exception
	{
		throw new OfflineException("Album lists not available in offline mode");
	}

	@Override
	public JukeboxStatus updateJukeboxPlaylist(List<String> ids) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus skipJukebox(int index, int offsetSeconds) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus stopJukebox() throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus startJukebox() throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus getJukeboxStatus() throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public JukeboxStatus setJukeboxGain(float gain) throws Exception
	{
		throw new OfflineException("Jukebox not available in offline mode");
	}

	@Override
	public SearchResult getStarred() throws Exception
	{
		throw new OfflineException("Starred not available in offline mode");
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset) throws Exception
	{
		throw new OfflineException("Getting Songs By Genre not available in offline mode");
	}

	@Override
	public List<Genre> getGenres(boolean refresh) throws Exception
	{
		throw new OfflineException("Getting Genres not available in offline mode");
	}

	@Override
	public UserInfo getUser(String username) throws Exception
	{
		throw new OfflineException("Getting user info not available in offline mode");
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires) throws Exception
	{
		throw new OfflineException("Creating shares not available in offline mode");
	}

	@Override
	public List<Share> getShares(boolean refresh) throws Exception
	{
		throw new OfflineException("Getting shares not available in offline mode");
	}

	@Override
	public void deleteShare(String id) throws Exception
	{
		throw new OfflineException("Deleting shares not available in offline mode");
	}

	@Override
	public void updateShare(String id, String description, Long expires) throws Exception
	{
		throw new OfflineException("Updating shares not available in offline mode");
	}

	@Override
	public void star(String id, String albumId, String artistId) throws Exception
	{
		throw new OfflineException("Star not available in offline mode");
	}

	@Override
	public void unstar(String id, String albumId, String artistId) throws Exception
	{
		throw new OfflineException("UnStar not available in offline mode");
	}
	@Override
	public List<MusicFolder> getMusicFolders(boolean refresh) throws Exception
	{
		throw new OfflineException("Music folders not available in offline mode");
	}

	@Override
	public MusicDirectory getAlbumList2(String type, int size, int offset, String musicFolderId) {
		Timber.w("OfflineMusicService.getAlbumList2 was called but it isn't available");
		return null;
	}

	@Override
	public String getVideoUrl(String id, boolean useFlash) {
		Timber.w("OfflineMusicService.getVideoUrl was called but it isn't available");
		return null;
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since) {
		Timber.w("OfflineMusicService.getChatMessages was called but it isn't available");
		return null;
	}

	@Override
	public void addChatMessage(String message) {
		Timber.w("OfflineMusicService.addChatMessage was called but it isn't available");
	}

	@Override
	public List<Bookmark> getBookmarks() {
		Timber.w("OfflineMusicService.getBookmarks was called but it isn't available");
		return null;
	}

	@Override
	public void deleteBookmark(String id) {
		Timber.w("OfflineMusicService.deleteBookmark was called but it isn't available");
	}

	@Override
	public void createBookmark(String id, int position) {
		Timber.w("OfflineMusicService.createBookmark was called but it isn't available");
	}

	@Override
	public MusicDirectory getVideos(boolean refresh) {
		Timber.w("OfflineMusicService.getVideos was called but it isn't available");
		return null;
	}

	@Override
	public SearchResult getStarred2() {
		Timber.w("OfflineMusicService.getStarred2 was called but it isn't available");
		return null;
	}

	@Override
	public void ping() {
	}

	@Override
	public boolean isLicenseValid() {
		return true;
	}

	@Override
	public Indexes getArtists(boolean refresh) {
		Timber.w("OfflineMusicService.getArtists was called but it isn't available");
		return null;
	}

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh) {
		Timber.w("OfflineMusicService.getArtist was called but it isn't available");
		return null;
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh) {
		Timber.w("OfflineMusicService.getAlbum was called but it isn't available");
		return null;
	}

	@Override
	public MusicDirectory getPodcastEpisodes(String podcastChannelId) {
		Timber.w("OfflineMusicService.getPodcastEpisodes was called but it isn't available");
		return null;
	}

	@Override
	public Pair<InputStream, Boolean> getDownloadInputStream(MusicDirectory.Entry song, long offset, int maxBitrate) {
		Timber.w("OfflineMusicService.getDownloadInputStream was called but it isn't available");
		return null;
	}

	@Override
	public void setRating(String id, int rating) {
		Timber.w("OfflineMusicService.setRating was called but it isn't available");
	}

	@Override
	public List<PodcastsChannel> getPodcastsChannels(boolean refresh) {
		Timber.w("OfflineMusicService.getPodcastsChannels was called but it isn't available");
		return null;
	}
}
