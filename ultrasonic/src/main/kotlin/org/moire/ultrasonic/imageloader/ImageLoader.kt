package org.moire.ultrasonic.imageloader

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.squareup.picasso.LruCache
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.moire.ultrasonic.BuildConfig
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.throwOnFailure
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber

/**
 * Our new image loader which uses Picasso as a backend.
 */
class ImageLoader(
    context: Context,
    apiClient: SubsonicAPIClient,
    private val config: ImageLoaderConfig
) {
    // Shortcut
    @Suppress("VariableNaming", "PropertyName")
    val API = apiClient.api

    private val picasso = Picasso.Builder(context)
        .addRequestHandler(CoverArtRequestHandler(apiClient))
        .addRequestHandler(AvatarRequestHandler(apiClient))
        .memoryCache(LruCache(calculateMemoryCacheSize(context)))
        .build().apply {
            setIndicatorsEnabled(BuildConfig.DEBUG)
        }

    private fun load(request: ImageRequest) = when (request) {
        is ImageRequest.CoverArt -> loadCoverArt(request)
        is ImageRequest.Avatar -> loadAvatar(request)
    }

    private fun loadCoverArt(request: ImageRequest.CoverArt) {
        picasso.load(createLoadCoverArtRequest(request.entityId, request.size.toLong()))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.cacheKey)
            .into(request.imageView)
    }

    private fun loadAvatar(request: ImageRequest.Avatar) {
        picasso.load(createLoadAvatarRequest(request.username))
            .addPlaceholder(request)
            .addError(request)
            .stableKey(request.username)
            .into(request.imageView)
    }

    private fun RequestCreator.addPlaceholder(request: ImageRequest): RequestCreator {
        if (request.placeHolderDrawableRes != null) {
            placeholder(request.placeHolderDrawableRes)
        }

        return this
    }

    private fun RequestCreator.addError(request: ImageRequest): RequestCreator {
        if (request.errorDrawableRes != null) {
            error(request.errorDrawableRes)
        }

        return this
    }

    /**
     * Load the cover of a given entry into an ImageView
     */
    @JvmOverloads
    fun loadImage(
        view: View?,
        entry: MusicDirectory.Entry?,
        large: Boolean,
        size: Int,
        defaultResourceId: Int = R.drawable.unknown_album
    ) {
        val id = entry?.coverArt
        val key = FileUtil.getAlbumArtKey(entry, large)

        loadImage(view, id, key, large, size, defaultResourceId)
    }

    /**
     * Load the cover of a given entry into an ImageView
     */
    @JvmOverloads
    @Suppress("LongParameterList", "ComplexCondition")
    fun loadImage(
        view: View?,
        id: String?,
        key: String?,
        large: Boolean,
        size: Int,
        defaultResourceId: Int = R.drawable.unknown_album
    ) {
        val requestedSize = resolveSize(size, large)

        if (id != null && key != null && id.isNotEmpty() && view is ImageView) {
            val request = ImageRequest.CoverArt(
                id, key, view, requestedSize,
                placeHolderDrawableRes = defaultResourceId,
                errorDrawableRes = defaultResourceId
            )
            load(request)
        } else if (view is ImageView) {
            view.setImageResource(defaultResourceId)
        }
    }

    /**
     * Load the avatar of a given user into an ImageView
     */
    fun loadAvatarImage(
        view: ImageView,
        username: String
    ) {
        if (username.isNotEmpty()) {
            val request = ImageRequest.Avatar(
                username, view,
                placeHolderDrawableRes = R.drawable.ic_contact_picture,
                errorDrawableRes = R.drawable.ic_contact_picture
            )
            load(request)
        } else {
            view.setImageResource(R.drawable.ic_contact_picture)
        }
    }

    /**
     * Download a cover art file and cache it on disk
     */
    fun cacheCoverArt(
        entry: MusicDirectory.Entry
    ) {

        // Synchronize on the entry so that we don't download concurrently for
        // the same song.
        synchronized(entry) {
            // Always download the large size..
            val size = config.largeSize

            // Check cache to avoid downloading existing files
            val file = FileUtil.getAlbumArtFile(entry)

            // Return if have a cache hit
            if (file.exists()) return

            // Can't load empty string ids
            val id = entry.coverArt
            if (TextUtils.isEmpty(id)) return

            // Query the API
            Timber.d("Loading cover art for: %s", entry)
            val response = API.getCoverArt(id!!, size.toLong()).execute().toStreamResponse()
            response.throwOnFailure()

            // Check for failure
            if (response.stream == null) return

            // Write Response stream to file
            var inputStream: InputStream? = null
            try {
                inputStream = response.stream
                val bytes = inputStream!!.readBytes()
                var outputStream: OutputStream? = null
                try {
                    outputStream = FileOutputStream(file)
                    outputStream.write(bytes)
                } finally {
                    Util.close(outputStream)
                }
            } finally {
                Util.close(inputStream)
            }
        }
    }

    private fun resolveSize(requested: Int, large: Boolean): Int {
        if (requested <= 0) {
            return if (large) config.largeSize else config.defaultSize
        } else {
            return requested
        }
    }

    private fun calculateMemoryCacheSize(context: Context): Int {
        val am = ContextCompat.getSystemService(
            context,
            ActivityManager::class.java
        )
        val largeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val memoryClass = if (largeHeap) am!!.largeMemoryClass else am!!.memoryClass
        // Target 25% of the available heap.
        @Suppress("MagicNumber")
        return (1024L * 1024L * memoryClass / 4).toInt()
    }
}

/**
 * Data classes to hold all the info we need later on to process the request
 */
sealed class ImageRequest(
    val placeHolderDrawableRes: Int? = null,
    val errorDrawableRes: Int? = null,
    val imageView: ImageView
) {
    class CoverArt(
        val entityId: String,
        val cacheKey: String,
        imageView: ImageView,
        val size: Int,
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null,
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )

    class Avatar(
        val username: String,
        imageView: ImageView,
        placeHolderDrawableRes: Int? = null,
        errorDrawableRes: Int? = null
    ) : ImageRequest(
        placeHolderDrawableRes,
        errorDrawableRes,
        imageView
    )
}

/**
 * Used to configure an instance of the ImageLoader
 */
data class ImageLoaderConfig(
    val largeSize: Int = 0,
    val defaultSize: Int = 0,
    val cacheFolder: File?
)
