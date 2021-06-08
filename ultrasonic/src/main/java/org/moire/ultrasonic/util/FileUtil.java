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

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import org.moire.ultrasonic.app.UApp;
import org.moire.ultrasonic.domain.MusicDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import kotlin.Lazy;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus
 */
public class FileUtil
{
	private static final String[] FILE_SYSTEM_UNSAFE = {"/", "\\", "..", ":", "\"", "?", "*", "<", ">", "|"};
	private static final String[] FILE_SYSTEM_UNSAFE_DIR = {"\\", "..", ":", "\"", "?", "*", "<", ">", "|"};
	private static final List<String> MUSIC_FILE_EXTENSIONS = Arrays.asList("mp3", "ogg", "aac", "flac", "m4a", "wav", "wma", "opus");
	private static final List<String> VIDEO_FILE_EXTENSIONS = Arrays.asList("flv", "mp4", "m4v", "wmv", "avi", "mov", "mpg", "mkv");
	private static final List<String> PLAYLIST_FILE_EXTENSIONS = Collections.singletonList("m3u");
	private static final Pattern TITLE_WITH_TRACK = Pattern.compile("^\\d\\d-.*");
	public static final String SUFFIX_LARGE = ".jpeg";
	public static final String SUFFIX_SMALL = ".jpeg-small";

	private static final Lazy<PermissionUtil> permissionUtil = inject(PermissionUtil.class);

	public static File getSongFile(MusicDirectory.Entry song)
	{
		File dir = getAlbumDirectory(song);

		// Do not generate new name for offline files. Offline files will have their Path as their Id.
		if (!TextUtils.isEmpty(song.getId()))
		{
			if (song.getId().startsWith(dir.getAbsolutePath())) return new File(song.getId());
		}

		// Generate a file name for the song
		StringBuilder fileName = new StringBuilder(256);
		Integer track = song.getTrack();

		//check if filename already had track number
		if (!TITLE_WITH_TRACK.matcher(song.getTitle()).matches()) {
			if (track != null) {
				if (track < 10) {
					fileName.append('0');
				}

				fileName.append(track).append('-');
			}
		}
		fileName.append(fileSystemSafe(song.getTitle())).append('.');

		if (!TextUtils.isEmpty(song.getTranscodedSuffix())) {
			fileName.append(song.getTranscodedSuffix());
		} else {
			fileName.append(song.getSuffix());
		}

		return new File(dir, fileName.toString());
	}

	public static File getPlaylistFile(String server, String name)
	{
		File playlistDir = getPlaylistDirectory(server);
		return new File(playlistDir, String.format("%s.m3u", fileSystemSafe(name)));
	}

	public static File getPlaylistDirectory()
	{
		File playlistDir = new File(getUltrasonicDirectory(), "playlists");
		ensureDirectoryExistsAndIsReadWritable(playlistDir);
		return playlistDir;
	}

	public static File getPlaylistDirectory(String server)
	{
		File playlistDir = new File(getPlaylistDirectory(), server);
		ensureDirectoryExistsAndIsReadWritable(playlistDir);
		return playlistDir;
	}

	/**
	 * Get the album art file for a given album entry
	 * @param entry The album entry
	 * @return File object. Not guaranteed that it exists
	 */
	public static File getAlbumArtFile(MusicDirectory.Entry entry)
	{
		File albumDir = getAlbumDirectory(entry);
		return getAlbumArtFile(albumDir);
	}

	/**
	 * Get the cache key for a given album entry
	 * @param entry The album entry
	 * @param large Whether to get the key for the large or the default image
	 * @return String The hash key
	 */
	public static String getAlbumArtKey(MusicDirectory.Entry entry, boolean large)
	{
		File albumDir = getAlbumDirectory(entry);
		File albumArtDir = getAlbumArtDirectory();

		if (albumArtDir == null || albumDir == null) {
			return null;
		}

		String suffix = (large) ? SUFFIX_LARGE : SUFFIX_SMALL;

		return String.format(Locale.ROOT, "%s%s", Util.md5Hex(albumDir.getPath()), suffix);
	}


	public static File getAvatarFile(String username)
	{
		File albumArtDir = getAlbumArtDirectory();

		if (albumArtDir == null || username == null)
		{
			return null;
		}

		String md5Hex = Util.md5Hex(username);
		return new File(albumArtDir, String.format("%s.jpeg", md5Hex));
	}

	/**
	 * Get the album art file for a given album directory
	 * @param albumDir The album directory
	 * @return File object. Not guaranteed that it exists
	 */
	public static File getAlbumArtFile(File albumDir)
	{
		File albumArtDir = getAlbumArtDirectory();

		if (albumArtDir == null || albumDir == null)
		{
			return null;
		}

		String md5Hex = Util.md5Hex(albumDir.getPath());
		return new File(albumArtDir, String.format("%s.jpeg", md5Hex));
	}


	/**
	 * Get the album art file for a given cache key
	 * @param cacheKey
	 * @return File object. Not guaranteed that it exists
	 */
	public static File getAlbumArtFile(String cacheKey)
	{
		File albumArtDir = getAlbumArtDirectory();

		if (albumArtDir == null || cacheKey == null)
		{
			return null;
		}

		return new File(albumArtDir, cacheKey);
	}


	public static File getAlbumArtDirectory()
	{
		File albumArtDir = new File(getUltrasonicDirectory(), "artwork");
		ensureDirectoryExistsAndIsReadWritable(albumArtDir);
		ensureDirectoryExistsAndIsReadWritable(new File(albumArtDir, ".nomedia"));
		return albumArtDir;
	}

	public static File getAlbumDirectory(MusicDirectory.Entry entry)
	{
		if (entry == null)
		{
			return null;
		}

		File dir;

		if (!TextUtils.isEmpty(entry.getPath()))
		{
			File f = new File(fileSystemSafeDir(entry.getPath()));
			dir = new File(String.format("%s/%s", getMusicDirectory().getPath(), entry.isDirectory() ? f.getPath() : f.getParent()));
		}
		else
		{
			String artist = fileSystemSafe(entry.getArtist());
			String album = fileSystemSafe(entry.getAlbum());

			if ("unnamed".equals(album))
			{
				album = fileSystemSafe(entry.getTitle());
			}

			dir = new File(String.format("%s/%s/%s", getMusicDirectory().getPath(), artist, album));
		}

		return dir;
	}

	public static void createDirectoryForParent(File file)
	{
		File dir = file.getParentFile();
		if (!dir.exists())
		{
			if (!dir.mkdirs())
			{
				Timber.e("Failed to create directory %s", dir);
			}
		}
	}

	private static File getOrCreateDirectory(String name)
	{
		File dir = new File(getUltrasonicDirectory(), name);

		if (!dir.exists() && !dir.mkdirs())
		{
			Timber.e("Failed to create %s", name);
		}

		return dir;
	}

	public static File getUltrasonicDirectory()
	{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return new File(Environment.getExternalStorageDirectory(), "Android/data/org.moire.ultrasonic");

        // After Android M, the location of the files must be queried differently. GetExternalFilesDir will always return a directory which Ultrasonic can access without any extra privileges.
        return UApp.Companion.applicationContext().getExternalFilesDir(null);
	}

	public static File getDefaultMusicDirectory()
	{
		return getOrCreateDirectory("music");
	}

	public static File getMusicDirectory()
	{
		File defaultMusicDirectory = getDefaultMusicDirectory();
		String path = Util.getPreferences().getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, defaultMusicDirectory.getPath());
		File dir = new File(path);

		boolean hasAccess = ensureDirectoryExistsAndIsReadWritable(dir);
		if (!hasAccess) permissionUtil.getValue().handlePermissionFailed(null);

		return  hasAccess ? dir : defaultMusicDirectory;
	}

	public static boolean ensureDirectoryExistsAndIsReadWritable(File dir)
	{
		if (dir == null)
		{
			return false;
		}

		if (dir.exists())
		{
			if (!dir.isDirectory())
			{
				Timber.w("%s exists but is not a directory.", dir);
				return false;
			}
		}
		else
		{
			if (dir.mkdirs())
			{
				Timber.i("Created directory %s", dir);
			}
			else
			{
				Timber.w("Failed to create directory %s", dir);
				return false;
			}
		}

		if (!dir.canRead())
		{
			Timber.w("No read permission for directory %s", dir);
            return false;
		}

		if (!dir.canWrite())
		{
			Timber.w("No write permission for directory %s", dir);
            return false;
		}

		return true;
	}

	/**
	 * Makes a given filename safe by replacing special characters like slashes ("/" and "\")
	 * with dashes ("-").
	 *
	 * @param filename The filename in question.
	 * @return The filename with special characters replaced by hyphens.
	 */
	private static String fileSystemSafe(String filename)
	{
		if (filename == null || filename.trim().isEmpty())
		{
			return "unnamed";
		}

		for (String s : FILE_SYSTEM_UNSAFE)
		{
			filename = filename.replace(s, "-");
		}

		return filename;
	}

	/**
	 * Makes a given filename safe by replacing special characters like colons (":")
	 * with dashes ("-").
	 *
	 * @param path The path of the directory in question.
	 * @return The the directory name with special characters replaced by hyphens.
	 */
	private static String fileSystemSafeDir(String path)
	{
		if (path == null || path.trim().isEmpty())
		{
			return "";
		}

		for (String s : FILE_SYSTEM_UNSAFE_DIR)
		{
			path = path.replace(s, "-");
		}

		return path;
	}

	/**
	 * Similar to {@link File#listFiles()}, but returns a sorted set.
	 * Never returns {@code null}, instead a warning is logged, and an empty set is returned.
	 */
	public static SortedSet<File> listFiles(File dir)
	{
		File[] files = dir.listFiles();

		if (files == null)
		{
			Timber.w("Failed to list children for %s", dir.getPath());
			return new TreeSet<>();
		}

		return new TreeSet<>(Arrays.asList(files));
	}

	public static SortedSet<File> listMediaFiles(File dir)
	{
		SortedSet<File> files = listFiles(dir);
		Iterator<File> iterator = files.iterator();

		while (iterator.hasNext())
		{
			File file = iterator.next();

			if (!file.isDirectory() && !isMediaFile(file))
			{
				iterator.remove();
			}
		}

		return files;
	}

	private static boolean isMediaFile(File file)
	{
		String extension = getExtension(file.getName());
		return MUSIC_FILE_EXTENSIONS.contains(extension) || VIDEO_FILE_EXTENSIONS.contains(extension);
	}

	public static boolean isPlaylistFile(File file)
	{
		String extension = getExtension(file.getName());
		return PLAYLIST_FILE_EXTENSIONS.contains(extension);
	}

	/**
	 * Returns the extension (the substring after the last dot) of the given file. The dot
	 * is not included in the returned extension.
	 *
	 * @param name The filename in question.
	 * @return The extension, or an empty string if no extension is found.
	 */
	public static String getExtension(String name)
	{
		int index = name.lastIndexOf('.');
		return index == -1 ? "" : name.substring(index + 1).toLowerCase();
	}

	/**
	 * Returns the base name (the substring before the last dot) of the given file. The dot
	 * is not included in the returned basename.
	 *
	 * @param name The filename in question.
	 * @return The base name, or an empty string if no basename is found.
	 */
	public static String getBaseName(String name)
	{
		int index = name.lastIndexOf('.');
		return index == -1 ? name : name.substring(0, index);
	}

	/**
	 * Returns the file name of a .partial file of the given file.
	 *
	 * @param name The filename in question.
	 * @return The .partial file name
	 */
	public static String getPartialFile(String name)
	{
		return String.format("%s.partial.%s", FileUtil.getBaseName(name), FileUtil.getExtension(name));
	}

	/**
	 * Returns the file name of a .complete file of the given file.
	 *
	 * @param name The filename in question.
	 * @return The .complete file name
	 */
	public static String getCompleteFile(String name)
	{
		return String.format("%s.complete.%s", FileUtil.getBaseName(name), FileUtil.getExtension(name));
	}

	public static <T extends Serializable> boolean serialize(Context context, T obj, String fileName)
	{
		File file = new File(context.getCacheDir(), fileName);
		ObjectOutputStream out = null;

		try
		{
			out = new ObjectOutputStream(new FileOutputStream(file));
			out.writeObject(obj);
			Timber.i("Serialized object to %s", file);
			return true;
		}
		catch (Throwable x)
		{
			Timber.w("Failed to serialize object to %s", file);
			return false;
		}
		finally
		{
			Util.close(out);
		}
	}

	@SuppressWarnings({"unchecked"})
	public static <T extends Serializable> T deserialize(Context context, String fileName)
	{
		File file = new File(context.getCacheDir(), fileName);

		if (!file.exists() || !file.isFile())
		{
			return null;
		}

		ObjectInputStream in = null;

		try
		{
			in = new ObjectInputStream(new FileInputStream(file));
			Object object = in.readObject();
			T result = (T) object;
			Timber.i("Deserialized object from %s", file);
			return result;
		}
		catch (Throwable x)
		{
			Timber.w(x,"Failed to deserialize object from %s", file);
			return null;
		}
		finally
		{
			Util.close(in);
		}
	}
}
