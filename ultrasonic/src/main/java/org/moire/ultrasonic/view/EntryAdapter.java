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
package org.moire.ultrasonic.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.imageloader.ImageLoader;

import java.util.List;

/**
 * This is the adapter for the display of a single list item (song, album, etc)
 *
 * @author Sindre Mehus
 */
public class EntryAdapter extends ArrayAdapter<Entry>
{
	private final Context context;
	private final ImageLoader imageLoader;
	private final boolean checkable;

	public EntryAdapter(Context context, ImageLoader imageLoader, List<Entry> entries, boolean checkable)
	{
		super(context, android.R.layout.simple_list_item_1, entries);

		this.context = context;
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

			if (convertView instanceof AlbumView)
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
				view = new AlbumView(context, imageLoader);
				view.setLayout();
			}

			view.setAlbum(entry);
			return view;
		}
		else
		{
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
					SongViewHolder viewHolder = (SongViewHolder) convertView.getTag();
					view = currentView;
					view.setViewHolder(viewHolder);
				}
			}
			else
			{
				view = new SongView(context);
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
		LinearLayout rating;
		ImageView fiveStar1;
		ImageView fiveStar2;
		ImageView fiveStar3;
		ImageView fiveStar4;
		ImageView fiveStar5;
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
