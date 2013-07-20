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
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.MusicService;
import com.thejoshwa.ultrasonic.androidapp.service.MusicServiceFactory;
import com.thejoshwa.ultrasonic.androidapp.util.Util;
import com.thejoshwa.ultrasonic.androidapp.util.VideoPlayerType;

import java.io.File;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView implements Checkable {

    private static final String TAG = SongView.class.getSimpleName();
    private static Drawable starHollowDrawable;
    private static Drawable starDrawable;
    private static Drawable unpinImage;
    private static Drawable downloadedImage;
    private static Drawable downloadingImage;
    private static Drawable playingImage;
    private static String theme;

    private CheckedTextView checkedTextView;
    private ImageView starImageView;
    private TextView titleTextView;
    private TextView statusTextView;
    private Entry song;
    private Context context;
    private Drawable leftImage;
    private ImageType previousLeftImageType;
    private ImageType previousRightImageType;
    private ImageType leftImageType;
    private ImageType rightImageType;
    private Drawable rightImage;
	private DownloadService downloadService;
    private DownloadFile downloadFile;
    private boolean playing = false;
	
	public SongView(Context context) {
        super(context);
        this.context = context;

        String theme = Util.getTheme(context);
        boolean themesMatch = theme.equals(this.theme);

        this.theme = theme;

        if (starHollowDrawable == null || !themesMatch) {
            starHollowDrawable = Util.getDrawableFromAttribute(context, R.attr.star_hollow);
        }

        if (starDrawable == null || !themesMatch) {
            starDrawable =  Util.getDrawableFromAttribute(context, R.attr.star_full);
        }

        if (unpinImage == null || !themesMatch) {
            unpinImage = Util.getDrawableFromAttribute(context, R.attr.unpin);
        }

        if (downloadedImage == null || !themesMatch) {
            downloadedImage = Util.getDrawableFromAttribute(context, R.attr.downloaded);
        }

        if (downloadingImage == null || !themesMatch) {
            downloadingImage = Util.getDrawableFromAttribute(context, R.attr.downloading);
        }

        if (playingImage == null || !themesMatch) {
            playingImage = Util.getDrawableFromAttribute(context, R.attr.media_play_small);
        }
    }

    protected void setSong(final Entry song, boolean checkable) {
        updateBackground();

        this.song = song;
        this.downloadFile = downloadService.forSong(song);

        if (song.isVideo()) {
        	LayoutInflater.from(this.context).inflate(R.layout.video_list_item, this, true);
        } else {
            LayoutInflater.from(this.context).inflate(R.layout.song_list_item, this, true);
        }

        checkedTextView = (CheckedTextView) findViewById(R.id.song_check);
        starImageView = (ImageView) findViewById(R.id.song_star);
        TextView trackTextView = (TextView) findViewById(R.id.song_track);
        titleTextView = (TextView) findViewById(R.id.song_title);
        TextView artistTextView = (TextView) findViewById(R.id.song_artist);
        TextView durationTextView = (TextView) findViewById(R.id.song_duration);
        statusTextView = (TextView) findViewById(R.id.song_status);

        StringBuilder artist = new StringBuilder(60);

        String bitRate = null;

        if (song.getBitRate() != null) {
        	bitRate = String.format(this.context.getString(R.string.song_details_kbps), song.getBitRate());
        }
        
        VideoPlayerType videoPlayer = Util.getVideoPlayerType(this.context);
        
        String fileFormat;
        String suffix = song.getSuffix();
        String transcodedSuffix = song.getTranscodedSuffix();

        if (transcodedSuffix == null || transcodedSuffix.equals(suffix) || (song.isVideo() && videoPlayer != VideoPlayerType.FLASH)) {
        	fileFormat = suffix;
        } else {
        	fileFormat = String.format("%s > %s", suffix, transcodedSuffix);
    	}
        
        String artistName = song.getArtist();

    	if (artistName != null) {
    		if (Util.shouldDisplayBitrateWithArtist(this.context)) {
    			artist.append(artistName).append(" (")
        			.append(String.format(this.context.getString(R.string.song_details_all), bitRate == null ? "" : bitRate + " ", fileFormat))
        			.append(")");
    		} else { 
    			artist.append(artistName);
    		}
    	}

        int trackNumber = song.getTrack();
        
        if (trackTextView != null) {
        	if (Util.shouldShowTrackNumber(this.context) && trackNumber != 0) {
        		trackTextView.setText(String.format("%02d.", trackNumber));	
        	} else {
        		trackTextView.setVisibility(View.GONE);
        	}
        }

        titleTextView.setText(song.getTitle());
        
        if (artistTextView != null) {
        	artistTextView.setText(artist);
        }

		Integer duration = song.getDuration();

		if (duration != null) {
        	durationTextView.setText(Util.formatTotalDuration(duration));
		}

        checkedTextView.setVisibility(checkable && !song.isVideo() ? View.VISIBLE : View.GONE);

        if (Util.isOffline(this.context)) {
        	starImageView.setVisibility(View.GONE);
        } else {
            starImageView.setImageDrawable(song.getStarred() ? starDrawable : starHollowDrawable);

            starImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final boolean isStarred = song.getStarred();
                    final String id = song.getId();

                    if (!isStarred) {
                        starImageView.setImageDrawable(starDrawable);
                        song.setStarred(true);
                    } else {
                        starImageView.setImageDrawable(starHollowDrawable);
                        song.setStarred(false);
                    }

                    new Thread(new Runnable() {
                        public void run() {
                            MusicService musicService = MusicServiceFactory.getMusicService(SongView.this.context);

                            try {
                                if (!isStarred) {
                                    musicService.star(id, null, null, SongView.this.context, null);
                                } else {
                                    musicService.unstar(id, null, null, SongView.this.context, null);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }
                    }).start();
                }
            });
        }

        update();
    }
    
	@Override
	protected void updateBackground() {
        if (downloadService == null) {
			downloadService = DownloadServiceImpl.getInstance();
			
			if (downloadService == null) {
				return;
			}
        }
	}

	@Override
    protected void update() {
        if (downloadService == null) {
            return;
        }

        downloadFile = downloadService.forSong(this.song);
        File partialFile = downloadFile.getPartialFile();

        if (downloadFile.isWorkDone()) {
            ImageType newLeftImageType = downloadFile.isSaved() ? ImageType.unpin : ImageType.downloaded;

            if (this.leftImageType != newLeftImageType) {
                this.leftImage = downloadFile.isSaved() ? unpinImage : downloadedImage;
                this.leftImageType = newLeftImageType;
            }
        } else {
            this.leftImageType = ImageType.none;
            this.leftImage = null;
        }

        if (downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && partialFile.exists()) {
            this.statusTextView.setText(Util.formatLocalizedBytes(partialFile.length(), this.context));
            this.rightImageType = ImageType.downloading;
            this.rightImage = downloadingImage;
        } else {
            this.rightImageType = ImageType.none;
            this.rightImage = null;

            if (this.statusTextView.getText() != null) {
        	    this.statusTextView.setText(null);
            }
        }

        if (this.previousLeftImageType != leftImageType || this.previousRightImageType != rightImageType) {
            this.previousLeftImageType = leftImageType;
            this.previousRightImageType = rightImageType;
            this.statusTextView.setCompoundDrawablesWithIntrinsicBounds(leftImage, null, rightImage, null);
        }

    	if (!song.getStarred()) {
            if (starImageView.getDrawable() != starHollowDrawable) {
			    starImageView.setImageDrawable(starHollowDrawable);
            }
    	} else {
            if (starImageView.getDrawable() != starDrawable) {
                starImageView.setImageDrawable(starDrawable);
            }
    	}

        boolean playing = downloadService.getCurrentPlaying() == downloadFile;

        if (playing) {
            if (!this.playing) {
                this.playing = true;
                titleTextView.setCompoundDrawablesWithIntrinsicBounds(playingImage, null, null, null);
            }
        } else {
            if (this.playing) {
                this.playing = false;
                titleTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
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

    public enum ImageType {
        none,
        unpin,
        downloaded,
        downloading
    }
}
