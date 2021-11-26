package org.moire.ultrasonic.adapters

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.featureflags.Feature
import org.moire.ultrasonic.featureflags.FeatureStorage
import org.moire.ultrasonic.service.DownloadFile
import org.moire.ultrasonic.service.DownloadStatus
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Used to display songs and videos in a `ListView`.
 * FIXME: Add video List item
 * FIXME: CHECKED bug
 */
class TrackViewHolder(val view: View) : RecyclerView.ViewHolder(view), Checkable, KoinComponent {

    var check: CheckedTextView = view.findViewById(R.id.song_check)
    var rating: LinearLayout = view.findViewById(R.id.song_rating)
    private var fiveStar1: ImageView = view.findViewById(R.id.song_five_star_1)
    private var fiveStar2: ImageView = view.findViewById(R.id.song_five_star_2)
    private var fiveStar3: ImageView = view.findViewById(R.id.song_five_star_3)
    private var fiveStar4: ImageView = view.findViewById(R.id.song_five_star_4)
    private var fiveStar5: ImageView = view.findViewById(R.id.song_five_star_5)
    var star: ImageView = view.findViewById(R.id.song_star)
    var drag: ImageView = view.findViewById(R.id.song_drag)
    var track: TextView = view.findViewById(R.id.song_track)
    var title: TextView = view.findViewById(R.id.song_title)
    var artist: TextView = view.findViewById(R.id.song_artist)
    var duration: TextView = view.findViewById(R.id.song_duration)
    var progress: TextView = view.findViewById(R.id.song_status)

    var entry: MusicDirectory.Entry? = null
        private set
    var downloadFile: DownloadFile? = null
        private set

    private var isMaximized = false
    private var cachedStatus = DownloadStatus.UNKNOWN
    private var statusImage: Drawable? = null
    private var isPlayingCached = false

    var observableChecked = MutableLiveData(false)

    private val useFiveStarRating: Boolean by lazy {
        val features: FeatureStorage = get()
        features.isFeatureEnabled(Feature.FIVE_STAR_RATING)
    }

    lateinit var imageHelper: Utils.ImageHelper

    fun setSong(
        file: DownloadFile,
        checkable: Boolean,
        draggable: Boolean,
        isSelected: Boolean = false
    ) {
        val song = file.song
        downloadFile = file
        entry = song

        val entryDescription = Util.readableEntryDescription(song)

        artist.text = entryDescription.artist
        title.text = entryDescription.title
        duration.text = entryDescription.duration

        if (Settings.shouldShowTrackNumber && song.track != null && song.track!! > 0) {
            track.text = entryDescription.trackNumber
        } else {
            track.isVisible = false
        }

        check.isVisible = (checkable && !song.isVideo)
        setCheckedSilent(isSelected)
        drag.isVisible = draggable

        if (ActiveServerProvider.isOffline()) {
            star.isVisible = false
            rating.isVisible = false
        } else {
            setupStarButtons(song)
        }

        updateProgress(downloadFile!!.progress.value!!)
        updateStatus(downloadFile!!.status.value!!)

        if (useFiveStarRating) {
            setFiveStars(entry?.userRating ?: 0)
        } else {
            setSingleStar(entry!!.starred)
        }

        RxBus.playerStateObservable.subscribe {
            setPlayIcon(it.track == downloadFile)
        }
    }

    private fun setPlayIcon(isPlaying: Boolean) {
        if (isPlaying && !isPlayingCached) {
            isPlayingCached = true
            title.setCompoundDrawablesWithIntrinsicBounds(
                imageHelper.playingImage, null, null, null
            )
        } else if (!isPlaying && isPlayingCached) {
            isPlayingCached = false
            title.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, 0, 0
            )
        }
    }

    private fun setupStarButtons(song: MusicDirectory.Entry) {
        if (useFiveStarRating) {
            // Hide single star
            star.isVisible = false
            val rating = if (song.userRating == null) 0 else song.userRating!!
            setFiveStars(rating)
        } else {
            // Hide five stars
            rating.isVisible = false

            setSingleStar(song.starred)
            star.setOnClickListener {
                val isStarred = song.starred
                val id = song.id

                if (!isStarred) {
                    star.setImageDrawable(imageHelper.starDrawable)
                    song.starred = true
                } else {
                    star.setImageDrawable(imageHelper.starHollowDrawable)
                    song.starred = false
                }
                Thread {
                    val musicService = MusicServiceFactory.getMusicService()
                    try {
                        if (!isStarred) {
                            musicService.star(id, null, null)
                        } else {
                            musicService.unstar(id, null, null)
                        }
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }.start()
            }
        }
    }

    @Suppress("MagicNumber")
    private fun setFiveStars(rating: Int) {
        fiveStar1.setImageDrawable(
            if (rating > 0) imageHelper.starDrawable else imageHelper.starHollowDrawable
        )
        fiveStar2.setImageDrawable(
            if (rating > 1) imageHelper.starDrawable else imageHelper.starHollowDrawable
        )
        fiveStar3.setImageDrawable(
            if (rating > 2) imageHelper.starDrawable else imageHelper.starHollowDrawable
        )
        fiveStar4.setImageDrawable(
            if (rating > 3) imageHelper.starDrawable else imageHelper.starHollowDrawable
        )
        fiveStar5.setImageDrawable(
            if (rating > 4) imageHelper.starDrawable else imageHelper.starHollowDrawable
        )
    }

    private fun setSingleStar(starred: Boolean) {
        if (starred) {
            if (star.drawable !== imageHelper.starDrawable) {
                star.setImageDrawable(imageHelper.starDrawable)
            }
        } else {
            if (star.drawable !== imageHelper.starHollowDrawable) {
                star.setImageDrawable(imageHelper.starHollowDrawable)
            }
        }
    }

    fun updateStatus(status: DownloadStatus) {
        if (status == cachedStatus) return
        cachedStatus = status

        Timber.w("STATUS: %s", status)

        when (status) {
            DownloadStatus.DONE -> {
                statusImage = imageHelper.downloadedImage
                progress.text = null
            }
            DownloadStatus.PINNED -> {
                statusImage = imageHelper.pinImage
                progress.text = null
            }
            DownloadStatus.FAILED,
            DownloadStatus.ABORTED -> {
                statusImage = imageHelper.errorImage
                progress.text = null
            }
            DownloadStatus.DOWNLOADING -> {
                statusImage = imageHelper.downloadingImage
            }
            else -> {
                statusImage = null
            }
        }

        updateImages()
    }

    fun updateProgress(p: Int) {
        if (cachedStatus == DownloadStatus.DOWNLOADING) {
            progress.text = Util.formatPercentage(p)
        } else {
            progress.text = null
        }
    }

    private fun updateImages() {
        progress.setCompoundDrawablesWithIntrinsicBounds(
            null, null, statusImage, null
        )

        if (statusImage === imageHelper.downloadingImage) {
            val frameAnimation = statusImage as AnimationDrawable?
            frameAnimation?.setVisible(true, true)
            frameAnimation?.start()
        }
    }

    private fun setCheckedSilent(newStatus: Boolean) {
        check.isChecked = newStatus
    }

    override fun setChecked(newStatus: Boolean) {
        observableChecked.postValue(newStatus)
        // FIXME, check if working
        // check.isChecked = newStatus
    }

    override fun isChecked(): Boolean {
        return check.isChecked
    }

    override fun toggle() {
        isChecked = isChecked
    }

    fun maximizeOrMinimize() {
        isMaximized = !isMaximized

        title.isSingleLine = !isMaximized
        artist.isSingleLine = !isMaximized
    }
}
