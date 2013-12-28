package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.thejoshwa.ultrasonic.androidapp.R;

/**
 * Created by Joshua Bahnsen on 12/22/13.
 */
public class TimeSpanPicker extends LinearLayout implements AdapterView.OnItemSelectedListener
{
	private EditText timeSpanEditText;
	private Spinner timeSpanSpinner;
	private CheckBox timeSpanDisableCheckbox;
	private TimeSpan timeSpan;
	private ArrayAdapter<CharSequence> adapter;
	private Context context;
	private View dialog;

	public TimeSpanPicker(Context context) {
		this(context, null);

		this.context = context;
	}

	public TimeSpanPicker(Context context, AttributeSet attrs) {
		this(context, attrs, 0);

		this.context = context;
	}

	public TimeSpanPicker(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		this.context = context;

		final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		dialog = inflater.inflate(R.layout.time_span_dialog, this, true);

  	timeSpan = new TimeSpan(-1);

		timeSpanEditText = (EditText) dialog.findViewById(R.id.timeSpanEditText);
		timeSpanEditText.setText("0");

		timeSpanSpinner = (Spinner) dialog.findViewById(R.id.timeSpanSpinner);
		timeSpanDisableCheckbox = (CheckBox) dialog.findViewById(R.id.timeSpanDisableCheckBox);

		timeSpanDisableCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(CompoundButton compoundButton, boolean b)
			{
				timeSpanEditText.setEnabled(!b);
				timeSpanSpinner.setEnabled(!b);
			}
		});

		adapter = ArrayAdapter.createFromResource(context, R.array.shareExpirationNames, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		timeSpanSpinner.setAdapter(adapter);

		timeSpanSpinner.setOnItemSelectedListener(this);
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		timeSpanEditText.setEnabled(enabled);
		timeSpanSpinner.setEnabled(enabled);
	}

	public TimeSpan getTimeSpan()
	{
		if (!timeSpanDisableCheckbox.isChecked())
		{
			this.timeSpan = getTimeSpanFromDialog(this.context, dialog);
		}
		else
		{
			this.timeSpan = new TimeSpan(0);
		}

		return timeSpan;
	}

	public boolean getTimeSpanEnabled()
	{
		return !timeSpanDisableCheckbox.isChecked();
	}

	public String getTimeSpanType()
	{
		EditText timeSpanEditText = (EditText) dialog.findViewById(R.id.timeSpanEditText);

		if (timeSpanEditText == null)
		{
			return null;
		}

		return (String) timeSpanSpinner.getSelectedItem();
	}

	public int getTimeSpanAmount()
	{
		Spinner timeSpanSpinner = (Spinner) dialog.findViewById(R.id.timeSpanSpinner);

		if (timeSpanSpinner == null)
		{
			return -1;
		}

		String timeSpanAmountString = timeSpanEditText.getText().toString();

		int timeSpanAmount = 0;

		if (timeSpanAmountString != null && !"".equals(timeSpanAmountString))
		{
			timeSpanAmount = Integer.parseInt(timeSpanAmountString);
		}

		return timeSpanAmount;
	}

	public void setTimeSpanAmount(CharSequence amount)
	{
		timeSpanEditText.setText(amount);
	}

	public void setTimeSpanType(CharSequence type)
	{
		timeSpanSpinner.setSelection(adapter.getPosition(type));
	}

	public void setTimeSpanDisableText(CharSequence text)
	{
		timeSpanDisableCheckbox.setText(text);
	}

	public void setTimeSpanDisableCheckboxChecked(boolean checked)
	{
		timeSpanDisableCheckbox.setChecked(checked);
	}

	public static TimeSpan getTimeSpanFromDialog(Context context, View dialog)
	{
		EditText timeSpanEditText = (EditText) dialog.findViewById(R.id.timeSpanEditText);
		Spinner timeSpanSpinner = (Spinner) dialog.findViewById(R.id.timeSpanSpinner);

		if (timeSpanEditText == null || timeSpanSpinner == null)
		{
			return new TimeSpan(-1);
		}

		String timeSpanType = (String) timeSpanSpinner.getSelectedItem();
		String timeSpanAmountString = timeSpanEditText.getText().toString();

		int timeSpanAmount = 0;

		if (timeSpanAmountString != null && !"".equals(timeSpanAmountString))
		{
			timeSpanAmount = Integer.parseInt(timeSpanAmountString);
		}

		return calculateTimeSpan(context, timeSpanType, timeSpanAmount);
	}

	public static TimeSpan calculateTimeSpan(Context context, String timeSpanType, int timeSpanAmount)
	{
		TimeSpan timeSpan = null;

		Resources resources = context.getResources();

		if (resources.getText(R.string.settings_share_milliseconds).equals(timeSpanType))
		{
			timeSpan = new TimeSpan(timeSpanAmount);
		}
		else if (resources.getText(R.string.settings_share_seconds).equals(timeSpanType))
		{
			timeSpan = TimeSpan.create(0, timeSpanAmount);
		}
		else if (resources.getText(R.string.settings_share_minutes).equals(timeSpanType))
		{
			timeSpan = TimeSpan.create(timeSpanAmount, 0);
		}
		else if (resources.getText(R.string.settings_share_hours).equals(timeSpanType))
		{
			timeSpan = TimeSpan.create(timeSpanAmount, 0, 0);
		}
		else if (resources.getText(R.string.settings_share_days).equals(timeSpanType))
		{
			timeSpan = TimeSpan.create(timeSpanAmount, 0, 0, 0);
		}

		return timeSpan;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
	{
		String timeSpanType = (String)parent.getItemAtPosition(pos);
		String timeSpanAmountString = timeSpanEditText.getText().toString();

		int timeSpanAmount = 0;

		if (timeSpanAmountString != null && !"".equals(timeSpanAmountString))
		{
			timeSpanAmount = Integer.parseInt(timeSpanAmountString);
		}

		this.timeSpan = calculateTimeSpan(this.context, timeSpanType, timeSpanAmount);
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView)
	{

	}
}
