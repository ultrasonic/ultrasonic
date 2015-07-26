package org.moire.ultrasonic.util;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;

import org.moire.ultrasonic.R;

import java.util.regex.Pattern;

/**
 * Created by Joshua Bahnsen on 12/22/13.
 */
public class TimeSpanPreference extends DialogPreference
{
	private static final Pattern COMPILE = Pattern.compile(":");
	Context context;
	TimeSpanPicker picker;

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

	@Override
	public View onCreateDialogView()
	{
		picker = new TimeSpanPicker(this.context);
		picker.setTimeSpanDisableText(this.context.getResources().getString(R.string.no_expiration));

		String persisted = getPersistedString("");

		if (!"".equals(persisted))
		{
			String[] split = COMPILE.split(persisted);

			if (split.length == 2)
			{
				String amount = split[0];

				if ("0".equals(amount) || "".equals(amount))
				{
					picker.setTimeSpanDisableCheckboxChecked(true);
				}

				picker.setTimeSpanAmount(amount);
				picker.setTimeSpanType(split[1]);
			}
		}
		else
		{
			picker.setTimeSpanDisableCheckboxChecked(true);
		}

		return picker;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);

		String persisted = "";

		if (picker.getTimeSpanEnabled())
		{
			int tsAmount = picker.getTimeSpanAmount();

			if (tsAmount > 0)
			{
				String tsType = picker.getTimeSpanType();

				persisted = String.format("%d:%s", tsAmount, tsType);
			}
		}

		persistString(persisted);
	}
}
