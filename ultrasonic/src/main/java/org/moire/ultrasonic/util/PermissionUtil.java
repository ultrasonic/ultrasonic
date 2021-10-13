package org.moire.ultrasonic.util;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.PermissionChecker;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.DexterError;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.PermissionRequestErrorListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import org.moire.ultrasonic.R;

import java.util.List;

import timber.log.Timber;

import static androidx.core.content.PermissionChecker.PERMISSION_DENIED;


/**
 * Contains static functions for Permission handling
 */
public class PermissionUtil {

    private Context activityContext;
    private final Context applicationContext;

    public PermissionUtil(Context context) {
        applicationContext = context;
    }

    public interface PermissionRequestFinishedCallback {
        void onPermissionRequestFinished(boolean hasPermission);
    }

    public void ForegroundApplicationStarted(Context context) {
        this.activityContext = context;
    }

    public void ForegroundApplicationStopped() {
        activityContext = null;
    }

    /**
     * This function can be used to handle file access permission failures.
     *
     * It will check if the failure is because the necessary permissions aren't available,
     * and it will request them, if necessary.
     *
     * @param callback callback function to execute after the permission request is finished
     */
    public void handlePermissionFailed(@Nullable final PermissionRequestFinishedCallback callback) {
        String currentCachePath = Settings.getPreferences().getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, FileUtil.getDefaultMusicDirectory().getPath());
        String defaultCachePath = FileUtil.getDefaultMusicDirectory().getPath();

        // Ultrasonic can do nothing about this error when the Music Directory is already set to the default.
        if (currentCachePath.compareTo(defaultCachePath) == 0) return;

        if ((PermissionChecker.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED) ||
                (PermissionChecker.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_DENIED)) {
            // While we request permission, the Music Directory is temporarily reset to its default location
            setCacheLocation(applicationContext, FileUtil.getDefaultMusicDirectory().getPath());
            // If the application is not running, we can't notify the user
            if (activityContext == null) return;
            requestFailedPermission(activityContext, currentCachePath, callback);
        } else {
            setCacheLocation(applicationContext, FileUtil.getDefaultMusicDirectory().getPath());
            // If the application is not running, we can't notify the user
            if (activityContext != null) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        showWarning(activityContext, activityContext.getString(R.string.permissions_message_box_title), activityContext.getString(R.string.permissions_access_error), null);
                    }
                });
            }
            if (callback != null) {
                callback.onPermissionRequestFinished(false);
            }
        }
    }

    /**
     * This function requests permission to access the filesystem.
     * It can be used to request the permission initially, e.g. when the user decides to use a non-default folder for the cache
     * @param context context for the operation
     * @param callback callback function to execute after the permission request is finished
     */
    public static void requestInitialPermission(final Context context, final PermissionRequestFinishedCallback callback) {
        Dexter.withContext(context)
                .withPermissions(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            Timber.i("Permission granted to read / write external storage");
                            if (callback != null) callback.onPermissionRequestFinished(true);
                            return;
                        }

                        if (report.isAnyPermissionPermanentlyDenied()) {
                            Timber.i("Found permanently denied permission to read / write external storage, offering settings");
                            showSettingsDialog(context);
                            if (callback != null) callback.onPermissionRequestFinished(false);
                            return;
                        }

                        Timber.i("At least one permission is missing to read / write external storage");
                        showWarning(context, context.getString(R.string.permissions_message_box_title),
                                context.getString(R.string.permissions_rationale_description_initial), null);
                        if (callback != null) callback.onPermissionRequestFinished(false);
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        showWarning(context, context.getString(R.string.permissions_rationale_title),
                                context.getString(R.string.permissions_rationale_description_initial), token);
                    }
                }).withErrorListener(new PermissionRequestErrorListener() {
            @Override
            public void onError(DexterError error) {
                Timber.e("An error has occurred during checking permissions with Dexter: %s", error.toString());
            }
        })
                .check();
    }

    private static void setCacheLocation(Context context, String cacheLocation) {
        Settings.getPreferences().edit()
                .putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, cacheLocation)
                .apply();
    }

    private static void requestFailedPermission(final Context context, final String cacheLocation, final PermissionRequestFinishedCallback callback) {
        Dexter.withContext(context)
                .withPermissions(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            Timber.i("Permission granted to use cache directory %s", cacheLocation);
                            setCacheLocation(context, cacheLocation);
                            if (callback != null) callback.onPermissionRequestFinished(true);
                            return;
                        }

                        if (report.isAnyPermissionPermanentlyDenied()) {
                            Timber.i("Found permanently denied permission to use cache directory %s, offering settings", cacheLocation);
                            showSettingsDialog(context);
                            if (callback != null) callback.onPermissionRequestFinished(false);
                            return;
                        }

                        Timber.i("At least one permission is missing to use directory %s ", cacheLocation);
                        setCacheLocation(context, FileUtil.getDefaultMusicDirectory().getPath());
                        showWarning(context, context.getString(R.string.permissions_message_box_title),
                                context.getString(R.string.permissions_permission_missing), null);
                        if (callback != null) callback.onPermissionRequestFinished(false);
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        showWarning(context, context.getString(R.string.permissions_rationale_title),
                                context.getString(R.string.permissions_rationale_description_failed), token);
                    }
                }).withErrorListener(new PermissionRequestErrorListener() {
            @Override
            public void onError(DexterError error) {
                Timber.e("An error has occurred during checking permissions with Dexter: %s", error.toString());
            }
        })
                .check();
    }

    private static void showSettingsDialog(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Theme_AppCompat_Dialog);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(context.getString(R.string.permissions_permanent_denial_title));
        builder.setMessage(context.getString(R.string.permissions_permanent_denial_description));

        builder.setPositiveButton(context.getString(R.string.permissions_open_settings), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                openSettings(context);
            }
        });

        builder.setNegativeButton(context.getString(R.string.common_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setCacheLocation(context, FileUtil.getDefaultMusicDirectory().getPath());
                dialog.cancel();
            }
        });

        builder.show();
    }

    private static void openSettings(Context context) {
        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        context.startActivity(i);
    }

    private static void showWarning(Context context, String title, String text, final PermissionToken token) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Theme_AppCompat_Dialog);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(context.getString(R.string.common_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                if (token != null) token.continuePermissionRequest();
            }
        });
        builder.show();
    }
}
