package org.moire.ultrasonic.util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.StatFs;
import android.util.Log;

import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.Playlist;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.Downloader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus
 * @version $Id$
 */

/**
 * Responsible for cleaning up files from the offline download cache on the filesystem
 */
public class CacheCleaner
{

	private static final String TAG = CacheCleaner.class.getSimpleName();
	private static final long MIN_FREE_SPACE = 500 * 1024L * 1024L;

	private final Context context;
	private Lazy<Downloader> downloader = inject(Downloader.class);
	private Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);

	public CacheCleaner(Context context)
	{
		this.context = context;
	}

	public void clean()
	{
		try
		{
			new BackgroundCleanup().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		catch (Exception ex)
		{
			// If an exception is thrown, assume we execute correctly the next time
			Log.w(TAG, "Exception in CacheCleaner.clean", ex);
		}
	}

	public void cleanSpace()
	{
		try
		{
			new BackgroundSpaceCleanup().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		catch (Exception ex)
		{
			// If an exception is thrown, assume we execute correctly the next time
			Log.w(TAG,"Exception in CacheCleaner.cleanSpace", ex);
		}
	}

	public void cleanPlaylists(List<Playlist> playlists)
	{
		try
		{
			new BackgroundPlaylistsCleanup().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, playlists);
		}
		catch (Exception ex)
		{
			// If an exception is thrown, assume we execute correctly the next time
			Log.w(TAG, "Exception in CacheCleaner.cleanPlaylists", ex);
		}
	}

	private void deleteEmptyDirs(Iterable<File> dirs, Collection<File> doNotDelete)
	{
		for (File dir : dirs)
		{
			if (doNotDelete.contains(dir))
			{
				continue;
			}

			File[] children = dir.listFiles();

			if (children != null)
			{
				// No songs left in the folder
				if (children.length == 1 && children[0].getPath().equals(FileUtil.getAlbumArtFile(context, dir).getPath()))
				{
					Util.delete(FileUtil.getAlbumArtFile(context, dir));
					children = dir.listFiles();
				}

				// Delete empty directory
				if (children != null && children.length == 0)
				{
					Util.delete(dir);
				}
			}
		}
	}

	private long getMinimumDelete(List<File> files)
	{
		if (files.isEmpty())
		{
			return 0L;
		}

		long cacheSizeBytes = Util.getCacheSizeMB(context) * 1024L * 1024L;
		long bytesUsedBySubsonic = 0L;

		for (File file : files)
		{
			bytesUsedBySubsonic += file.length();
		}

		// Ensure that file system is not more than 95% full.
		StatFs stat = new StatFs(files.get(0).getPath());
		long bytesTotalFs = (long) stat.getBlockCount() * (long) stat.getBlockSize();
		long bytesAvailableFs = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
		long bytesUsedFs = bytesTotalFs - bytesAvailableFs;
		long minFsAvailability = bytesTotalFs - MIN_FREE_SPACE;

		long bytesToDeleteCacheLimit = Math.max(bytesUsedBySubsonic - cacheSizeBytes, 0L);
		long bytesToDeleteFsLimit = Math.max(bytesUsedFs - minFsAvailability, 0L);
		long bytesToDelete = Math.max(bytesToDeleteCacheLimit, bytesToDeleteFsLimit);

		Log.i(TAG, String.format("File system       : %s of %s available", Util.formatBytes(bytesAvailableFs), Util.formatBytes(bytesTotalFs)));
		Log.i(TAG, String.format("Cache limit       : %s", Util.formatBytes(cacheSizeBytes)));
		Log.i(TAG, String.format("Cache size before : %s", Util.formatBytes(bytesUsedBySubsonic)));
		Log.i(TAG, String.format("Minimum to delete : %s", Util.formatBytes(bytesToDelete)));

		return bytesToDelete;
	}

	private static void deleteFiles(Collection<File> files, Collection<File> doNotDelete, long bytesToDelete, boolean deletePartials)
	{
		if (files.isEmpty())
		{
			return;
		}

		long bytesDeleted = 0L;
		for (File file : files)
		{
			if (!deletePartials && bytesDeleted > bytesToDelete) break;

			if (bytesToDelete > bytesDeleted || (deletePartials && (file.getName().endsWith(".partial") || file.getName().contains(".partial."))))
			{
				if (!doNotDelete.contains(file) && !file.getName().equals(Constants.ALBUM_ART_FILE))
				{
					long size = file.length();

					if (Util.delete(file))
					{
						bytesDeleted += size;
					}
				}
			}
		}

		Log.i(TAG, String.format("Deleted           : %s", Util.formatBytes(bytesDeleted)));
	}

	private static void findCandidatesForDeletion(File file, List<File> files, List<File> dirs)
	{
		if (file.isFile())
		{
			String name = file.getName();
			boolean isCacheFile = name.endsWith(".partial") || name.contains(".partial.") || name.endsWith(".complete") || name.contains(".complete.");

			if (isCacheFile)
			{
				files.add(file);
			}
		}
		else
		{
			// Depth-first
			for (File child : FileUtil.listFiles(file))
			{
				findCandidatesForDeletion(child, files, dirs);
			}

			dirs.add(file);
		}
	}

	private static void sortByAscendingModificationTime(List<File> files)
	{
		Collections.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(File a, File b)
			{
				if (a.lastModified() < b.lastModified())
				{
					return -1;
				}

				if (a.lastModified() > b.lastModified())
				{
					return 1;
				}

				return 0;
			}
		});
	}

	private Set<File> findFilesToNotDelete()
	{
		Set<File> filesToNotDelete = new HashSet<File>(5);

		for (DownloadFile downloadFile : downloader.getValue().getDownloads())
		{
			filesToNotDelete.add(downloadFile.getPartialFile());
			filesToNotDelete.add(downloadFile.getCompleteFile());
		}

		filesToNotDelete.add(FileUtil.getMusicDirectory(context));
		return filesToNotDelete;
	}

	private class BackgroundCleanup extends AsyncTask<Void, Void, Void>
	{
		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				Thread.currentThread().setName("BackgroundCleanup");
				List<File> files = new ArrayList<File>();
				List<File> dirs = new ArrayList<File>();

				findCandidatesForDeletion(FileUtil.getMusicDirectory(context), files, dirs);
				sortByAscendingModificationTime(files);

				Set<File> filesToNotDelete = findFilesToNotDelete();

				deleteFiles(files, filesToNotDelete, getMinimumDelete(files), true);
				deleteEmptyDirs(dirs, filesToNotDelete);
			}
			catch (RuntimeException x)
			{
				Log.e(TAG, "Error in cache cleaning.", x);
			}

			return null;
		}
	}

	private class BackgroundSpaceCleanup extends AsyncTask<Void, Void, Void>
	{
		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				Thread.currentThread().setName("BackgroundSpaceCleanup");
				List<File> files = new ArrayList<File>();
				List<File> dirs = new ArrayList<File>();
				findCandidatesForDeletion(FileUtil.getMusicDirectory(context), files, dirs);

				long bytesToDelete = getMinimumDelete(files);
				if (bytesToDelete > 0L)
				{
					sortByAscendingModificationTime(files);
					Set<File> filesToNotDelete = findFilesToNotDelete();
					deleteFiles(files, filesToNotDelete, bytesToDelete, false);
				}
			}
			catch (RuntimeException x)
			{
				Log.e(TAG, "Error in cache cleaning.", x);
			}

			return null;
		}
	}

	private class BackgroundPlaylistsCleanup extends AsyncTask<List<Playlist>, Void, Void>
	{
		@Override
		protected Void doInBackground(List<Playlist>... params)
		{
			try
			{
				Thread.currentThread().setName("BackgroundPlaylistsCleanup");
				String server = activeServerProvider.getValue().getActiveServer().getName();
				SortedSet<File> playlistFiles = FileUtil.listFiles(FileUtil.getPlaylistDirectory(context, server));
				List<Playlist> playlists = params[0];
				for (Playlist playlist : playlists)
				{
					playlistFiles.remove(FileUtil.getPlaylistFile(context, server, playlist.getName()));
				}

				for (File playlist : playlistFiles)
				{
					playlist.delete();
				}
			}
			catch (RuntimeException x)
			{
				Log.e(TAG, "Error in playlist cache cleaning.", x);
			}

			return null;
		}
	}
}