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
package org.moire.ultrasonic.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Playlist;

/**
 * Used to display playlists in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class PlaylistView extends LinearLayout
{
	private final Context context;
	private PlaylistAdapter.ViewHolder viewHolder;

	public PlaylistView(Context context)
	{
		super(context);
		this.context = context;
	}

	public void setLayout()
	{
		LayoutInflater.from(context).inflate(R.layout.playlist_list_item, this, true);
		viewHolder = new PlaylistAdapter.ViewHolder();
		viewHolder.name = findViewById(R.id.playlist_name);
		setTag(viewHolder);
	}

	public void setViewHolder(PlaylistAdapter.ViewHolder viewHolder)
	{
		this.viewHolder = viewHolder;
		setTag(this.viewHolder);
	}

	public void setPlaylist(Playlist playlist)
	{
		viewHolder.name.setText(playlist.getName());
	}
}