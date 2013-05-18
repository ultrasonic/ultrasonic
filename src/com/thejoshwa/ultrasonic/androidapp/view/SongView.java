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
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.io.File;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView implements Checkable {

    private static final String TAG = SongView.class.getSimpleName();
    private CheckedTextView checkedTextView;
    private ImageView starImageView;
    private TextView trackTextView;
    private TextView titleTextView;
    private TextView artistTextView;
    private TextView durationTextView;
    private TextView statusTextView;
    private MusicDirectory.Entry song;
    
	private DownloadService downloadService;
	
	public SongView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.song_list_item, this, true);

        checkedTextView = (CheckedTextView) findViewById(R.id.song_check);
        starImageView = (ImageView) findViewById(R.id.song_star);
        trackTextView = (TextView) findViewById(R.id.song_track);
        titleTextView = (TextView) findViewById(R.id.song_title);
        artistTextView = (TextView) findViewById(R.id.song_artist);
        durationTextView = (TextView) findViewById(R.id.song_duration);
        statusTextView = (TextView) findViewById(R.id.song_status);
    }

    public void setSong(final MusicDirectory.Entry song, boolean checkable) {
        this.song = song;
        StringBuilder artist = new StringBuilder(60);

        String bitRate = null;
        if (song.getBitRate() != null) {
        	bitRate = String.format(getContext().getString(R.string.song_details_kbps), song.getBitRate());
        }
        
        String fileFormat = null;
        if (song.getTranscodedSuffix() != null && !song.getTranscodedSuffix().equals(song.getSuffix())) {
        	fileFormat = String.format("%s > %s", song.getSuffix(), song.getTranscodedSuffix());
    	} else {
            fileFormat = song.getSuffix();
        }

        if (Util.shouldDisplayBitrateWithArtist(getContext())) {
        	artist.append(song.getArtist()).append(" (")
        		.append(String.format(getContext().getString(R.string.song_details_all), bitRate == null ? "" : bitRate + " ", fileFormat))
        		.append(")");
        } else { 
        	artist.append(song.getArtist());
        }

        int trackNumber = song.getTrack();
        
        if (Util.shouldShowTrackNumber(getContext()) && trackNumber != 0) {
            trackTextView.setText(String.format("%02d.", trackNumber));	
        } else {
        	trackTextView.setVisibility(View.GONE);
        }

        titleTextView.setText(song.getTitle());
        artistTextView.setText(artist);
        durationTextView.setText(Util.formatDuration(song.getDuration()));
        starImageView.setImageDrawable(song.getStarred() ? Util.getDrawableFromAttribute(getContext(), R.attr.star_full) : Util.getDrawableFromAttribute(getContext(), R.attr.star_hollow));
        checkedTextView.setVisibility(checkable && !song.isVideo() ? View.VISIBLE : View.GONE);

        if (Util.isOffline(getContext())) {
        	starImageView.setVisibility(View.GONE);
        }
        
        starImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            	final boolean isStarred = song.getStarred();
            	final String id = song.getId();
            	
            	if (!isStarred) {
					starImageView.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.star_full));
					song.setStarred(true);
            	} else {
            		starImageView.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.star_hollow));
            		song.setStarred(false);
            	}
            	
            	new Thread(new Runnable() {
            	    public void run() {
                    	MusicService musicService = MusicServiceFactory.getMusicService(null);
                    	
            			try {
            				if (!isStarred) {
            					musicService.star(id, null, null, getContext(), null);
            				} else {
            					musicService.unstar(id, null, null, getContext(), null);
            				}
            			} catch (Exception e) {
							Log.e(TAG, e.getMessage(), e);
						}
            	    }
            	  }).start();
            }
        });
        
        updateBackground();
        update();
    }
    
	@Override
	protected void updateBackground() {
        if (downloadService == null) {
        	
			downloadService = DownloadServiceImpl.getInstance();
			
			if(downloadService == null) {
				return;
			}
        }
		
		downloadService.forSong(song);
	}

	@Override
    protected void update() {
        if (downloadService == null) {
            return;
        }

        DownloadFile downloadFile = downloadService.forSong(song);
        downloadFile.getCompleteFile();
        File partialFile = downloadFile.getPartialFile();

        Drawable leftImage = null;
        Drawable rightImage = null;

        if (downloadFile.isWorkDone()) {
            leftImage = downloadFile.isSaved() ? Util.getDrawableFromAttribute(getContext(), R.attr.unpin) : Util.getDrawableFromAttribute(getContext(), R.attr.downloaded);
        }

        if (downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && partialFile.exists()) {
            statusTextView.setText(Util.formatLocalizedBytes(partialFile.length(), getContext()));
            rightImage = Util.getDrawableFromAttribute(getContext(), R.attr.downloading);
        } else {
            statusTextView.setText(null);
        }

        statusTextView.setCompoundDrawablesWithIntrinsicBounds(leftImage, null, rightImage, null);
        
    	if (!song.getStarred()) {
			starImageView.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.star_hollow));
    	} else {
    		starImageView.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.star_full));
    	}

        boolean playing = downloadService.getCurrentPlaying() == downloadFile;
        if (playing) {
            titleTextView.setCompoundDrawablesWithIntrinsicBounds(Util.getDrawableFromAttribute(getContext(), R.attr.media_play_small), null, null, null);
        } else {
            titleTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    @Override
    public void setChecked(boolean b) {
        checkedTextView.setChecked(b);
    }

    @Override
    public boolean isChecked() {
        return checkedTextView.isChecked();
    }

    @Override
    public void toggle() {
        checkedTextView.toggle();
    }
}
