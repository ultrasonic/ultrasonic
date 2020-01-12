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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.RemoteControlClient;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.SeekBar;

import org.koin.java.standalone.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.DownloadActivity;
import org.moire.ultrasonic.activity.SubsonicTabActivity;
import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;
import org.moire.ultrasonic.domain.UserInfo;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x1;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x2;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x3;
import org.moire.ultrasonic.provider.UltraSonicAppWidgetProvider4x4;
import org.moire.ultrasonic.receiver.MediaButtonIntentReceiver;
import org.moire.ultrasonic.util.CancellableTask;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.LRUCache;
import org.moire.ultrasonic.util.ShufflePlayBuffer;
import org.moire.ultrasonic.util.SimpleServiceBinder;
import org.moire.ultrasonic.util.StreamProxy;
import org.moire.ultrasonic.util.Util;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.moire.ultrasonic.domain.PlayerState.COMPLETED;
import static org.moire.ultrasonic.domain.PlayerState.DOWNLOADING;
import static org.moire.ultrasonic.domain.PlayerState.IDLE;
import static org.moire.ultrasonic.domain.PlayerState.PAUSED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARED;
import static org.moire.ultrasonic.domain.PlayerState.PREPARING;
import static org.moire.ultrasonic.domain.PlayerState.STARTED;
import static org.moire.ultrasonic.domain.PlayerState.STOPPED;

/**
 * @author Sindre Mehus, Joshua Bahnsen
 * @version $Id$
 */
public class DownloadServiceImpl extends Service implements DownloadService
{
	private static final String TAG = DownloadServiceImpl.class.getSimpleName();

	public static final String CMD_PLAY = "org.moire.ultrasonic.CMD_PLAY";
	public static final String CMD_TOGGLEPAUSE = "org.moire.ultrasonic.CMD_TOGGLEPAUSE";
	public static final String CMD_PAUSE = "org.moire.ultrasonic.CMD_PAUSE";
	public static final String CMD_STOP = "org.moire.ultrasonic.CMD_STOP";
	public static final String CMD_PREVIOUS = "org.moire.ultrasonic.CMD_PREVIOUS";
	public static final String CMD_NEXT = "org.moire.ultrasonic.CMD_NEXT";

    private static final int NOTIFICATION_ID = 3033;

	private final IBinder binder = new SimpleServiceBinder<DownloadService>(this);
	private Looper mediaPlayerLooper;
	private MediaPlayer mediaPlayer;
	private MediaPlayer nextMediaPlayer;
	private boolean nextSetup;
	private final List<DownloadFile> downloadList = new ArrayList<DownloadFile>();
	private final List<DownloadFile> backgroundDownloadList = new ArrayList<DownloadFile>();
	private final Handler handler = new Handler();
	private Handler mediaPlayerHandler;
	private final DownloadServiceLifecycleSupport lifecycleSupport = new DownloadServiceLifecycleSupport(this);
	private final ShufflePlayBuffer shufflePlayBuffer = new ShufflePlayBuffer(this);

	private final LRUCache<MusicDirectory.Entry, DownloadFile> downloadFileCache = new LRUCache<MusicDirectory.Entry, DownloadFile>(100);
	private final List<DownloadFile> cleanupCandidates = new ArrayList<DownloadFile>();
	private final Scrobbler scrobbler = new Scrobbler();
	private final JukeboxService jukeboxService = new JukeboxService(this);

	private DownloadFile currentPlaying;
	private DownloadFile nextPlaying;
	private DownloadFile currentDownloading;
	private CancellableTask bufferTask;
	private CancellableTask nextPlayingTask;
	private PlayerState playerState = IDLE;
	private PlayerState nextPlayerState = IDLE;
	private boolean shufflePlay;
	private long revision;
	private static DownloadService instance;
	private String suggestedPlaylistName;
	private PowerManager.WakeLock wakeLock;
	private boolean keepScreenOn;
	private int cachedPosition;

	private static boolean equalizerAvailable;
	private static boolean visualizerAvailable;
	private EqualizerController equalizerController;
	private VisualizerController visualizerController;
	private boolean showVisualization;
	private boolean jukeboxEnabled;
	private PositionCache positionCache;
	private StreamProxy proxy;
	public RemoteControlClient remoteControlClient;
	private AudioManager audioManager;
	private int secondaryProgress = -1;
	private boolean autoPlayStart;
	private final static int lockScreenBitmapSize = 500;

	static
	{
		try
		{
			EqualizerController.checkAvailable();
			equalizerAvailable = true;
		}
		catch (Throwable t)
		{
			equalizerAvailable = false;
		}
	}

	static
	{
		try
		{
			VisualizerController.checkAvailable();
			visualizerAvailable = true;
		}
		catch (Throwable t)
		{
			visualizerAvailable = false;
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onCreate()
	{
		super.onCreate();

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("DownloadServiceImpl");

				Looper.prepare();

				if (mediaPlayer != null)
				{
					mediaPlayer.release();
				}

				mediaPlayer = new MediaPlayer();
				mediaPlayer.setWakeMode(DownloadServiceImpl.this, PowerManager.PARTIAL_WAKE_LOCK);

				mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
				{
					@Override
					public boolean onError(MediaPlayer mediaPlayer, int what, int more)
					{
						handleError(new Exception(String.format("MediaPlayer error: %d (%d)", what, more)));
						return false;
					}
				});

				try
				{
					Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
					i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
					i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
					sendBroadcast(i);
				}
				catch (Throwable e)
				{
					// Froyo or lower
				}

				mediaPlayerLooper = Looper.myLooper();
				mediaPlayerHandler = new Handler(mediaPlayerLooper);
				Looper.loop();
			}
		}).start();

		audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		setUpRemoteControlClient();

		if (equalizerAvailable)
		{
			equalizerController = new EqualizerController(this, mediaPlayer);
			if (!equalizerController.isAvailable())
			{
				equalizerController = null;
			}
			else
			{
				equalizerController.loadSettings();
			}
		}
		if (visualizerAvailable)
		{
			visualizerController = new VisualizerController(mediaPlayer);
			if (!visualizerController.isAvailable())
			{
				visualizerController = null;
			}
		}

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
		wakeLock.setReferenceCounted(false);

		instance = this;
		lifecycleSupport.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		super.onStartCommand(intent, flags, startId);
		lifecycleSupport.onStart(intent);
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		try
		{
			instance = null;
			lifecycleSupport.onDestroy();
			mediaPlayer.release();

			if (nextMediaPlayer != null)
			{
				nextMediaPlayer.release();
			}

			mediaPlayerLooper.quit();
			shufflePlayBuffer.shutdown();

			if (equalizerController != null)
			{
				equalizerController.release();
			}

			if (visualizerController != null)
			{
				visualizerController.release();
			}

			if (bufferTask != null)
			{
				bufferTask.cancel();
			}

			if (nextPlayingTask != null)
			{
				nextPlayingTask.cancel();
			}

			Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
			i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getAudioSessionId());
			i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
			sendBroadcast(i);

			audioManager.unregisterRemoteControlClient(remoteControlClient);
			clearRemoteControl();

			wakeLock.release();
		}
		catch (Throwable ignored)
		{
		}
	}

	public static DownloadService getInstance()
	{
		return instance;
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	@Override
	public synchronized void download(List<MusicDirectory.Entry> songs, boolean save, boolean autoplay, boolean playNext, boolean shuffle, boolean newPlaylist)
	{
		shufflePlay = false;
		int offset = 1;

		if (songs.isEmpty())
		{
			return;
		}

		if (newPlaylist)
		{
			downloadList.clear();
		}

		if (playNext)
		{
			if (autoplay && getCurrentPlayingIndex() >= 0)
			{
				offset = 0;
			}

			for (MusicDirectory.Entry song : songs)
			{
				DownloadFile downloadFile = new DownloadFile(this, song, save);
				downloadList.add(getCurrentPlayingIndex() + offset, downloadFile);
				offset++;
			}

			revision++;
		}
		else
		{
			int size = size();
			int index = getCurrentPlayingIndex();

			for (MusicDirectory.Entry song : songs)
			{
				DownloadFile downloadFile = new DownloadFile(this, song, save);
				downloadList.add(downloadFile);
			}

			if (!autoplay && (size - 1) == index)
			{
				setNextPlaying();
			}

			revision++;
		}

		updateJukeboxPlaylist();

		if (shuffle) shuffle();

		if (autoplay)
		{
			play(0);
		}
		else
		{
			if (currentPlaying == null)
			{
				currentPlaying = downloadList.get(0);
				currentPlaying.setPlaying(true);
			}

			checkDownloads();
		}

		lifecycleSupport.serializeDownloadQueue();
	}

	@Override
	public synchronized void downloadBackground(List<MusicDirectory.Entry> songs, boolean save)
	{
		for (MusicDirectory.Entry song : songs)
		{
			DownloadFile downloadFile = new DownloadFile(this, song, save);
			backgroundDownloadList.add(downloadFile);
		}

		revision++;

		checkDownloads();
		lifecycleSupport.serializeDownloadQueue();
	}

	private void updateJukeboxPlaylist()
	{
		if (jukeboxEnabled)
		{
			jukeboxService.updatePlaylist();
		}
	}

	@Override
	public void restore(List<MusicDirectory.Entry> songs, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay, boolean newPlaylist)
	{
		download(songs, false, false, false, false, newPlaylist);

		if (currentPlayingIndex != -1)
		{
			while (mediaPlayer == null)
			{
				Util.sleepQuietly(50L);
			}

			play(currentPlayingIndex, autoPlayStart);

			if (currentPlaying != null)
			{
				if (autoPlay && jukeboxEnabled)
				{
					jukeboxService.skip(getCurrentPlayingIndex(), currentPlayingPosition / 1000);
				}
				else
				{
					if (currentPlaying.isCompleteFileAvailable())
					{
						doPlay(currentPlaying, currentPlayingPosition, autoPlay);
					}
				}
			}

			autoPlayStart = false;
		}
	}

	@Override
	public void stopJukeboxService()
	{
		jukeboxService.stopJukeboxService();
	}

	@Override
	public void startJukeboxService()
	{
		jukeboxService.startJukeboxService();
	}

	@Override
	public synchronized void setShufflePlayEnabled(boolean enabled)
	{
		shufflePlay = enabled;
		if (shufflePlay)
		{
			clear();
			checkDownloads();
		}
	}

	@Override
	public boolean isShufflePlayEnabled()
	{
		return shufflePlay;
	}

	@Override
	public synchronized void shuffle()
	{
		Collections.shuffle(downloadList);
		if (currentPlaying != null)
		{
			downloadList.remove(getCurrentPlayingIndex());
			downloadList.add(0, currentPlaying);
		}
		revision++;
		lifecycleSupport.serializeDownloadQueue();
		updateJukeboxPlaylist();
		setNextPlaying();
	}

	@Override
	public RepeatMode getRepeatMode()
	{
		return Util.getRepeatMode(this);
	}

	@Override
	public void setRepeatMode(RepeatMode repeatMode)
	{
		Util.setRepeatMode(this, repeatMode);
		setNextPlaying();
	}

	@Override
	public boolean getKeepScreenOn()
	{
		return keepScreenOn;
	}

	@Override
	public void setKeepScreenOn(boolean keepScreenOn)
	{
		this.keepScreenOn = keepScreenOn;
	}

	@Override
	public boolean getShowVisualization()
	{
		return showVisualization;
	}

	@Override
	public void setShowVisualization(boolean showVisualization)
	{
		this.showVisualization = showVisualization;
	}

	@Override
	public synchronized DownloadFile forSong(MusicDirectory.Entry song)
	{
		for (DownloadFile downloadFile : downloadList)
		{
			if (downloadFile.getSong().equals(song) && ((downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && downloadFile.getPartialFile().exists()) || downloadFile.isWorkDone()))
			{
				return downloadFile;
			}
		}
		for (DownloadFile downloadFile : backgroundDownloadList)
		{
			if (downloadFile.getSong().equals(song))
			{
				return downloadFile;
			}
		}

		DownloadFile downloadFile = downloadFileCache.get(song);
		if (downloadFile == null)
		{
			downloadFile = new DownloadFile(this, song, false);
			downloadFileCache.put(song, downloadFile);
		}
		return downloadFile;
	}

	@Override
	public synchronized void clear()
	{
		clear(true);
	}

	@Override
	public synchronized void clearBackground()
	{
		if (currentDownloading != null && backgroundDownloadList.contains(currentDownloading))
		{
			currentDownloading.cancelDownload();
			currentDownloading = null;
		}
		backgroundDownloadList.clear();
	}

	@Override
	public synchronized void clearIncomplete()
	{
		reset();
		Iterator<DownloadFile> iterator = downloadList.iterator();

		while (iterator.hasNext())
		{
			DownloadFile downloadFile = iterator.next();
			if (!downloadFile.isCompleteFileAvailable())
			{
				iterator.remove();
			}
		}

		lifecycleSupport.serializeDownloadQueue();
		updateJukeboxPlaylist();
	}

	@Override
	public synchronized int size()
	{
		return downloadList.size();
	}

	public synchronized void clear(boolean serialize)
	{
		reset();
		downloadList.clear();
		revision++;
		if (currentDownloading != null)
		{
			currentDownloading.cancelDownload();
			currentDownloading = null;
		}
		setCurrentPlaying(null);

		if (serialize)
		{
			lifecycleSupport.serializeDownloadQueue();
		}
		updateJukeboxPlaylist();
		setNextPlaying();
	}

	@Override
	public synchronized void remove(int which)
	{
		downloadList.remove(which);
	}

	@Override
	public synchronized void remove(DownloadFile downloadFile)
	{
		if (downloadFile == currentDownloading)
		{
			currentDownloading.cancelDownload();
			currentDownloading = null;
		}
		if (downloadFile == currentPlaying)
		{
			reset();
			setCurrentPlaying(null);
		}
		downloadList.remove(downloadFile);
		backgroundDownloadList.remove(downloadFile);
		revision++;
		lifecycleSupport.serializeDownloadQueue();
		updateJukeboxPlaylist();
		if (downloadFile == nextPlaying)
		{
			setNextPlaying();
		}
	}

	@Override
	public synchronized void delete(List<MusicDirectory.Entry> songs)
	{
		for (MusicDirectory.Entry song : songs)
		{
			forSong(song).delete();
		}
	}

	@Override
	public synchronized void unpin(List<MusicDirectory.Entry> songs)
	{
		for (MusicDirectory.Entry song : songs)
		{
			forSong(song).unpin();
		}
	}

	synchronized void setCurrentPlaying(int currentPlayingIndex)
	{
		try
		{
			setCurrentPlaying(downloadList.get(currentPlayingIndex));
		}
		catch (IndexOutOfBoundsException x)
		{
			// Ignored
		}
	}

	synchronized void setCurrentPlaying(DownloadFile currentPlaying)
	{
		this.currentPlaying = currentPlaying;

		if (currentPlaying != null)
		{
			Util.broadcastNewTrackInfo(this, currentPlaying.getSong());
			Util.broadcastA2dpMetaDataChange(this, instance);
		}
		else
		{
			Util.broadcastNewTrackInfo(this, null);
			Util.broadcastA2dpMetaDataChange(this, null);
		}

		updateRemoteControl();

		// Update widget
		UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, true);
		UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(this, this, playerState == PlayerState.STARTED, false);
		SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

		if (currentPlaying != null)
		{
			if (tabInstance != null) {
                if (Util.isNotificationEnabled(this)) {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification());
                }
				tabInstance.showNowPlaying();
			}
		}
		else
		{
			if (tabInstance != null)
			{
				stopForeground(true);
				tabInstance.hideNowPlaying();
			}
		}
	}

	synchronized void setNextPlaying()
	{
		boolean gaplessPlayback = Util.getGaplessPlaybackPreference(DownloadServiceImpl.this);

		if (!gaplessPlayback)
		{
			nextPlaying = null;
			nextPlayerState = IDLE;
			return;
		}

		int index = getCurrentPlayingIndex();

		if (index != -1)
		{
			switch (getRepeatMode())
			{
				case OFF:
					index += 1;
					break;
				case ALL:
					index = (index + 1) % size();
					break;
				case SINGLE:
					break;
				default:
					break;
			}
		}

		nextSetup = false;
		if (nextPlayingTask != null)
		{
			nextPlayingTask.cancel();
			nextPlayingTask = null;
		}

		if (index < size() && index != -1)
		{
			nextPlaying = downloadList.get(index);
			nextPlayingTask = new CheckCompletionTask(nextPlaying);
			nextPlayingTask.start();
		}
		else
		{
			nextPlaying = null;
			setNextPlayerState(IDLE);
		}
	}

	@Override
	public synchronized int getCurrentPlayingIndex()
	{
		return downloadList.indexOf(currentPlaying);
	}

	@Override
	public DownloadFile getCurrentPlaying()
	{
		return currentPlaying;
	}

	@Override
	public DownloadFile getCurrentDownloading()
	{
		return currentDownloading;
	}

	@Override
	public List<DownloadFile> getSongs()
	{
		return downloadList;
	}

	@Override
	public long getDownloadListDuration()
	{
		long totalDuration = 0;

		for (DownloadFile downloadFile : downloadList)
		{
			Entry entry = downloadFile.getSong();

			if (!entry.isDirectory())
			{
				if (entry.getArtist() != null)
				{
					Integer duration = entry.getDuration();

					if (duration != null)
					{
						totalDuration += duration;
					}
				}
			}
		}

		return totalDuration;
	}

	@Override
	public synchronized List<DownloadFile> getDownloads()
	{
		List<DownloadFile> temp = new ArrayList<DownloadFile>();
		temp.addAll(downloadList);
		temp.addAll(backgroundDownloadList);
		return temp;
	}

	@Override
	public List<DownloadFile> getBackgroundDownloads()
	{
		return backgroundDownloadList;
	}

	/**
	 * Plays either the current song (resume) or the first/next one in queue.
	 */
	public synchronized void play()
	{
		int current = getCurrentPlayingIndex();
		if (current == -1)
		{
			play(0);
		}
		else
		{
			play(current);
		}
	}

	@Override
	public synchronized void play(int index)
	{
		play(index, true);
	}

	private synchronized void play(int index, boolean start)
	{
		updateRemoteControl();

		if (index < 0 || index >= size())
		{
			resetPlayback();
		}
		else
		{
			if (nextPlayingTask != null)
			{
				nextPlayingTask.cancel();
				nextPlayingTask = null;
			}

			setCurrentPlaying(index);

			if (start)
			{
				if (jukeboxEnabled)
				{
					jukeboxService.skip(getCurrentPlayingIndex(), 0);
					setPlayerState(STARTED);
				}
				else
				{
					bufferAndPlay();
				}
			}

			checkDownloads();
			setNextPlaying();
		}
	}

	private synchronized void resetPlayback()
	{
		reset();
		setCurrentPlaying(null);
		lifecycleSupport.serializeDownloadQueue();
	}

	private synchronized void playNext()
	{
		MediaPlayer tmp = mediaPlayer;
		mediaPlayer = nextMediaPlayer;
		nextMediaPlayer = tmp;
		setCurrentPlaying(nextPlaying);
		setPlayerState(PlayerState.STARTED);
		setupHandlers(currentPlaying, false);
		setNextPlaying();

		// Proxy should not be being used here since the next player was already setup to play
		if (proxy != null)
		{
			proxy.stop();
			proxy = null;
		}
	}

	/**
	 * Plays or resumes the playback, depending on the current player state.
	 */
	@Override
	public synchronized void togglePlayPause()
	{
		if (playerState == PAUSED || playerState == COMPLETED || playerState == STOPPED)
		{
			start();
		}
		else if (playerState == IDLE)
		{
			autoPlayStart = true;
			play();
		}
		else if (playerState == STARTED)
		{
			pause();
		}
	}

	@Override
	public synchronized void seekTo(int position)
	{
		try
		{
			if (jukeboxEnabled)
			{
				jukeboxService.skip(getCurrentPlayingIndex(), position / 1000);
			}
			else
			{
				mediaPlayer.seekTo(position);
				cachedPosition = position;

				updateRemoteControl();
			}
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void previous()
	{
		int index = getCurrentPlayingIndex();
		if (index == -1)
		{
			return;
		}

		// Restart song if played more than five seconds.
		if (getPlayerPosition() > 5000 || index == 0)
		{
			play(index);
		}
		else
		{
			play(index - 1);
		}
	}

	@Override
	public synchronized void next()
	{
		int index = getCurrentPlayingIndex();
		if (index != -1)
		{
			play(index + 1);
		}
	}

	private void onSongCompleted()
	{
		int index = getCurrentPlayingIndex();

		if (currentPlaying != null)
		{
			final Entry song = currentPlaying.getSong();

			if (song != null && song.getBookmarkPosition() > 0 && Util.getShouldClearBookmark(this))
			{
				MusicService musicService = MusicServiceFactory.getMusicService(DownloadServiceImpl.this);
				try
				{
					musicService.deleteBookmark(song.getId(), DownloadServiceImpl.this, null);
				}
				catch (Exception ignored)
				{

				}
			}
		}

		if (index != -1)
		{
			switch (getRepeatMode())
			{
				case OFF:
					if (index + 1 < 0 || index + 1 >= size())
					{
						if (Util.getShouldClearPlaylist(this))
						{
							clear();
						}

						resetPlayback();
						break;
					}

					play(index + 1);
					break;
				case ALL:
					play((index + 1) % size());
					break;
				case SINGLE:
					play(index);
					break;
				default:
					break;
			}
		}
	}

	@Override
	public synchronized void pause()
	{
		try
		{
			if (playerState == STARTED)
			{
				if (jukeboxEnabled)
				{
					jukeboxService.stop();
				}
				else
				{
					mediaPlayer.pause();
				}
				setPlayerState(PAUSED);
			}
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void stop()
	{
		try
		{
			if (playerState == STARTED)
			{
				if (jukeboxEnabled)
				{
					jukeboxService.stop();
				}
				else
				{
					mediaPlayer.pause();
				}
				setPlayerState(STOPPED);
			}
			else
			{
				setPlayerState(STOPPED);
			}
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void start()
	{
		try
		{
			if (jukeboxEnabled)
			{
				jukeboxService.start();
			}
			else
			{
				mediaPlayer.start();
			}
			setPlayerState(STARTED);
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized void reset()
	{
		if (bufferTask != null)
		{
			bufferTask.cancel();
		}
		try
		{
			setPlayerState(IDLE);
			mediaPlayer.setOnErrorListener(null);
			mediaPlayer.setOnCompletionListener(null);
			mediaPlayer.reset();
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	@Override
	public synchronized int getPlayerPosition()
	{
		try
		{
			if (playerState == IDLE || playerState == DOWNLOADING || playerState == PREPARING)
			{
				return 0;
			}

			return jukeboxEnabled ? jukeboxService.getPositionSeconds() * 1000 : cachedPosition;
		}
		catch (Exception x)
		{
			handleError(x);
			return 0;
		}
	}

	@Override
	public synchronized int getPlayerDuration()
	{
		if (currentPlaying != null)
		{
			Integer duration = currentPlaying.getSong().getDuration();
			if (duration != null)
			{
				return duration * 1000;
			}
		}
		if (playerState != IDLE && playerState != DOWNLOADING && playerState != PlayerState.PREPARING)
		{
			try
			{
				return mediaPlayer.getDuration();
			}
			catch (Exception x)
			{
				handleError(x);
			}
		}
		return 0;
	}

	@Override
	public PlayerState getPlayerState()
	{
		return playerState;
	}

	synchronized void setPlayerState(PlayerState playerState)
	{
		Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), playerState.name(), currentPlaying));

		this.playerState = playerState;

		if (this.playerState == PAUSED)
		{
			lifecycleSupport.serializeDownloadQueue();
		}

		if (this.playerState == PlayerState.STARTED)
		{
			Util.requestAudioFocus(this);
		}

		boolean showWhenPaused = (this.playerState != PlayerState.STOPPED && Util.isNotificationAlwaysEnabled(this));
		boolean show = this.playerState == PlayerState.STARTED || showWhenPaused;

		Util.broadcastPlaybackStatusChange(this, this.playerState);
		Util.broadcastA2dpPlayStatusChange(this, this.playerState, instance);

		if (this.playerState == PlayerState.STARTED || this.playerState == PlayerState.PAUSED)
		{
			// Set remote control
			updateRemoteControl();
		}

		// Update widget
		UltraSonicAppWidgetProvider4x1.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x2.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, true);
		UltraSonicAppWidgetProvider4x3.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, false);
		UltraSonicAppWidgetProvider4x4.getInstance().notifyChange(this, this, this.playerState == PlayerState.STARTED, false);
		SubsonicTabActivity tabInstance = SubsonicTabActivity.getInstance();

		if (show)
		{
			if (tabInstance != null)
			{
				// Only update notification is player state is one that will change the icon
				if (this.playerState == PlayerState.STARTED || this.playerState == PlayerState.PAUSED)
				{
					updateNotification();
					tabInstance.showNowPlaying();
				}
			}
		}
		else
		{
			if (tabInstance != null)
			{
				stopForeground(true);
				tabInstance.hideNowPlaying();
			}
		}

		if (this.playerState == STARTED)
		{
			scrobbler.scrobble(this, currentPlaying, false);
		}
		else if (this.playerState == COMPLETED)
		{
			scrobbler.scrobble(this, currentPlaying, true);
		}

		if (playerState == STARTED && positionCache == null)
		{
			positionCache = new PositionCache();
			Thread thread = new Thread(positionCache);
			thread.start();
		}
		else if (playerState != STARTED && positionCache != null)
		{
			positionCache.stop();
			positionCache = null;
		}
	}

	private void setPlayerStateCompleted()
	{
		Log.i(TAG, String.format("%s -> %s (%s)", this.playerState.name(), PlayerState.COMPLETED, currentPlaying));
		this.playerState = PlayerState.COMPLETED;

		if (positionCache != null)
		{
			positionCache.stop();
			positionCache = null;
		}

		scrobbler.scrobble(this, currentPlaying, true);
	}

	private synchronized void setNextPlayerState(PlayerState playerState)
	{
		Log.i(TAG, String.format("Next: %s -> %s (%s)", this.nextPlayerState.name(), playerState.name(), nextPlaying));
		this.nextPlayerState = playerState;
	}

	@Override
	public void setSuggestedPlaylistName(String name)
	{
		this.suggestedPlaylistName = name;
	}

	@Override
	public String getSuggestedPlaylistName()
	{
		return suggestedPlaylistName;
	}

	@Override
	public boolean getEqualizerAvailable()
	{
		return equalizerAvailable;
	}

	@Override
	public boolean getVisualizerAvailable()
	{
		return visualizerAvailable;
	}

	@Override
	public EqualizerController getEqualizerController()
	{
		if (equalizerAvailable && equalizerController == null)
		{
			equalizerController = new EqualizerController(this, mediaPlayer);
			if (!equalizerController.isAvailable())
			{
				equalizerController = null;
			}
			else
			{
				equalizerController.loadSettings();
			}
		}
		return equalizerController;
	}

	@Override
	public VisualizerController getVisualizerController()
	{
		if (visualizerAvailable && visualizerController == null)
		{
			visualizerController = new VisualizerController(mediaPlayer);
			if (!visualizerController.isAvailable())
			{
				visualizerController = null;
			}
		}
		return visualizerController;
	}

	@Override
	public boolean isJukeboxEnabled()
	{
		return jukeboxEnabled;
	}

	@Override
	public boolean isJukeboxAvailable()
	{
		MusicService musicService = MusicServiceFactory.getMusicService(DownloadServiceImpl.this);

		try
		{
			String username = Util.getUserName(DownloadServiceImpl.this, Util.getActiveServer(DownloadServiceImpl.this));
			UserInfo user = musicService.getUser(username, DownloadServiceImpl.this, null);
			return user.getJukeboxRole();
		}
		catch (Exception e)
		{
			Log.w(TAG, "Error getting user information", e);
		}

		return false;
	}

	@Override
	public boolean isSharingAvailable()
	{
		MusicService musicService = MusicServiceFactory.getMusicService(DownloadServiceImpl.this);

		try
		{
			String username = Util.getUserName(DownloadServiceImpl.this, Util.getActiveServer(DownloadServiceImpl.this));
			UserInfo user = musicService.getUser(username, DownloadServiceImpl.this, null);
			return user.getShareRole();
		}
		catch (Exception e)
		{
			Log.w(TAG, "Error getting user information", e);
		}

		return false;
	}

	@Override
	public void setJukeboxEnabled(boolean jukeboxEnabled)
	{
		this.jukeboxEnabled = jukeboxEnabled;
		jukeboxService.setEnabled(jukeboxEnabled);

		if (jukeboxEnabled)
		{
			jukeboxService.startJukeboxService();

			reset();

			// Cancel current download, if necessary.
			if (currentDownloading != null)
			{
				currentDownloading.cancelDownload();
			}
		}
		else
		{
			jukeboxService.stopJukeboxService();
		}
	}

	@Override
	public void adjustJukeboxVolume(boolean up)
	{
		jukeboxService.adjustVolume(up);
	}

	@SuppressLint("NewApi")
	public void setUpRemoteControlClient()
	{
		if (!Util.isLockScreenEnabled(this)) return;

		ComponentName componentName = new ComponentName(getPackageName(), MediaButtonIntentReceiver.class.getName());

		if (remoteControlClient == null)
		{
			final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
			mediaButtonIntent.setComponent(componentName);
			PendingIntent broadcast = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			remoteControlClient = new RemoteControlClient(broadcast);
			audioManager.registerRemoteControlClient(remoteControlClient);

			// Flags for the media transport control that this client supports.
			int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
					RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
					RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
					RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
					RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE |
					RemoteControlClient.FLAG_KEY_MEDIA_STOP;

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
			{
				flags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;

				remoteControlClient.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener()
				{
					@Override
					public long onGetPlaybackPosition()
					{
						return mediaPlayer.getCurrentPosition();
					}
				});

				remoteControlClient.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener()
				{
					@Override
					public void onPlaybackPositionUpdate(long newPositionMs)
					{
						seekTo((int) newPositionMs);
					}
				});
			}

			remoteControlClient.setTransportControlFlags(flags);
		}
	}

	private void clearRemoteControl()
	{
		if (remoteControlClient != null)
		{
			remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			audioManager.unregisterRemoteControlClient(remoteControlClient);
			remoteControlClient = null;
		}
	}

	private void updateRemoteControl()
	{
		if (!Util.isLockScreenEnabled(this))
		{
			clearRemoteControl();
			return;
		}

		if (remoteControlClient != null)
		{
			audioManager.unregisterRemoteControlClient(remoteControlClient);
			audioManager.registerRemoteControlClient(remoteControlClient);
		}
		else
		{
			setUpRemoteControlClient();
		}

		Log.i(TAG, String.format("In updateRemoteControl, playerState: %s [%d]", playerState, getPlayerPosition()));

		switch (playerState)
		{
			case STARTED:
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
				}
				else
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING, getPlayerPosition(), 1.0f);
				}
				break;
			default:
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
				}
				else
				{
					remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED, getPlayerPosition(), 1.0f);
				}
				break;
		}

		if (currentPlaying != null)
		{
			MusicDirectory.Entry currentSong = currentPlaying.getSong();

			Bitmap lockScreenBitmap = FileUtil.getAlbumArtBitmap(this, currentSong, Util.getMinDisplayMetric(this), true);

			String artist = currentSong.getArtist();
			String album = currentSong.getAlbum();
			String title = currentSong.getTitle();
			Integer currentSongDuration = currentSong.getDuration();
			Long duration = 0L;

			if (currentSongDuration != null) duration = (long) currentSongDuration * 1000;

			remoteControlClient.editMetadata(true).putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist).putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, artist).putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album).putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title).putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
					.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, lockScreenBitmap).apply();
		}
	}

	private synchronized void bufferAndPlay()
	{
		if (playerState != PREPARED)
		{
			reset();

			bufferTask = new BufferTask(currentPlaying, 0);
			bufferTask.start();
		}
		else
		{
			doPlay(currentPlaying, 0, true);
		}
	}

	private synchronized void doPlay(final DownloadFile downloadFile, final int position, final boolean start)
	{
		try
		{
			downloadFile.setPlaying(false);
			//downloadFile.setPlaying(true);
			final File file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteFile() : downloadFile.getPartialFile();
			boolean partial = file.equals(downloadFile.getPartialFile());
			downloadFile.updateModificationDate();

			mediaPlayer.setOnCompletionListener(null);
			secondaryProgress = -1; // Ensure seeking in non StreamProxy playback works
			mediaPlayer.reset();
			setPlayerState(IDLE);
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			String dataSource = file.getPath();

			if (partial)
			{
				if (proxy == null)
				{
					proxy = new StreamProxy(this);
					proxy.start();
				}

				dataSource = String.format("http://127.0.0.1:%d/%s", proxy.getPort(), URLEncoder.encode(dataSource, Constants.UTF_8));
				Log.i(TAG, String.format("Data Source: %s", dataSource));
			}
			else if (proxy != null)
			{
				proxy.stop();
				proxy = null;
			}

			Log.i(TAG, "Preparing media player");
			mediaPlayer.setDataSource(dataSource);
			setPlayerState(PREPARING);

			mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener()
			{
				@Override
				public void onBufferingUpdate(MediaPlayer mp, int percent)
				{
					SeekBar progressBar = DownloadActivity.getProgressBar();
					MusicDirectory.Entry song = downloadFile.getSong();

					if (percent == 100)
					{
						if (progressBar != null)
						{
							progressBar.setSecondaryProgress(100 * progressBar.getMax());
						}

						mp.setOnBufferingUpdateListener(null);
					}
					else if (progressBar != null && song.getTranscodedContentType() == null && Util.getMaxBitRate(DownloadServiceImpl.this) == 0)
					{
						secondaryProgress = (int) (((double) percent / (double) 100) * progressBar.getMax());
						progressBar.setSecondaryProgress(secondaryProgress);
					}
				}
			});

			mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
			{
				@Override
				public void onPrepared(MediaPlayer mp)
				{
					Log.i(TAG, "Media player prepared");

					setPlayerState(PREPARED);

					SeekBar progressBar = DownloadActivity.getProgressBar();

					if (progressBar != null && downloadFile.isWorkDone())
					{
						// Populate seek bar secondary progress if we have a complete file for consistency
						DownloadActivity.getProgressBar().setSecondaryProgress(100 * progressBar.getMax());
					}

					synchronized (DownloadServiceImpl.this)
					{
						if (position != 0)
						{
							Log.i(TAG, String.format("Restarting player from position %d", position));
							seekTo(position);
						}
						cachedPosition = position;

						if (start)
						{
							mediaPlayer.start();
							setPlayerState(STARTED);
						}
						else
						{
							setPlayerState(PAUSED);
						}
					}

					lifecycleSupport.serializeDownloadQueue();
				}
			});

			setupHandlers(downloadFile, partial);

			mediaPlayer.prepareAsync();
		}
		catch (Exception x)
		{
			handleError(x);
		}
	}

	private synchronized void setupNext(final DownloadFile downloadFile)
	{
		try
		{
			final File file = downloadFile.isCompleteFileAvailable() ? downloadFile.getCompleteFile() : downloadFile.getPartialFile();

			if (nextMediaPlayer != null)
			{
				nextMediaPlayer.setOnCompletionListener(null);
				nextMediaPlayer.release();
				nextMediaPlayer = null;
			}

			nextMediaPlayer = new MediaPlayer();
			nextMediaPlayer.setWakeMode(DownloadServiceImpl.this, PowerManager.PARTIAL_WAKE_LOCK);

			try
			{
				nextMediaPlayer.setAudioSessionId(mediaPlayer.getAudioSessionId());
			}
			catch (Throwable e)
			{
				nextMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			}

			nextMediaPlayer.setDataSource(file.getPath());
			setNextPlayerState(PREPARING);

			nextMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
			{
				@Override
				@SuppressLint("NewApi")
				public void onPrepared(MediaPlayer mp)
				{
					try
					{
						setNextPlayerState(PREPARED);

						if (Util.getGaplessPlaybackPreference(DownloadServiceImpl.this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED))
						{
							mediaPlayer.setNextMediaPlayer(nextMediaPlayer);
							nextSetup = true;
						}
					}
					catch (Exception x)
					{
						handleErrorNext(x);
					}
				}
			});

			nextMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
			{
				@Override
				public boolean onError(MediaPlayer mediaPlayer, int what, int extra)
				{
					Log.w(TAG, String.format("Error on playing next (%d, %d): %s", what, extra, downloadFile));
					return true;
				}
			});

			nextMediaPlayer.prepareAsync();
		}
		catch (Exception x)
		{
			handleErrorNext(x);
		}
	}

	private void setupHandlers(final DownloadFile downloadFile, final boolean isPartial)
	{
		mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener()
		{
			@Override
			public boolean onError(MediaPlayer mediaPlayer, int what, int extra)
			{
				Log.w(TAG, String.format("Error on playing file (%d, %d): %s", what, extra, downloadFile));
				int pos = cachedPosition;
				reset();
				downloadFile.setPlaying(false);
				doPlay(downloadFile, pos, true);
				downloadFile.setPlaying(true);
				return true;
			}
		});

		final int duration = downloadFile.getSong().getDuration() == null ? 0 : downloadFile.getSong().getDuration() * 1000;

		mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
		{
			@Override
			public void onCompletion(MediaPlayer mediaPlayer)
			{
				// Acquire a temporary wakelock, since when we return from
				// this callback the MediaPlayer will release its wakelock
				// and allow the device to go to sleep.
				wakeLock.acquire(60000);

				int pos = cachedPosition;
				Log.i(TAG, String.format("Ending position %d of %d", pos, duration));

				if (!isPartial || (downloadFile.isWorkDone() && (Math.abs(duration - pos) < 1000)))
				{
					setPlayerStateCompleted();

					if (Util.getGaplessPlaybackPreference(DownloadServiceImpl.this) && nextPlaying != null && nextPlayerState == PlayerState.PREPARED)
					{
						if (!nextSetup)
						{
							playNext();
						}
						else
						{
							nextSetup = false;
							playNext();
						}
					}
					else
					{
						onSongCompleted();
					}

					return;
				}

				synchronized (DownloadServiceImpl.this)
				{
					if (downloadFile.isWorkDone())
					{
						// Complete was called early even though file is fully buffered
						Log.i(TAG, String.format("Requesting restart from %d of %d", pos, duration));
						reset();
						downloadFile.setPlaying(false);
						doPlay(downloadFile, pos, true);
						downloadFile.setPlaying(true);
					}
					else
					{
						Log.i(TAG, String.format("Requesting restart from %d of %d", pos, duration));
						reset();
						bufferTask = new BufferTask(downloadFile, pos);
						bufferTask.start();
					}
				}
			}
		});
	}

	@Override
	public void setVolume(float volume)
	{
		if (mediaPlayer != null)
		{
			mediaPlayer.setVolume(volume, volume);
		}
	}

	@Override
	public synchronized void swap(boolean mainList, int from, int to)
	{
		List<DownloadFile> list = mainList ? downloadList : backgroundDownloadList;
		int max = list.size();

		if (to >= max)
		{
			to = max - 1;
		}
		else if (to < 0)
		{
			to = 0;
		}

		int currentPlayingIndex = getCurrentPlayingIndex();
		DownloadFile movedSong = list.remove(from);
		list.add(to, movedSong);

		if (jukeboxEnabled && mainList)
		{
			updateJukeboxPlaylist();
		}
		else if (mainList && (movedSong == nextPlaying || (currentPlayingIndex + 1) == to))
		{
			// Moving next playing or moving a song to be next playing
			setNextPlaying();
		}
	}

	private void handleError(Exception x)
	{
		Log.w(TAG, String.format("Media player error: %s", x), x);

		try
		{
			mediaPlayer.reset();
		}
		catch (Exception ex)
		{
			Log.w(TAG, String.format("Exception encountered when resetting media player: %s", ex), ex);
		}

		setPlayerState(IDLE);
	}

	private void handleErrorNext(Exception x)
	{
		Log.w(TAG, String.format("Next Media player error: %s", x), x);
		nextMediaPlayer.reset();
		setNextPlayerState(IDLE);
	}

	protected synchronized void checkDownloads()
	{
		if (!Util.isExternalStoragePresent() || !lifecycleSupport.isExternalStorageAvailable())
		{
			return;
		}

		if (shufflePlay)
		{
			checkShufflePlay();
		}

		if (jukeboxEnabled || !Util.isNetworkConnected(this))
		{
			return;
		}

		if (downloadList.isEmpty() && backgroundDownloadList.isEmpty())
		{
			return;
		}

		// Need to download current playing?
		if (currentPlaying != null && currentPlaying != currentDownloading && !currentPlaying.isWorkDone())
		{
			// Cancel current download, if necessary.
			if (currentDownloading != null)
			{
				currentDownloading.cancelDownload();
			}

			currentDownloading = currentPlaying;
			currentDownloading.download();
			cleanupCandidates.add(currentDownloading);
		}

		// Find a suitable target for download.
		else
		{
			if (currentDownloading == null || currentDownloading.isWorkDone() || currentDownloading.isFailed() && (!downloadList.isEmpty() || !backgroundDownloadList.isEmpty()))
			{
				currentDownloading = null;
				int n = size();

				int preloaded = 0;

				if (n != 0)
				{
					int start = currentPlaying == null ? 0 : getCurrentPlayingIndex();
					if (start == -1)
					{
						start = 0;
					}
					int i = start;
					do
					{
						DownloadFile downloadFile = downloadList.get(i);
						if (!downloadFile.isWorkDone())
						{
							if (downloadFile.shouldSave() || preloaded < Util.getPreloadCount(this))
							{
								currentDownloading = downloadFile;
								currentDownloading.download();
								cleanupCandidates.add(currentDownloading);
								if (i == (start + 1))
								{
									setNextPlayerState(DOWNLOADING);
								}
								break;
							}
						}
						else if (currentPlaying != downloadFile)
						{
							preloaded++;
						}

						i = (i + 1) % n;
					} while (i != start);
				}

				if ((preloaded + 1 == n || preloaded >= Util.getPreloadCount(this) || downloadList.isEmpty()) && !backgroundDownloadList.isEmpty())
				{
					for (int i = 0; i < backgroundDownloadList.size(); i++)
					{
						DownloadFile downloadFile = backgroundDownloadList.get(i);
						if (downloadFile.isWorkDone() && (!downloadFile.shouldSave() || downloadFile.isSaved()))
						{
							if (Util.getShouldScanMedia(this))
							{
								Util.scanMedia(this, downloadFile.getCompleteFile());
							}

							// Don't need to keep list like active song list
							backgroundDownloadList.remove(i);
							revision++;
							i--;
						}
						else
						{
							currentDownloading = downloadFile;
							currentDownloading.download();
							cleanupCandidates.add(currentDownloading);
							break;
						}
					}
				}
			}
		}

		// Delete obsolete .partial and .complete files.
		cleanup();
	}

	private synchronized void checkShufflePlay()
	{
		// Get users desired random playlist size
		int listSize = Util.getMaxSongs(this);
		boolean wasEmpty = downloadList.isEmpty();

		long revisionBefore = revision;

		// First, ensure that list is at least 20 songs long.
		int size = size();
		if (size < listSize)
		{
			for (MusicDirectory.Entry song : shufflePlayBuffer.get(listSize - size))
			{
				DownloadFile downloadFile = new DownloadFile(this, song, false);
				downloadList.add(downloadFile);
				revision++;
			}
		}

		int currIndex = currentPlaying == null ? 0 : getCurrentPlayingIndex();

		// Only shift playlist if playing song #5 or later.
		if (currIndex > 4)
		{
			int songsToShift = currIndex - 2;
			for (MusicDirectory.Entry song : shufflePlayBuffer.get(songsToShift))
			{
				downloadList.add(new DownloadFile(this, song, false));
				downloadList.get(0).cancelDownload();
				downloadList.remove(0);
				revision++;
			}
		}

		if (revisionBefore != revision)
		{
			updateJukeboxPlaylist();
		}

		if (wasEmpty && !downloadList.isEmpty())
		{
			play(0);
		}
	}

	@Override
	public long getDownloadListUpdateRevision()
	{
		return revision;
	}

	private synchronized void cleanup()
	{
		Iterator<DownloadFile> iterator = cleanupCandidates.iterator();
		while (iterator.hasNext())
		{
			DownloadFile downloadFile = iterator.next();
			if (downloadFile != currentPlaying && downloadFile != currentDownloading)
			{
				if (downloadFile.cleanup())
				{
					iterator.remove();
				}
			}
		}
	}

	@Override
	public void updateNotification()
	{
		if (Util.isNotificationEnabled(this)) {
			final NotificationManagerCompat notificationManager =
					NotificationManagerCompat.from(this);
			notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification());
		}
	}

    @SuppressWarnings("IconColors")
    private Notification buildForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_ultrasonic);

        builder.setAutoCancel(false);
        builder.setOngoing(true);
        builder.setWhen(System.currentTimeMillis());
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        RemoteViews contentView = new RemoteViews(this.getPackageName(), R.layout.notification);
        Util.linkButtons(this, contentView, false);
        RemoteViews bigView = new RemoteViews(this.getPackageName(), R.layout.notification_large);
        Util.linkButtons(this, bigView, false);

        builder.setContent(contentView);

        Intent notificationIntent = new Intent(this, DownloadActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0));

        if (playerState == PlayerState.PAUSED || playerState == PlayerState.IDLE) {
            contentView.setImageViewResource(R.id.control_play, R.drawable.media_start_normal_dark);
            bigView.setImageViewResource(R.id.control_play, R.drawable.media_start_normal_dark);
        } else if (playerState == PlayerState.STARTED) {
            contentView.setImageViewResource(R.id.control_play, R.drawable.media_pause_normal_dark);
            bigView.setImageViewResource(R.id.control_play, R.drawable.media_pause_normal_dark);
        }

        final Entry song = currentPlaying.getSong();
        final String title = song.getTitle();
        final String text = song.getArtist();
        final String album = song.getAlbum();
		final int rating = song.getUserRating() == null ? 0 : song.getUserRating();
        final int imageSize = Util.getNotificationImageSize(this);

        try {
            final Bitmap nowPlayingImage = FileUtil.getAlbumArtBitmap(this, currentPlaying.getSong(), imageSize, true);
            if (nowPlayingImage == null) {
                contentView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
                bigView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
            } else {
                contentView.setImageViewBitmap(R.id.notification_image, nowPlayingImage);
                bigView.setImageViewBitmap(R.id.notification_image, nowPlayingImage);
            }
        } catch (Exception x) {
            Log.w(TAG, "Failed to get notification cover art", x);
            contentView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
            bigView.setImageViewResource(R.id.notification_image, R.drawable.unknown_album);
        }

        contentView.setTextViewText(R.id.trackname, title);
        bigView.setTextViewText(R.id.trackname, title);
        contentView.setTextViewText(R.id.artist, text);
        bigView.setTextViewText(R.id.artist, text);
        contentView.setTextViewText(R.id.album, album);
        bigView.setTextViewText(R.id.album, album);

		boolean useFiveStarRating = KoinJavaComponent.get(FeatureStorage.class).isFeatureEnabled(Feature.FIVE_STAR_RATING);
		if (!useFiveStarRating)	bigView.setViewVisibility(R.id.notification_rating, View.INVISIBLE);
		else
		{
			bigView.setImageViewResource(R.id.notification_five_star_1, rating > 0 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
			bigView.setImageViewResource(R.id.notification_five_star_2, rating > 1 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
			bigView.setImageViewResource(R.id.notification_five_star_3, rating > 2 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
			bigView.setImageViewResource(R.id.notification_five_star_4, rating > 3 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
			bigView.setImageViewResource(R.id.notification_five_star_5, rating > 4 ? R.drawable.ic_star_full_dark : R.drawable.ic_star_hollow_dark);
		}

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification.bigContentView = bigView;
        }

        return notification;
    }

    public void setSongRating(final int rating)
	{
		if (!new FeatureStorage(this).isFeatureEnabled(Feature.FIVE_STAR_RATING))
			return;

		final Entry song = currentPlaying.getSong();
		song.setUserRating(rating);

		updateNotification();
	}

	private class BufferTask extends CancellableTask
	{
		private final DownloadFile downloadFile;
		private final int position;
		private final long expectedFileSize;
		private final File partialFile;

		public BufferTask(DownloadFile downloadFile, int position)
		{
			this.downloadFile = downloadFile;
			this.position = position;
			partialFile = downloadFile.getPartialFile();

			long bufferLength = Util.getBufferLength(DownloadServiceImpl.this);

			if (bufferLength == 0)
			{
				// Set to seconds in a day, basically infinity
				bufferLength = 86400L;
			}

			// Calculate roughly how many bytes BUFFER_LENGTH_SECONDS corresponds to.
			int bitRate = downloadFile.getBitRate();
			long byteCount = Math.max(100000, bitRate * 1024L / 8L * bufferLength);

			// Find out how large the file should grow before resuming playback.
			Log.i(TAG, String.format("Buffering from position %d and bitrate %d", position, bitRate));
			expectedFileSize = (position * bitRate / 8) + byteCount;
		}

		@Override
		public void execute()
		{
			setPlayerState(DOWNLOADING);

			while (!bufferComplete() && !Util.isOffline(DownloadServiceImpl.this))
			{
				Util.sleepQuietly(1000L);
				if (isCancelled())
				{
					return;
				}
			}
			doPlay(downloadFile, position, true);
		}

		private boolean bufferComplete()
		{
			boolean completeFileAvailable = downloadFile.isWorkDone();
			long size = partialFile.length();

			Log.i(TAG, String.format("Buffering %s (%d/%d, %s)", partialFile, size, expectedFileSize, completeFileAvailable));
			return completeFileAvailable || size >= expectedFileSize;
		}

		@Override
		public String toString()
		{
			return String.format("BufferTask (%s)", downloadFile);
		}
	}

	private class PositionCache implements Runnable
	{
		boolean isRunning = true;

		public void stop()
		{
			isRunning = false;
		}

		@Override
		public void run()
		{
			Thread.currentThread().setName("PositionCache");

			// Stop checking position before the song reaches completion
			while (isRunning)
			{
				try
				{
					if (mediaPlayer != null && playerState == STARTED)
					{
						cachedPosition = mediaPlayer.getCurrentPosition();
					}

					Util.sleepQuietly(25L);
				}
				catch (Exception e)
				{
					Log.w(TAG, "Crashed getting current position", e);
					isRunning = false;
					positionCache = null;
				}
			}
		}
	}

	private class CheckCompletionTask extends CancellableTask
	{
		private final DownloadFile downloadFile;
		private final File partialFile;

		public CheckCompletionTask(DownloadFile downloadFile)
		{
			super();
			setNextPlayerState(PlayerState.IDLE);

			this.downloadFile = downloadFile;

			partialFile = downloadFile != null ? downloadFile.getPartialFile() : null;
		}

		@Override
		public void execute()
		{
			Thread.currentThread().setName("CheckCompletionTask");

			if (downloadFile == null)
			{
				return;
			}

			// Do an initial sleep so this prepare can't compete with main prepare
			Util.sleepQuietly(5000L);

			while (!bufferComplete())
			{
				Util.sleepQuietly(5000L);

				if (isCancelled())
				{
					return;
				}
			}

			// Start the setup of the next media player
			mediaPlayerHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					setupNext(downloadFile);
				}
			});
		}

		private boolean bufferComplete()
		{
			boolean completeFileAvailable = downloadFile.isWorkDone();
			Log.i(TAG, String.format("Buffering next %s (%d)", partialFile, partialFile.length()));
			return completeFileAvailable && (playerState == PlayerState.STARTED || playerState == PlayerState.PAUSED);
		}

		@Override
		public String toString()
		{
			return String.format("CheckCompletionTask (%s)", downloadFile);
		}
	}
}