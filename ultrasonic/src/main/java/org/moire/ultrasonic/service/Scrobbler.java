package org.moire.ultrasonic.service;

import android.content.Context;
import timber.log.Timber;

import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.util.Util;

/**
 * Scrobbles played songs to Last.fm.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class Scrobbler
{
	private String lastSubmission;
	private String lastNowPlaying;

	public void scrobble(final Context context, final DownloadFile song, final boolean submission)
	{
		if (song == null || !ActiveServerProvider.Companion.isScrobblingEnabled(context))
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
					Timber.i("Scrobbled '%s' for %s", submission ? "submission" : "now playing", song);
				}
				catch (Exception x)
				{
					Timber.i(x, "Failed to scrobble'%s' for %s", submission ? "submission" : "now playing", song);
				}
			}
		}.start();
	}
}
