package com.thejoshwa.ultrasonic.androidapp.service;

import android.content.Context;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.util.Util;

/**
 * Scrobbles played songs to Last.fm.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class Scrobbler
{

	private static final String TAG = Scrobbler.class.getSimpleName();

	private String lastSubmission;
	private String lastNowPlaying;

	public void scrobble(final Context context, final DownloadFile song, final boolean submission)
	{
		if (song == null || !Util.isScrobblingEnabled(context))
		{
			return;
		}

		final String id = song.getSong().getId();

		// Avoid duplicate registrations.
		if (submission && id.equals(lastSubmission))
		{
			return;
		}

		if (!submission && id.equals(lastNowPlaying))
		{
			return;
		}

		if (submission)
		{
			lastSubmission = id;
		}
		else
		{
			lastNowPlaying = id;
		}

		new Thread(String.format("Scrobble %s", song))
		{
			@Override
			public void run()
			{
				MusicService service = MusicServiceFactory.getMusicService(context);
				try
				{
					service.scrobble(id, submission, context, null);
					Log.i(TAG, String.format("Scrobbled '%s' for %s", submission ? "submission" : "now playing", song));
				}
				catch (Exception x)
				{
					Log.i(TAG, String.format("Failed to scrobble'%s' for %s", submission ? "submission" : "now playing", song), x);
				}
			}
		}.start();
	}
}
