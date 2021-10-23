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

 Copyright 2020 (C) Jozsef Varga
 */
package org.moire.ultrasonic.view

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.Checkable
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.featureflags.Feature
import org.moire.ultrasonic.featureflags.FeatureStorage
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.MediaPlayerController
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.EntryAdapter.SongViewHolder
import timber.log.Timber

/**
 * Used to display songs and videos in a `ListView`.
 */
class SongView(context: Context) : UpdateView(context), Checkable, KoinComponent {

    var entry: MusicDirectory.Entry? = null
        private set

    private var isMaximized = false
    private var leftImage: Drawable? = null
    private var previousLeftImageType: ImageType? = null
    private var previousRightImageType: ImageType? = null
    private var leftImageType: ImageType? = null
    private var downloadFile: DownloadFile? = null
    private var playing = false
    private var viewHolder: SongViewHolder? = null
    private val features: FeatureStorage = get()
    private val useFiveStarRating: Boolean = features.isFeatureEnabled(Feature.FIVE_STAR_RATING)
    private val mediaPlayerController: MediaPlayerController by inject()

    fun setLayout(song: MusicDirectory.Entry) {

        inflater?.inflate(
            if (song.isVideo) R.layout.video_list_item
            else R.layout.song_list_item,
            this,
            true
        )

        viewHolder = SongViewHolder()
        viewHolder!!.check = findViewById(R.id.song_check)
        viewHolder!!.rating = findViewById(R.id.song_rating)
        viewHolder!!.fiveStar1 = findViewById(R.id.song_five_star_1)
        viewHolder!!.fiveStar2 = findViewById(R.id.song_five_star_2)
        viewHolder!!.fiveStar3 = findViewById(R.id.song_five_star_3)
        viewHolder!!.fiveStar4 = findViewById(R.id.song_five_star_4)
        viewHolder!!.fiveStar5 = findViewById(R.id.song_five_star_5)
        viewHolder!!.star = findViewById(R.id.song_star)
        viewHolder!!.drag = findViewById(R.id.song_drag)
        viewHolder!!.track = findViewById(R.id.song_track)
        viewHolder!!.title = findViewById(R.id.song_title)
        viewHolder!!.artist = findViewById(R.id.song_artist)
        viewHolder!!.duration = findViewById(R.id.song_duration)
        viewHolder!!.status = findViewById(R.id.song_status)
        tag = viewHolder
    }

    fun setViewHolder(viewHolder: SongViewHolder?) {
        this.viewHolder = viewHolder
        tag = this.viewHolder
    }

    fun setSong(song: MusicDirectory.Entry, checkable: Boolean, draggable: Boolean) {
        updateBackground()

        entry = song
        downloadFile = mediaPlayerController.getDownloadFileForSong(song)

        val artist = StringBuilder(60)
        var bitRate: String? = null

        if (song.bitRate != null)
            bitRate = String.format(
                this.context.getString(R.string.song_details_kbps), song.bitRate
            )

        val fileFormat: String?
        val suffix = song.suffix
        val transcodedSuffix = song.transcodedSuffix

        fileFormat = if (
            TextUtils.isEmpty(transcodedSuffix) || transcodedSuffix == suffix || song.isVideo
        ) suffix else String.format("%s > %s", suffix, transcodedSuffix)

        val artistName = song.artist

        if (artistName != null) {
            if (Settings.shouldDisplayBitrateWithArtist) {
                artist.append(artistName).append(" (").append(
                    String.format(
                        this.context.getString(R.string.song_details_all),
                        if (bitRate == null) "" else String.format("%s ", bitRate), fileFormat
                    )
                ).append(')')
            } else {
                artist.append(artistName)
            }
        }

        val trackNumber = song.track ?: 0

        if (Settings.shouldShowTrackNumber && trackNumber != 0) {
            viewHolder?.track?.text = String.format("%02d.", trackNumber)
        } else {
            viewHolder?.track?.visibility = GONE
        }

        val title = StringBuilder(60)
        title.append(song.title)

        if (song.isVideo && Settings.shouldDisplayBitrateWithArtist) {
            title.append(" (").append(
                String.format(
                    this.context.getString(R.string.song_details_all),
                    if (bitRate == null) "" else String.format("%s ", bitRate), fileFormat
                )
            ).append(')')
        }

        viewHolder?.title?.text = title
        viewHolder?.artist?.text = artist

        val duration = song.duration
        if (duration != null) {
            viewHolder?.duration?.text = Util.formatTotalDuration(duration.toLong())
        }

        viewHolder?.check?.visibility = if (checkable && !song.isVideo) VISIBLE else GONE
        viewHolder?.drag?.visibility = if (draggable) VISIBLE else GONE

        if (isOffline()) {
            viewHolder?.star?.visibility = GONE
            viewHolder?.rating?.visibility = GONE
        } else {
            if (useFiveStarRating) {
                viewHolder?.star?.visibility = GONE
                val rating = if (song.userRating == null) 0 else song.userRating!!
                viewHolder?.fiveStar1?.setImageDrawable(
                    if (rating > 0) starDrawable else starHollowDrawable
                )
                viewHolder?.fiveStar2?.setImageDrawable(
                    if (rating > 1) starDrawable else starHollowDrawable
                )
                viewHolder?.fiveStar3?.setImageDrawable(
                    if (rating > 2) starDrawable else starHollowDrawable
                )
                viewHolder?.fiveStar4?.setImageDrawable(
                    if (rating > 3) starDrawable else starHollowDrawable
                )
                viewHolder?.fiveStar5?.setImageDrawable(
                    if (rating > 4) starDrawable else starHollowDrawable
                )
            } else {
                viewHolder?.rating?.visibility = GONE
                viewHolder?.star?.setImageDrawable(
                    if (song.starred) starDrawable else starHollowDrawable
                )

                viewHolder?.star?.setOnClickListener {
                    val isStarred = song.starred
                    val id = song.id

                    if (!isStarred) {
                        viewHolder?.star?.setImageDrawable(starDrawable)
                        song.starred = true
                    } else {
                        viewHolder?.star?.setImageDrawable(starHollowDrawable)
                        song.starred = false
                    }
                    Thread {
                        val musicService = getMusicService()
                        try {
                            if (!isStarred) {
                                musicService.star(id, null, null)
                            } else {
                                musicService.unstar(id, null, null)
                            }
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }.start()
                }
            }
        }
        update()
    }

    override fun updateBackground() {}

    @Synchronized
    public override fun update() {
        updateBackground()

        val song = entry ?: return

        downloadFile = mediaPlayerController.getDownloadFileForSong(song)

        updateDownloadStatus(downloadFile!!)

        if (entry?.starred != true) {
            if (viewHolder?.star?.drawable !== starHollowDrawable) {
                viewHolder?.star?.setImageDrawable(starHollowDrawable)
            }
        } else {
            if (viewHolder?.star?.drawable !== starDrawable) {
                viewHolder?.star?.setImageDrawable(starDrawable)
            }
        }

        val rating = entry?.userRating ?: 0
        viewHolder?.fiveStar1?.setImageDrawable(
            if (rating > 0) starDrawable else starHollowDrawable
        )
        viewHolder?.fiveStar2?.setImageDrawable(
            if (rating > 1) starDrawable else starHollowDrawable
        )
        viewHolder?.fiveStar3?.setImageDrawable(
            if (rating > 2) starDrawable else starHollowDrawable
        )
        viewHolder?.fiveStar4?.setImageDrawable(
            if (rating > 3) starDrawable else starHollowDrawable
        )
        viewHolder?.fiveStar5?.setImageDrawable(
            if (rating > 4) starDrawable else starHollowDrawable
        )

        val playing = mediaPlayerController.currentPlaying === downloadFile

        if (playing) {
            if (!this.playing) {
                this.playing = true
                viewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(
                    playingImage, null, null, null
                )
            }
        } else {
            if (this.playing) {
                this.playing = false
                viewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, 0, 0
                )
            }
        }
    }

    private fun updateDownloadStatus(downloadFile: DownloadFile) {

        if (downloadFile.isWorkDone) {
            val newLeftImageType =
                if (downloadFile.isSaved) ImageType.Pin else ImageType.Downloaded

            if (leftImageType != newLeftImageType) {
                leftImage = if (downloadFile.isSaved) pinImage else downloadedImage
                leftImageType = newLeftImageType
            }
        } else {
            leftImageType = ImageType.None
            leftImage = null
        }

        val rightImageType: ImageType
        val rightImage: Drawable?

        if (downloadFile.isDownloading && !downloadFile.isDownloadCancelled) {
            viewHolder?.status?.text = Util.formatPercentage(downloadFile.progress.value!!)

            rightImageType = ImageType.Downloading
            rightImage = downloadingImage
        } else {
            rightImageType = ImageType.None
            rightImage = null

            val statusText = viewHolder?.status?.text
            if (!statusText.isNullOrEmpty()) viewHolder?.status?.text = null
        }

        if (previousLeftImageType != leftImageType || previousRightImageType != rightImageType) {
            previousLeftImageType = leftImageType
            previousRightImageType = rightImageType

            if (viewHolder?.status != null) {
                viewHolder?.status?.setCompoundDrawablesWithIntrinsicBounds(
                    leftImage, null, rightImage, null
                )

                if (rightImage === downloadingImage) {
                    val frameAnimation = rightImage as AnimationDrawable?

                    frameAnimation!!.setVisible(true, true)
                    frameAnimation.start()
                }
            }
        }
    }

    override fun setChecked(b: Boolean) {
        viewHolder?.check?.isChecked = b
    }

    override fun isChecked(): Boolean {
        return viewHolder?.check?.isChecked ?: false
    }

    override fun toggle() {
        viewHolder?.check?.toggle()
    }

    fun maximizeOrMinimize() {
        isMaximized = !isMaximized

        viewHolder?.title?.isSingleLine = !isMaximized
        viewHolder?.artist?.isSingleLine = !isMaximized
    }

    enum class ImageType {
        None, Pin, Downloaded, Downloading
    }

    companion object {
        private var starHollowDrawable: Drawable? = null
        private var starDrawable: Drawable? = null
        var pinImage: Drawable? = null
        var downloadedImage: Drawable? = null
        var downloadingImage: Drawable? = null
        private var playingImage: Drawable? = null
        private var theme: String? = null
        private var inflater: LayoutInflater? = null
    }

    init {
        val theme = Settings.theme
        val themesMatch = theme == Companion.theme
        inflater = LayoutInflater.from(this.context)

        if (!themesMatch) Companion.theme = theme

        if (starHollowDrawable == null || !themesMatch) {
            starHollowDrawable = Util.getDrawableFromAttribute(context, R.attr.star_hollow)
        }

        if (starDrawable == null || !themesMatch) {
            starDrawable = Util.getDrawableFromAttribute(context, R.attr.star_full)
        }

        if (pinImage == null || !themesMatch) {
            pinImage = Util.getDrawableFromAttribute(context, R.attr.pin)
        }

        if (downloadedImage == null || !themesMatch) {
            downloadedImage = Util.getDrawableFromAttribute(context, R.attr.downloaded)
        }

        if (downloadingImage == null || !themesMatch) {
            downloadingImage = Util.getDrawableFromAttribute(context, R.attr.downloading)
        }

        if (playingImage == null || !themesMatch) {
            playingImage = Util.getDrawableFromAttribute(context, R.attr.media_play_small)
        }
    }
}
