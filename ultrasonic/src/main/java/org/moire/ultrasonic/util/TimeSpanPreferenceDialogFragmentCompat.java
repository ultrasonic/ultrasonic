package org.moire.ultrasonic.util;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import org.moire.ultrasonic.R;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Created by Joshua Bahnsen on 12/22/13.
 */
public class TimeSpanPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment
{
	private static final Pattern COMPILE = Pattern.compile(":");
	Context context;
	TimeSpanPicker picker;

	@Override
	protected View onCreateDialogView(Context context)	{
		picker = new TimeSpanPicker(context);
		this.context = context;

		picker.setTimeSpanDisableText(this.context.getResources().getString(R.string.no_expiration));

		Preference preference = getPreference();
		String persisted = preference.getSharedPreferences().getString(preference.getKey(), "");

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
	public void onDialogClosed(boolean positiveResult)
	{
		String persisted = "";

		if (picker.getTimeSpanEnabled())
		{
			int tsAmount = picker.getTimeSpanAmount();

			if (tsAmount > 0)
			{
				String tsType = picker.getTimeSpanType();

				persisted = String.format(Locale.US, "%d:%s", tsAmount, tsType);
			}
		}

		Preference preference = getPreference();
		preference.getSharedPreferences().edit().putString(preference.getKey(), persisted).apply();
	}

	@Nullable
	@Override
	public Preference findPreference(@NonNull CharSequence key) {
		return getPreference();
	}
}
