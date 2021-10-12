package org.moire.ultrasonic.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.PermissionChecker
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.FileUtil.defaultMusicDirectory
import timber.log.Timber

/**
 * Contains static functions for Permission handling
 */
class PermissionUtil(private val applicationContext: Context) {
    private var activityContext: Context? = null

    interface PermissionRequestFinishedCallback {
        fun onPermissionRequestFinished(hasPermission: Boolean)
    }

    fun onForegroundApplicationStarted(context: Context?) {
        activityContext = context
    }

    fun onForegroundApplicationStopped() {
        activityContext = null
    }

    /**
     * This function can be used to handle file access permission failures.
     *
     * It will check if the failure is because the necessary permissions aren't available,
     * and it will request them, if necessary.
     *
     * @param callback callback function to execute after the permission request is finished
     */
    fun handlePermissionFailed(callback: PermissionRequestFinishedCallback?) {
        val currentCachePath = Settings.cacheLocation
        val defaultCachePath = defaultMusicDirectory.path

        // Ultrasonic can do nothing about this error when the Music Directory is already set to the default.
        if (currentCachePath.compareTo(defaultCachePath) == 0) return

        if (PermissionChecker.checkSelfPermission(
            applicationContext,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PermissionChecker.PERMISSION_DENIED ||
            PermissionChecker.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PermissionChecker.PERMISSION_DENIED
        ) {
            // While we request permission, the Music Directory is temporarily reset to its default location
            Settings.cacheLocation = defaultMusicDirectory.path
            // If the application is not running, we can't notify the user
            if (activityContext == null) return
            requestFailedPermission(activityContext!!, currentCachePath, callback)
        } else {
            Settings.cacheLocation = defaultMusicDirectory.path
            // If the application is not running, we can't notify the user
            if (activityContext != null) {
                Handler(Looper.getMainLooper()).post {
                    showWarning(
                        activityContext!!,
                        activityContext!!.getString(R.string.permissions_message_box_title),
                        activityContext!!.getString(R.string.permissions_access_error),
                        null
                    )
                }
            }
            callback?.onPermissionRequestFinished(false)
        }
    }

    companion object {
        /**
         * This function requests permission to access the filesystem.
         * It can be used to request the permission initially, e.g. when the user decides to
         * use a non-default folder for the cache
         * @param context context for the operation
         * @param callback callback function to execute after the permission request is finished
         */
        @JvmStatic
        fun requestInitialPermission(
            context: Context,
            callback: PermissionRequestFinishedCallback?
        ) {
            Dexter.withContext(context)
                .withPermissions(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()) {
                            Timber.i("R/W permission granted for external storage")
                            callback?.onPermissionRequestFinished(true)
                            return
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Timber.i(
                                "R/W permission is permanently denied for external storage"
                            )
                            showSettingsDialog(context)
                            callback?.onPermissionRequestFinished(false)
                            return
                        }
                        Timber.i("R/W permission is missing for external storage")
                        showWarning(
                            context,
                            context.getString(R.string.permissions_message_box_title),
                            context.getString(R.string.permissions_rationale_description_initial),
                            null
                        )
                        callback?.onPermissionRequestFinished(false)
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: List<PermissionRequest>,
                        token: PermissionToken
                    ) {
                        showWarning(
                            context,
                            context.getString(R.string.permissions_rationale_title),
                            context.getString(R.string.permissions_rationale_description_initial),
                            token
                        )
                    }
                }).withErrorListener { error ->
                    Timber.e(
                        "An error has occurred during checking permissions with Dexter: %s",
                        error.toString()
                    )
                }
                .check()
        }

        private fun requestFailedPermission(
            context: Context,
            cacheLocation: String?,
            callback: PermissionRequestFinishedCallback?
        ) {
            Dexter.withContext(context)
                .withPermissions(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()) {
                            Timber.i("Permission granted to use cache directory %s", cacheLocation)

                            if (cacheLocation != null) {
                                Settings.cacheLocation = cacheLocation
                            }
                            callback?.onPermissionRequestFinished(true)
                            return
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Timber.i(
                                "R/W permission for cache directory %s was permanently denied",
                                cacheLocation
                            )
                            showSettingsDialog(context)
                            callback?.onPermissionRequestFinished(false)
                            return
                        }
                        Timber.i(
                            "At least one permission is missing to use directory %s ",
                            cacheLocation
                        )
                        Settings.cacheLocation = defaultMusicDirectory.path
                        showWarning(
                            context, context.getString(R.string.permissions_message_box_title),
                            context.getString(R.string.permissions_permission_missing), null
                        )
                        callback?.onPermissionRequestFinished(false)
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: List<PermissionRequest>,
                        token: PermissionToken
                    ) {
                        showWarning(
                            context,
                            context.getString(R.string.permissions_rationale_title),
                            context.getString(R.string.permissions_rationale_description_failed),
                            token
                        )
                    }
                }).withErrorListener { error ->
                    Timber.e(
                        "An error has occurred during checking permissions with Dexter: %s",
                        error.toString()
                    )
                }
                .check()
        }

        private fun showSettingsDialog(ctx: Context) {

            val builder = Util.createDialog(
                context = ctx,
                android.R.drawable.ic_dialog_alert,
                ctx.getString(R.string.permissions_permanent_denial_title),
                ctx.getString(R.string.permissions_permanent_denial_description)
            )

            builder.setPositiveButton(ctx.getString(R.string.permissions_open_settings)) {
                dialog, _ ->
                dialog.cancel()
                openSettings(ctx)
            }

            builder.setNegativeButton(ctx.getString(R.string.common_cancel)) { dialog, _ ->
                Settings.cacheLocation = defaultMusicDirectory.path
                dialog.cancel()
            }

            builder.show()
        }

        private fun openSettings(context: Context) {
            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.addCategory(Intent.CATEGORY_DEFAULT)
            i.data = Uri.parse("package:" + context.packageName)
            context.startActivity(i)
        }

        private fun showWarning(
            context: Context,
            title: String,
            text: String,
            token: PermissionToken?
        ) {

            val builder = Util.createDialog(
                context = context,
                android.R.drawable.ic_dialog_alert,
                title,
                text
            )

            builder.setPositiveButton(context.getString(R.string.common_ok)) { dialog, _ ->
                dialog.cancel()
                token?.continuePermissionRequest()
            }
            builder.show()
        }
    }
}
