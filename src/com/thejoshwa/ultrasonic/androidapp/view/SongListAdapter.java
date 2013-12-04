package com.thejoshwa.ultrasonic.androidapp.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;

import java.util.List;

public class SongListAdapter extends ArrayAdapter<DownloadFile>
{
	Context context;

	public SongListAdapter(Context context, final List<DownloadFile> entries)
	{
		super(context, android.R.layout.simple_list_item_1, entries);
		this.context = context;
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent)
	{
		final SongView view;
		view = convertView != null && convertView instanceof SongView ? (SongView) convertView : new SongView(this.context);
		final DownloadFile downloadFile = getItem(position);
		view.setSong(downloadFile.getSong(), false);
		return view;
	}
}