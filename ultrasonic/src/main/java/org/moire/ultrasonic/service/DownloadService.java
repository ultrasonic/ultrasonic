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

import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.audiofx.VisualizerController;
import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;

import java.util.List;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public interface DownloadService
{

	void download(List<Entry> songs, boolean save, boolean autoplay, boolean playNext, boolean shuffle, boolean newPlaylist);

	void downloadBackground(List<Entry> songs, boolean save);

	void setShufflePlayEnabled(boolean enabled);

	boolean isShufflePlayEnabled();

	void shuffle();

	RepeatMode getRepeatMode();

	void setRepeatMode(RepeatMode repeatMode);

	boolean getKeepScreenOn();

	void setKeepScreenOn(boolean screenOn);

	boolean getShowVisualization();

	boolean getEqualizerAvailable();

	boolean getVisualizerAvailable();

	void setShowVisualization(boolean showVisualization);

	void clear();

	void clearBackground();

	void clearIncomplete();

	int size();

	void remove(int which);

	void remove(DownloadFile downloadFile);

	long getDownloadListDuration();

	List<DownloadFile> getSongs();

	List<DownloadFile> getDownloads();

	List<DownloadFile> getBackgroundDownloads();

	int getCurrentPlayingIndex();

	DownloadFile getCurrentPlaying();

	DownloadFile getCurrentDownloading();

	void play(int index);

	void seekTo(int position);

	void previous();

	void next();

	void pause();

	void stop();

	void start();

	void reset();

	PlayerState getPlayerState();

	int getPlayerPosition();

	int getPlayerDuration();

	void delete(List<Entry> songs);

	void unpin(List<Entry> songs);

	DownloadFile forSong(Entry song);

	long getDownloadListUpdateRevision();

	void setSuggestedPlaylistName(String name);

	String getSuggestedPlaylistName();

	EqualizerController getEqualizerController();

	VisualizerController getVisualizerController();

	boolean isJukeboxEnabled();

	boolean isJukeboxAvailable();

	boolean isSharingAvailable();

	void setJukeboxEnabled(boolean b);

	void adjustJukeboxVolume(boolean up);

	void togglePlayPause();

	void setVolume(float volume);

	void swap(boolean mainList, int from, int to);

	void restore(List<Entry> songs, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay, boolean newPlaylist);

	void stopJukeboxService();

	void startJukeboxService();

	void updateNotification();
}
