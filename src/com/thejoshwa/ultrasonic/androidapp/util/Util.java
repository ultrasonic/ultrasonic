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
package com.thejoshwa.ultrasonic.androidapp.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.DownloadActivity;
import com.thejoshwa.ultrasonic.androidapp.activity.MainActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.domain.RepeatMode;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchResult;
import com.thejoshwa.ultrasonic.androidapp.domain.Version;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.receiver.MediaButtonIntentReceiver;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadFile;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import org.apache.http.HttpEntity;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class Util extends DownloadActivity {

    private static final String TAG = Util.class.getSimpleName();

    private static final DecimalFormat GIGA_BYTE_FORMAT = new DecimalFormat("0.00 GB");
    private static final DecimalFormat MEGA_BYTE_FORMAT = new DecimalFormat("0.00 MB");
    private static final DecimalFormat KILO_BYTE_FORMAT = new DecimalFormat("0 KB");

    private static DecimalFormat GIGA_BYTE_LOCALIZED_FORMAT = null;
    private static DecimalFormat MEGA_BYTE_LOCALIZED_FORMAT = null;
    private static DecimalFormat KILO_BYTE_LOCALIZED_FORMAT = null;
    private static DecimalFormat BYTE_LOCALIZED_FORMAT = null;

    public static final String EVENT_META_CHANGED = "com.thejoshwa.ultrasonic.androidapp.EVENT_META_CHANGED";
    public static final String EVENT_PLAYSTATE_CHANGED = "com.thejoshwa.ultrasonic.androidapp.EVENT_PLAYSTATE_CHANGED";
    
    public static final String CM_AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    public static final String CM_AVRCP_METADATA_CHANGED = "com.android.music.metachanged";
    
    public final static int bluetoothImagesize = 500;
    
	private static boolean hasFocus = false;
	private static boolean pauseFocus = false;
	private static boolean lowerFocus = false;

    private static final Map<Integer, Version> SERVER_REST_VERSIONS = new ConcurrentHashMap<Integer, Version>();

    // Used by hexEncode()
    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static Toast toast;
    
    public static Bitmap bluetoothBitmap;
    private static Entry currentSong;

    private Util() {
    }

    public static boolean isOffline(Context context) {
        if (context == null)
        	return false;
        else
        	return  getActiveServer(context) == 0;
    }

    public static boolean isScreenLitOnDownload(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SCREEN_LIT_ON_DOWNLOAD, false);
    }

    public static RepeatMode getRepeatMode(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return RepeatMode.valueOf(prefs.getString(Constants.PREFERENCES_KEY_REPEAT_MODE, RepeatMode.OFF.name()));
    }

    public static void setRepeatMode(Context context, RepeatMode repeatMode) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCES_KEY_REPEAT_MODE, repeatMode.name());
        editor.commit();
    }

    public static boolean isScrobblingEnabled(Context context) {
        if (isOffline(context)) {
            return false;
        }
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SCROBBLE, false);
    }
    
    public static boolean isServerScalingEnabled(Context context) {
        if (isOffline(context)) {
            return false;
        }
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SERVER_SCALING, false);
    }
    
    public static boolean isNotificationEnabled(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SHOW_NOTIFICATION, false);
    }
    
    public static boolean isNotificationAlwaysEnabled(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_ALWAYS_SHOW_NOTIFICATION, false);
    }
    
    public static boolean isLockScreenEnabled(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS, false);
    }
    
    public static boolean isStreamProxyEnabled(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_USE_STREAM_PROXY, false);
    }

    public static void setActiveServer(Context context, int instance) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, instance);
        editor.commit();
    }

    public static int getActiveServer(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
    }
    
    public static int getActiveServers(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, 3);
    }

    public static String getServerName(Context context, int instance) {
        if (instance == 0) {
            return context.getResources().getString(R.string.main_offline);
        }
        SharedPreferences prefs = getPreferences(context);
        return prefs.getString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, null);
    }
    
    public static String getUserName(Context context, int instance) {
        if (instance == 0) {
            return context.getResources().getString(R.string.main_offline);
        }
        SharedPreferences prefs = getPreferences(context);
        return prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
    }
    
    public static boolean getServerEnabled(Context context, int instance) {
        if (instance == 0) {
            return true;
        }
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + instance, true);
    }

    public static void setServerRestVersion(Context context, Version version) {
        SERVER_REST_VERSIONS.put(getActiveServer(context), version);
    }

    public static Version getServerRestVersion(Context context) {
        return SERVER_REST_VERSIONS.get(getActiveServer(context));
    }
    
    public static void removeInstanceName(Context context, int instance, int activeInstance) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        
        int newInstance = instance + 1;
        
        String server = prefs.getString(Constants.PREFERENCES_KEY_SERVER + newInstance, null);
        String serverName = prefs.getString(Constants.PREFERENCES_KEY_SERVER_NAME + newInstance, null);
        String serverUrl = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + newInstance, null);
        String userName = prefs.getString(Constants.PREFERENCES_KEY_USERNAME + newInstance, null);
        String password = prefs.getString(Constants.PREFERENCES_KEY_PASSWORD + newInstance, null);
        boolean serverEnabled = prefs.getBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + newInstance, true);
        
		editor.putString(Constants.PREFERENCES_KEY_SERVER + instance, server);
		editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, serverName);
		editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + instance, serverUrl);
		editor.putString(Constants.PREFERENCES_KEY_USERNAME + instance, userName);
		editor.putString(Constants.PREFERENCES_KEY_PASSWORD + instance, password);
		editor.putBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + instance, serverEnabled);
		
		editor.putString(Constants.PREFERENCES_KEY_SERVER + newInstance, null);
		editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + newInstance, null);
		editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + newInstance, null);
		editor.putString(Constants.PREFERENCES_KEY_USERNAME + newInstance, null);
		editor.putString(Constants.PREFERENCES_KEY_PASSWORD + newInstance, null);
		editor.putBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + newInstance, true);
		editor.commit();
		
		if (instance == activeInstance) {
			Util.setActiveServer(context, 0);
		}
		
		if (newInstance == activeInstance) {
			Util.setActiveServer(context, instance);
		}
    }

    public static void setSelectedMusicFolderId(Context context, String musicFolderId) {
        int instance = getActiveServer(context);
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + instance, musicFolderId);
        editor.commit();
    }

    public static String getSelectedMusicFolderId(Context context) {
        SharedPreferences prefs = getPreferences(context);
        int instance = getActiveServer(context);
        return prefs.getString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + instance, null);
    }

    public static String getTheme(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getString(Constants.PREFERENCES_KEY_THEME, "dark");
    }

    public static int getMaxBitrate(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return 0;
        }

        boolean wifi = networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(wifi ? Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI : Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE, "0"));
    }

    public static int getPreloadCount(Context context) {
        SharedPreferences prefs = getPreferences(context);
        int preloadCount = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_PRELOAD_COUNT, "-1"));
        return preloadCount == -1 ? Integer.MAX_VALUE : preloadCount;
    }

    public static int getCacheSizeMB(Context context) {
        SharedPreferences prefs = getPreferences(context);
        int cacheSize = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_CACHE_SIZE, "-1"));
        return cacheSize == -1 ? Integer.MAX_VALUE : cacheSize;
    }

    public static String getRestUrl(Context context, String method) {
        StringBuilder builder = new StringBuilder();

        SharedPreferences prefs = getPreferences(context);

        int instance = prefs.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
        String serverUrl = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);
        String username = prefs.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
        String password = prefs.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);

        // Slightly obfuscate password
        password = "enc:" + Util.utf8HexEncode(password);

        builder.append(serverUrl);
        if (builder.charAt(builder.length() - 1) != '/') {
            builder.append("/");
        }
        builder.append("rest/").append(method).append(".view");
        builder.append("?u=").append(username);
        builder.append("&p=").append(password);
        builder.append("&v=").append(Constants.REST_PROTOCOL_VERSION);
        builder.append("&c=").append(Constants.REST_CLIENT_ID);

        return builder.toString();
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCES_FILE_NAME, 0);
    }

    public static String getContentType(HttpEntity entity) {
        if (entity == null || entity.getContentType() == null) {
            return null;
        }
        return entity.getContentType().getValue();
    }

    public static int getRemainingTrialDays(Context context) {
        SharedPreferences prefs = getPreferences(context);
        long installTime = prefs.getLong(Constants.PREFERENCES_KEY_INSTALL_TIME, 0L);

        if (installTime == 0L) {
            installTime = System.currentTimeMillis();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(Constants.PREFERENCES_KEY_INSTALL_TIME, installTime);
            editor.commit();
        }

        long now = System.currentTimeMillis();
        long millisPerDay = 24L * 60L * 60L * 1000L;
        int daysSinceInstall = (int) ((now - installTime) / millisPerDay);
        return Math.max(0, Constants.FREE_TRIAL_DAYS - daysSinceInstall);
    }

    /**
     * Get the contents of an <code>InputStream</code> as a <code>byte[]</code>.
     * <p/>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     *
     * @param input the <code>InputStream</code> to read from
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException          if an I/O error occurs
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    public static long copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[1024 * 4];
        long count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static void atomicCopy(File from, File to) throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        File tmp = null;
        try {
            tmp = new File(to.getPath() + ".tmp");
            in = new FileInputStream(from);
            out = new FileOutputStream(tmp);
            in.getChannel().transferTo(0, from.length(), out.getChannel());
            out.close();
            if (!tmp.renameTo(to)) {
                throw new IOException("Failed to rename " + tmp + " to " + to);
            }
            Log.i(TAG, "Copied " + from + " to " + to);
        } catch (IOException x) {
            close(out);
            delete(to);
            throw x;
        } finally {
            close(in);
            close(out);
            delete(tmp);
        }
    }
    
    public static void renameFile(File from, File to) throws IOException {
		if(from.renameTo(to)) {
			Log.i(TAG, "Renamed " + from + " to " + to);
		} else {
			atomicCopy(from, to);
		}
	}    

    public static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Throwable x) {
            // Ignored
        }
    }

    public static boolean delete(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete file " + file);
                return false;
            }
            Log.i(TAG, "Deleted file " + file);
        }
        return true;
    }
    
    public static boolean recursiveDelete(File dir) {
		if (dir != null && dir.exists()) {
			for(File file: dir.listFiles()) {
				if(file.isDirectory()) {
					if(!recursiveDelete(file)) {
						return false;
					}
				} else if(file.exists()) {
					if(!file.delete()) {
						return false;
					}
				}
			}
			return dir.delete();
		}
		return false;
	}

    public static void toast(Context context, int messageId) {
        toast(context, messageId, true);
    }

    public static void toast(Context context, int messageId, boolean shortDuration) {
        toast(context, context.getString(messageId), shortDuration);
    }

    public static void toast(Context context, String message) {
        toast(context, message, true);
    }

    public static void toast(Context context, String message, boolean shortDuration) {
        if (toast == null) {
            toast = Toast.makeText(context, message, shortDuration ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
        } else {
            toast.setText(message);
            toast.setDuration(shortDuration ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
        }
        toast.show();
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     * <ul>
     * <li><code>format(918)</code> returns <em>"918 B"</em>.</li>
     * <li><code>format(98765)</code> returns <em>"96 KB"</em>.</li>
     * <li><code>format(1238476)</code> returns <em>"1.2 MB"</em>.</li>
     * </ul>
     * This method assumes that 1 KB is 1024 bytes.
     * To get a localized string, please use formatLocalizedBytes instead.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    public static synchronized String formatBytes(long byteCount) {

        // More than 1 GB?
        if (byteCount >= 1024 * 1024 * 1024) {
            NumberFormat gigaByteFormat = GIGA_BYTE_FORMAT;
            return gigaByteFormat.format((double) byteCount / (1024 * 1024 * 1024));
        }

        // More than 1 MB?
        if (byteCount >= 1024 * 1024) {
            NumberFormat megaByteFormat = MEGA_BYTE_FORMAT;
            return megaByteFormat.format((double) byteCount / (1024 * 1024));
        }

        // More than 1 KB?
        if (byteCount >= 1024) {
            NumberFormat kiloByteFormat = KILO_BYTE_FORMAT;
            return kiloByteFormat.format((double) byteCount / 1024);
        }

        return byteCount + " B";
    }

    /**
     * Converts a byte-count to a formatted string suitable for display to the user.
     * For instance:
     * <ul>
     * <li><code>format(918)</code> returns <em>"918 B"</em>.</li>
     * <li><code>format(98765)</code> returns <em>"96 KB"</em>.</li>
     * <li><code>format(1238476)</code> returns <em>"1.2 MB"</em>.</li>
     * </ul>
     * This method assumes that 1 KB is 1024 bytes.
     * This version of the method returns a localized string.
     *
     * @param byteCount The number of bytes.
     * @return The formatted string.
     */
    public static synchronized String formatLocalizedBytes(long byteCount, Context context) {

        // More than 1 GB?
        if (byteCount >= 1024 * 1024 * 1024) {
            if (GIGA_BYTE_LOCALIZED_FORMAT == null) {
                GIGA_BYTE_LOCALIZED_FORMAT = new DecimalFormat(context.getResources().getString(R.string.util_bytes_format_gigabyte));
            }

            return GIGA_BYTE_LOCALIZED_FORMAT.format((double) byteCount / (1024 * 1024 * 1024));
        }

        // More than 1 MB?
        if (byteCount >= 1024 * 1024) {
            if (MEGA_BYTE_LOCALIZED_FORMAT == null) {
                MEGA_BYTE_LOCALIZED_FORMAT = new DecimalFormat(context.getResources().getString(R.string.util_bytes_format_megabyte));
            }

            return MEGA_BYTE_LOCALIZED_FORMAT.format((double) byteCount / (1024 * 1024));
        }

        // More than 1 KB?
        if (byteCount >= 1024) {
            if (KILO_BYTE_LOCALIZED_FORMAT == null) {
                KILO_BYTE_LOCALIZED_FORMAT = new DecimalFormat(context.getResources().getString(R.string.util_bytes_format_kilobyte));
            }

            return KILO_BYTE_LOCALIZED_FORMAT.format((double) byteCount / 1024);
        }

        if (BYTE_LOCALIZED_FORMAT == null) {
            BYTE_LOCALIZED_FORMAT = new DecimalFormat(context.getResources().getString(R.string.util_bytes_format_byte));
        }

        return BYTE_LOCALIZED_FORMAT.format((double) byteCount);
    }

    public static String formatDuration(Integer seconds) {
        if (seconds == null) {
            return null;
        }

        int minutes = seconds / 60;
        int secs = seconds % 60;

        StringBuilder builder = new StringBuilder(6);
        builder.append(minutes).append(":");
        if (secs < 10) {
            builder.append("0");
        }
        builder.append(secs);
        return builder.toString();
    }

    public static boolean equals(Object object1, Object object2) {
        if (object1 == object2) {
            return true;
        }
        if (object1 == null || object2 == null) {
            return false;
        }
        return object1.equals(object2);

    }

    /**
     * Encodes the given string by using the hexadecimal representation of its UTF-8 bytes.
     *
     * @param s The string to encode.
     * @return The encoded string.
     */
    public static String utf8HexEncode(String s) {
        if (s == null) {
            return null;
        }
        byte[] utf8;
        try {
            utf8 = s.getBytes(Constants.UTF_8);
        } catch (UnsupportedEncodingException x) {
            throw new RuntimeException(x);
        }
        return hexEncode(utf8);
    }

    /**
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data Bytes to convert to hexadecimal characters.
     * @return A string containing hexadecimal characters.
     */
    public static String hexEncode(byte[] data) {
        int length = data.length;
        char[] out = new char[length << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < length; i++) {
            out[j++] = HEX_DIGITS[(0xF0 & data[i]) >>> 4];
            out[j++] = HEX_DIGITS[0x0F & data[i]];
        }
        return new String(out);
    }

    /**
     * Calculates the MD5 digest and returns the value as a 32 character hex string.
     *
     * @param s Data to digest.
     * @return MD5 digest as a hex string.
     */
    public static String md5Hex(String s) {
        if (s == null) {
            return null;
        }

        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return hexEncode(md5.digest(s.getBytes(Constants.UTF_8)));
        } catch (Exception x) {
            throw new RuntimeException(x.getMessage(), x);
        }
    }
    
    public static String getGrandparent(final String path) {
    	// Find the top level folder, assume it is the album artist
		if (path != null) {
			int slashIndex = path.indexOf("/");
			if (slashIndex != 0) {
				return path.substring(0, slashIndex);
			}
		}
		
		return null;
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean connected = networkInfo != null && networkInfo.isConnected();

        boolean wifiConnected = connected && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        boolean wifiRequired = isWifiRequiredForDownload(context);

        return connected && (!wifiRequired || wifiConnected);
    }

    public static boolean isExternalStoragePresent() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private static boolean isWifiRequiredForDownload(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_WIFI_REQUIRED_FOR_DOWNLOAD, false);
    }
    
    public static boolean shouldDisplayBitrateWithArtist(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_DISPLAY_BITRATE_WITH_ARTIST, true);
    }
    
    public static boolean shouldUseFolderForArtistName(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_USE_FOLDER_FOR_ALBUM_ARTIST, false);
    }
    
    public static boolean shouldShowTrackNumber(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SHOW_TRACK_NUMBER, false);
    }
    
    public static void info(Context context, int titleId, int messageId) {
        showDialog(context, android.R.drawable.ic_dialog_info, titleId, messageId);
    }

    private static void showDialog(Context context, int icon, int titleId, int messageId) {
        new AlertDialog.Builder(context)
                .setIcon(icon)
                .setTitle(titleId)
                .setMessage(messageId)
                .setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
    
    
    public static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException x) {
            Log.w(TAG, "Interrupted from sleep.", x);
        }
    }

    public static void startActivityWithoutTransition(Activity currentActivity, Class<? extends Activity> newActivitiy) {
        startActivityWithoutTransition(currentActivity, new Intent(currentActivity, newActivitiy));
    }

    public static void startActivityWithoutTransition(Activity currentActivity, Intent intent) {
        currentActivity.startActivity(intent);
        disablePendingTransition(currentActivity);
    }

    public static void disablePendingTransition(Activity activity) {

        // Activity.overridePendingTransition() was introduced in Android 2.0.  Use reflection to maintain
        // compatibility with 1.5.
        try {
            Method method = Activity.class.getMethod("overridePendingTransition", int.class, int.class);
            method.invoke(activity, 0, 0);
        } catch (Throwable x) {
            // Ignored
        }
    }
    
    public static Drawable getDrawableFromAttribute(Context context, int attr) {
    	int[] attrs = new int[] { attr };
    	android.content.res.TypedArray ta = context.obtainStyledAttributes(attrs);
    	Drawable drawableFromTheme = ta.getDrawable(0);
    	ta.recycle();
    	
    	return drawableFromTheme;
    }

    public static Drawable createDrawableFromBitmap(Context context, Bitmap bitmap) {
        // BitmapDrawable(Resources, Bitmap) was introduced in Android 1.6.  Use reflection to maintain
        // compatibility with 1.5.
        try {
            Constructor<BitmapDrawable> constructor = BitmapDrawable.class.getConstructor(Resources.class, Bitmap.class);
            return constructor.newInstance(context.getResources(), bitmap);
        } catch (Throwable x) {
            return new BitmapDrawable(bitmap);
        }
    }
    
    public static WifiManager.WifiLock createWifiLock(Context context, String tag) {
		WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag);
	}
    
    public static int getScaledHeight(double height, double width, int newWidth) {
    	// Try to keep correct aspect ratio of the original image, do not force a square
		double aspectRatio = height / width;
		
		// Assume the size given refers to the width of the image, so calculate the new height using
		//	the previously determined aspect ratio
		return (int) Math.round(newWidth * aspectRatio);		
    }
        
    public static int getScaledHeight(Bitmap bitmap, int width) {
    	return getScaledHeight((double)bitmap.getHeight(), (double)bitmap.getWidth(), width);
    }
    
    public static Bitmap scaleBitmap(Bitmap bitmap, int size) {
		return Bitmap.createScaledBitmap(bitmap, size, getScaledHeight(bitmap, size), true);
    }

    public static void registerMediaButtonEventReceiver(Context context) {
        if (getMediaButtonsPreference(context)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.registerMediaButtonEventReceiver(new ComponentName(context.getPackageName(), MediaButtonIntentReceiver.class.getName()));

        }
    }

    public static void unregisterMediaButtonEventReceiver(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.unregisterMediaButtonEventReceiver(new ComponentName(context.getPackageName(), MediaButtonIntentReceiver.class.getName()));
    }
    
    public static MusicDirectory getSongsFromSearchResult(SearchResult searchResult) {
    	MusicDirectory musicDirectory = new MusicDirectory();
    	
    	for (Entry entry : searchResult.getSongs()) {
    		musicDirectory.addChild(entry);	
    	}
    	
    	return musicDirectory;
    }

    /**
     * <p>Broadcasts the given song info as the new song being played.</p>
     */
    public static void broadcastNewTrackInfo(Context context, MusicDirectory.Entry song) {
        Intent intent = new Intent(EVENT_META_CHANGED);

        if (song != null) {
            intent.putExtra("title", song.getTitle());
            intent.putExtra("artist", song.getArtist());
            intent.putExtra("album", song.getAlbum());

            File albumArtFile = FileUtil.getAlbumArtFile(context, song);
            intent.putExtra("coverart", albumArtFile.getAbsolutePath());
        } else {
            intent.putExtra("title", "");
            intent.putExtra("artist", "");
            intent.putExtra("album", "");
            intent.putExtra("coverart", "");
        }

        context.sendBroadcast(intent);
    }
    
	public static void broadcastA2dpMetaDataChange(Context context,	DownloadService downloadService) {
		Entry song = null;
		Intent avrcpIntent = new Intent(CM_AVRCP_METADATA_CHANGED);
		
		if (downloadService != null) {
			DownloadFile entry = downloadService.getCurrentPlaying();
			
			if (entry != null) {
				song = entry.getSong();	
			}
		}
		
		if (downloadService == null || song == null) {
			avrcpIntent.putExtra("track", "");
			avrcpIntent.putExtra("track_name", "");
			avrcpIntent.putExtra("artist", "");
			avrcpIntent.putExtra("artist_name", "");
			avrcpIntent.putExtra("album", "");
			avrcpIntent.putExtra("album_name", "");
			avrcpIntent.putExtra("cover", (Parcelable) null);
			avrcpIntent.putExtra("coverart", (Parcelable) null);
			avrcpIntent.putExtra("ListSize", (long) 0);
			avrcpIntent.putExtra("id", (long) 0);
			avrcpIntent.putExtra("duration", (long) 0);
			avrcpIntent.putExtra("position", (long) 0);
			bluetoothBitmap = null;
		} else {
			if (song != currentSong) {
				currentSong = song;
				bluetoothBitmap = FileUtil.getAlbumArtBitmap(context, song, bluetoothImagesize, true);		
			}

			String title = song.getTitle();
			String artist = song.getArtist();
			String album = song.getAlbum();
			Integer duration = song.getDuration();
			Integer listSize = downloadService.getDownloads().size();
			Integer id = downloadService.getCurrentPlayingIndex() + 1;
			Integer playerPosition = downloadService.getPlayerPosition();
			
			avrcpIntent.putExtra("track", title);
			avrcpIntent.putExtra("track_name", title);
			avrcpIntent.putExtra("artist", artist);
			avrcpIntent.putExtra("artist_name", artist);
			avrcpIntent.putExtra("album", album);
			avrcpIntent.putExtra("album_name", album);
			avrcpIntent.putExtra("cover", (Parcelable) bluetoothBitmap);
			avrcpIntent.putExtra("coverart", (Parcelable) bluetoothBitmap);
			
			if (playerPosition != null) {
				avrcpIntent.putExtra("position", (long) playerPosition);
			}
			
			if (id != null) {
				avrcpIntent.putExtra("id", (long) id);
			}
			
			if (listSize != null) {
				avrcpIntent.putExtra("ListSize", (long) listSize);
			}
			
			if (duration != null) {
				avrcpIntent.putExtra("duration", (long) duration);
			}
		}
		
		context.sendBroadcast(avrcpIntent);
	}

	public static void broadcastA2dpPlayStatusChange(Context context, PlayerState state, DownloadService downloadService) {

		if (downloadService.getCurrentPlaying() != null) {
			Intent avrcpIntent = new Intent(CM_AVRCP_PLAYSTATE_CHANGED);

			Entry song = downloadService.getCurrentPlaying().getSong();
			
			if (song == null) {
				return;
			}
			
			if (song != currentSong) {
				currentSong = song;
				bluetoothBitmap = FileUtil.getAlbumArtBitmap(context, song, bluetoothImagesize, true);
			}
			
			String title = song.getTitle();
			String artist = song.getArtist();
			String album = song.getAlbum();
			Integer duration = song.getDuration();
			Integer listSize = downloadService.getDownloads().size();
			Integer id = downloadService.getCurrentPlayingIndex() + 1;
			Integer playerPosition = downloadService.getPlayerPosition();
			
			avrcpIntent.putExtra("track", title);
			avrcpIntent.putExtra("track_name", title);
			avrcpIntent.putExtra("artist", artist);
			avrcpIntent.putExtra("artist_name", artist);
			avrcpIntent.putExtra("album", album);
			avrcpIntent.putExtra("album_name", album);
			avrcpIntent.putExtra("cover", (Parcelable) bluetoothBitmap);
			avrcpIntent.putExtra("coverart", (Parcelable) bluetoothBitmap);
			
			if (playerPosition != null) {
				avrcpIntent.putExtra("position", (long) playerPosition);
			}
			
			if (id != null) {
				avrcpIntent.putExtra("id", (long) id);
			}
			
			if (listSize != null) {
				avrcpIntent.putExtra("ListSize", (long) listSize);
			}
			
			if (duration != null) {
				avrcpIntent.putExtra("duration", (long) duration);
			}

			switch (state) {
				case STARTED:
					avrcpIntent.putExtra("playing", true);
					break;
				case STOPPED:
					avrcpIntent.putExtra("playing", false);
					break;
				case PAUSED:
					avrcpIntent.putExtra("playing", false);
					break;
				case COMPLETED:
					avrcpIntent.putExtra("playing", false);
					break;
				default:
					return; // No need to broadcast.
			}
			
			context.sendBroadcast(avrcpIntent);
		}
	}

    /**
     * <p>Broadcasts the given player state as the one being set.</p>
     */
    public static void broadcastPlaybackStatusChange(Context context, PlayerState state) {
        Intent intent = new Intent(EVENT_PLAYSTATE_CHANGED);

        switch (state) {
            case STARTED:
                intent.putExtra("state", "play");
                break;
            case STOPPED:
                intent.putExtra("state", "stop");
                break;
            case PAUSED:
                intent.putExtra("state", "pause");
                break;
            case COMPLETED:
                intent.putExtra("state", "complete");
                break;
            default:
                return; // No need to broadcast.
        }

        context.sendBroadcast(intent);
    }
    
    public static int getNotificationImageSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int imageSizeLarge = (int) Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));
		
        int size = 64;
        
        if (imageSizeLarge <= 480) {
        	size = 64;
        } else if (imageSizeLarge <= 768) {
        	size = 128;
        } else {
        	size = 256;
        }
        
        return size;
    }
    
    public static int getAlbumImageSize(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int imageSizeLarge = (int) Math.round(Math.min(metrics.widthPixels, metrics.heightPixels));
		
        int size = 128;
        
        if (imageSizeLarge <= 480) {
        	size = 128;
        } else if (imageSizeLarge <= 768) {
        	size = 256;
        } else {
        	size = 512;
        }
        
        return size;
    }
    
	public static void requestAudioFocus(final Context context) {
    	if (!hasFocus) {
    		final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			hasFocus = true;
    		audioManager.requestAudioFocus(new OnAudioFocusChangeListener() {
				public void onAudioFocusChange(int focusChange) {
					DownloadServiceImpl downloadService = (DownloadServiceImpl)context;
					if((focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) && !downloadService.isJukeboxEnabled()) {
						if(downloadService.getPlayerState() == PlayerState.STARTED) {							
							SharedPreferences prefs = getPreferences(context);
							int lossPref = Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_TEMP_LOSS, "1"));
							if(lossPref == 2 || (lossPref == 1 && focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {
								lowerFocus = true;
								downloadService.setVolume(0.1f);
							} else if(lossPref == 0 || (lossPref == 1 && focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) {
								pauseFocus = true;
								downloadService.pause();
							}
						}
					} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
						if(pauseFocus) {
							pauseFocus = false;
							downloadService.start();
						} else if(lowerFocus) {
							lowerFocus = false;
							downloadService.setVolume(1.0f);
						}
					} else if(focusChange == AudioManager.AUDIOFOCUS_LOSS && !downloadService.isJukeboxEnabled()) {
						hasFocus = false;
						downloadService.pause();
						audioManager.abandonAudioFocus(this);
					}
				}
			}, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    	}
    }
	
	public static int getMinDisplayMetric(Context context)
	{
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.min(metrics.widthPixels, metrics.heightPixels);
	}
	
	public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			// Calculate ratios of height and width to requested height and
			// width
			final int heightRatio = Math.round((float) height / (float) reqHeight);
			final int widthRatio = Math.round((float) width / (float) reqWidth);

			// Choose the smallest ratio as inSampleSize value, this will
			// guarantee
			// a final image with both dimensions larger than or equal to the
			// requested height and width.
			inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
		}

		return inSampleSize;
	}

	public static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(path, options);
	}
    
    public static void linkButtons(Context context, RemoteViews views, boolean playerActive) {

        Intent intent = new Intent(context, playerActive ? DownloadActivity.class : MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.appwidget_coverart, pendingIntent);
        views.setOnClickPendingIntent(R.id.appwidget_top, pendingIntent);
        
        // Emulate media button clicks.
        intent = new Intent("1");
        intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.control_play, pendingIntent);

        intent = new Intent("2");
        intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
        pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.control_next, pendingIntent);
        
        intent = new Intent("3");
        intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.control_previous, pendingIntent);
        
        intent = new Intent("4");
        intent.setComponent(new ComponentName(context, DownloadServiceImpl.class));
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP));
        pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.control_stop, pendingIntent);
    }
    
	public static int getMaxVideoBitrate(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return 0;
        }

        boolean wifi = networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(wifi ? Constants.PREFERENCES_KEY_MAX_VIDEO_BITRATE_WIFI : Constants.PREFERENCES_KEY_MAX_VIDEO_BITRATE_MOBILE, "0"));
    }
    
    public static int getNetworkTimeout(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT, "15000"));
    }
    
    public static int getDefaultAlbums(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS, "5"));
    }
    
    public static int getMaxAlbums(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_MAX_ALBUMS, "20"));
    }

    public static int getDefaultSongs(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_DEFAULT_SONGS, "10"));
    }
    
    public static int getMaxSongs(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_MAX_SONGS, "25"));
    }
    
    public static int getMaxArtists(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_MAX_ARTISTS, "10"));
    }
    
    public static int getDefaultArtists(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS, "3"));
    }
    
    public static int getBufferLength(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_BUFFER_LENGTH, "5"));
    }
    
    public static int getIncrementTime(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return Integer.parseInt(prefs.getString(Constants.PREFERENCES_KEY_INCREMENT_TIME, "5"));
    }
    
    public static boolean getMediaButtonsPreference(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_MEDIA_BUTTONS, true);
    }
    
    public static boolean getShowNowPlayingPreference(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_SHOW_NOW_PLAYING, true);
    }
    
    public static boolean getGaplessPlaybackPreference(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK, true);
    }
    
    public static boolean getShouldTransitionOnPlaybackPreference(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_DOWNLOAD_TRANSITION, true);
    }
    
    public static boolean getShouldUseId3Tags(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(Constants.PREFERENCES_KEY_ID3_TAGS, false);
    }
    
    public static boolean isNullOrWhiteSpace(String string) {
    	return string == null || string.isEmpty() || string.trim().isEmpty();
    }
}
