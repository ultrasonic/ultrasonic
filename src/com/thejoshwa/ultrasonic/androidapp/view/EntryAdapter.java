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
package com.thejoshwa.ultrasonic.androidapp.view;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.util.ImageLoader;

import java.util.List;

/**
 * @author Sindre Mehus
 */
public class EntryAdapter extends ArrayAdapter<Entry>
{
	private final SubsonicTabActivity activity;
	private final ImageLoader imageLoader;
	private final boolean checkable;

	public EntryAdapter(SubsonicTabActivity activity, ImageLoader imageLoader, List<Entry> entries, boolean checkable)
	{
		super(activity, android.R.layout.simple_list_item_1, entries);

		this.activity = activity;
		this.imageLoader = imageLoader;
		this.checkable = checkable;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Entry entry = getItem(position);

		if (entry.isDirectory())
		{
			AlbumView view;

			if (convertView != null && convertView instanceof AlbumView)
			{
				AlbumView currentView = (AlbumView) convertView;

				if (currentView.getEntry().equals(entry))
				{
					return currentView;
				}
				else
				{
					AlbumViewHolder viewHolder = (AlbumViewHolder) currentView.getTag();
					view = currentView;
					view.setViewHolder(viewHolder);
				}
			}
			else
			{
				view = new AlbumView(activity, imageLoader);
				view.setLayout();
			}

			view.setAlbum(entry);
			return view;
		}
		else
		{
			SongView view;

			if (convertView != null && convertView instanceof SongView)
			{
				SongView currentView = (SongView) convertView;

				if (currentView.getEntry().equals(entry))
				{
					currentView.update();
					return currentView;
				}
				else
				{
					SongViewHolder viewHolder = (SongViewHolder) convertView.getTag();
					view = currentView;
					view.setViewHolder(viewHolder);
				}
			}
			else
			{
				view = new SongView(activity);
				view.setLayout(entry);
			}

			view.setSong(entry, checkable, false);
			return view;
		}
	}

	public static class SongViewHolder
	{
		CheckedTextView check;
		TextView track;
		TextView title;
		TextView status;
		TextView artist;
		TextView duration;
		ImageView star;
		ImageView drag;
	}

	public static class AlbumViewHolder
	{
		TextView artist;
		ImageView cover_art;
		ImageView star;
		TextView title;
	}
}
