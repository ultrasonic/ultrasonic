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
package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;

/**
 * Used to display albums in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class AlbumView extends LinearLayout {

	private static final String TAG = AlbumView.class.getSimpleName();
    private TextView titleView;
    private TextView artistView;
    private View coverArtView;
    private ImageView starImageView;

    public AlbumView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.album_list_item, this, true);

        titleView = (TextView) findViewById(R.id.album_title);
        artistView = (TextView) findViewById(R.id.album_artist);
        coverArtView = findViewById(R.id.album_coverart);
        starImageView = (ImageView) findViewById(R.id.album_star);
    }

    public void setAlbum(final MusicDirectory.Entry album, ImageLoader imageLoader) {
        titleView.setText(album.getTitle());
        artistView.setText(album.getArtist());
        artistView.setVisibility(album.getArtist() == null ? View.GONE : View.VISIBLE);
        starImageView.setImageDrawable(album.getStarred() ? getResources().getDrawable(R.drawable.star) : getResources().getDrawable(R.drawable.star_hollow));
        imageLoader.loadImage(coverArtView, album, false, true);
        
        starImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            	final boolean isStarred = album.getStarred();
            	final String id = album.getId();
            	
            	if (!isStarred) {
					starImageView.setImageDrawable(getResources().getDrawable(R.drawable.star));
					album.setStarred(true);
            	} else {
            		starImageView.setImageDrawable(getResources().getDrawable(R.drawable.star_hollow));
            		album.setStarred(false);
            	}
            	
            	new Thread(new Runnable() {
            	    public void run() {
                    	MusicService musicService = MusicServiceFactory.getMusicService(null);
                    	
            			try {
            				if (!isStarred) {
            					musicService.star(id, getContext(), null);
            				} else {
            					musicService.unstar(id, getContext(), null);
            				}
            			} catch (Exception e) {
            				Log.e(TAG, e.getMessage(), e);
						}
            	    }
            	  }).start();
            }
        });
    }
}
