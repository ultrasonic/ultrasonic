package org.moire.ultrasonic.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.provider.SearchRecentSuggestions;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.View;
import org.koin.java.standalone.KoinJavaComponent;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.ServerSettingsActivity;
import org.moire.ultrasonic.activity.SubsonicTabActivity;
import org.moire.ultrasonic.featureflags.Feature;
import org.moire.ultrasonic.featureflags.FeatureStorage;
import org.moire.ultrasonic.provider.SearchSuggestionProvider;
import org.moire.ultrasonic.service.DownloadService;
import org.moire.ultrasonic.service.DownloadServiceImpl;
import org.moire.ultrasonic.util.*;

import java.io.File;

import kotlin.Lazy;

import static org.koin.java.standalone.KoinJavaComponent.inject;

/**
 * Shows main app settings.
 */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = SettingsFragment.class.getSimpleName();

    private ListPreference theme;
    private ListPreference videoPlayer;
    private ListPreference maxBitrateWifi;
    private ListPreference maxBitrateMobile;
    private ListPreference cacheSize;
    private EditTextPreference cacheLocation;
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

    private int maxServerCount = 10;
    private SharedPreferences settings;
    private int activeServers;

    private Lazy<DownloadServiceImpl> downloadServiceImpl = inject(DownloadServiceImpl.class);

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
        cacheLocation = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_CACHE_LOCATION);
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

        sharingDefaultGreeting.setText(Util.getShareGreeting(getActivity()));
        setupClearSearchPreference();
        setupGaplessControlSettingsV14();
        setupFeatureFlagsPreferences();

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
        Log.d(LOG_TAG, "Preference changed: " + key);
        update();

        if (Constants.PREFERENCES_KEY_HIDE_MEDIA.equals(key)) {
            setHideMedia(sharedPreferences.getBoolean(key, false));
        } else if (Constants.PREFERENCES_KEY_MEDIA_BUTTONS.equals(key)) {
            setMediaButtonsEnabled(sharedPreferences.getBoolean(key, true));
        } else if (Constants.PREFERENCES_KEY_CACHE_LOCATION.equals(key)) {
            setCacheLocation(sharedPreferences.getString(key, ""));
        } else if (Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS.equals(key)) {
            setBluetoothPreferences(sharedPreferences.getBoolean(key, true));
        } else if (Constants.PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY.equals(key)) {
            setImageLoaderConcurrency(Integer.parseInt(sharedPreferences.getString(key, "5")));
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
        activeServers = settings.getInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, 0);
        final Preference addServerPreference = new Preference(getActivity());
        addServerPreference.setKey(Constants.PREFERENCES_KEY_ADD_SERVER);
        addServerPreference.setPersistent(false);
        addServerPreference.setTitle(getResources().getString(R.string.settings_server_add_server));
        addServerPreference.setEnabled(activeServers < maxServerCount);

        serversCategory.removeAll();
        serversCategory.addPreference(addServerPreference);

        for (int i = 1; i <= activeServers; i++) {
            final int serverId = i;
            Preference preference = new Preference(getActivity());
            preference.setPersistent(false);
            preference.setTitle(settings.getString(Constants.PREFERENCES_KEY_SERVER_NAME + serverId,
                    getString(R.string.settings_server_name)));
            preference.setSummary(settings.getString(Constants.PREFERENCES_KEY_SERVER_URL + serverId,
                    getString(R.string.settings_server_address_unset)));
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final Intent intent = new Intent(getActivity(), ServerSettingsActivity.class);
                    intent.putExtra(ServerSettingsActivity.ARG_SERVER_ID, serverId);
                    startActivity(intent);
                    return true;
                }
            });
            serversCategory.addPreference(preference);
        }

        addServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (activeServers == maxServerCount) {
                    return false;
                }

                activeServers++;

                settings.edit()
                        .putInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, activeServers)
                        .apply();

                Preference addServerPreference = findPreference(Constants.PREFERENCES_KEY_ADD_SERVER);

                if (addServerPreference != null) {
                    serversCategory.removePreference(addServerPreference);
                }

                Preference newServerPrefs = new Preference(getActivity());
                newServerPrefs.setTitle(getString(R.string.settings_server_name));
                newServerPrefs.setSummary(getString(R.string.settings_server_address_unset));
                newServerPrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        final Intent intent = new Intent(getActivity(), ServerSettingsActivity.class);
                        intent.putExtra(ServerSettingsActivity.ARG_SERVER_ID, activeServers);
                        startActivity(intent);
                        return true;
                    }
                });
                serversCategory.addPreference(newServerPrefs);

                if (addServerPreference != null) {
                    serversCategory.addPreference(addServerPreference);
                    addServerPreference.setEnabled(activeServers < maxServerCount);
                }

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
        cacheLocation.setSummary(cacheLocation.getText());
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
        File nomediaDir = new File(FileUtil.getUltraSonicDirectory(getActivity()), ".nomedia");
        if (hide && !nomediaDir.exists()) {
            if (!nomediaDir.mkdir()) {
                Log.w(LOG_TAG, "Failed to create " + nomediaDir);
            }
        } else if (nomediaDir.exists()) {
            if (!nomediaDir.delete()) {
                Log.w(LOG_TAG, "Failed to delete " + nomediaDir);
            }
        }
        Util.toast(getActivity(), R.string.settings_hide_media_toast, false);
    }

    private void setMediaButtonsEnabled(boolean enabled) {
        if (enabled) {
            lockScreenEnabled.setEnabled(true);
            Util.registerMediaButtonEventReceiver(getActivity());
        } else {
            lockScreenEnabled.setEnabled(false);
            Util.unregisterMediaButtonEventReceiver(getActivity());
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
                public void onPermissionRequestFinished() {
                    String currentPath = settings.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION,
                            FileUtil.getDefaultMusicDirectory(getActivity()).getPath());
                    cacheLocation.setSummary(currentPath);
                    cacheLocation.setText(currentPath);
                }
            });
        }

        // Clear download queue.
        downloadServiceImpl.getValue().clear();
    }
}
