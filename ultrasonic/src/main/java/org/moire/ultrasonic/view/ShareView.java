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
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Share;

/**
 * Used to display playlists in a {@code ListView}.
 *
 * @author Joshua Bahnsen
 */
public class ShareView extends UpdateView
{
	private Context context;
	private ShareAdapter.ViewHolder viewHolder;

	public ShareView(Context context)
	{
		super(context);
		this.context = context;
	}

	public void setLayout()
	{
		LayoutInflater.from(context).inflate(R.layout.share_list_item, this, true);
		viewHolder = new ShareAdapter.ViewHolder();
		viewHolder.url = (TextView) findViewById(R.id.share_url);
		viewHolder.description = (TextView) findViewById(R.id.share_description);
		setTag(viewHolder);
	}

	public void setViewHolder(ShareAdapter.ViewHolder viewHolder)
	{
		this.viewHolder = viewHolder;
		setTag(this.viewHolder);
	}

	public void setShare(Share share)
	{
		viewHolder.url.setText(share.getName());
		viewHolder.description.setText(share.getDescription());
		update();
	}
}