package org.moire.ultrasonic.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.moire.ultrasonic.service.MediaPlayerLifecycleSupport;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

public class UltrasonicIntentReceiver extends BroadcastReceiver
{
	private static final String TAG = UltrasonicIntentReceiver.class.getSimpleName();
	private Lazy<MediaPlayerLifecycleSupport> lifecycleSupport = inject(MediaPlayerLifecycleSupport.class);

	@Override
	public void onReceive(Context context, Intent intent)
	{
		String intentAction = intent.getAction();
		Log.i(TAG, String.format("Received Ultrasonic Intent: %s", intentAction));

		try
		{
			lifecycleSupport.getValue().receiveIntent(intent);

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
