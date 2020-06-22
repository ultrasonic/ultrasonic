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

 Copyright 2010 (C) Sindre Mehus
 */
package org.moire.ultrasonic.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.KeyEvent;

import org.moire.ultrasonic.service.DownloadServiceImpl;
import org.moire.ultrasonic.util.Util;

/**
 * @author Sindre Mehus
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver
{

	private static final String TAG = MediaButtonIntentReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (Util.getMediaButtonsPreference(context))
		{
			String intentAction = intent.getAction();

			if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) return;

			Bundle extras = intent.getExtras();

			if (extras == null)
			{
				return;
			}

			Parcelable event = (Parcelable) extras.get(Intent.EXTRA_KEY_EVENT);
			Log.i(TAG, "Got MEDIA_BUTTON key event: " + event);

			Intent serviceIntent = new Intent(context, DownloadServiceImpl.class);
			serviceIntent.putExtra(Intent.EXTRA_KEY_EVENT, event);

			try
			{
				context.startService(serviceIntent);
			}
			catch (IllegalStateException exception)
			{
				Log.i(TAG, "MediaButtonIntentReceiver couldn't start DownloadServiceImpl because the application was in the background.");
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
				{
					KeyEvent keyEvent = (KeyEvent) event;
					if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getRepeatCount() == 0)
					{
						int keyCode = keyEvent.getKeyCode();
						if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
								keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
								keyCode == KeyEvent.KEYCODE_MEDIA_PLAY)
						{
							// TODO: The only time it is OK to start DownloadServiceImpl as a foreground service is when we now it will display its notification.
							// When DownloadServiceImpl is refactored to a proper foreground service, this can be removed.
							context.startForegroundService(serviceIntent);
							Log.i(TAG, "MediaButtonIntentReceiver started DownloadServiceImpl as foreground service");
						}
					}
				}
			}

			try
			{
				if (isOrderedBroadcast())
				{
					abortBroadcast();
				}
			}
			catch (Exception x)
			{
				// Ignored.
			}
		}
	}
}
