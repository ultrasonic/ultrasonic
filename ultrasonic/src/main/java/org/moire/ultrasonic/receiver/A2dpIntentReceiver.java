package org.moire.ultrasonic.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.service.DownloadService;
import org.moire.ultrasonic.service.DownloadServiceImpl;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;

public class A2dpIntentReceiver extends BroadcastReceiver
{
	private static final String PLAYSTATUS_RESPONSE = "com.android.music.playstatusresponse";
	private Lazy<DownloadServiceImpl> downloadServiceImpl = inject(DownloadServiceImpl.class);

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (downloadServiceImpl.getValue().getCurrentPlaying() == null)
		{
			return;
		}

		Entry song = downloadServiceImpl.getValue().getCurrentPlaying().getSong();

		if (song == null)
		{
			return;
		}

		Intent avrcpIntent = new Intent(PLAYSTATUS_RESPONSE);

		Integer duration = song.getDuration();
		Integer playerPosition = downloadServiceImpl.getValue().getPlayerPosition();
		Integer listSize = downloadServiceImpl.getValue().getDownloads().size();

		if (duration != null)
		{
			avrcpIntent.putExtra("duration", (long) duration);
		}

		avrcpIntent.putExtra("position", (long) playerPosition);
		avrcpIntent.putExtra("ListSize", (long) listSize);

		switch (downloadServiceImpl.getValue().getPlayerState())
		{
			case STARTED:
				avrcpIntent.putExtra("playing", true);
				break;
			case STOPPED:
				avrcpIntent.putExtra("playing", false);
				break;
			case PAUSED:
				avrcpIntent.putExtra("playing", false);
				break;
			case COMPLETED:
				avrcpIntent.putExtra("playing", false);
				break;
			default:
				return;
		}

		context.sendBroadcast(avrcpIntent);
	}
}