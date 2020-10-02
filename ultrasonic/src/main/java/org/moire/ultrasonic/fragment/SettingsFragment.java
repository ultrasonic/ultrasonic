package org.moire.ultrasonic.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.provider.SearchRecentSuggestions;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import timber.log.Timber;
import android.view.View;

import org.koin.java.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.ServerSelectorActivity;
import org.moire.ultrasonic.activity.SubsonicTabActivity;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.filepicker.FilePickerDialog;
import org.moire.ultrasonic.filepicker.OnFileSelectedListener;
import org.moire.ultrasonic.log.FileLoggerTree;
import org.moire.ultrasonic.provider.SearchSuggestionProvider;
import org.moire.ultrasonic.service.Consumer;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.util.*;

import java.io.File;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;
import static org.moire.ultrasonic.activity.ServerSelectorActivity.SERVER_SELECTOR_MANAGE_MODE;

/**
 * Shows main app settings.
 */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ListPreference theme;
    private ListPreference videoPlayer;
    private ListPreference maxBitrateWifi;
    private ListPreference maxBitrateMobile;
    private ListPreference cacheSize;
    private Preference cacheLocation;
    private ListPreference preloadCount;
    private ListPreference bufferLength;
    private ListPreference incrementTime;
    private ListPreference networkTimeout;
    private ListPreference maxAlbums;
    private ListPreference maxSongs;
    private ListPreference maxArtists;
    private ListPreference defaultAlbums;
    private ListPreference defaultSongs;
    private ListPreference defaultArtists;
    private ListPreference chatRefreshInterval;
    private ListPreference directoryCacheTime;
    private CheckBoxPreference mediaButtonsEnabled;
    private CheckBoxPreference lockScreenEnabled;
    private CheckBoxPreference sendBluetoothNotifications;
    private CheckBoxPreference sendBluetoothAlbumArt;
    private ListPreference viewRefresh;
    private ListPreference imageLoaderConcurrency;
    private EditTextPreference sharingDefaultDescription;
    private EditTextPreference sharingDefaultGreeting;
    private TimeSpanPreference sharingDefaultExpiration;
    private PreferenceCategory serversCategory;
    private Preference resumeOnBluetoothDevice;
    private Preference pauseOnBluetoothDevice;
    private CheckBoxPreference debugLogToFile;

    private SharedPreferences settings;

    private Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        theme = (ListPreference) findPreference(Constants.PREFERENCES_KEY_THEME);
        videoPlayer = (ListPreference) findPreference(Constants.PREFERENCES_KEY_VIDEO_PLAYER);
        maxBitrateWifi = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI);
        maxBitrateMobile = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE);
        cacheSize = (ListPreference) findPreference(Constants.PREFERENCES_KEY_CACHE_SIZE);
        cacheLocation = findPreference(Constants.PREFERENCES_KEY_CACHE_LOCATION);
        preloadCount = (ListPreference) findPreference(Constants.PREFERENCES_KEY_PRELOAD_COUNT);
        bufferLength = (ListPreference) findPreference(Constants.PREFERENCES_KEY_BUFFER_LENGTH);
        incrementTime = (ListPreference) findPreference(Constants.PREFERENCES_KEY_INCREMENT_TIME);
        networkTimeout = (ListPreference) findPreference(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT);
        maxAlbums = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_ALBUMS);
        maxSongs = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_SONGS);
        maxArtists = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_ARTISTS);
        defaultArtists = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS);
        defaultSongs = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SONGS);
        defaultAlbums = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS);
        chatRefreshInterval = (ListPreference) findPreference(Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL);
        directoryCacheTime = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME);
        mediaButtonsEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_MEDIA_BUTTONS);
        lockScreenEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS);
        sendBluetoothAlbumArt = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART);
        sendBluetoothNotifications = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS);
        viewRefresh = (ListPreference) findPreference(Constants.PREFERENCES_KEY_VIEW_REFRESH);
        imageLoaderConcurrency = (ListPreference) findPreference(Constants.PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY);
        sharingDefaultDescription = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION);
        sharingDefaultGreeting = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING);
        sharingDefaultExpiration = (TimeSpanPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION);
        serversCategory = (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_SERVERS_KEY);
        resumeOnBluetoothDevice = findPreference(Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE);
        pauseOnBluetoothDevice = findPreference(Constants.PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE);
        debugLogToFile = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE);

        sharingDefaultGreeting.setText(Util.getShareGreeting(getActivity()));
        setupClearSearchPreference();
        setupGaplessControlSettingsV14();
        setupFeatureFlagsPreferences();
        setupCacheLocationPreference();
        setupBluetoothDevicePreferences();

        // After API26 foreground services must be used for music playback, and they must have a notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PreferenceCategory notificationsCategory = (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_CATEGORY_NOTIFICATIONS);
            notificationsCategory.removePreference(findPreference(Constants.PREFERENCES_KEY_SHOW_NOTIFICATION));
            notificationsCategory.removePreference(findPreference(Constants.PREFERENCES_KEY_ALWAYS_SHOW_NOTIFICATION));
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        update();
    }

    @Override
    public void onResume() {
        super.onResume();

        setupServersCategory();
        SharedPreferences preferences = Util.getPreferences(getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        SharedPreferences prefs = Util.getPreferences(getActivity());
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Timber.d("Preference changed: %s", key);
        update();

        if (Constants.PREFERENCES_KEY_HIDE_MEDIA.equals(key)) {
            setHideMedia(sharedPreferences.getBoolean(key, false));
        } else if (Constants.PREFERENCES_KEY_MEDIA_BUTTONS.equals(key)) {
            setMediaButtonsEnabled(sharedPreferences.getBoolean(key, true));
        } else if (Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS.equals(key)) {
            setBluetoothPreferences(sharedPreferences.getBoolean(key, true));
        } else if (Constants.PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY.equals(key)) {
            setImageLoaderConcurrency(Integer.parseInt(sharedPreferences.getString(key, "5")));
        } else if (Constants.PREFERENCES_KEY_DEBUG_LOG_TO_FILE.equals(key)) {
            setDebugLogToFile(sharedPreferences.getBoolean(key, false));
        }
    }

    private void setupCacheLocationPreference() {
        cacheLocation.setSummary(settings.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION,
            FileUtil.getDefaultMusicDirectory(getActivity()).getPath()));

        cacheLocation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
            // If the user tries to change the cache location, we must first check to see if we have write access.
            PermissionUtil.requestInitialPermission(getActivity(), new PermissionUtil.PermissionRequestFinishedCallback() {
                    @Override
                    public void onPermissionRequestFinished(boolean hasPermission) {
                        if (hasPermission) {
                            FilePickerDialog filePickerDialog = FilePickerDialog.Companion.createFilePickerDialog(getActivity());
                            filePickerDialog.setDefaultDirectory(FileUtil.getDefaultMusicDirectory(getActivity()).getPath());
                            filePickerDialog.setInitialDirectory(cacheLocation.getSummary().toString());
                            filePickerDialog.setOnFileSelectedListener(new OnFileSelectedListener() {
                                @Override
                                public void onFileSelected(File file, String path) {
                                    SharedPreferences.Editor editor = cacheLocation.getEditor();
                                    editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, path);
                                    editor.commit();

                                    setCacheLocation(path);
                                }
                            });
                            filePickerDialog.show();
                        }
                    }
                });
            return true;
            }
        });
    }

    private void setupBluetoothDevicePreferences() {
        final int resumeSetting = Util.getResumeOnBluetoothDevice(getActivity());
        final int pauseSetting = Util.getPauseOnBluetoothDevice(getActivity());

        resumeOnBluetoothDevice.setSummary(bluetoothDevicePreferenceToString(resumeSetting));
        pauseOnBluetoothDevice.setSummary(bluetoothDevicePreferenceToString(pauseSetting));

        resumeOnBluetoothDevice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
            showBluetoothDevicePreferenceDialog(
                R.string.settings_playback_resume_on_bluetooth_device,
                Util.getResumeOnBluetoothDevice(getActivity()),
                new Consumer<Integer>() {
                    @Override
                    public void accept(Integer choice) {
                        SharedPreferences.Editor editor = resumeOnBluetoothDevice.getEditor();
                        editor.putInt(Constants.PREFERENCES_KEY_RESUME_ON_BLUETOOTH_DEVICE, choice);
                        editor.commit();
                        resumeOnBluetoothDevice.setSummary(bluetoothDevicePreferenceToString(choice));
                    }
                });
            return true;
            }
        });

        pauseOnBluetoothDevice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
            showBluetoothDevicePreferenceDialog(
                R.string.settings_playback_pause_on_bluetooth_device,
                Util.getPauseOnBluetoothDevice(getActivity()),
                new Consumer<Integer>() {
                    @Override
                    public void accept(Integer choice) {
                        SharedPreferences.Editor editor = pauseOnBluetoothDevice.getEditor();
                        editor.putInt(Constants.PREFERENCES_KEY_PAUSE_ON_BLUETOOTH_DEVICE, choice);
                        editor.commit();
                        pauseOnBluetoothDevice.setSummary(bluetoothDevicePreferenceToString(choice));
                    }
                });
            return true;
            }
        });
    }

    private void showBluetoothDevicePreferenceDialog(@StringRes int title, int defaultChoice, final Consumer<Integer> onChosen) {
        final int[] choice = {defaultChoice};
        new AlertDialog.Builder(getActivity()).setTitle(title)
            .setSingleChoiceItems(R.array.bluetoothDeviceSettingNames, defaultChoice,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        choice[0] = i;
                    }
                })
            .setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            })
            .setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    onChosen.accept(choice[0]);
                    dialogInterface.dismiss();
                }
            })
            .create().show();
    }

    private String bluetoothDevicePreferenceToString(int preferenceValue) {
        switch (preferenceValue) {
            case Constants.PREFERENCE_VALUE_ALL: return getString(R.string.settings_playback_bluetooth_all);
            case Constants.PREFERENCE_VALUE_A2DP: return getString(R.string.settings_playback_bluetooth_a2dp);
            case Constants.PREFERENCE_VALUE_DISABLED: return getString(R.string.settings_playback_bluetooth_disabled);
            default: return "";
        }
    }

    private void setupClearSearchPreference() {
        Preference clearSearchPreference = findPreference(Constants.PREFERENCES_KEY_CLEAR_SEARCH_HISTORY);

        if (clearSearchPreference != null) {
            clearSearchPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SearchRecentSuggestions suggestions =
                            new SearchRecentSuggestions(getActivity(),
                                    SearchSuggestionProvider.AUTHORITY,
                                    SearchSuggestionProvider.MODE);
                    suggestions.clearHistory();
                    Util.toast(getActivity(), R.string.settings_search_history_cleared);
                    return false;
                }
            });
        }
    }

    private void setupFeatureFlagsPreferences() {
        final FeatureStorage featureStorage = KoinJavaComponent.get(FeatureStorage.class);

        CheckBoxPreference ffImageLoader = (CheckBoxPreference) findPreference(
                Constants.PREFERENCES_KEY_FF_IMAGE_LOADER);

        if (ffImageLoader != null) {
            ffImageLoader.setChecked(featureStorage.isFeatureEnabled(Feature.NEW_IMAGE_DOWNLOADER));
            ffImageLoader.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    featureStorage.changeFeatureFlag(Feature.NEW_IMAGE_DOWNLOADER, (Boolean) o);
                    ((SubsonicTabActivity) getActivity()).clearImageLoader();
                    return true;
                }
            });
        }

        CheckBoxPreference useFiveStarRating = (CheckBoxPreference) findPreference(
                Constants.PREFERENCES_KEY_USE_FIVE_STAR_RATING);

        if (useFiveStarRating != null) {
            useFiveStarRating.setChecked(featureStorage.isFeatureEnabled(Feature.FIVE_STAR_RATING));
            useFiveStarRating.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    featureStorage.changeFeatureFlag(Feature.FIVE_STAR_RATING, (Boolean) o);
                    return true;
                }
            });
        }

    }

    private void setupGaplessControlSettingsV14() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            PreferenceCategory playbackControlSettings =
                    (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_PLAYBACK_CONTROL_SETTINGS);
            CheckBoxPreference gaplessPlaybackEnabled =
                    (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK);

            if (gaplessPlaybackEnabled != null) {
                gaplessPlaybackEnabled.setChecked(false);
                gaplessPlaybackEnabled.setEnabled(false);

                if (playbackControlSettings != null) {
                    playbackControlSettings.removePreference(gaplessPlaybackEnabled);
                }
            }
        }
    }

    private void setupServersCategory() {
        final Preference addServerPreference = new Preference(getActivity());
        addServerPreference.setPersistent(false);
        addServerPreference.setTitle(getResources().getString(R.string.settings_server_manage_servers));
        addServerPreference.setEnabled(true);

        // TODO new server management here
        serversCategory.removeAll();
        serversCategory.addPreference(addServerPreference);

        addServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent intent = new Intent(getActivity(), ServerSelectorActivity.class);
                intent.putExtra(SERVER_SELECTOR_MANAGE_MODE, true);
                startActivityForResult(intent, 0);
                return true;
            }
        });

    }

    private void update() {
        theme.setSummary(theme.getEntry());
        videoPlayer.setSummary(videoPlayer.getEntry());
        maxBitrateWifi.setSummary(maxBitrateWifi.getEntry());
        maxBitrateMobile.setSummary(maxBitrateMobile.getEntry());
        cacheSize.setSummary(cacheSize.getEntry());
        preloadCount.setSummary(preloadCount.getEntry());
        bufferLength.setSummary(bufferLength.getEntry());
        incrementTime.setSummary(incrementTime.getEntry());
        networkTimeout.setSummary(networkTimeout.getEntry());
        maxAlbums.setSummary(maxAlbums.getEntry());
        maxArtists.setSummary(maxArtists.getEntry());
        maxSongs.setSummary(maxSongs.getEntry());
        defaultAlbums.setSummary(defaultAlbums.getEntry());
        defaultArtists.setSummary(defaultArtists.getEntry());
        defaultSongs.setSummary(defaultSongs.getEntry());
        chatRefreshInterval.setSummary(chatRefreshInterval.getEntry());
        directoryCacheTime.setSummary(directoryCacheTime.getEntry());
        viewRefresh.setSummary(viewRefresh.getEntry());
        imageLoaderConcurrency.setSummary(imageLoaderConcurrency.getEntry());
        sharingDefaultExpiration.setSummary(sharingDefaultExpiration.getText());
        sharingDefaultDescription.setSummary(sharingDefaultDescription.getText());
        sharingDefaultGreeting.setSummary(sharingDefaultGreeting.getText());

        if (!mediaButtonsEnabled.isChecked()) {
            lockScreenEnabled.setChecked(false);
            lockScreenEnabled.setEnabled(false);
        }

        if (!sendBluetoothNotifications.isChecked()) {
            sendBluetoothAlbumArt.setChecked(false);
            sendBluetoothAlbumArt.setEnabled(false);
        }

        if (debugLogToFile.isChecked()) {
            debugLogToFile.setSummary(getString(R.string.settings_debug_log_path,
                FileUtil.getUltrasonicDirectory(getActivity()), FileLoggerTree.FILENAME));
        } else {
            debugLogToFile.setSummary("");
        }
    }

    private static void setImageLoaderConcurrency(int concurrency) {
        SubsonicTabActivity instance = SubsonicTabActivity.getInstance();

        if (instance != null) {
            ImageLoader imageLoader = instance.getImageLoader();

            if (imageLoader != null) {
                imageLoader.stopImageLoader();
                imageLoader.setConcurrency(concurrency);
            }
        }
    }

    private void setHideMedia(boolean hide) {
        File nomediaDir = new File(FileUtil.getUltrasonicDirectory(getActivity()), ".nomedia");
        if (hide && !nomediaDir.exists()) {
            if (!nomediaDir.mkdir()) {
                Timber.w("Failed to create %s", nomediaDir);
            }
        } else if (nomediaDir.exists()) {
            if (!nomediaDir.delete()) {
                Timber.w("Failed to delete %s", nomediaDir);
            }
        }
        Util.toast(getActivity(), R.string.settings_hide_media_toast, false);
    }

    private void setMediaButtonsEnabled(boolean enabled) {
        if (enabled) {
            lockScreenEnabled.setEnabled(true);
            Util.registerMediaButtonEventReceiver(getActivity(), false);
        } else {
            lockScreenEnabled.setEnabled(false);
            Util.unregisterMediaButtonEventReceiver(getActivity(), false);
        }
    }

    private void setBluetoothPreferences(boolean enabled) {
        if (enabled) {
            sendBluetoothAlbumArt.setEnabled(true);
        } else {
            sendBluetoothAlbumArt.setEnabled(false);
        }
    }

    private void setCacheLocation(String path) {
        File dir = new File(path);

        if (!FileUtil.ensureDirectoryExistsAndIsReadWritable(dir)) {
            PermissionUtil.handlePermissionFailed(getActivity(), new PermissionUtil.PermissionRequestFinishedCallback() {
                @Override
                public void onPermissionRequestFinished(boolean hasPermission) {
                    String currentPath = settings.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION,
                            FileUtil.getDefaultMusicDirectory(getActivity()).getPath());
                    cacheLocation.setSummary(currentPath);
                }
            });
        }
        else {
            cacheLocation.setSummary(path);
        }

        // Clear download queue.
        mediaPlayerControllerLazy.getValue().clear();
    }

    private void setDebugLogToFile(boolean writeLog) {
        if (writeLog) {
            FileLoggerTree.Companion.plantToTimberForest(getActivity().getApplicationContext());
            Timber.i("Enabled debug logging to file");
        } else {
            FileLoggerTree.Companion.uprootFromTimberForest();
            Timber.i("Disabled debug logging to file");

            int fileNum = FileLoggerTree.Companion.getLogFileNumber(getActivity());
            long fileSize = FileLoggerTree.Companion.getLogFileSizes(getActivity());
            String message = getString(R.string.settings_debug_log_summary,
                String.valueOf(fileNum),
                String.valueOf(Math.ceil(fileSize / 1000000d)),
                FileUtil.getUltrasonicDirectory(getActivity()));

            new AlertDialog.Builder(getActivity())
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setNegativeButton(R.string.settings_debug_log_keep, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setPositiveButton(R.string.settings_debug_log_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        FileLoggerTree.Companion.deleteLogFiles(getActivity());
                        Timber.i("Deleted debug log files");
                        dialogInterface.dismiss();
                        new AlertDialog.Builder(getActivity()).setMessage(R.string.settings_debug_log_deleted)
                            .setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            }).create().show();
                    }
                })
                .create().show();
        }
    }
}
