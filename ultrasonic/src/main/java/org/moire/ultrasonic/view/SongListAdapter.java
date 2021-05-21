package org.moire.ultrasonic.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.service.DownloadFile;

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
		DownloadFile downloadFile = getItem(position);
   	MusicDirectory.Entry entry = downloadFile.getSong();

		SongView view;

		if (convertView instanceof SongView)
		{
			SongView currentView = (SongView) convertView;
			if (currentView.getEntry().equals(entry))
			{
				currentView.update();
				return currentView;
			}
			else
			{
				EntryAdapter.SongViewHolder viewHolder = (EntryAdapter.SongViewHolder) convertView.getTag();
				view = currentView;
				view.setViewHolder(viewHolder);
			}
		}
		else
		{
			view = new SongView(this.context);
			view.setLayout(entry);
		}

		view.setSong(entry, false, true);
		return view;
	}
}
