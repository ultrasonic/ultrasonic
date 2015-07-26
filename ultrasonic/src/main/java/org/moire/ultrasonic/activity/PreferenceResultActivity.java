package org.moire.ultrasonic.activity;

import android.app.Activity;
import android.content.Intent;
import android.preference.PreferenceActivity;

import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

/**
 * Created by Joshua Bahnsen on 12/30/13.
 */
public class PreferenceResultActivity extends PreferenceActivity
{
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (resultCode)
		{
			case Constants.RESULT_CLOSE_ALL:
				setResult(Constants.RESULT_CLOSE_ALL);
				finish();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	protected void startActivityForResultWithoutTransition(Activity currentActivity, Class<? extends Activity> newActivity)
	{
		startActivityForResultWithoutTransition(currentActivity, new Intent(currentActivity, newActivity));
	}

	protected void startActivityForResultWithoutTransition(Activity currentActivity, Intent intent)
	{
		startActivityForResult(intent, 0);
		Util.disablePendingTransition(currentActivity);
	}
}
