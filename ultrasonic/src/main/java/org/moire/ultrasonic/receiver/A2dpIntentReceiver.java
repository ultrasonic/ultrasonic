package org.moire.ultrasonic.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.service.MediaPlayerController;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

public class A2dpIntentReceiver extends BroadcastReceiver
{
	private static final String PLAYSTATUS_RESPONSE = "com.android.music.playstatusresponse";
	private Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (mediaPlayerControllerLazy.getValue().getCurrentPlaying() == null)
		{
			return;
		}

		Entry song = mediaPlayerControllerLazy.getValue().getCurrentPlaying().getSong();

		if (song == null)
		{
			return;
		}

		Intent avrcpIntent = new Intent(PLAYSTATUS_RESPONSE);

		Integer duration = song.getDuration();
		int playerPosition = mediaPlayerControllerLazy.getValue().getPlayerPosition();
		int listSize = mediaPlayerControllerLazy.getValue().getPlaylistSize();

		if (duration != null)
		{
			avrcpIntent.putExtra("duration", (long) duration);
		}

		avrcpIntent.putExtra("position", (long) playerPosition);
		avrcpIntent.putExtra("ListSize", (long) listSize);

		switch (mediaPlayerControllerLazy.getValue().getPlayerState())
		{
			case STARTED:
				avrcpIntent.putExtra("playing", true);
				break;
			case STOPPED:
			case PAUSED:
			case COMPLETED:
				avrcpIntent.putExtra("playing", false);
				break;
			default:
				return;
		}

		context.sendBroadcast(avrcpIntent);
	}
}