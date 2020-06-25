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
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import org.koin.java.standalone.KoinJavaComponent;
import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;
import org.moire.ultrasonic.domain.UserInfo;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.util.ShufflePlayBuffer;
import org.moire.ultrasonic.util.Util;

import java.util.Iterator;
import java.util.List;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus, Joshua Bahnsen
 * @version $Id$
 */
public class DownloadServiceImpl implements DownloadService
{
	private static final String TAG = DownloadServiceImpl.class.getSimpleName();

	private String suggestedPlaylistName;
	private boolean keepScreenOn;

	private boolean showVisualization;
	private boolean autoPlayStart;

	private Context context;
	public Lazy<JukeboxService> jukeboxService = inject(JukeboxService.class);
	private Lazy<DownloadQueueSerializer> downloadQueueSerializer = inject(DownloadQueueSerializer.class);
	private Lazy<ExternalStorageMonitor> externalStorageMonitor = inject(ExternalStorageMonitor.class);
	private final Downloader downloader;
	private final ShufflePlayBuffer shufflePlayBuffer;
	private final Player player;

	public DownloadServiceImpl(Context context, Downloader downloader, ShufflePlayBuffer shufflePlayBuffer,
							   Player player)
	{
		this.context = context;
		this.downloader = downloader;
		this.shufflePlayBuffer = shufflePlayBuffer;
		this.player = player;

		externalStorageMonitor.getValue().onCreate(new Runnable() {
			@Override
			public void run() {
				reset();
			}
		});

		int instance = Util.getActiveServer(context);
		setJukeboxEnabled(Util.getJukeboxEnabled(context, instance));

		Log.i(TAG, "DownloadServiceImpl created");
	}

	public void onDestroy()
	{
		externalStorageMonitor.getValue().onDestroy();
		context.stopService(new Intent(context, MediaPlayerService.class));
		Log.i(TAG, "DownloadServiceImpl destroyed");
	}

	private void executeOnStartedMediaPlayerService(final Consumer<MediaPlayerService> taskToExecute)
	{
		Thread t = new Thread()
		{
			public void run()
			{
				MediaPlayerService instance = MediaPlayerService.getInstance(context);
				taskToExecute.accept(instance);
			}
		};
		t.start();
	}

	@Override
	public synchronized void restore(List<MusicDirectory.Entry> songs, final int currentPlayingIndex, final int currentPlayingPosition, final boolean autoPlay, boolean newPlaylist)
	{
		download(songs, false, false, false, false, newPlaylist);

		if (currentPlayingIndex != -1)
		{
			executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
				@Override
				public void accept(MediaPlayerService mediaPlayerService) {
					mediaPlayerService.play(currentPlayingIndex, autoPlayStart);
				}
			});

			if (player.currentPlaying != null)
			{
				if (autoPlay && jukeboxService.getValue().isEnabled())
				{
					jukeboxService.getValue().skip(downloader.getCurrentPlayingIndex(), currentPlayingPosition / 1000);
				}
				else
				{
					if (player.currentPlaying.isCompleteFileAvailable())
					{
						executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
							@Override
							public void accept(MediaPlayerService mediaPlayerService) {
								player.doPlay(player.currentPlaying, currentPlayingPosition, autoPlay);
							}
						});
					}
				}
			}
			autoPlayStart = false;
		}
	}

	@Override
	public synchronized void play(final int index)
	{
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.play(index, true);
			}
		});
	}

	public synchronized void play()
	{
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.play();
			}
		});
	}

	@Override
	public synchronized void togglePlayPause()
	{
		if (player.playerState == PlayerState.IDLE) autoPlayStart = true;
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.togglePlayPause();
			}
		});
	}

	@Override
	public synchronized void seekTo(final int position)
	{
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.seekTo(position);
			}
		});
	}

	@Override
	public synchronized void pause()
	{
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.pause();
			}
		});
	}

	@Override
	public synchronized void start()
	{
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.start();
			}
		});
	}

	@Override
	public synchronized void stop()
	{
		executeOnStartedMediaPlayerService(new Consumer<MediaPlayerService>() {
			@Override
			public void accept(MediaPlayerService mediaPlayerService) {
				mediaPlayerService.stop();
			}
		});
	}

	@Override
	public synchronized void download(List<MusicDirectory.Entry> songs, boolean save, boolean autoPlay, boolean playNext, boolean shuffle, boolean newPlaylist)
	{
		downloader.download(songs, save, autoPlay, playNext, newPlaylist);
		jukeboxService.getValue().updatePlaylist();

		if (shuffle) shuffle();

		if (!playNext && !autoPlay && (downloader.downloadList.size() - 1) == downloader.getCurrentPlayingIndex())
		{
			MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
			if (mediaPlayerService != null) mediaPlayerService.setNextPlaying();
		}

		if (autoPlay)
		{
			play(0);
		}
		else
		{
			downloader.setFirstPlaying();
		}

		downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
	}

	@Override
	public synchronized void downloadBackground(List<MusicDirectory.Entry> songs, boolean save)
	{
		downloader.downloadBackground(songs, save);
		downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
	}

	public synchronized void setCurrentPlaying(DownloadFile currentPlaying)
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) player.setCurrentPlaying(currentPlaying);
	}

	public synchronized void setCurrentPlaying(int index)
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) mediaPlayerService.setCurrentPlaying(index);
	}

	public synchronized void setPlayerState(PlayerState state)
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) player.setPlayerState(state);
	}

	@Override
	public void stopJukeboxService()
	{
		jukeboxService.getValue().stopJukeboxService();
	}

	@Override
	public void startJukeboxService()
	{
		jukeboxService.getValue().startJukeboxService();
	}

	@Override
	public synchronized void setShufflePlayEnabled(boolean enabled)
	{
		shufflePlayBuffer.isEnabled = enabled;
		if (enabled)
		{
			clear();
			downloader.checkDownloads();
		}
	}

	@Override
	public boolean isShufflePlayEnabled()
	{
		return shufflePlayBuffer.isEnabled;
	}

	@Override
	public synchronized void shuffle()
	{
		downloader.shuffle();

		downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
		jukeboxService.getValue().updatePlaylist();

		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) mediaPlayerService.setNextPlaying();
	}

	@Override
	public RepeatMode getRepeatMode()
	{
		return Util.getRepeatMode(context);
	}

	@Override
	public synchronized void setRepeatMode(RepeatMode repeatMode)
	{
		Util.setRepeatMode(context, repeatMode);
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) mediaPlayerService.setNextPlaying();
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
	public synchronized void clear()
	{
		clear(true);
	}

	public synchronized void clear(boolean serialize)
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null)	mediaPlayerService.clear(serialize);

		jukeboxService.getValue().updatePlaylist();
	}

	@Override
	public synchronized void clearIncomplete()
	{
		reset();
		Iterator<DownloadFile> iterator = downloader.downloadList.iterator();

		while (iterator.hasNext())
		{
			DownloadFile downloadFile = iterator.next();
			if (!downloadFile.isCompleteFileAvailable())
			{
				iterator.remove();
			}
		}

		downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
		jukeboxService.getValue().updatePlaylist();
	}

	@Override
	public synchronized void remove(DownloadFile downloadFile)
	{
		if (downloadFile == player.currentPlaying)
		{
			reset();
			setCurrentPlaying(null);
		}

		downloader.removeDownloadFile(downloadFile);

		downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList, downloader.getCurrentPlayingIndex(), getPlayerPosition());
		jukeboxService.getValue().updatePlaylist();

		if (downloadFile == player.nextPlaying)
		{
			MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
			if (mediaPlayerService != null) mediaPlayerService.setNextPlaying();
		}
	}

	@Override
	public synchronized void delete(List<MusicDirectory.Entry> songs)
	{
		for (MusicDirectory.Entry song : songs)
		{
			downloader.getDownloadFileForSong(song).delete();
		}
	}

	@Override
	public synchronized void unpin(List<MusicDirectory.Entry> songs)
	{
		for (MusicDirectory.Entry song : songs)
		{
			downloader.getDownloadFileForSong(song).unpin();
		}
	}

	@Override
	public synchronized void previous()
	{
		int index = downloader.getCurrentPlayingIndex();
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
		int index = downloader.getCurrentPlayingIndex();
		if (index != -1)
		{
			play(index + 1);
		}
	}

	@Override
	public synchronized void reset()
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) player.reset();
	}

	@Override
	public synchronized int getPlayerPosition()
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService == null) return 0;
		return mediaPlayerService.getPlayerPosition();
	}

	@Override
	public synchronized int getPlayerDuration()
	{
		if (player.currentPlaying != null)
		{
			Integer duration = player.currentPlaying.getSong().getDuration();
			if (duration != null)
			{
				return duration * 1000;
			}
		}

		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService == null) return 0;
		return mediaPlayerService.getPlayerDuration();
	}

	@Override
	public PlayerState getPlayerState()	{ return player.playerState; }

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
	public boolean getEqualizerAvailable()	{ return player.equalizerAvailable; }

	@Override
	public boolean getVisualizerAvailable()
	{
		return player.visualizerAvailable;
	}

	@Override
	public EqualizerController getEqualizerController()
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService == null) return null;
		return player.getEqualizerController();
	}

	@Override
	public VisualizerController getVisualizerController()
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService == null) return null;
		return player.getVisualizerController();
	}

	@Override
	public boolean isJukeboxEnabled()
	{
		return jukeboxService.getValue().isEnabled();
	}

	@Override
	public boolean isJukeboxAvailable()
	{
		try
		{
			String username = Util.getUserName(context, Util.getActiveServer(context));
			UserInfo user = MusicServiceFactory.getMusicService(context).getUser(username, context, null);
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
		try
		{
			String username = Util.getUserName(context, Util.getActiveServer(context));
			UserInfo user = MusicServiceFactory.getMusicService(context).getUser(username, context, null);
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
		jukeboxService.getValue().setEnabled(jukeboxEnabled);
		setPlayerState(PlayerState.IDLE);

		if (jukeboxEnabled)
		{
			jukeboxService.getValue().startJukeboxService();

			reset();

			// Cancel current download, if necessary.
			if (downloader.currentDownloading != null)
			{
				downloader.currentDownloading.cancelDownload();
			}
		}
		else
		{
			jukeboxService.getValue().stopJukeboxService();
		}
	}

	@Override
	public void adjustJukeboxVolume(boolean up)
	{
		jukeboxService.getValue().adjustVolume(up);
	}

	@Override
	public void setVolume(float volume)
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) player.setVolume(volume);
	}

	@Override
	public synchronized void swap(boolean mainList, int from, int to)
	{
		List<DownloadFile> list = mainList ? downloader.downloadList : downloader.backgroundDownloadList;
		int max = list.size();

		if (to >= max)
		{
			to = max - 1;
		}
		else if (to < 0)
		{
			to = 0;
		}

		int currentPlayingIndex = downloader.getCurrentPlayingIndex();
		DownloadFile movedSong = list.remove(from);
		list.add(to, movedSong);

		if (jukeboxService.getValue().isEnabled() && mainList)
		{
			jukeboxService.getValue().updatePlaylist();
		}
		else if (mainList && (movedSong == player.nextPlaying || (currentPlayingIndex + 1) == to))
		{
			// Moving next playing or moving a song to be next playing
			MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
			if (mediaPlayerService != null) mediaPlayerService.setNextPlaying();
		}
	}

	@Override
	public void updateNotification()
	{
		MediaPlayerService mediaPlayerService = MediaPlayerService.getRunningInstance();
		if (mediaPlayerService != null) mediaPlayerService.updateNotification(player.playerState, player.currentPlaying);
	}

    public void setSongRating(final int rating)
	{
		if (!KoinJavaComponent.get(FeatureStorage.class).isFeatureEnabled(Feature.FIVE_STAR_RATING))
			return;

		if (player.currentPlaying == null)
			return;

		final Entry song = player.currentPlaying.getSong();
		song.setUserRating(rating);

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					MusicServiceFactory.getMusicService(context).setRating(song.getId(), rating, context, null);
				}
				catch (Exception e)
				{
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}).start();

		updateNotification();
	}
}