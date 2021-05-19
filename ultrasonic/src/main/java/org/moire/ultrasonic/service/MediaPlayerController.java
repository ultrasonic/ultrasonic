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

import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.domain.RepeatMode;

import java.util.List;

/**
 * This interface contains all functions which are necessary for the Application UI
 * to control the Media Player implementation.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public interface MediaPlayerController
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

	void setShowVisualization(boolean showVisualization);

	void clear();

	void clearIncomplete();

	void remove(DownloadFile downloadFile);

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

	void setSuggestedPlaylistName(String name);

	String getSuggestedPlaylistName();

	boolean isJukeboxEnabled();

	boolean isJukeboxAvailable();

	void setJukeboxEnabled(boolean b);

	void adjustJukeboxVolume(boolean up);

	void togglePlayPause();

	void setVolume(float volume);

	void restore(List<Entry> songs, int currentPlayingIndex, int currentPlayingPosition, boolean autoPlay, boolean newPlaylist);

	void stopJukeboxService();

	void updateNotification();

	void setSongRating(final int rating);

	DownloadFile getCurrentPlaying();

	int getPlaylistSize();

	int getCurrentPlayingNumberOnPlaylist();

	DownloadFile getCurrentDownloading();

	List<DownloadFile> getPlayList();

	long getPlayListUpdateRevision();

	long getPlayListDuration();

	DownloadFile getDownloadFileForSong(Entry song);
}
