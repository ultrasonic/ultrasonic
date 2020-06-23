package org.moire.ultrasonic.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.service.DownloadServiceImpl;
import org.moire.ultrasonic.service.Downloader;
import org.moire.ultrasonic.service.Player;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;

public class A2dpIntentReceiver extends BroadcastReceiver
{
	private static final String PLAYSTATUS_RESPONSE = "com.android.music.playstatusresponse";
	private Lazy<DownloadServiceImpl> downloadServiceImpl = inject(DownloadServiceImpl.class);
	private Lazy<Downloader> downloader = inject(Downloader.class);
	protected Lazy<Player> player = inject(Player.class);

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (player.getValue().currentPlaying == null)
		{
			return;
		}

		Entry song = player.getValue().currentPlaying.getSong();

		if (song == null)
		{
			return;
		}

		Intent avrcpIntent = new Intent(PLAYSTATUS_RESPONSE);

		Integer duration = song.getDuration();
		int playerPosition = downloadServiceImpl.getValue().getPlayerPosition();
		int listSize = downloader.getValue().getDownloads().size();

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