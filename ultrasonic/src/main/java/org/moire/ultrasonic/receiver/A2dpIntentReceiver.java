package org.moire.ultrasonic.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.service.DownloadService;
import org.moire.ultrasonic.service.DownloadServiceImpl;

public class A2dpIntentReceiver extends BroadcastReceiver
{

	private static final String PLAYSTATUS_RESPONSE = "com.android.music.playstatusresponse";

	@Override
	public void onReceive(Context context, Intent intent)
	{

		DownloadService downloadService = DownloadServiceImpl.getInstance();

		if (downloadService == null)
		{
			return;
		}

		if (downloadService.getCurrentPlaying() == null)
		{
			return;
		}

		Entry song = downloadService.getCurrentPlaying().getSong();

		if (song == null)
		{
			return;
		}

		Intent avrcpIntent = new Intent(PLAYSTATUS_RESPONSE);

		Integer duration = song.getDuration();
		Integer playerPosition = downloadService.getPlayerPosition();
		Integer listSize = downloadService.getDownloads().size();

		if (duration != null)
		{
			avrcpIntent.putExtra("duration", (long) duration);
		}

		avrcpIntent.putExtra("position", (long) playerPosition);
		avrcpIntent.putExtra("ListSize", (long) listSize);

		switch (downloadService.getPlayerState())
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