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
package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.domain.Artist;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;

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
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Sindre Mehus
 */
public class FileUtil
{
	private static final String TAG = FileUtil.class.getSimpleName();
	private static final String[] FILE_SYSTEM_UNSAFE = {"/", "\\", "..", ":", "\"", "?", "*", "<", ">", "|"};
	private static final String[] FILE_SYSTEM_UNSAFE_DIR = {"\\", "..", ":", "\"", "?", "*", "<", ">", "|"};
	private static final List<String> MUSIC_FILE_EXTENSIONS = Arrays.asList("mp3", "ogg", "aac", "flac", "m4a", "wav", "wma");
	private static final List<String> VIDEO_FILE_EXTENSIONS = Arrays.asList("flv", "mp4", "m4v", "wmv", "avi", "mov", "mpg", "mkv");
	private static final List<String> PLAYLIST_FILE_EXTENSIONS = Collections.singletonList("m3u");
	private static final File DEFAULT_MUSIC_DIR = createDirectory("music");

	public static File getSongFile(Context context, MusicDirectory.Entry song)
	{
		File dir = getAlbumDirectory(context, song);

		StringBuilder fileName = new StringBuilder(256);
		Integer track = song.getTrack();

		if (track != null)
		{
			if (track < 10)
			{
				fileName.append('0');
			}

			fileName.append(track).append('-');
		}

		fileName.append(fileSystemSafe(song.getTitle())).append('.');

		if (song.getTranscodedSuffix() != null)
		{
			fileName.append(song.getTranscodedSuffix());
		}
		else
		{
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
		File playlistDir = new File(getUltraSonicDirectory(), "playlists");
		ensureDirectoryExistsAndIsReadWritable(playlistDir);
		return playlistDir;
	}

	public static File getPlaylistDirectory(String server)
	{
		File playlistDir = new File(getPlaylistDirectory(), server);
		ensureDirectoryExistsAndIsReadWritable(playlistDir);
		return playlistDir;
	}

	public static File getAlbumArtFile(Context context, MusicDirectory.Entry entry)
	{
		File albumDir = getAlbumDirectory(context, entry);
		return getAlbumArtFile(albumDir);
	}

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

	public static Bitmap getAlbumArtBitmap(Context context, MusicDirectory.Entry entry, int size, boolean highQuality)
	{
		File albumArtFile = getAlbumArtFile(context, entry);

		if (albumArtFile != null && albumArtFile.exists())
		{
			final BitmapFactory.Options opt = new BitmapFactory.Options();

			if (size > 0)
			{
				opt.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(albumArtFile.getPath(), opt);

				if (highQuality)
				{
					opt.inDither = true;
					opt.inPreferQualityOverSpeed = true;
				}

				opt.inPurgeable = true;
				opt.inSampleSize = Util.calculateInSampleSize(opt, size, Util.getScaledHeight(opt.outHeight, opt.outWidth, size));
				opt.inJustDecodeBounds = false;
			}

			Bitmap bitmap = BitmapFactory.decodeFile(albumArtFile.getPath(), opt);
			Log.i("getAlbumArtBitmap", String.valueOf(size));

			return bitmap == null ? null : bitmap;
		}

		return null;
	}

	public static Bitmap getSampledBitmap(byte[] bytes, int size, boolean highQuality)
	{
		final BitmapFactory.Options opt = new BitmapFactory.Options();

		if (size > 0)
		{
			opt.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);

			if (highQuality)
			{
				opt.inDither = true;
				opt.inPreferQualityOverSpeed = true;
			}

			opt.inPurgeable = true;
			opt.inSampleSize = Util.calculateInSampleSize(opt, size, Util.getScaledHeight(opt.outHeight, opt.outWidth, size));
			opt.inJustDecodeBounds = false;
		}

		Log.i("getSampledBitmap", String.valueOf(size));
		return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
	}

	public static File getArtistDirectory(Context context, Artist artist)
	{
		return new File(String.format("%s/%s", getMusicDirectory(context).getPath(), fileSystemSafe(artist.getName())));
	}

	public static File getAlbumArtDirectory()
	{
		File albumArtDir = new File(getUltraSonicDirectory(), "artwork");
		ensureDirectoryExistsAndIsReadWritable(albumArtDir);
		ensureDirectoryExistsAndIsReadWritable(new File(albumArtDir, ".nomedia"));
		return albumArtDir;
	}

	public static File getAlbumDirectory(Context context, MusicDirectory.Entry entry)
	{
		if (entry == null)
		{
			return null;
		}

		File dir;

		if (entry.getPath() != null)
		{
			File f = new File(fileSystemSafeDir(entry.getPath()));
			dir = new File(String.format("%s/%s", getMusicDirectory(context).getPath(), entry.isDirectory() ? f.getPath() : f.getParent()));
		}
		else
		{
			String artist = fileSystemSafe(entry.getArtist());
			String album = fileSystemSafe(entry.getAlbum());

			if ("unnamed".equals(album))
			{
				album = fileSystemSafe(entry.getTitle());
			}

			dir = new File(String.format("%s/%s/%s", getMusicDirectory(context).getPath(), artist, album));
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
				Log.e(TAG, String.format("Failed to create directory %s", dir));
			}
		}
	}

	private static File createDirectory(String name)
	{
		File dir = new File(getUltraSonicDirectory(), name);

		if (!dir.exists() && !dir.mkdirs())
		{
			Log.e(TAG, String.format("Failed to create %s", name));
		}

		return dir;
	}

	public static File getUltraSonicDirectory()
	{
		return new File(Environment.getExternalStorageDirectory(), "ultrasonic");
	}

	public static File getDefaultMusicDirectory()
	{
		return DEFAULT_MUSIC_DIR;
	}

	public static File getMusicDirectory(Context context)
	{
		String path = Util.getPreferences(context).getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, DEFAULT_MUSIC_DIR.getPath());
		File dir = new File(path);
		return ensureDirectoryExistsAndIsReadWritable(dir) ? dir : DEFAULT_MUSIC_DIR;
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
				Log.w(TAG, String.format("%s exists but is not a directory.", dir));
				return false;
			}
		}
		else
		{
			if (dir.mkdirs())
			{
				Log.i(TAG, String.format("Created directory %s", dir));
			}
			else
			{
				Log.w(TAG, String.format("Failed to create directory %s", dir));
				return false;
			}
		}

		if (!dir.canRead())
		{
			Log.w(TAG, String.format("No read permission for directory %s", dir));
			return false;
		}

		if (!dir.canWrite())
		{
			Log.w(TAG, String.format("No write permission for directory %s", dir));
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
			Log.w(TAG, String.format("Failed to list children for %s", dir.getPath()));
			return new TreeSet<File>();
		}

		return new TreeSet<File>(Arrays.asList(files));
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

	public static <T extends Serializable> boolean serialize(Context context, T obj, String fileName)
	{
		File file = new File(context.getCacheDir(), fileName);
		ObjectOutputStream out = null;

		try
		{
			out = new ObjectOutputStream(new FileOutputStream(file));
			out.writeObject(obj);
			Log.i(TAG, String.format("Serialized object to %s", file));
			return true;
		}
		catch (Throwable x)
		{
			Log.w(TAG, String.format("Failed to serialize object to %s", file));
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
			Log.i(TAG, String.format("Deserialized object from %s", file));
			return result;
		}
		catch (Throwable x)
		{
			Log.w(TAG, String.format("Failed to deserialize object from %s", file), x);
			return null;
		}
		finally
		{
			Util.close(in);
		}
	}
}
