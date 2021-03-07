package org.moire.ultrasonic.util;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;
import org.moire.ultrasonic.R;

/**
 * Created by Joshua Bahnsen on 12/22/13.
 */
public class TimeSpanPreference extends DialogPreference
{
	Context context;

	public TimeSpanPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		this.context = context;

		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);

		setDialogIcon(null);

	}

	public String getText()
	{
		String persisted = getPersistedString("");

		if (!"".equals(persisted))
		{
			return persisted.replace(':', ' ');
		}

		return this.context.getResources().getString(R.string.time_span_disabled);
	}
}
