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
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.util.CacheCleaner;
import org.moire.ultrasonic.util.CancellableTask;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import kotlin.Lazy;
import kotlin.Pair;

import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.ON_AFTER_RELEASE;
import static android.os.PowerManager.SCREEN_DIM_WAKE_LOCK;
import static org.koin.java.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class DownloadFile
{
	private static final String TAG = DownloadFile.class.getSimpleName();
	private final Context context;
	private final MusicDirectory.Entry song;
	private final File partialFile;
	private final File completeFile;
	private final File saveFile;

	private final MediaStoreService mediaStoreService;
	private CancellableTask downloadTask;
	private final boolean save;
	private boolean failed;
	private int bitRate;
	private volatile boolean isPlaying;
	private volatile boolean saveWhenDone;
	private volatile boolean completeWhenDone;

	private Lazy<Downloader> downloader = inject(Downloader.class);

	public DownloadFile(Context context, MusicDirectory.Entry song, boolean save)
	{
		super();
		this.context = context;
		this.song = song;
		this.save = save;

		saveFile = FileUtil.getSongFile(context, song);
		bitRate = Util.getMaxBitRate(context);
		partialFile = new File(saveFile.getParent(), String.format("%s.partial.%s", FileUtil.getBaseName(saveFile.getName()), FileUtil.getExtension(saveFile.getName())));
		completeFile = new File(saveFile.getParent(), String.format("%s.complete.%s", FileUtil.getBaseName(saveFile.getName()), FileUtil.getExtension(saveFile.getName())));
		mediaStoreService = new MediaStoreService(context);
	}

	public MusicDirectory.Entry getSong()
	{
		return song;
	}

	/**
	 * Returns the effective bit rate.
	 */
	public int getBitRate()
	{
		if (!partialFile.exists())
		{
			bitRate = Util.getMaxBitRate(context);
		}

		if (bitRate > 0)
		{
			return bitRate;
		}

		return song.getBitRate() == null ? 160 : song.getBitRate();
	}

	public synchronized void download()
	{
		FileUtil.createDirectoryForParent(saveFile);
		failed = false;

		if (!partialFile.exists())
		{
			bitRate = Util.getMaxBitRate(context);
		}

		downloadTask = new DownloadTask();
		downloadTask.start();
	}

	public synchronized void cancelDownload()
	{
		if (downloadTask != null)
		{
			downloadTask.cancel();
		}
	}

	public File getCompleteFile()
	{
		if (saveFile.exists())
		{
			return saveFile;
		}

		if (completeFile.exists())
		{
			return completeFile;
		}

		return saveFile;
	}

	public File getPartialFile()
	{
		return partialFile;
	}

	public boolean isSaved()
	{
		return saveFile.exists();
	}

	public synchronized boolean isCompleteFileAvailable()
	{
		return saveFile.exists() || completeFile.exists();
	}

	public synchronized boolean isWorkDone()
	{
		return saveFile.exists() || (completeFile.exists() && !save) || saveWhenDone || completeWhenDone;
	}

	public synchronized boolean isDownloading()
	{
		return downloadTask != null && downloadTask.isRunning();
	}

	public synchronized boolean isDownloadCancelled()
	{
		return downloadTask != null && downloadTask.isCancelled();
	}

	public boolean shouldSave()
	{
		return save;
	}

	public boolean isFailed()
	{
		return failed;
	}

	public void delete()
	{
		cancelDownload();
		Util.delete(partialFile);
		Util.delete(completeFile);
		Util.delete(saveFile);
		mediaStoreService.deleteFromMediaStore(this);
	}

	public void unpin()
	{
		if (saveFile.exists())
		{
			saveFile.renameTo(completeFile);
		}
	}

	public boolean cleanup()
	{
		boolean ok = true;

		if (completeFile.exists() || saveFile.exists())
		{
			ok = Util.delete(partialFile);
		}

		if (saveFile.exists())
		{
			ok &= Util.delete(completeFile);
		}

		return ok;
	}

	// In support of LRU caching.
	public void updateModificationDate()
	{
		updateModificationDate(saveFile);
		updateModificationDate(partialFile);
		updateModificationDate(completeFile);
	}

	private static void updateModificationDate(File file)
	{
		if (file.exists())
		{
			boolean ok = file.setLastModified(System.currentTimeMillis());

			if (!ok)
			{
				Log.i(TAG, String.format("Failed to set last-modified date on %s, trying alternate method", file));

				try
				{
					// Try alternate method to update last modified date to current time
					// 	Found at https://code.google.com/p/android/issues/detail?id=18624
					RandomAccessFile raf = new RandomAccessFile(file, "rw");
					long length = raf.length();
					raf.setLength(length + 1);
					raf.setLength(length);
					raf.close();
				}
				catch (Exception e)
				{
					Log.w(TAG, String.format("Failed to set last-modified date on %s", file));
				}
			}
		}
	}

	public void setPlaying(boolean isPlaying)
	{
		try
		{
			if (saveWhenDone && !isPlaying)
			{
				Util.renameFile(completeFile, saveFile);
				saveWhenDone = false;
			}
			else if (completeWhenDone && !isPlaying)
			{
				if (save)
				{
					Util.renameFile(partialFile, saveFile);
					mediaStoreService.saveInMediaStore(DownloadFile.this);
				}
				else
				{
					Util.renameFile(partialFile, completeFile);
				}

				completeWhenDone = false;
			}
		}
		catch (IOException ex)
		{
			Log.w(TAG, String.format("Failed to rename file %s to %s", completeFile, saveFile));
		}

		this.isPlaying = isPlaying;
	}

	@NotNull
	@Override
	public String toString()
	{
		return String.format("DownloadFile (%s)", song);
	}

	private class DownloadTask extends CancellableTask
	{
		@Override
		public void execute()
		{
			InputStream in = null;
			FileOutputStream out = null;
			PowerManager.WakeLock wakeLock = null;
			WifiManager.WifiLock wifiLock = null;

			try
			{
				if (Util.isScreenLitOnDownload(context))
				{
					PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
					wakeLock = pm.newWakeLock(SCREEN_DIM_WAKE_LOCK | ON_AFTER_RELEASE, toString());
					wakeLock.acquire(10*60*1000L /*10 minutes*/);
					Log.i(TAG, String.format("Acquired wake lock %s", wakeLock));
				}

				wifiLock = Util.createWifiLock(context, toString());
				wifiLock.acquire();

				if (saveFile.exists())
				{
					Log.i(TAG, String.format("%s already exists. Skipping.", saveFile));
					return;
				}
				if (completeFile.exists())
				{
					if (save)
					{
						if (isPlaying)
						{
							saveWhenDone = true;
						}
						else
						{
							Util.renameFile(completeFile, saveFile);
						}
					}
					else
					{
						Log.i(TAG, String.format("%s already exists. Skipping.", completeFile));
					}
					return;
				}

				MusicService musicService = MusicServiceFactory.getMusicService(context);

				// Some devices seem to throw error on partial file which doesn't exist
				boolean compare;

				Integer duration = song.getDuration();
				long fileLength = 0;

				if (!partialFile.exists())
				{
					fileLength = partialFile.length();
				}

				try
				{
					compare = (bitRate == 0) || (duration == null || duration == 0) || (fileLength == 0);
					//(bitRate * song.getDuration() * 1000 / 8) > partialFile.length();
				}
				catch (Exception e)
				{
					compare = true;
				}

				if (compare)
				{
					// Attempt partial HTTP GET, appending to the file if it exists.
					Pair<InputStream, Boolean> response = musicService
							.getDownloadInputStream(context, song, partialFile.length(), bitRate,
									DownloadTask.this);

					if (response.getSecond())
					{
						Log.i(TAG, String.format("Executed partial HTTP GET, skipping %d bytes", partialFile.length()));
					}

					out = new FileOutputStream(partialFile, response.getSecond());
					long n = copy(response.getFirst(), out);
					Log.i(TAG, String.format("Downloaded %d bytes to %s", n, partialFile));
					out.flush();
					out.close();

					if (isCancelled())
					{
						throw new Exception(String.format("Download of '%s' was cancelled", song));
					}

					downloadAndSaveCoverArt(musicService);
				}

				if (isPlaying)
				{
					completeWhenDone = true;
				}
				else
				{
					if (save)
					{
						Util.renameFile(partialFile, saveFile);
						mediaStoreService.saveInMediaStore(DownloadFile.this);

						if (Util.getShouldScanMedia(context))
						{
							Util.scanMedia(context, saveFile);
						}
					}
					else
					{
						Util.renameFile(partialFile, completeFile);

						if (Util.getShouldScanMedia(context))
						{
							Util.scanMedia(context, completeFile);
						}
					}
				}
			}
			catch (Exception x)
			{
				Util.close(out);
				Util.delete(completeFile);
				Util.delete(saveFile);

				if (!isCancelled())
				{
					failed = true;
					Log.w(TAG, String.format("Failed to download '%s'.", song), x);
				}

			}
			finally
			{
				Util.close(in);
				Util.close(out);
				if (wakeLock != null)
				{
					wakeLock.release();
					Log.i(TAG, String.format("Released wake lock %s", wakeLock));
				}
				if (wifiLock != null)
				{
					wifiLock.release();
				}

				new CacheCleaner(context).cleanSpace();

				downloader.getValue().checkDownloads();
			}
		}

		@NotNull
		@Override
		public String toString()
		{
			return String.format("DownloadTask (%s)", song);
		}

		private void downloadAndSaveCoverArt(MusicService musicService) throws Exception
		{
			try
			{
				if (!TextUtils.isEmpty(song.getCoverArt())) {
					int size = Util.getMinDisplayMetric(context);
					musicService.getCoverArt(context, song, size, true, true, null);
				}
			}
			catch (Exception x)
			{
				Log.e(TAG, "Failed to get cover art.", x);
			}
		}

		private long copy(final InputStream in, OutputStream out) throws IOException
		{
			// Start a thread that will close the input stream if the task is
			// cancelled, thus causing the copy() method to return.
			new Thread()
			{
				@Override
				public void run()
				{
					while (true)
					{
						Util.sleepQuietly(3000L);

						if (isCancelled())
						{
							Util.close(in);
							return;
						}

						if (!isRunning())
						{
							return;
						}
					}
				}
			}.start();

			byte[] buffer = new byte[1024 * 16];
			long count = 0;
			int n;
			long lastLog = System.currentTimeMillis();

			while (!isCancelled() && (n = in.read(buffer)) != -1)
			{
				out.write(buffer, 0, n);
				count += n;

				long now = System.currentTimeMillis();
				if (now - lastLog > 3000L)
				{  // Only every so often.
					Log.i(TAG, String.format("Downloaded %s of %s", Util.formatBytes(count), song));
					lastLog = now;
				}
			}
			return count;
		}
	}
}