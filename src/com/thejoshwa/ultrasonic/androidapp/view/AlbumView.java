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
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.ImageLoader;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

/**
 * Used to display albums in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class AlbumView extends UpdateView
{

	private static final String TAG = AlbumView.class.getSimpleName();
	private static Drawable starDrawable;
	private static Drawable starHollowDrawable;
	private static String theme;

	private TextView titleView;
	private TextView artistView;
	private View coverArtView;
	private ImageView starImageView;
	private Context context;
	private MusicDirectory.Entry entry;

	public AlbumView(Context context)
	{
		super(context);
		this.context = context;

		LayoutInflater.from(context).inflate(R.layout.album_list_item, this, true);

		String theme = Util.getTheme(context);
		boolean themesMatch = theme.equals(AlbumView.theme);
		AlbumView.theme = theme;

		if (starHollowDrawable == null || !themesMatch)
		{
			starHollowDrawable = Util.getDrawableFromAttribute(context, R.attr.star_hollow);
		}

		if (starDrawable == null || !themesMatch)
		{
			starDrawable = Util.getDrawableFromAttribute(context, R.attr.star_full);
		}

		titleView = (TextView) findViewById(R.id.album_title);
		artistView = (TextView) findViewById(R.id.album_artist);
		coverArtView = findViewById(R.id.album_coverart);
		starImageView = (ImageView) findViewById(R.id.album_star);
	}

	public MusicDirectory.Entry getEntry()
	{
		return this.entry;
	}

	public void setAlbum(final MusicDirectory.Entry album, ImageLoader imageLoader)
	{
		this.entry = album;

		String title = album.getTitle();
		String artist = album.getArtist();
		boolean starred = album.getStarred();

		titleView.setText(title);
		artistView.setText(artist);
		artistView.setVisibility(artist == null ? View.GONE : View.VISIBLE);
		starImageView.setImageDrawable(starred ? starDrawable : starHollowDrawable);
		imageLoader.loadImage(this.coverArtView, album, false, 0, false, true);

		if (Util.isOffline(this.context))
		{
			starImageView.setVisibility(View.GONE);
		}
		else
		{
			starImageView.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					final boolean isStarred = album.getStarred();
					final String id = album.getId();

					if (!isStarred)
					{
						starImageView.setImageDrawable(starDrawable);
						album.setStarred(true);
					}
					else
					{
						starImageView.setImageDrawable(starHollowDrawable);
						album.setStarred(false);
					}

					new Thread(new Runnable()
					{
						@Override
						public void run()
						{
							MusicService musicService = MusicServiceFactory.getMusicService(null);
							boolean useId3 = Util.getShouldUseId3Tags(getContext());

							try
							{
								if (!isStarred)
								{
									musicService.star(!useId3 ? id : null, useId3 ? id : null, null, getContext(), null);
								}
								else
								{
									musicService.unstar(!useId3 ? id : null, useId3 ? id : null, null, getContext(), null);
								}
							}
							catch (Exception e)
							{
								Log.e(TAG, e.getMessage(), e);
							}
						}
					}).start();
				}
			});
		}
	}
}