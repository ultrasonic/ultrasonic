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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.util.CacheCleaner;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;

/**
 * @author Sindre Mehus
 */
public class DownloadServiceLifecycleSupport
{
	private static final String TAG = DownloadServiceLifecycleSupport.class.getSimpleName();

	private Lazy<DownloadQueueSerializer> downloadQueueSerializer = inject(DownloadQueueSerializer.class);
	private final DownloadServiceImpl downloadService; // From DI
	private final Downloader downloader; // From DI

	private BroadcastReceiver headsetEventReceiver;
	private Context context;

	public DownloadServiceLifecycleSupport(Context context, final DownloadServiceImpl downloadService, final Downloader downloader)
	{
		this.downloadService = downloadService;
		this.context = context;
		this.downloader = downloader;

		registerHeadsetReceiver();

		// React to media buttons.
		Util.registerMediaButtonEventReceiver(context);

		// Register the handler for outside intents.
		IntentFilter commandFilter = new IntentFilter();
		commandFilter.addAction(Constants.CMD_PLAY);
		commandFilter.addAction(Constants.CMD_TOGGLEPAUSE);
		commandFilter.addAction(Constants.CMD_PAUSE);
		commandFilter.addAction(Constants.CMD_STOP);
		commandFilter.addAction(Constants.CMD_PREVIOUS);
		commandFilter.addAction(Constants.CMD_NEXT);
		commandFilter.addAction(Constants.CMD_PROCESS_KEYCODE);
		context.registerReceiver(intentReceiver, commandFilter);

		downloadQueueSerializer.getValue().deserializeDownloadQueue(new Consumer<State>() {
			@Override
			public void accept(State state) {
				downloadService.restore(state.songs, state.currentPlayingIndex, state.currentPlayingPosition, false, false);

				// Work-around: Serialize again, as the restore() method creates a serialization without current playing info.
				downloadQueueSerializer.getValue().serializeDownloadQueue(downloader.downloadList,
						downloader.getCurrentPlayingIndex(), downloadService.getPlayerPosition());
			}
		});

		new CacheCleaner(context).clean();
		Log.i(TAG, "LifecycleSupport created");
	}

	public void onDestroy()
	{
		downloadService.clear(false);
		context.unregisterReceiver(headsetEventReceiver);
		context.unregisterReceiver(intentReceiver);
		downloadService.onDestroy();
		Log.i(TAG, "LifecycleSupport destroyed");
	}

	private void registerHeadsetReceiver() {
        // Pause when headset is unplugged.
        final SharedPreferences sp = Util.getPreferences(context);
        final String spKey = context
                .getString(R.string.settings_playback_resume_play_on_headphones_plug);

        headsetEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final Bundle extras = intent.getExtras();

                if (extras == null) {
                    return;
                }

                Log.i(TAG, String.format("Headset event for: %s", extras.get("name")));
                final int state = extras.getInt("state");
                if (state == 0) {
                    if (!downloadService.isJukeboxEnabled()) {
                        downloadService.pause();
                    }
                } else if (state == 1) {
                    if (!downloadService.isJukeboxEnabled() &&
                            sp.getBoolean(spKey, false) &&
                            downloadService.getPlayerState() == PlayerState.PAUSED) {
                        downloadService.start();
                    }
                }
            }
        };
        @SuppressLint("InlinedApi")
        IntentFilter headsetIntentFilter = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ?
                new IntentFilter(AudioManager.ACTION_HEADSET_PLUG) :
                new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        context.registerReceiver(headsetEventReceiver, headsetIntentFilter);
    }

    public void receiveIntent(Intent intent)
	{
		Log.i(TAG, "Received intent");
		if (intent != null && intent.getExtras() != null)
		{
			KeyEvent event = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
			if (event != null)
			{
				handleKeyEvent(event);
			}
		}
	}

	private void handleKeyEvent(KeyEvent event)
	{
		if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0)
		{
			return;
		}

		switch (event.getKeyCode())
		{
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			case KeyEvent.KEYCODE_HEADSETHOOK:
				downloadService.togglePlayPause();
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				downloadService.previous();
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				if (downloader.getCurrentPlayingIndex() < downloader.downloadList.size() - 1)
				{
					downloadService.next();
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_STOP:
				downloadService.stop();
				break;
			case KeyEvent.KEYCODE_MEDIA_PLAY:
				if (downloadService.getPlayerState() != PlayerState.STARTED)
				{
					downloadService.start();
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
				downloadService.pause();
				break;
			case KeyEvent.KEYCODE_1:
				downloadService.setSongRating(1);
				break;
			case KeyEvent.KEYCODE_2:
				downloadService.setSongRating(2);
				break;
			case KeyEvent.KEYCODE_3:
				downloadService.setSongRating(3);
				break;
			case KeyEvent.KEYCODE_4:
				downloadService.setSongRating(4);
				break;
			case KeyEvent.KEYCODE_5:
				downloadService.setSongRating(5);
				break;
			default:
				break;
		}
	}

	/**
	 * This receiver manages the intent that could come from other applications.
	 */
	private BroadcastReceiver intentReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (action == null) return;
			Log.i(TAG, "intentReceiver.onReceive: " + action);

			switch(action)
			{
				case Constants.CMD_PLAY:
					downloadService.play();
					break;
				case Constants.CMD_NEXT:
					downloadService.next();
					break;
				case Constants.CMD_PREVIOUS:
					downloadService.previous();
					break;
				case Constants.CMD_TOGGLEPAUSE:
					downloadService.togglePlayPause();
					break;
				case Constants.CMD_STOP:
					downloadService.pause();
					downloadService.seekTo(0);
					break;
				case Constants.CMD_PROCESS_KEYCODE:
					receiveIntent(intent);
					break;
			}
		}
	};
}