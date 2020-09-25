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
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.koin.java.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory.Entry;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.util.VideoPlayerType;

import java.io.File;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView implements Checkable
{

	private static final String TAG = SongView.class.getSimpleName();
	private static Drawable starHollowDrawable;
	private static Drawable starDrawable;
	private static Drawable pinImage;
	private static Drawable downloadedImage;
	private static Drawable downloadingImage;
	private static Drawable playingImage;
	private static String theme;
	private static LayoutInflater inflater;

	private Entry song;
	private Context context;
	private Drawable leftImage;
	private ImageType previousLeftImageType;
	private ImageType previousRightImageType;
	private ImageType leftImageType;
	private DownloadFile downloadFile;
	private boolean playing;
	private EntryAdapter.SongViewHolder viewHolder;
	private boolean maximized = false;
	private boolean useFiveStarRating;

	private Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);

	public SongView(Context context)
	{
		super(context);
		this.context = context;

		useFiveStarRating = KoinJavaComponent.get(FeatureStorage.class).isFeatureEnabled(Feature.FIVE_STAR_RATING);

		String theme = Util.getTheme(context);
		boolean themesMatch = theme.equals(SongView.theme);
		inflater = LayoutInflater.from(this.context);

		if (!themesMatch)
		{
			SongView.theme = theme;
		}

		if (starHollowDrawable == null || !themesMatch)
		{
			starHollowDrawable = Util.getDrawableFromAttribute(context, R.attr.star_hollow);
		}

		if (starDrawable == null || !themesMatch)
		{
			starDrawable = Util.getDrawableFromAttribute(context, R.attr.star_full);
		}

		if (pinImage == null || !themesMatch)
		{
			pinImage = Util.getDrawableFromAttribute(context, R.attr.pin);
		}

		if (downloadedImage == null || !themesMatch)
		{
			downloadedImage = Util.getDrawableFromAttribute(context, R.attr.downloaded);
		}

		if (downloadingImage == null || !themesMatch)
		{
			downloadingImage = Util.getDrawableFromAttribute(context, R.attr.downloading);
		}

		if (playingImage == null || !themesMatch)
		{
			playingImage = Util.getDrawableFromAttribute(context, R.attr.media_play_small);
		}
	}

	public void setLayout(final Entry song)
	{
		inflater.inflate(song.isVideo() ? R.layout.video_list_item : R.layout.song_list_item, this, true);
		viewHolder = new EntryAdapter.SongViewHolder();
		viewHolder.check = findViewById(R.id.song_check);
		viewHolder.rating = findViewById(R.id.song_rating);
		viewHolder.fiveStar1 = findViewById(R.id.song_five_star_1);
		viewHolder.fiveStar2 = findViewById(R.id.song_five_star_2);
		viewHolder.fiveStar3 = findViewById(R.id.song_five_star_3);
		viewHolder.fiveStar4 = findViewById(R.id.song_five_star_4);
		viewHolder.fiveStar5 = findViewById(R.id.song_five_star_5);
		viewHolder.star = findViewById(R.id.song_star);
		viewHolder.drag = findViewById(R.id.song_drag);
		viewHolder.track = findViewById(R.id.song_track);
		viewHolder.title = findViewById(R.id.song_title);
		viewHolder.artist = findViewById(R.id.song_artist);
		viewHolder.duration = findViewById(R.id.song_duration);
		viewHolder.status = findViewById(R.id.song_status);
		setTag(viewHolder);
	}

	public void setViewHolder(EntryAdapter.SongViewHolder viewHolder)
	{
		this.viewHolder = viewHolder;
		setTag(this.viewHolder);
	}

	public Entry getEntry()
	{
		return this.song;
	}

	protected void setSong(final Entry song, boolean checkable, boolean draggable)
	{
		updateBackground();

		this.song = song;

		this.downloadFile = mediaPlayerControllerLazy.getValue().getDownloadFileForSong(song);

		StringBuilder artist = new StringBuilder(60);

		String bitRate = null;

		if (song.getBitRate() != null)
		{
			bitRate = String.format(this.context.getString(R.string.song_details_kbps), song.getBitRate());
		}

		String fileFormat;
		String suffix = song.getSuffix();
		String transcodedSuffix = song.getTranscodedSuffix();

        fileFormat = TextUtils.isEmpty(transcodedSuffix) ||
                transcodedSuffix.equals(suffix) ||
                (song.isVideo() && Util.getVideoPlayerType(this.context) != VideoPlayerType.FLASH) ?
                suffix : String.format("%s > %s", suffix, transcodedSuffix);

		String artistName = song.getArtist();

		if (artistName != null)
		{
			if (Util.shouldDisplayBitrateWithArtist(this.context))
			{
				artist.append(artistName).append(" (").append(String.format(this.context.getString(R.string.song_details_all), bitRate == null ? "" : String.format("%s ", bitRate), fileFormat)).append(')');
			}
			else
			{
				artist.append(artistName);
			}
		}

		int trackNumber = (song.getTrack() == null) ? 0 : song.getTrack();

		if (viewHolder.track != null)
		{
			if (Util.shouldShowTrackNumber(this.context) && trackNumber != 0)
			{
				viewHolder.track.setText(String.format("%02d.", trackNumber));
			}
			else
			{
				viewHolder.track.setVisibility(View.GONE);
			}
		}

		StringBuilder title = new StringBuilder(60);
		title.append(song.getTitle());

		if (song.isVideo() && Util.shouldDisplayBitrateWithArtist(this.context))
		{
			title.append(" (").append(String.format(this.context.getString(R.string.song_details_all), bitRate == null ? "" : String.format("%s ", bitRate), fileFormat)).append(')');
		}

		viewHolder.title.setText(title);

		if (viewHolder.artist != null)
		{
			viewHolder.artist.setText(artist);
		}

		Integer duration = song.getDuration();

		if (duration != null)
		{
			viewHolder.duration.setText(Util.formatTotalDuration(duration));
		}

		if (viewHolder.check != null)
		{
			viewHolder.check.setVisibility(checkable && !song.isVideo() ? View.VISIBLE : View.GONE);
		}

		if (viewHolder.drag != null)
		{
			viewHolder.drag.setVisibility(draggable ? View.VISIBLE : View.GONE);
		}

		if (ActiveServerProvider.Companion.isOffline(this.context))
		{
			viewHolder.star.setVisibility(View.GONE);
			viewHolder.rating.setVisibility(View.GONE);
		}
		else
		{
			if (useFiveStarRating)
			{
				viewHolder.star.setVisibility(View.GONE);

				int rating = song.getUserRating() == null ? 0 : song.getUserRating();
				viewHolder.fiveStar1.setImageDrawable(rating > 0 ? starDrawable : starHollowDrawable);
				viewHolder.fiveStar2.setImageDrawable(rating > 1 ? starDrawable : starHollowDrawable);
				viewHolder.fiveStar3.setImageDrawable(rating > 2 ? starDrawable : starHollowDrawable);
				viewHolder.fiveStar4.setImageDrawable(rating > 3 ? starDrawable : starHollowDrawable);
				viewHolder.fiveStar5.setImageDrawable(rating > 4 ? starDrawable : starHollowDrawable);

			}
			else {
				viewHolder.rating.setVisibility(View.GONE);
				viewHolder.star.setImageDrawable(song.getStarred() ? starDrawable : starHollowDrawable);

				viewHolder.star.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						final boolean isStarred = song.getStarred();
						final String id = song.getId();

						if (!isStarred) {
							viewHolder.star.setImageDrawable(starDrawable);
							song.setStarred(true);
						} else {
							viewHolder.star.setImageDrawable(starHollowDrawable);
							song.setStarred(false);
						}

						new Thread(new Runnable() {
							@Override
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
		}

		update();
	}

	@Override
	protected void updateBackground()
	{
	}

	@Override
	protected void update()
	{
		updateBackground();

		downloadFile = mediaPlayerControllerLazy.getValue().getDownloadFileForSong(this.song);
		File partialFile = downloadFile.getPartialFile();

		if (downloadFile.isWorkDone())
		{
			ImageType newLeftImageType = downloadFile.isSaved() ? ImageType.pin : ImageType.downloaded;

			if (this.leftImageType != newLeftImageType)
			{
				this.leftImage = downloadFile.isSaved() ? pinImage : downloadedImage;
				this.leftImageType = newLeftImageType;
			}
		}
		else
		{
			this.leftImageType = ImageType.none;
			this.leftImage = null;
		}

		ImageType rightImageType;
		Drawable rightImage;
		if (downloadFile.isDownloading() && !downloadFile.isDownloadCancelled() && partialFile.exists())
		{
			if (this.viewHolder.status != null)
			{
				this.viewHolder.status.setText(Util.formatLocalizedBytes(partialFile.length(), this.context));
			}

			rightImageType = ImageType.downloading;
			rightImage = downloadingImage;
		}
		else
		{
			rightImageType = ImageType.none;
			rightImage = null;

			if (this.viewHolder.status != null)
			{
				CharSequence statusText = this.viewHolder.status.getText();

				if (statusText != "" || statusText != null)
				{
					this.viewHolder.status.setText(null);
				}
			}
		}

		if (this.previousLeftImageType != leftImageType || this.previousRightImageType != rightImageType)
		{
			this.previousLeftImageType = leftImageType;
			this.previousRightImageType = rightImageType;

			if (this.viewHolder.status != null)
			{
				this.viewHolder.status.setCompoundDrawablesWithIntrinsicBounds(leftImage, null, rightImage, null);

				if (rightImage == downloadingImage)
				{
					AnimationDrawable frameAnimation = (AnimationDrawable) rightImage;
					frameAnimation.setVisible(true, true);
					frameAnimation.start();
				}
			}
		}

		if (!song.getStarred())
		{
			if (viewHolder.star != null)
			{
				if (viewHolder.star.getDrawable() != starHollowDrawable)
				{
					viewHolder.star.setImageDrawable(starHollowDrawable);
				}
			}
		}
		else
		{
			if (viewHolder.star != null)
			{
				if (viewHolder.star.getDrawable() != starDrawable)
				{
					viewHolder.star.setImageDrawable(starDrawable);
				}
			}
		}

		int rating = song.getUserRating() == null ? 0 : song.getUserRating();
		viewHolder.fiveStar1.setImageDrawable(rating > 0 ? starDrawable : starHollowDrawable);
		viewHolder.fiveStar2.setImageDrawable(rating > 1 ? starDrawable : starHollowDrawable);
		viewHolder.fiveStar3.setImageDrawable(rating > 2 ? starDrawable : starHollowDrawable);
		viewHolder.fiveStar4.setImageDrawable(rating > 3 ? starDrawable : starHollowDrawable);
		viewHolder.fiveStar5.setImageDrawable(rating > 4 ? starDrawable : starHollowDrawable);

		boolean playing = mediaPlayerControllerLazy.getValue().getCurrentPlaying() == downloadFile;

		if (playing)
		{
			if (!this.playing)
			{
				this.playing = true;
				viewHolder.title.setCompoundDrawablesWithIntrinsicBounds(playingImage, null, null, null);
			}
		}
		else
		{
			if (this.playing)
			{
				this.playing = false;
				viewHolder.title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
			}
		}
	}

	@Override
	public void setChecked(boolean b)
	{
		viewHolder.check.setChecked(b);
	}

	@Override
	public boolean isChecked()
	{
		return viewHolder.check.isChecked();
	}

	@Override
	public void toggle()
	{
		viewHolder.check.toggle();
	}

	public boolean isMaximized() {
		return maximized;
	}

	public void maximizeOrMinimize() {
		if (maximized) {
			maximized = false;
		} else {
			maximized = true;
		}
		if (this.viewHolder.title != null) {
			this.viewHolder.title.setSingleLine(!maximized);
		}
		if (this.viewHolder.artist != null) {
			this.viewHolder.artist.setSingleLine(!maximized);
		}
	}

	public enum ImageType
	{
		none,
		pin,
		downloaded,
		downloading
	}
}
