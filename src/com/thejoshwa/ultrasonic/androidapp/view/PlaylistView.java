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

 Copyright 2009 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;

/**
 * Used to display playlists in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class PlaylistView extends UpdateView
{
	private TextView titleView;

	public PlaylistView(Context context)
	{
		super(context);
		LayoutInflater.from(context).inflate(R.layout.playlist_list_item, this, true);

		titleView = (TextView) findViewById(R.id.playlist_name);
	}

	public void setPlaylist(Playlist playlist)
	{
		titleView.setText(playlist.getName());
		update();
	}
}