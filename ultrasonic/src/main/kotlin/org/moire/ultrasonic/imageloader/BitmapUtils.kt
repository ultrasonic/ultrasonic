package org.moire.ultrasonic.imageloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.Util
import timber.log.Timber

@Suppress("UtilityClassWithPublicConstructor")
class BitmapUtils {
    companion object {
        fun getAvatarBitmapFromDisk(
            username: String?,
            size: Int
        ): Bitmap? {
            if (username == null) return null
            val avatarFile = FileUtil.getAvatarFile(username)
            val bitmap: Bitmap? = null
            if (avatarFile != null && avatarFile.exists()) {
                return getBitmapFromDisk(avatarFile.path, size, bitmap)
            }
            return null
        }

        fun getAlbumArtBitmapFromDisk(
            entry: MusicDirectory.Entry?,
            size: Int
        ): Bitmap? {
            if (entry == null) return null
            val albumArtFile = FileUtil.getAlbumArtFile(entry)
            val bitmap: Bitmap? = null
            if (albumArtFile != null && albumArtFile.exists()) {
                return getBitmapFromDisk(albumArtFile.path, size, bitmap)
            }
            return null
        }

        fun getAlbumArtBitmapFromDisk(
            filename: String,
            size: Int?
        ): Bitmap? {
            val albumArtFile = FileUtil.getAlbumArtFile(filename)
            val bitmap: Bitmap? = null
            if (albumArtFile != null && albumArtFile.exists()) {
                return getBitmapFromDisk(albumArtFile.path, size, bitmap)
            }
            return null
        }

        @Suppress("DEPRECATION")
        fun getSampledBitmap(bytes: ByteArray, size: Int): Bitmap? {
            val opt = BitmapFactory.Options()
            if (size > 0) {
                // With this flag we only calculate the size first
                opt.inJustDecodeBounds = true

                // Decode the size
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)

                // Now set the remaining flags
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    opt.inDither = true
                    opt.inPreferQualityOverSpeed = true
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    opt.inPurgeable = true
                }

                opt.inSampleSize = Util.calculateInSampleSize(
                    opt,
                    size,
                    Util.getScaledHeight(opt.outHeight.toDouble(), opt.outWidth.toDouble(), size)
                )

                // Enable real decoding
                opt.inJustDecodeBounds = false
            }
            Timber.i("getSampledBitmap %s", size.toString())
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
        }

        @Suppress("DEPRECATION")
        private fun getBitmapFromDisk(
            path: String,
            size: Int?,
            bitmap: Bitmap?
        ): Bitmap? {
            var bitmap1 = bitmap
            val opt = BitmapFactory.Options()
            if (size != null && size > 0) {
                // With this flag we only calculate the size first
                opt.inJustDecodeBounds = true

                // Decode the size
                BitmapFactory.decodeFile(path, opt)

                // Now set the remaining flags
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    opt.inDither = true
                    opt.inPreferQualityOverSpeed = true
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    opt.inPurgeable = true
                }

                opt.inSampleSize = Util.calculateInSampleSize(
                    opt,
                    size,
                    Util.getScaledHeight(opt.outHeight.toDouble(), opt.outWidth.toDouble(), size)
                )

                // Enable real decoding
                opt.inJustDecodeBounds = false
            }
            try {
                bitmap1 = BitmapFactory.decodeFile(path, opt)
            } catch (expected: Exception) {
                Timber.e(expected, "Exception in BitmapFactory.decodeFile()")
            }
            Timber.i("getBitmapFromDisk %s", size.toString())
            return bitmap1
        }
    }
}
