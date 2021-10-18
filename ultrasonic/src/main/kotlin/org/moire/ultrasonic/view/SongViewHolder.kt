//package org.moire.ultrasonic.view
//
//import android.content.Context
//import android.graphics.drawable.AnimationDrawable
//import android.graphics.drawable.Drawable
//import android.view.View
//import android.widget.Checkable
//import android.widget.CheckedTextView
//import android.widget.ImageView
//import android.widget.LinearLayout
//import android.widget.TextView
//import androidx.core.view.isVisible
//import androidx.recyclerview.widget.RecyclerView
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.get
//import org.koin.core.component.inject
//import org.moire.ultrasonic.R
//import org.moire.ultrasonic.data.ActiveServerProvider
//import org.moire.ultrasonic.domain.MusicDirectory
//import org.moire.ultrasonic.featureflags.Feature
//import org.moire.ultrasonic.featureflags.FeatureStorage
//import org.moire.ultrasonic.fragment.DownloadRowAdapter
//import org.moire.ultrasonic.service.DownloadFile
//import org.moire.ultrasonic.service.MediaPlayerController
//import org.moire.ultrasonic.service.MusicServiceFactory
//import org.moire.ultrasonic.util.Settings
//import org.moire.ultrasonic.util.Util
//import timber.log.Timber
//
///**
// * Used to display songs and videos in a `ListView`.
// * TODO: Video List item
// */
//class SongViewHolder(view: View, context: Context) : RecyclerView.ViewHolder(view), Checkable, KoinComponent {
//    var check: CheckedTextView = view.findViewById(R.id.song_check)
//    var rating: LinearLayout = view.findViewById(R.id.song_rating)
//    var fiveStar1: ImageView = view.findViewById(R.id.song_five_star_1)
//    var fiveStar2: ImageView = view.findViewById(R.id.song_five_star_2)
//    var fiveStar3: ImageView = view.findViewById(R.id.song_five_star_3)
//    var fiveStar4: ImageView = view.findViewById(R.id.song_five_star_4)
//    var fiveStar5: ImageView = view.findViewById(R.id.song_five_star_5)
//    var star: ImageView = view.findViewById(R.id.song_star)
//    var drag: ImageView = view.findViewById(R.id.song_drag)
//    var track: TextView = view.findViewById(R.id.song_track)
//    var title: TextView = view.findViewById(R.id.song_title)
//    var artist: TextView = view.findViewById(R.id.song_artist)
//    var duration: TextView = view.findViewById(R.id.song_duration)
//    var status: TextView = view.findViewById(R.id.song_status)
//
//    var entry: MusicDirectory.Entry? = null
//        private set
//    var downloadFile: DownloadFile? = null
//        private set
//
//    private var isMaximized = false
//    private var leftImage: Drawable? = null
//    private var previousLeftImageType: ImageType? = null
//    private var previousRightImageType: ImageType? = null
//    private var leftImageType: ImageType? = null
//    private var playing = false
//
//    private val features: FeatureStorage = get()
//    private val useFiveStarRating: Boolean = features.isFeatureEnabled(Feature.FIVE_STAR_RATING)
//    private val mediaPlayerController: MediaPlayerController by inject()
//
//    fun setSong(file: DownloadFile, checkable: Boolean, draggable: Boolean) {
//        val song = file.song
//        downloadFile = file
//        entry = song
//
//        val entryDescription = Util.readableEntryDescription(song)
//
//        artist.text = entryDescription.artist
//        title.text = entryDescription.title
//        duration.text = entryDescription.duration
//
//
//        if (Settings.shouldShowTrackNumber && song.track != null && song.track!! > 0) {
//            track.text = entryDescription.trackNumber
//        } else {
//            track.isVisible = false
//        }
//
//        check.isVisible = (checkable && !song.isVideo)
//        drag.isVisible = draggable
//
//        if (ActiveServerProvider.isOffline()) {
//            star.isVisible = false
//            rating.isVisible = false
//        } else {
//            setupStarButtons(song)
//        }
//        update()
//    }
//
//    private fun setupStarButtons(song: MusicDirectory.Entry) {
//        if (useFiveStarRating) {
//            star.isVisible = false
//            val rating = if (song.userRating == null) 0 else song.userRating!!
//            fiveStar1.setImageDrawable(
//                if (rating > 0) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//            )
//            fiveStar2.setImageDrawable(
//                if (rating > 1) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//            )
//            fiveStar3.setImageDrawable(
//                if (rating > 2) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//            )
//            fiveStar4.setImageDrawable(
//                if (rating > 3) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//            )
//            fiveStar5.setImageDrawable(
//                if (rating > 4) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//            )
//        } else {
//            rating.isVisible = false
//            star.setImageDrawable(
//                if (song.starred) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//            )
//
//            star.setOnClickListener {
//                val isStarred = song.starred
//                val id = song.id
//
//                if (!isStarred) {
//                    star.setImageDrawable(DownloadRowAdapter.starDrawable)
//                    song.starred = true
//                } else {
//                    star.setImageDrawable(DownloadRowAdapter.starHollowDrawable)
//                    song.starred = false
//                }
//                Thread {
//                    val musicService = MusicServiceFactory.getMusicService()
//                    try {
//                        if (!isStarred) {
//                            musicService.star(id, null, null)
//                        } else {
//                            musicService.unstar(id, null, null)
//                        }
//                    } catch (all: Exception) {
//                        Timber.e(all)
//                    }
//                }.start()
//            }
//        }
//    }
//
//
//    @Synchronized
//    // TDOD: Should be removed
//    fun update() {
//        val song = entry ?: return
//
//        updateDownloadStatus(downloadFile!!)
//
//        if (entry?.starred != true) {
//            if (star.drawable !== DownloadRowAdapter.starHollowDrawable) {
//                star.setImageDrawable(DownloadRowAdapter.starHollowDrawable)
//            }
//        } else {
//            if (star.drawable !== DownloadRowAdapter.starDrawable) {
//                star.setImageDrawable(DownloadRowAdapter.starDrawable)
//            }
//        }
//
//        val rating = entry?.userRating ?: 0
//        fiveStar1.setImageDrawable(
//            if (rating > 0) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//        )
//        fiveStar2.setImageDrawable(
//            if (rating > 1) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//        )
//        fiveStar3.setImageDrawable(
//            if (rating > 2) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//        )
//        fiveStar4.setImageDrawable(
//            if (rating > 3) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//        )
//        fiveStar5.setImageDrawable(
//            if (rating > 4) DownloadRowAdapter.starDrawable else DownloadRowAdapter.starHollowDrawable
//        )
//
//        val playing = mediaPlayerController.currentPlaying === downloadFile
//
//        if (playing) {
//            if (!this.playing) {
//                this.playing = true
//                title.setCompoundDrawablesWithIntrinsicBounds(
//                    DownloadRowAdapter.playingImage, null, null, null
//                )
//            }
//        } else {
//            if (this.playing) {
//                this.playing = false
//                title.setCompoundDrawablesWithIntrinsicBounds(
//                    0, 0, 0, 0
//                )
//            }
//        }
//    }
//
//    fun updateDownloadStatus(downloadFile: DownloadFile) {
//
//        if (downloadFile.isWorkDone) {
//            val newLeftImageType =
//                if (downloadFile.isSaved) ImageType.Pin else ImageType.Downloaded
//
//            if (leftImageType != newLeftImageType) {
//                leftImage = if (downloadFile.isSaved) {
//                    DownloadRowAdapter.pinImage
//                } else {
//                    DownloadRowAdapter.downloadedImage
//                }
//                leftImageType = newLeftImageType
//            }
//        } else {
//            leftImageType = ImageType.None
//            leftImage = null
//        }
//
//        val rightImageType: ImageType
//        val rightImage: Drawable?
//
//        if (downloadFile.isDownloading && !downloadFile.isDownloadCancelled) {
//            status.text = Util.formatPercentage(downloadFile.progress.value!!)
//
//            rightImageType = ImageType.Downloading
//            rightImage = DownloadRowAdapter.downloadingImage
//        } else {
//            rightImageType = ImageType.None
//            rightImage = null
//
//            val statusText = status.text
//            if (!statusText.isNullOrEmpty()) status.text = null
//        }
//
//        if (previousLeftImageType != leftImageType || previousRightImageType != rightImageType) {
//            previousLeftImageType = leftImageType
//            previousRightImageType = rightImageType
//
//            status.setCompoundDrawablesWithIntrinsicBounds(
//                leftImage, null, rightImage, null
//            )
//
//            if (rightImage === DownloadRowAdapter.downloadingImage) {
//                // FIXME
//                val frameAnimation = rightImage as AnimationDrawable?
//
//                frameAnimation?.setVisible(true, true)
//                frameAnimation?.start()
//            }
//        }
//    }
//
////    fun updateDownloadStatus2(
////        downloadFile: DownloadFile,
////    ) {
////
////        var image: Drawable? = null
////
////        when (downloadFile.status.value) {
////            DownloadStatus.DONE -> {
////                image = if (downloadFile.isSaved) DownloadRowAdapter.pinImage else DownloadRowAdapter.downloadedImage
////                status.text = null
////            }
////            DownloadStatus.DOWNLOADING -> {
////                status.text = Util.formatPercentage(downloadFile.progress.value!!)
////                image = DownloadRowAdapter.downloadingImage
////            }
////            else -> {
////                status.text = null
////            }
////        }
////
////        // TODO: Migrate the image animation stuff from SongView into this class
////
////        if (image != null) {
////            status.setCompoundDrawablesWithIntrinsicBounds(
////                image, null, null, null
////            )
////        }
////
////        if (image === DownloadRowAdapter.downloadingImage) {
////            // FIXME
//////            val frameAnimation = image as AnimationDrawable
//////
//////            frameAnimation.setVisible(true, true)
//////            frameAnimation.start()
////        }
////    }
//
//    override fun setChecked(newStatus: Boolean) {
//        check.isChecked = newStatus
//    }
//
//    override fun isChecked(): Boolean {
//        return check.isChecked
//    }
//
//    override fun toggle() {
//        check.toggle()
//    }
//
//    fun maximizeOrMinimize() {
//        isMaximized = !isMaximized
//
//        title.isSingleLine = !isMaximized
//        artist.isSingleLine = !isMaximized
//    }
//
//    enum class ImageType {
//        None, Pin, Downloaded, Downloading
//    }
//
//
//}